package io.github.laretframework.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class BatchingPrintStreamTest {

    private val sink = ByteArrayOutputStream()

    private fun written() = sink.toString(Charsets.UTF_8)

    @Test
    fun holdsTextUntilFlushed() {
        val out = BatchingPrintStream(sink, batchChars = 64)

        out.println("one")
        out.print(2)
        assertThat(sink.size()).isZero()

        out.flush()
        assertThat(written()).isEqualTo("one${System.lineSeparator()}2")
    }

    @Test
    fun writesWholeBlocksOnceTheBatchFills() {
        val out = BatchingPrintStream(sink, batchChars = 8)

        out.print("abcd")
        assertThat(sink.size()).isZero()
        out.print("efghij")

        assertThat(written()).isEqualTo("abcdefghij")
    }

    @Test
    fun rawBytesKeepTheirPlaceBetweenText() {
        val out = BatchingPrintStream(sink, batchChars = 64)

        out.print("a")
        out.write('b'.code)
        out.write("cd".toByteArray(), 0, 2)
        out.append('e').append("fg").append("xhix", 1, 3)
        out.printf("%d", 7)
        out.flush()

        assertThat(written()).isEqualTo("abcdefghi7")
    }

    @Test
    fun aSurrogatePairSplitAtTheBatchBoundaryStaysIntact() {
        val out = BatchingPrintStream(sink, batchChars = 4)

        out.print("abc\uD83D")
        out.print("\uDE00z")
        out.flush()

        assertThat(written()).isEqualTo("abc😀z")
    }

    @Test
    fun printsNullsAndPrimitivesLikePrintStream() {
        val out = BatchingPrintStream(sink)

        out.print(null as String?)
        out.print(true)
        out.print('x')
        out.print(3L)
        out.print(1.5)
        out.print(charArrayOf('o', 'k'))
        out.print(null as Any?)
        out.close()

        assertThat(written()).isEqualTo("nulltruex31.5oknull")
    }

    @Test
    fun closeFlushesPendingText() {
        BatchingPrintStream(sink).use { it.print("tail") }

        assertThat(written()).isEqualTo("tail")
    }
}
