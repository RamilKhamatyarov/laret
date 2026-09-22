package io.github.laretframework.core

import java.io.InputStream
import java.io.OutputStream
import java.io.PrintStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A bounded byte pipe connecting one producer thread to one consumer thread.
 *
 * Capacity is what makes a streaming pipeline safe: once [capacityChunks]
 * writes are queued, the producer blocks until the consumer catches up, so a
 * fast stage cannot accumulate the whole stream in memory ahead of a slow one.
 *
 * `java.io.PipedInputStream` would do something similar, but it throws once it
 * decides the writing thread is dead, which makes stage teardown order
 * load-bearing. This queue has explicit end-of-stream and abandonment signals
 * instead.
 */
internal class BoundedPipe(capacityChunks: Int = DEFAULT_CAPACITY_CHUNKS) {

    private val queue = ArrayBlockingQueue<ByteArray>(capacityChunks)
    private val producerDone = AtomicBoolean(false)
    private val consumerGone = AtomicBoolean(false)

    /** Written by the producing stage; [OutputStream.close] signals end of stream. */
    val sink: OutputStream = object : OutputStream() {
        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (len == 0 || consumerGone.get()) return
            queue.put(b.copyOfRange(off, off + len))
        }

        override fun close() {
            if (producerDone.compareAndSet(false, true)) queue.put(END_OF_STREAM)
        }
    }

    /**
     * Read by the consuming stage. Closing it before the stream is exhausted
     * releases a producer that would otherwise block forever on a full queue.
     */
    val source: InputStream = object : InputStream() {
        private var chunk: ByteArray? = null
        private var offset = 0
        private var exhausted = false

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (exhausted) return -1
            if (len == 0) return 0

            var current = chunk
            while (current == null || offset >= current.size) {
                val next = queue.take()
                if (next.isEmpty()) {
                    exhausted = true
                    return -1
                }
                current = next
                chunk = next
                offset = 0
            }

            val count = minOf(len, current.size - offset)
            current.copyInto(b, off, offset, offset + count)
            offset += count
            return count
        }

        override fun available(): Int = (chunk?.size ?: 0) - offset

        override fun close() {
            exhausted = true
            if (consumerGone.compareAndSet(false, true)) queue.clear()
        }
    }

    companion object {
        /** An empty chunk is never written by a caller, so it is unambiguous as a sentinel. */
        private val END_OF_STREAM = ByteArray(0)

        const val DEFAULT_CAPACITY_CHUNKS = 256
    }
}

/**
 * A [PrintStream] that forwards every call to whichever stream the calling
 * thread bound, falling back to [fallback] for unbound threads.
 *
 * `System.out` is process-global, so concurrent pipeline stages cannot each
 * redirect it. Installing one router as `System.out` and having every stage
 * thread bind its own destination gives each stage a private stdout without
 * any stage knowing it has been redirected.
 *
 * It must be a `PrintStream` subclass that overrides the printing surface, not
 * a `PrintStream` wrapping a routing `OutputStream`. Every `PrintStream`
 * method synchronizes on the stream, so a single shared instance would let a
 * stage blocked on a full pipe hold the monitor that every other stage needs
 * to write: an immediate deadlock. These overrides are deliberately not
 * synchronized, and each bound target is a separate `PrintStream` with its own
 * lock.
 */
internal class ThreadRoutedPrintStream(private val fallback: PrintStream) :
    PrintStream(nullOutputStream(), false, Charsets.UTF_8) {

    private val route = ThreadLocal<PrintStream?>()

    fun bind(target: PrintStream) = route.set(target)

    fun unbind() {
        route.get()?.flush()
        route.remove()
    }

    private fun current(): PrintStream = route.get() ?: fallback

    override fun print(b: Boolean) = current().print(b)

    override fun print(c: Char) = current().print(c)

    override fun print(i: Int) = current().print(i)

    override fun print(l: Long) = current().print(l)

    override fun print(f: Float) = current().print(f)

    override fun print(d: Double) = current().print(d)

    override fun print(s: CharArray) = current().print(s)

    override fun print(s: String?) = current().print(s)

    override fun print(obj: Any?) = current().print(obj)

    override fun println() = current().println()

    override fun println(x: Boolean) = current().println(x)

    override fun println(x: Char) = current().println(x)

    override fun println(x: Int) = current().println(x)

    override fun println(x: Long) = current().println(x)

    override fun println(x: Float) = current().println(x)

    override fun println(x: Double) = current().println(x)

    override fun println(x: CharArray) = current().println(x)

    override fun println(x: String?) = current().println(x)

    override fun println(x: Any?) = current().println(x)

    override fun append(csq: CharSequence?): PrintStream = also { current().append(csq) }

    override fun append(csq: CharSequence?, start: Int, end: Int): PrintStream = also {
        current().append(csq, start, end)
    }

    override fun append(c: Char): PrintStream = also { current().append(c) }

    override fun write(b: Int) = current().write(b)

    override fun write(buf: ByteArray, off: Int, len: Int) = current().write(buf, off, len)

    override fun flush() = current().flush()
}

/** The [InputStream] counterpart of [ThreadRoutedPrintStream], for `System.in`. */
internal class ThreadRoutedInputStream(private val fallback: InputStream) : InputStream() {

    private val route = ThreadLocal<InputStream?>()

    fun bind(source: InputStream) = route.set(source)

    fun unbind() = route.remove()

    private fun current(): InputStream = route.get() ?: fallback

    override fun read(): Int = current().read()

    override fun read(b: ByteArray, off: Int, len: Int): Int = current().read(b, off, len)

    override fun available(): Int = current().available()
}
