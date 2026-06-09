package org.javacs.kt.diagnostic

import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.nio.file.Paths

class StructuralDiagnosticsTest {
    private val uri = Paths.get("Structural.kt").toUri()

    @Test fun `reports unterminated string literal`() {
        val diagnostics = structuralDiagnostics(uri, """
            class Sample {
                val text = \"unterminated
            }
        """.trimIndent())

        assertHasStructuralDiagnostic(
            diagnostics = diagnostics,
            code = STRUCTURAL_UNTERMINATED_STRING_LITERAL,
            messageFragment = "Unterminated string literal"
        )
    }

    @Test fun `reports unterminated block comment`() {
        val diagnostics = structuralDiagnostics(uri, """
            class Sample {
                /* unterminated
            }
        """.trimIndent())

        assertHasStructuralDiagnostic(
            diagnostics = diagnostics,
            code = STRUCTURAL_UNTERMINATED_BLOCK_COMMENT,
            messageFragment = "Unterminated block comment"
        )
    }

    @Test fun `reports unterminated char literal`() {
        val diagnostics = structuralDiagnostics(uri, """
            class Sample {
                val ch = '
            }
        """.trimIndent())

        assertHasStructuralDiagnostic(
            diagnostics = diagnostics,
            code = STRUCTURAL_UNTERMINATED_CHAR_LITERAL,
            messageFragment = "Unterminated char literal"
        )
    }

    @Test fun `reports unterminated backtick identifier`() {
        val diagnostics = structuralDiagnostics(uri, """
            class Sample {
                val `unterminated = 1
            }
        """.trimIndent())

        assertHasStructuralDiagnostic(
            diagnostics = diagnostics,
            code = STRUCTURAL_UNTERMINATED_BACKTICK_IDENTIFIER,
            messageFragment = "Unterminated backtick identifier"
        )
    }

    @Test fun `reports unterminated raw string literal`() {
        val diagnostics = structuralDiagnostics(uri, """
            class Sample {
                val text = ""${'"'}oops
            }
        """.trimIndent())

        assertHasStructuralDiagnostic(
            diagnostics = diagnostics,
            code = STRUCTURAL_UNTERMINATED_RAW_STRING_LITERAL,
            messageFragment = "Unterminated raw string literal"
        )
    }

    @Test fun `reports incomplete expression after assignment`() {
        val diagnostics = structuralDiagnostics(uri, """
            class Sample {
                fun broken() {
                    val value =
                }
            }
        """.trimIndent())

        assertHasStructuralDiagnostic(
            diagnostics = diagnostics,
            code = STRUCTURAL_INCOMPLETE_EXPRESSION,
            messageFragment = "Incomplete expression after '='"
        )
    }

    @Test fun `reports incomplete expression after trailing operator`() {
        val diagnostics = structuralDiagnostics(uri, """
            class Sample {
                fun broken() {
                    val value = foo +
                }
            }
        """.trimIndent())

        assertHasStructuralDiagnostic(
            diagnostics = diagnostics,
            code = STRUCTURAL_INCOMPLETE_EXPRESSION,
            messageFragment = "Incomplete expression after '+'"
        )
    }

    @Test fun `reports incomplete expression after prefix operator without operand`() {
        val diagnostics = structuralDiagnostics(uri, """
            class Sample {
                fun broken() {
                    val value = +
                }
            }
        """.trimIndent())

        assertHasStructuralDiagnostic(
            diagnostics = diagnostics,
            code = STRUCTURAL_INCOMPLETE_EXPRESSION,
            messageFragment = "Incomplete expression after '+'"
        )
    }

    @Test fun `reports incomplete return expression after trailing operator`() {
        val diagnostics = structuralDiagnostics(uri, """
            class Sample {
                fun broken(): Int {
                    return foo +
                }
            }
        """.trimIndent())

        assertHasStructuralDiagnostic(
            diagnostics = diagnostics,
            code = STRUCTURAL_INCOMPLETE_EXPRESSION,
            messageFragment = "Incomplete expression after '+'"
        )
    }

    @Test fun `reports incomplete boolean expression after and operator`() {
        val diagnostics = structuralDiagnostics(uri, """
            class Sample {
                fun broken(foo: Boolean) {
                    if (foo &&
                    ) {
                        println("ok")
                    }
                }
            }
        """.trimIndent())

        assertHasStructuralDiagnostic(
            diagnostics = diagnostics,
            code = STRUCTURAL_INCOMPLETE_EXPRESSION,
            messageFragment = "Incomplete expression after '&&'"
        )
    }

    @Test fun `reports incomplete expression after elvis operator`() {
        val diagnostics = structuralDiagnostics(uri, """
            class Sample {
                fun broken(foo: String?): String {
                    val value = foo ?:
                    return value
                }
            }
        """.trimIndent())

        assertHasStructuralDiagnostic(
            diagnostics = diagnostics,
            code = STRUCTURAL_INCOMPLETE_EXPRESSION,
            messageFragment = "Incomplete expression after '?:'"
        )
    }

    @Test fun `reports incomplete expression after safe call operator`() {
        val diagnostics = structuralDiagnostics(uri, """
            class Sample {
                fun broken(foo: String?) {
                    val value = foo?.
                }
            }
        """.trimIndent())

        assertHasStructuralDiagnostic(
            diagnostics = diagnostics,
            code = STRUCTURAL_INCOMPLETE_EXPRESSION,
            messageFragment = "Incomplete expression after '?.'"
        )
    }

    @Test fun `reports incomplete expression after as keyword`() {
        val diagnostics = structuralDiagnostics(uri, """
            class Sample {
                fun broken(foo: Any) {
                    val value = foo as
                }
            }
        """.trimIndent())

        assertHasStructuralDiagnostic(
            diagnostics = diagnostics,
            code = STRUCTURAL_INCOMPLETE_EXPRESSION,
            messageFragment = "Incomplete expression after 'as'"
        )
    }

    @Test fun `reports incomplete expression after safe as keyword`() {
        val diagnostics = structuralDiagnostics(uri, """
            class Sample {
                fun broken(foo: Any) {
                    val value = foo as?
                }
            }
        """.trimIndent())

        assertHasStructuralDiagnostic(
            diagnostics = diagnostics,
            code = STRUCTURAL_INCOMPLETE_EXPRESSION,
            messageFragment = "Incomplete expression after 'as?'"
        )
    }

    @Test fun `reports incomplete expression after is keyword`() {
        val diagnostics = structuralDiagnostics(uri, """
            class Sample {
                fun broken(foo: Any) {
                    val ok = foo is
                }
            }
        """.trimIndent())

        assertHasStructuralDiagnostic(
            diagnostics = diagnostics,
            code = STRUCTURAL_INCOMPLETE_EXPRESSION,
            messageFragment = "Incomplete expression after 'is'"
        )
    }

    @Test fun `reports incomplete expression after callable reference operator`() {
        val diagnostics = structuralDiagnostics(uri, """
            class Sample {
                fun broken(foo: Any) {
                    val ref = foo::
                }
            }
        """.trimIndent())

        assertHasStructuralDiagnostic(
            diagnostics = diagnostics,
            code = STRUCTURAL_INCOMPLETE_EXPRESSION,
            messageFragment = "Incomplete expression after '::'"
        )
    }

    @Test fun `does not report incomplete expression for completed callable reference expression`() {
        val diagnostics = structuralDiagnostics(uri, """
            class Sample {
                fun ok(foo: Any) {
                    val ref = foo::class
                }
            }
        """.trimIndent())

        assertTrue(
            "Expected no incomplete expression diagnostic, actual=${diagnostics.map { it.second.message }}",
            diagnostics.none { diagnosticCode(it.second) == STRUCTURAL_INCOMPLETE_EXPRESSION }
        )
    }

    @Test fun `does not report incomplete expression for completed type operator expressions`() {
        val diagnostics = structuralDiagnostics(uri, """
            class Sample {
                fun ok(foo: Any) {
                    val a = foo as String
                    val b = foo as? String
                    val c = foo is String
                }
            }
        """.trimIndent())

        assertTrue(
            "Expected no incomplete expression diagnostic, actual=${diagnostics.map { it.second.message }}",
            diagnostics.none { diagnosticCode(it.second) == STRUCTURAL_INCOMPLETE_EXPRESSION }
        )
    }

    @Test fun `does not report incomplete expression for completed elvis and safe call expressions`() {
        val diagnostics = structuralDiagnostics(uri, """
            class Sample {
                fun ok(foo: String?): Int {
                    val a = foo ?: "fallback"
                    val b = foo?.length
                    return b ?: a.length
                }
            }
        """.trimIndent())

        assertTrue(
            "Expected no incomplete expression diagnostic, actual=${diagnostics.map { it.second.message }}",
            diagnostics.none { diagnosticCode(it.second) == STRUCTURAL_INCOMPLETE_EXPRESSION }
        )
    }

    @Test fun `does not report incomplete expression for completed boolean operator expression`() {
        val diagnostics = structuralDiagnostics(uri, """
            class Sample {
                fun ok(foo: Boolean, bar: Boolean) {
                    if (foo && bar) {
                        println("ok")
                    }
                }
            }
        """.trimIndent())

        assertTrue(
            "Expected no incomplete expression diagnostic, actual=${diagnostics.map { it.second.message }}",
            diagnostics.none { diagnosticCode(it.second) == STRUCTURAL_INCOMPLETE_EXPRESSION }
        )
    }

    @Test fun `does not report incomplete expression for completed operator expression`() {
        val diagnostics = structuralDiagnostics(uri, """
            class Sample {
                fun ok() {
                    val value = foo + bar
                }
            }
        """.trimIndent())

        assertTrue(
            "Expected no incomplete expression diagnostic, actual=${diagnostics.map { it.second.message }}",
            diagnostics.none { diagnosticCode(it.second) == STRUCTURAL_INCOMPLETE_EXPRESSION }
        )
    }

    @Test fun `reports incomplete function body instead of generic unclosed delimiter`() {
        val diagnostics = structuralDiagnostics(uri, """
            fun broken() {
        """.trimIndent())

        assertHasStructuralDiagnostic(
            diagnostics = diagnostics,
            code = STRUCTURAL_INCOMPLETE_FUNCTION_BODY,
            messageFragment = "Incomplete function body"
        )
        assertTrue(
            "Expected incomplete function body structural diagnostic to replace generic unclosed delimiter for function body, actual codes=${diagnostics.map { diagnosticCode(it.second) }}",
            diagnostics.none {
                diagnosticCode(it.second) == STRUCTURAL_UNCLOSED_DELIMITER && it.second.message.contains("expected '}'")
            }
        )
    }

    @Test fun `keeps diagnostic source as kotlin structural diagnostics`() {
        val diagnostics = structuralDiagnostics(uri, """
            class Sample {
                fun broken() {
                    val value =
            }
        """.trimIndent())

        val diagnostic = diagnostics.first().second
        assertThat(diagnostic.source, equalTo(STRUCTURAL_DIAGNOSTIC_SOURCE))
    }

    @Test fun `unterminated block comment does not cascade into delimiter diagnostics`() {
        val diagnostics = structuralDiagnostics(uri, """
            class UnterminatedBlockComment {
                fun broken() {
                    /* comment
                    val value = 1
                }
            }
        """.trimIndent())

        assertThat(
            diagnostics.map { diagnosticCode(it.second) },
            equalTo(listOf(STRUCTURAL_UNTERMINATED_BLOCK_COMMENT))
        )
    }

    @Test fun `unterminated string literal does not cascade into delimiter diagnostics`() {
        val diagnostics = structuralDiagnostics(uri, """
            class UnterminatedStringLiteral {
                fun broken() {
                    val value = "oops
                }
            }
        """.trimIndent())

        assertThat(
            diagnostics.map { diagnosticCode(it.second) },
            equalTo(listOf(STRUCTURAL_UNTERMINATED_STRING_LITERAL))
        )
    }

    @Test fun `unterminated string literal with CRLF does not cascade into delimiter diagnostics`() {
        val diagnostics = structuralDiagnostics(
            uri,
            "class UnterminatedStringLiteral {\r\n" +
                "    fun broken() {\r\n" +
                "        val value = \"oops\r\n" +
                "    }\r\n" +
                "}"
        )

        assertThat(
            diagnostics.map { diagnosticCode(it.second) },
            equalTo(listOf(STRUCTURAL_UNTERMINATED_STRING_LITERAL))
        )
    }

    @Test fun `unterminated raw string literal does not cascade into delimiter diagnostics`() {
        val diagnostics = structuralDiagnostics(uri, """
            class UnterminatedRawStringLiteral {
                fun broken() {
                    val value = ""${'"'}oops
                }
            }
        """.trimIndent())

        assertThat(
            diagnostics.map { diagnosticCode(it.second) },
            equalTo(listOf(STRUCTURAL_UNTERMINATED_RAW_STRING_LITERAL))
        )
    }

    @Test fun `unterminated backtick identifier is reported before parser recovery noise`() {
        val diagnostics = structuralDiagnostics(uri, """
            class UnterminatedBacktickIdentifier {
                fun `broken() {
                    println("ok")
                }
            }
        """.trimIndent())

        assertThat(
            diagnostics.map { diagnosticCode(it.second) },
            hasItem(STRUCTURAL_UNTERMINATED_BACKTICK_IDENTIFIER)
        )
    }

    @Test fun `missing closing paren reports unclosed paren at opener`() {
        val diagnostics = structuralDiagnostics(uri, """
            class MissingClosingParen {
                fun broken() {
                    println("ok"
                }
            }
        """.trimIndent())

        assertHasStructuralDiagnostic(
            diagnostics = diagnostics,
            code = STRUCTURAL_UNCLOSED_DELIMITER,
            messageFragment = "Unclosed '('; expected ')'"
        )
    }

    @Test fun `extra closing brace reports unexpected closing brace`() {
        val diagnostics = structuralDiagnostics(uri, """
            class ExtraClosingBrace {
                fun broken() {
                    println("ok")
                }
            }
            }
        """.trimIndent())

        assertHasStructuralDiagnostic(
            diagnostics = diagnostics,
            code = STRUCTURAL_UNMATCHED_CLOSING,
            messageFragment = "Unexpected closing '}'"
        )
    }

    @Test fun `mismatched closing delimiter reports expected delimiter`() {
        val diagnostics = structuralDiagnostics(uri, """
            class MismatchedClosingDelimiter {
                fun broken() {
                    println("ok")
                )
            }
        """.trimIndent())

        assertHasStructuralDiagnostic(
            diagnostics = diagnostics,
            code = STRUCTURAL_MISMATCHED_DELIMITER,
            messageFragment = "Mismatched closing ')'; expected '}' for '{'"
        )
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
