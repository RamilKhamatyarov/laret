// Cobra implementation of the concurrency benchmark contract.
//
// Same four commands, same flags, same one-line key=value summaries as every
// other target, so the harness invokes all five identically. See
// .github/adr/concurrency-benchmark-suite.md.
package main

import (
	"fmt"
	"os"
	"os/signal"
	"runtime"
	"strings"
	"sync"
	"sync/atomic"
	"syscall"
	"time"

	"github.com/spf13/cobra"
)

func main() {
	root := &cobra.Command{Use: "bench-cobra", SilenceUsage: true}
	bench := &cobra.Command{Use: "bench", Short: "Concurrency benchmark payloads"}
	bench.AddCommand(fanoutCmd(), stormCmd(), pipelineCmd(), cancelCmd())
	root.AddCommand(bench)
	if err := root.Execute(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

// Scenario A: fan out N units of work, join them, aggregate.
func fanoutCmd() *cobra.Command {
	var tasks, jobs int
	cmd := &cobra.Command{
		Use:   "fanout",
		Short: "Dispatch N concurrent tasks and aggregate their results",
		RunE: func(_ *cobra.Command, _ []string) error {
			results := make([]int64, tasks)
			var wg sync.WaitGroup

			if jobs <= 0 {
				// One goroutine per task: the language's native fan-out.
				for i := 0; i < tasks; i++ {
					wg.Add(1)
					go func(index int) {
						defer wg.Done()
						runtime.Gosched()
						results[index] = int64(index) + 1
					}(i)
				}
			} else {
				// Bounded worker pool claiming tasks from a shared cursor, so
				// submission order is preserved the way Laret's dispatcher does.
				var cursor int64
				for w := 0; w < jobs; w++ {
					wg.Add(1)
					go func() {
						defer wg.Done()
						for {
							index := int(atomic.AddInt64(&cursor, 1) - 1)
							if index >= tasks {
								return
							}
							runtime.Gosched()
							results[index] = int64(index) + 1
						}
					}()
				}
			}
			wg.Wait()

			var sum int64
			for _, value := range results {
				sum += value
			}
			fmt.Printf("fanout tasks=%d jobs=%d completed=%d sum=%d\n", tasks, jobs, len(results), sum)
			return nil
		},
	}
	cmd.Flags().IntVarP(&tasks, "tasks", "t", 1000, "How many tasks to dispatch")
	cmd.Flags().IntVarP(&jobs, "jobs", "j", 0, "Concurrency limit; 0 dispatches all at once")
	return cmd
}

// Scenario B: flood a debouncer and coalesce to a single run.
func stormCmd() *cobra.Command {
	var events, window, debounce int
	cmd := &cobra.Command{
		Use:   "storm",
		Short: "Fire an event storm and count the runs it coalesces into",
		RunE: func(_ *cobra.Command, _ []string) error {
			changes := make(chan string, 1024)
			var emitted int64
			var runs int64

			go func() {
				defer close(changes)
				started := time.Now()
				for i := 0; i < events; i++ {
					changes <- fmt.Sprintf("storm-%d.txt", i)
					atomic.AddInt64(&emitted, 1)
				}
				if spent := time.Since(started); spent < time.Duration(window)*time.Millisecond {
					time.Sleep(time.Duration(window)*time.Millisecond - spent)
				}
				time.Sleep(time.Duration(debounce)*2*time.Millisecond + 100*time.Millisecond)
			}()

			// Coalesce: every event restarts the quiet timer, and only the
			// silence at the end of the burst triggers a run.
			var pending bool
			timer := time.NewTimer(time.Hour)
			if !timer.Stop() {
				<-timer.C
			}
			for open := true; open; {
				select {
				case path, more := <-changes:
					if !more {
						open = false
						break
					}
					if !strings.HasSuffix(path, ".txt") {
						continue
					}
					if pending && !timer.Stop() {
						select {
						case <-timer.C:
						default:
						}
					}
					timer.Reset(time.Duration(debounce) * time.Millisecond)
					pending = true
				case <-timer.C:
					atomic.AddInt64(&runs, 1)
					pending = false
				}
			}
			if pending {
				<-timer.C
				atomic.AddInt64(&runs, 1)
			}

			fmt.Printf("storm events=%d window=%d debounce=%d runs=%d restarts=%d\n",
				atomic.LoadInt64(&emitted), window, debounce, atomic.LoadInt64(&runs), atomic.LoadInt64(&runs))
			return nil
		},
	}
	cmd.Flags().IntVarP(&events, "events", "e", 10000, "How many events to fire")
	cmd.Flags().IntVarP(&window, "window", "w", 50, "Milliseconds to spread the events over")
	cmd.Flags().IntVarP(&debounce, "debounce", "d", 150, "Debounce window in milliseconds")
	return cmd
}

// Scenario C: three concurrent stages over bounded channels.
func pipelineCmd() *cobra.Command {
	var lines int
	var pattern string
	cmd := &cobra.Command{
		Use:   "pipeline",
		Short: "Run a three-stage streaming pipeline over N lines",
		RunE: func(_ *cobra.Command, _ []string) error {
			const capacity = 256
			emitted := make(chan string, capacity)
			kept := make(chan string, capacity)
			counted := make(chan int)

			go func() {
				defer close(emitted)
				for i := 0; i < lines; i++ {
					emitted <- fmt.Sprintf("line-%d", i)
				}
			}()
			go func() {
				defer close(kept)
				for line := range emitted {
					if pattern == "" || strings.Contains(line, pattern) {
						kept <- line
					}
				}
			}()
			go func() {
				seen := 0
				for range kept {
					seen++
				}
				counted <- seen
			}()

			fmt.Printf("pipeline lines=%d mode=streaming stages=3 kept=%d\n", lines, <-counted)
			return nil
		},
	}
	cmd.Flags().IntVarP(&lines, "lines", "l", 100000, "How many lines stage one emits")
	cmd.Flags().StringVarP(&pattern, "pattern", "p", "7", "Substring stage two keeps")
	return cmd
}

// Scenario D: many workers, LIFO cleanup hooks, a signal arriving mid-flight.
func cancelCmd() *cobra.Command {
	var workers, seconds int
	var marker string
	cmd := &cobra.Command{
		Use:   "cancel",
		Short: "Spawn N workers and wait to be cancelled",
		RunE: func(_ *cobra.Command, _ []string) error {
			done := make(chan struct{})
			var ticks int64
			var wg sync.WaitGroup

			cleanups := make([]func(), 0, workers)
			order := make([]int, 0, workers)
			var mu sync.Mutex

			for i := 0; i < workers; i++ {
				wg.Add(1)
				go func() {
					defer wg.Done()
					for {
						select {
						case <-done:
							return
						default:
							atomic.AddInt64(&ticks, 1)
							runtime.Gosched()
						}
					}
				}()
				index := i
				cleanups = append(cleanups, func() {
					mu.Lock()
					order = append(order, index)
					mu.Unlock()
				})
			}

			signals := make(chan os.Signal, 1)
			signal.Notify(signals, syscall.SIGINT, syscall.SIGTERM)

			fmt.Printf("cancel workers=%d pid=%d ready=true\n", workers, os.Getpid())
			os.Stdout.Sync()

			var received os.Signal
			select {
			case received = <-signals:
			case <-time.After(time.Duration(seconds) * time.Second):
			}

			close(done)
			wg.Wait()
			// LIFO: the last hook registered runs first.
			for i := len(cleanups) - 1; i >= 0; i-- {
				cleanups[i]()
			}
			if marker != "" {
				var builder strings.Builder
				for _, index := range order {
					fmt.Fprintf(&builder, "%d\n", index)
				}
				if err := os.WriteFile(marker, []byte(builder.String()), 0o644); err != nil {
					return err
				}
			}
			fmt.Fprintf(os.Stderr, "cancel ticks=%d cleaned=%d\n", atomic.LoadInt64(&ticks), len(order))

			// Go's runtime does not re-raise a handled signal, so the
			// 128+signal convention has to be honoured explicitly.
			if received != nil {
				if sig, ok := received.(syscall.Signal); ok {
					os.Exit(128 + int(sig))
				}
			}
			return nil
		},
	}
	cmd.Flags().IntVarP(&workers, "workers", "w", 500, "How many background workers to spawn")
	cmd.Flags().StringVarP(&marker, "marker", "m", "", "File recording cleanup order, one index per line")
	cmd.Flags().IntVarP(&seconds, "seconds", "s", 30, "Give up after this long if no signal arrives")
	return cmd
}
