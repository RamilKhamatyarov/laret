/*
 * rusage_exec: run a command and report its exit, peak RSS and elapsed time.
 *
 *   rusage_exec <result-file> <command> [args...]
 *
 * Why this exists: the kernel folds a process's pre-exec memory into the
 * ru_maxrss it reports after exec. A child forked from the Python harness
 * starts as a copy of the interpreter, so every target smaller than Python
 * (about 12.5 MiB) would read as exactly that. Forking from this tiny process
 * instead lowers that floor to this helper's own footprint, which is well
 * below any real target.
 *
 * The target inherits this process's CPU affinity. SIGINT and SIGTERM are
 * ignored here, because the harness signals the whole process group, but reset
 * to their defaults in the child before exec, since an ignored disposition
 * would otherwise be inherited by the target.
 */
#define _GNU_SOURCE
#include <signal.h>
#include <stdio.h>
#include <sys/resource.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

static long long now_ns(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (long long) ts.tv_sec * 1000000000LL + ts.tv_nsec;
}

int main(int argc, char **argv) {
    if (argc < 3) {
        fprintf(stderr, "usage: rusage_exec <result-file> <command> [args...]\n");
        return 2;
    }

    signal(SIGINT, SIG_IGN);
    signal(SIGTERM, SIG_IGN);

    long long started = now_ns();
    pid_t child = fork();
    if (child < 0) {
        perror("fork");
        return 2;
    }
    if (child == 0) {
        signal(SIGINT, SIG_DFL);
        signal(SIGTERM, SIG_DFL);
        execvp(argv[2], &argv[2]);
        perror("execvp");
        _exit(127);
    }

    int status = 0;
    struct rusage usage;
    if (wait4(child, &status, 0, &usage) < 0) {
        perror("wait4");
        return 2;
    }
    long long elapsed = now_ns() - started;

    int code = WIFEXITED(status) ? WEXITSTATUS(status) : 128 + WTERMSIG(status);
    FILE *out = fopen(argv[1], "w");
    if (out == NULL) {
        perror("fopen");
        return 2;
    }
    /* Linux reports ru_maxrss in kilobytes. */
    fprintf(out, "exit=%d maxrss_kb=%ld elapsed_ns=%lld\n", code, usage.ru_maxrss, elapsed);
    fclose(out);
    return code;
}
