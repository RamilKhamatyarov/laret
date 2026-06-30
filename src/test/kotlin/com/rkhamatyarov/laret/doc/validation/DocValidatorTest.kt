package com.rkhamatyarov.laret.doc.validation

import com.rkhamatyarov.laret.doc.DocFile
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocValidatorTest {

    private fun page(path: String, content: String) = DocFile(path, content)

    @Test
    fun test_valid_internal_links_produce_no_errors() {
        val files = listOf(
            page("en/index.md", "- [file](file/index.md)"),
            page("en/file/index.md", "- [create](create.md)"),
            page("en/file/create.md", "# Create\n"),
        )

        assertFalse(DocValidator.validate(files).hasErrors)
    }

    @Test
    fun test_broken_internal_link_is_an_error() {
        val files = listOf(page("en/file/create.md", "see [delete](delete.md)"))

        val report = DocValidator.validate(files)

        assertTrue(report.errors.any { it.contains("broken link 'delete.md'") })
    }

    @Test
    fun test_broken_local_anchor_is_an_error() {
        val files = listOf(page("en/file/create.md", "# Create\n\njump to [opts](#options)"))

        val report = DocValidator.validate(files)

        assertTrue(report.errors.any { it.contains("broken anchor '#options'") })
    }

    @Test
    fun test_valid_local_anchor_passes() {
        val files = listOf(page("en/file/create.md", "# Create\n\n## Options\n\n[opts](#options)"))

        assertFalse(DocValidator.validate(files).hasErrors)
    }

    @Test
    fun test_external_links_are_not_validated() {
        val files = listOf(page("en/file/create.md", "[site](https://example.com/missing)"))

        assertFalse(DocValidator.validate(files).hasErrors)
    }

    @Test
    fun test_cross_file_anchor_checks_only_the_file_part() {
        val files = listOf(
            page("en/file/create.md", "[del opts](../file/delete.md#flags)"),
            page("en/file/delete.md", "# Delete\n"),
        )

        assertFalse(DocValidator.validate(files).hasErrors)
    }

    @Test
    fun test_non_markdown_internal_links_are_out_of_scope() {
        val files = listOf(page("en/file/create.md", "![diagram](../img/flow.png) and [dir](../dir/)"))

        assertFalse(DocValidator.validate(files).hasErrors)
    }
}
