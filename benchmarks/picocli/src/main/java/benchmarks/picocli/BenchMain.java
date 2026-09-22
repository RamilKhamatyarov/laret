package benchmarks.picocli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * picocli implementation of the concurrency benchmark contract.
 *
 * <p>Same four commands, same flags, same one-line {@code key=value} summaries
 * as every other target, so the harness invokes all five identically. See
 * {@code .github/adr/concurrency-benchmark-suite.md}.
 */
@Command(name = "bench-picocli", subcommands = BenchMain.Bench.class)
public final class BenchMain {

    private BenchMain() {
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new BenchMain()).execute(args));
    }

    @Command(
            name = "bench",
            description = "Concurrency benchmark payloads",
            subcommands = {Fanout.class, Storm.class, Pipeline.class, Cancel.class})
    static final class Bench {
    }

    /** Scenario A: fan out N units of work, join them, aggregate. */
    @Command(name = "fanout", description = "Dispatch N concurrent tasks and aggregate their results")
    static final class Fanout implements Runnable {

        @Option(names = {"-t", "--tasks"}, description = "How many tasks to dispatch")
        int tasks = 1000;

        @Option(names = {"-j", "--jobs"}, description = "Concurrency limit; 0 dispatches all at once")
        int jobs = 0;

        @Override
        public void run() {
            long[] results = new long[tasks];
            ExecutorService executor = jobs <= 0
                    ? Executors.newVirtualThreadPerTaskExecutor()
                    : Executors.newFixedThreadPool(jobs);
            try (executor) {
                List<Future<?>> pending = new ArrayList<>(tasks);
                for (int i = 0; i < tasks; i++) {
                    final int index = i;
                    pending.add(executor.submit(() -> {
                        Thread.yield();
                        results[index] = index + 1L;
                    }));
                }
                for (Future<?> future : pending) {
                    future.get();
                }
            } catch (Exception e) {
                throw new IllegalStateException("fan-out failed", e);
            }

            long sum = 0;
            for (long value : results) {
                sum += value;
            }
            System.out.printf("fanout tasks=%d jobs=%d completed=%d sum=%d%n", tasks, jobs, results.length, sum);
        }
    }

    /** Scenario B: flood a debouncer and coalesce to a single run. */
    @Command(name = "storm", description = "Fire an event storm and count the runs it coalesces into")
    static final class Storm implements Runnable {

        @Option(names = {"-e", "--events"}, description = "How many events to fire")
        int events = 10_000;

        @Option(names = {"-w", "--window"}, description = "Milliseconds to spread the events over")
        long window = 50;

        @Option(names = {"-d", "--debounce"}, description = "Debounce window in milliseconds")
        long debounce = 150;

        private static final String POISON = "";

        @Override
        public void run() {
            BlockingQueue<String> changes = new ArrayBlockingQueue<>(1024);
            AtomicLong emitted = new AtomicLong();
            AtomicInteger runs = new AtomicInteger();

            Thread producer = Thread.ofVirtual().start(() -> {
                try {
                    long started = System.nanoTime();
                    for (int index = 0; index < events; index++) {
                        changes.put("storm-" + index + ".txt");
                        emitted.incrementAndGet();
                    }
                    long spentMillis = (System.nanoTime() - started) / 1_000_000L;
                    if (spentMillis < window) {
                        Thread.sleep(window - spentMillis);
                    }
                    Thread.sleep(debounce * 2 + 100);
                    changes.put(POISON);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            try {
                boolean pending = false;
                while (true) {
                    String path = pending
                            ? changes.poll(debounce, TimeUnit.MILLISECONDS)
                            : changes.take();
                    if (path == null) {
                        runs.incrementAndGet();
                        pending = false;
                        continue;
                    }
                    if (POISON.equals(path)) {
                        if (pending) {
                            runs.incrementAndGet();
                        }
                        break;
                    }
                    if (path.endsWith(".txt")) {
                        pending = true;
                    }
                }
                producer.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            System.out.printf(
                    "storm events=%d window=%d debounce=%d runs=%d restarts=%d%n",
                    emitted.get(), window, debounce, runs.get(), runs.get());
        }
    }

    /** Scenario C: three concurrent stages over bounded queues. */
    @Command(name = "pipeline", description = "Run a three-stage streaming pipeline over N lines")
    static final class Pipeline implements Runnable {

        @Option(names = {"-l", "--lines"}, description = "How many lines stage one emits")
        int lines = 100_000;

        @Option(names = {"-p", "--pattern"}, description = "Substring stage two keeps")
        String pattern = "7";

        private static final String POISON = "\u0000end";
        private static final int CAPACITY = 256;

        @Override
        public void run() {
            BlockingQueue<String> emitted = new ArrayBlockingQueue<>(CAPACITY);
            BlockingQueue<String> kept = new ArrayBlockingQueue<>(CAPACITY);
            AtomicInteger counted = new AtomicInteger();
            CountDownLatch done = new CountDownLatch(1);

            Thread.ofVirtual().start(() -> exchange(() -> {
                for (int index = 0; index < lines; index++) {
                    emitted.put("line-" + index);
                }
                emitted.put(POISON);
            }));
            Thread.ofVirtual().start(() -> exchange(() -> {
                while (true) {
                    String line = emitted.take();
                    if (POISON.equals(line)) {
                        kept.put(POISON);
                        return;
                    }
                    if (pattern.isEmpty() || line.contains(pattern)) {
                        kept.put(line);
                    }
                }
            }));
            Thread.ofVirtual().start(() -> exchange(() -> {
                int seen = 0;
                while (true) {
                    String line = kept.take();
                    if (POISON.equals(line)) {
                        counted.set(seen);
                        done.countDown();
                        return;
                    }
                    seen++;
                }
            }));

            try {
                done.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.printf("pipeline lines=%d mode=streaming stages=3 kept=%d%n", lines, counted.get());
        }

        private interface Stage {
            void run() throws InterruptedException;
        }

        private static void exchange(Stage stage) {
            try {
                stage.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Scenario D: many workers, LIFO cleanup hooks, a signal arriving mid-flight. */
    @Command(name = "cancel", description = "Spawn N workers and wait to be cancelled")
    static final class Cancel implements Runnable {

        @Option(names = {"-w", "--workers"}, description = "How many background workers to spawn")
        int workers = 500;

        @Option(names = {"-m", "--marker"}, description = "File recording cleanup order, one index per line")
        String marker = "";

        @Option(names = {"-s", "--seconds"}, description = "Give up after this long if no signal arrives")
        long seconds = 30;

        @Override
        public void run() {
            AtomicLong ticks = new AtomicLong();
            AtomicInteger stop = new AtomicInteger();
            List<Integer> order = Collections.synchronizedList(new ArrayList<>(workers));
            CountDownLatch cleaned = new CountDownLatch(1);

            List<Thread> running = IntStream.range(0, workers)
                    .mapToObj(index -> Thread.ofVirtual().start(() -> {
                        while (stop.get() == 0) {
                            ticks.incrementAndGet();
                            Thread.yield();
                        }
                    }))
                    .toList();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                stop.set(1);
                for (Thread worker : running) {
                    worker.interrupt();
                }
                for (int index = workers - 1; index >= 0; index--) {
                    order.add(index);
                }
                if (!marker.isEmpty()) {
                    StringBuilder body = new StringBuilder();
                    synchronized (order) {
                        for (Integer index : order) {
                            body.append(index).append('\n');
                        }
                    }
                    try {
                        Files.writeString(Path.of(marker), body.toString());
                    } catch (IOException e) {
                        System.err.println("cannot write marker: " + e.getMessage());
                    }
                }
                System.err.printf("cancel ticks=%d cleaned=%d%n", ticks.get(), order.size());
                cleaned.countDown();
            }));

            System.out.printf("cancel workers=%d pid=%d ready=true%n", workers, ProcessHandle.current().pid());
            System.out.flush();

            try {
                cleaned.await(seconds, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
