package org.javacs.kt.diagnostic

import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.junit.Assert.assertThat
import org.junit.Test
import java.net.URI
import java.nio.file.Paths

class DiagnosticMergerTest {
    private val uri: URI = Paths.get("Sample.kt").toUri()

    @Test fun `keeps primary diagnostics when no structural diagnostics exist`() {
        val primary = listOf(uri to kotlinDiagnostic("Unresolved reference: foo", DiagnosticSeverity.Error, "UNRESOLVED_REFERENCE"))

        val merged = mergeStructuralDiagnostics(primary, emptyList())

        assertThat(merged, equalTo(primary))
    }

    @Test fun `incomplete expression suppresses type annotation recovery diagnostic`() {
        val primary = listOf(
            uri to kotlinDiagnostic(
                "This variable must either have a type annotation or be initialized",
                DiagnosticSeverity.Error,
                "MUST_BE_INITIALIZED_OR_BE_ABSTRACT"
            )
        )
        val structural = listOf(uri to structuralDiagnostic("Incomplete expression after '='", STRUCTURAL_INCOMPLETE_EXPRESSION))

        val merged = mergeStructuralDiagnostics(primary, structural)

        assertThat(merged.map { it.second.message }, equalTo(listOf("Incomplete expression after '='")))
    }

    @Test fun `incomplete expression suppresses secondary non-error diagnostics`() {
        val primary = listOf(
            uri to kotlinDiagnostic("Variable 'value' is never used", DiagnosticSeverity.Warning, "UNUSED_VARIABLE")
        )
        val structural = listOf(uri to structuralDiagnostic("Incomplete expression after '='", STRUCTURAL_INCOMPLETE_EXPRESSION))

        val merged = mergeStructuralDiagnostics(primary, structural)

        assertThat(merged.map { diagnosticCode(it.second) }, equalTo(listOf(STRUCTURAL_INCOMPLETE_EXPRESSION)))
    }

    @Test fun `unterminated backtick suppresses parser recovery function name diagnostic`() {
        val primary = listOf(
            uri to kotlinDiagnostic("Function declaration must have a name", DiagnosticSeverity.Error, "ANONYMOUS_FUNCTION_WITH_NAME")
        )
        val structural = listOf(
            uri to structuralDiagnostic("Unterminated backtick identifier", STRUCTURAL_UNTERMINATED_BACKTICK_IDENTIFIER)
        )

        val merged = mergeStructuralDiagnostics(primary, structural)

        assertThat(merged.map { diagnosticCode(it.second) }, equalTo(listOf(STRUCTURAL_UNTERMINATED_BACKTICK_IDENTIFIER)))
    }

    @Test fun `unterminated string suppresses cascading function body and brace diagnostics`() {
        val primary = listOf(
            uri to kotlinDiagnostic("Incomplete function body", DiagnosticSeverity.Error, "NO_BODY"),
            uri to kotlinDiagnostic("Unclosed '{'", DiagnosticSeverity.Error, "SYNTAX")
        )
        val structural = listOf(uri to structuralDiagnostic("Unterminated string literal", STRUCTURAL_UNTERMINATED_STRING_LITERAL))

        val merged = mergeStructuralDiagnostics(primary, structural)

        assertThat(merged.map { diagnosticCode(it.second) }, equalTo(listOf(STRUCTURAL_UNTERMINATED_STRING_LITERAL)))
    }

    @Test fun `unterminated raw string suppresses cascading function body and brace diagnostics`() {
        val primary = listOf(
            uri to kotlinDiagnostic("Incomplete function body", DiagnosticSeverity.Error, "NO_BODY"),
            uri to kotlinDiagnostic("Unclosed '{'", DiagnosticSeverity.Error, "SYNTAX")
        )
        val structural = listOf(uri to structuralDiagnostic("Unterminated raw string literal", STRUCTURAL_UNTERMINATED_RAW_STRING_LITERAL))

        val merged = mergeStructuralDiagnostics(primary, structural)

        assertThat(merged.map { diagnosticCode(it.second) }, equalTo(listOf(STRUCTURAL_UNTERMINATED_RAW_STRING_LITERAL)))
    }

    @Test fun `unterminated block comment suppresses cascading function body and brace diagnostics`() {
        val primary = listOf(
            uri to kotlinDiagnostic("Incomplete function body", DiagnosticSeverity.Error, "NO_BODY"),
            uri to kotlinDiagnostic("Unclosed '{'", DiagnosticSeverity.Error, "SYNTAX")
        )
        val structural = listOf(uri to structuralDiagnostic("Unterminated block comment", STRUCTURAL_UNTERMINATED_BLOCK_COMMENT))

        val merged = mergeStructuralDiagnostics(primary, structural)

        assertThat(merged.map { diagnosticCode(it.second) }, equalTo(listOf(STRUCTURAL_UNTERMINATED_BLOCK_COMMENT)))
    }

    @Test fun `structural diagnostics do not suppress unrelated compiler errors`() {
        val primary = listOf(uri to kotlinDiagnostic("Unresolved reference: foo", DiagnosticSeverity.Error, "UNRESOLVED_REFERENCE"))
        val structural = listOf(uri to structuralDiagnostic("Incomplete expression after '='", STRUCTURAL_INCOMPLETE_EXPRESSION))

        val merged = mergeStructuralDiagnostics(primary, structural)

        assertThat(merged.map { diagnosticCode(it.second) }, hasItem("UNRESOLVED_REFERENCE"))
        assertThat(merged.map { diagnosticCode(it.second) }, hasItem(STRUCTURAL_INCOMPLETE_EXPRESSION))
        assertThat(merged.map { diagnosticCode(it.second) }, not(hasItem("UNUSED_VARIABLE")))
    }

    private fun kotlinDiagnostic(message: String, severity: DiagnosticSeverity, code: String): Diagnostic =
        Diagnostic(testRange(), message, severity, "kotlin", code)

    private fun structuralDiagnostic(message: String, code: String): Diagnostic =
        Diagnostic(testRange(), message, DiagnosticSeverity.Error, STRUCTURAL_DIAGNOSTIC_SOURCE, code)

    private fun testRange(): Range = Range(Position(0, 0), Position(0, 1))

    private fun diagnosticCode(diagnostic: Diagnostic): String? = diagnostic.code?.left
}
