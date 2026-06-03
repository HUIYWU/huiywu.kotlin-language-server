package org.javacs.kt.diagnostic

import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.nio.file.Paths

class StructuralFallbackDiagnosticsTest {
    private val uri = Paths.get("Fallback.kt").toUri()

    @Test fun `reports unterminated string literal`() {
        val diagnostics = structuralFallbackDiagnostics(uri, """
            class Sample {
                val text = \"unterminated
            }
        """.trimIndent())

        assertHasStructuralDiagnostic(
            diagnostics = diagnostics,
            code = "STRUCTURAL_UNTERMINATED_STRING_LITERAL",
            messageFragment = "Unterminated string literal"
        )
    }

    @Test fun `reports unterminated block comment`() {
        val diagnostics = structuralFallbackDiagnostics(uri, """
            class Sample {
                /* unterminated
            }
        """.trimIndent())

        assertHasStructuralDiagnostic(
            diagnostics = diagnostics,
            code = "STRUCTURAL_UNTERMINATED_BLOCK_COMMENT",
            messageFragment = "Unterminated block comment"
        )
    }

    @Test fun `reports unterminated char literal`() {
        val diagnostics = structuralFallbackDiagnostics(uri, """
            class Sample {
                val ch = '
            }
        """.trimIndent())

        assertHasStructuralDiagnostic(
            diagnostics = diagnostics,
            code = "STRUCTURAL_UNTERMINATED_CHAR_LITERAL",
            messageFragment = "Unterminated char literal"
        )
    }

    @Test fun `reports unterminated backtick identifier`() {
        val diagnostics = structuralFallbackDiagnostics(uri, """
            class Sample {
                val `unterminated = 1
            }
        """.trimIndent())

        assertHasStructuralDiagnostic(
            diagnostics = diagnostics,
            code = "STRUCTURAL_UNTERMINATED_BACKTICK_IDENTIFIER",
            messageFragment = "Unterminated backtick identifier"
        )
    }

    @Test fun `reports incomplete expression after assignment`() {
        val diagnostics = structuralFallbackDiagnostics(uri, """
            class Sample {
                fun broken() {
                    val value =
                }
            }
        """.trimIndent())

        assertHasStructuralDiagnostic(
            diagnostics = diagnostics,
            code = "STRUCTURAL_INCOMPLETE_EXPRESSION",
            messageFragment = "Incomplete expression after '='"
        )
    }

    @Test fun `reports incomplete function body instead of generic unclosed delimiter`() {
        val diagnostics = structuralFallbackDiagnostics(uri, """
            fun broken() {
        """.trimIndent())

        assertHasStructuralDiagnostic(
            diagnostics = diagnostics,
            code = "STRUCTURAL_INCOMPLETE_FUNCTION_BODY",
            messageFragment = "Incomplete function body"
        )
        assertTrue(
            "Expected incomplete function body fallback to replace generic unclosed delimiter for function body, actual codes=${diagnostics.map { diagnosticCode(it.second) }}",
            diagnostics.none {
                diagnosticCode(it.second) == "STRUCTURAL_UNCLOSED_DELIMITER" && it.second.message.contains("expected '}'")
            }
        )
    }

    @Test fun `keeps diagnostic source as kotlin structural fallback`() {
        val diagnostics = structuralFallbackDiagnostics(uri, """
            class Sample {
                fun broken() {
                    val value =
            }
        """.trimIndent())

        val diagnostic = diagnostics.first().second
        assertThat(diagnostic.source, equalTo("kotlin-structural-fallback"))
    }

    private fun assertHasStructuralDiagnostic(
        diagnostics: List<Pair<URI, Diagnostic>>,
        code: String,
        messageFragment: String,
    ) {
        val match = diagnostics.map { it.second }.firstOrNull { diagnostic ->
            diagnosticCode(diagnostic) == code
        }

        assertTrue(
            "Expected diagnostic code=$code, actual codes=${diagnostics.map { diagnosticCode(it.second) }}",
            match != null
        )
        assertThat(match?.message, containsString(messageFragment))
        assertThat(match?.severity, equalTo(DiagnosticSeverity.Error))
    }

    private fun diagnosticCode(diagnostic: Diagnostic): String? = diagnostic.code?.left
}
