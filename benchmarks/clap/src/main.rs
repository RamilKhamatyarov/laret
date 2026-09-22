//! clap implementation of the concurrency benchmark contract.
//!
//! Same four commands, same flags, same one-line `key=value` summaries as every
//! other target, so the harness invokes all five identically. See
//! `.github/adr/concurrency-benchmark-suite.md`.

use std::io::Write;
use std::sync::atomic::{AtomicI64, AtomicUsize, Ordering};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

use clap::{Parser, Subcommand};
use tokio::sync::mpsc;
use tokio::task::JoinSet;

#[derive(Parser)]
#[command(name = "bench-clap")]
struct Cli {
    #[command(subcommand)]
    command: Top,
}

#[derive(Subcommand)]
enum Top {
    /// Concurrency benchmark payloads
    Bench {
        #[command(subcommand)]
        scenario: Scenario,
    },
}

#[derive(Subcommand)]
enum Scenario {
    /// Dispatch N concurrent tasks and aggregate their results
    Fanout {
        #[arg(short, long, default_value_t = 1000)]
        tasks: usize,
        /// Concurrency limit; 0 dispatches all at once
        #[arg(short, long, default_value_t = 0)]
        jobs: usize,
    },
    /// Fire an event storm and count the runs it coalesces into
    Storm {
        #[arg(short, long, default_value_t = 10000)]
        events: usize,
        /// Milliseconds to spread the events over
        #[arg(short, long, default_value_t = 50)]
        window: u64,
        /// Debounce window in milliseconds
        #[arg(short, long, default_value_t = 150)]
        debounce: u64,
    },
    /// Run a three-stage streaming pipeline over N lines
    Pipeline {
        #[arg(short, long, default_value_t = 100000)]
        lines: usize,
        #[arg(short, long, default_value = "7")]
        pattern: String,
    },
    /// Spawn N workers and wait to be cancelled
    Cancel {
        #[arg(short, long, default_value_t = 500)]
        workers: usize,
        /// File recording cleanup order, one index per line
        #[arg(short, long, default_value = "")]
        marker: String,
        /// Give up after this long if no signal arrives
        #[arg(short, long, default_value_t = 30)]
        seconds: u64,
    },
}

#[tokio::main]
async fn main() {
    match Cli::parse().command {
        Top::Bench { scenario } => match scenario {
            Scenario::Fanout { tasks, jobs } => fanout(tasks, jobs).await,
            Scenario::Storm { events, window, debounce } => storm(events, window, debounce).await,
            Scenario::Pipeline { lines, pattern } => pipeline(lines, &pattern).await,
            Scenario::Cancel { workers, marker, seconds } => cancel(workers, &marker, seconds).await,
        },
    }
}

/// Scenario A: fan out N units of work, join them, aggregate.
async fn fanout(tasks: usize, jobs: usize) {
    let mut results = vec![0i64; tasks];

    if jobs == 0 {
        // One task per unit: the runtime's native fan-out.
        let mut set = JoinSet::new();
        for index in 0..tasks {
            set.spawn(async move {
                tokio::task::yield_now().await;
                (index, index as i64 + 1)
            });
        }
        while let Some(joined) = set.join_next().await {
            let (index, value) = joined.expect("task panicked");
            results[index] = value;
        }
    } else {
        // Bounded worker pool claiming units from a shared cursor, so
        // submission order is preserved the way Laret's dispatcher does.
        let cursor = Arc::new(AtomicUsize::new(0));
        let mut set = JoinSet::new();
        for _ in 0..jobs {
            let cursor = Arc::clone(&cursor);
            set.spawn(async move {
                let mut produced = Vec::new();
                loop {
                    let index = cursor.fetch_add(1, Ordering::SeqCst);
                    if index >= tasks {
                        return produced;
                    }
                    tokio::task::yield_now().await;
                    produced.push((index, index as i64 + 1));
                }
            });
        }
        while let Some(joined) = set.join_next().await {
            for (index, value) in joined.expect("worker panicked") {
                results[index] = value;
            }
        }
    }

    let sum: i64 = results.iter().sum();
    println!("fanout tasks={} jobs={} completed={} sum={}", tasks, jobs, results.len(), sum);
}

/// Scenario B: flood a debouncer and coalesce to a single run.
async fn storm(events: usize, window: u64, debounce: u64) {
    let (tx, mut rx) = mpsc::channel::<String>(1024);
    let emitted = Arc::new(AtomicI64::new(0));

    let producer_emitted = Arc::clone(&emitted);
    let producer = tokio::spawn(async move {
        let started = Instant::now();
        for index in 0..events {
            if tx.send(format!("storm-{}.txt", index)).await.is_err() {
                break;
            }
            producer_emitted.fetch_add(1, Ordering::SeqCst);
        }
        let spent = started.elapsed();
        if spent < Duration::from_millis(window) {
            tokio::time::sleep(Duration::from_millis(window) - spent).await;
        }
        tokio::time::sleep(Duration::from_millis(debounce * 2 + 100)).await;
    });

    // Coalesce: every event restarts the quiet timer, and only the silence at
    // the end of the burst triggers a run.
    let mut runs: i64 = 0;
    let mut pending = false;
    loop {
        if pending {
            tokio::select! {
                received = rx.recv() => match received {
                    Some(path) if path.ends_with(".txt") => {}
                    Some(_) => {}
                    None => {
                        tokio::time::sleep(Duration::from_millis(debounce)).await;
                        runs += 1;
                        break;
                    }
                },
                _ = tokio::time::sleep(Duration::from_millis(debounce)) => {
                    runs += 1;
                    pending = false;
                }
            }
        } else {
            match rx.recv().await {
                Some(path) if path.ends_with(".txt") => pending = true,
                Some(_) => {}
                None => break,
            }
        }
    }
    let _ = producer.await;

    println!(
        "storm events={} window={} debounce={} runs={} restarts={}",
        emitted.load(Ordering::SeqCst),
        window,
        debounce,
        runs,
        runs
    );
}

/// Scenario C: three concurrent stages over bounded channels.
async fn pipeline(lines: usize, pattern: &str) {
    const CAPACITY: usize = 256;
    let (emit_tx, mut emit_rx) = mpsc::channel::<String>(CAPACITY);
    let (keep_tx, mut keep_rx) = mpsc::channel::<String>(CAPACITY);

    tokio::spawn(async move {
        for index in 0..lines {
            if emit_tx.send(format!("line-{}", index)).await.is_err() {
                return;
            }
        }
    });

    let needle = pattern.to_string();
    tokio::spawn(async move {
        while let Some(line) = emit_rx.recv().await {
            if needle.is_empty() || line.contains(&needle) {
                if keep_tx.send(line).await.is_err() {
                    return;
                }
            }
        }
    });

    let counter = tokio::spawn(async move {
        let mut seen = 0usize;
        while keep_rx.recv().await.is_some() {
            seen += 1;
        }
        seen
    });

    let kept = counter.await.expect("counting stage panicked");
    println!("pipeline lines={} mode=streaming stages=3 kept={}", lines, kept);
}

/// Scenario D: many workers, LIFO cleanup hooks, a signal arriving mid-flight.
async fn cancel(workers: usize, marker: &str, seconds: u64) {
    use tokio::signal::unix::{signal, SignalKind};

    let token = Arc::new(AtomicUsize::new(0));
    let ticks = Arc::new(AtomicI64::new(0));
    let order: Arc<Mutex<Vec<usize>>> = Arc::new(Mutex::new(Vec::with_capacity(workers)));

    let mut set = JoinSet::new();
    for _ in 0..workers {
        let token = Arc::clone(&token);
        let ticks = Arc::clone(&ticks);
        set.spawn(async move {
            while token.load(Ordering::Relaxed) == 0 {
                ticks.fetch_add(1, Ordering::Relaxed);
                tokio::task::yield_now().await;
            }
        });
    }

    // Cleanups registered in ascending order, so a correct LIFO teardown
    // records them descending.
    let cleanups: Vec<usize> = (0..workers).collect();

    println!("cancel workers={} pid={} ready=true", workers, std::process::id());
    let _ = std::io::stdout().flush();

    let mut sigterm = signal(SignalKind::terminate()).expect("cannot listen for SIGTERM");
    let mut sigint = signal(SignalKind::interrupt()).expect("cannot listen for SIGINT");

    let received: Option<i32> = tokio::select! {
        _ = sigterm.recv() => Some(15),
        _ = sigint.recv() => Some(2),
        _ = tokio::time::sleep(Duration::from_secs(seconds)) => None,
    };

    token.store(1, Ordering::SeqCst);
    while set.join_next().await.is_some() {}

    for index in cleanups.into_iter().rev() {
        order.lock().expect("cleanup order poisoned").push(index);
    }
    let recorded = order.lock().expect("cleanup order poisoned").clone();
    if !marker.is_empty() {
        let body: String = recorded.iter().map(|index| format!("{}\n", index)).collect();
        std::fs::write(marker, body).expect("cannot write marker file");
    }
    eprintln!("cancel ticks={} cleaned={}", ticks.load(Ordering::SeqCst), recorded.len());

    // A handled signal is not re-raised, so the 128+signal convention has to be
    // honoured explicitly.
    if let Some(sig) = received {
        std::process::exit(128 + sig);
    }
}
