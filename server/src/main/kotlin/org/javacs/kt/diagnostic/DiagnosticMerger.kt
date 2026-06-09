package org.javacs.kt.diagnostic

import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import java.net.URI

fun mergeStructuralDiagnostics(
    primaryDiagnostics: List<Pair<URI, Diagnostic>>,
    structuralDiagnostics: List<Pair<URI, Diagnostic>>
): List<Pair<URI, Diagnostic>> {
    if (structuralDiagnostics.isEmpty()) return primaryDiagnostics

    val structuralCodes = structuralDiagnostics.mapNotNull { it.second.code?.left }.toSet()
    val hasUnterminatedToken = structuralCodes.any(::isUnterminatedTokenCode)
    val hasIncompleteExpression = STRUCTURAL_INCOMPLETE_EXPRESSION in structuralCodes

    val filteredPrimary = primaryDiagnostics.filterNot { (_, diagnostic) ->
        shouldSuppressPrimaryDiagnostic(
            diagnostic = diagnostic,
            hasIncompleteExpression = hasIncompleteExpression,
            hasUnterminatedToken = hasUnterminatedToken
        )
    }

    return filteredPrimary + structuralDiagnostics
}

// Structural diagnostic codes are defined in StructuralDiagnosticCodes.kt.

private fun isUnterminatedTokenCode(code: String): Boolean =
    code == STRUCTURAL_UNTERMINATED_BLOCK_COMMENT ||
        code == STRUCTURAL_UNTERMINATED_STRING_LITERAL ||
        code == STRUCTURAL_UNTERMINATED_RAW_STRING_LITERAL ||
        code == STRUCTURAL_UNTERMINATED_CHAR_LITERAL ||
        code == STRUCTURAL_UNTERMINATED_BACKTICK_IDENTIFIER

private fun shouldSuppressPrimaryDiagnostic(
    diagnostic: Diagnostic,
    hasIncompleteExpression: Boolean,
    hasUnterminatedToken: Boolean
): Boolean {
    val message = diagnostic.message.lowercase()
    val code = diagnostic.code?.left.orEmpty()

    return shouldSuppressForIncompleteExpression(diagnostic, message, code, hasIncompleteExpression) ||
        shouldSuppressForUnterminatedToken(message, hasUnterminatedToken)
}

private fun shouldSuppressForIncompleteExpression(
    diagnostic: Diagnostic,
    message: String,
    code: String,
    hasIncompleteExpression: Boolean,
): Boolean = hasIncompleteExpression &&
    (diagnostic.severity != DiagnosticSeverity.Error ||
        code == "UNUSED_VARIABLE" ||
        message.contains("type annotation") && message.contains("initialized"))

private fun shouldSuppressForUnterminatedToken(
    message: String,
    hasUnterminatedToken: Boolean,
): Boolean = hasUnterminatedToken &&
    (message.contains("function declaration must have a name") ||
        message.contains("incomplete function body") ||
        message.contains("unclosed") && message.contains("{"))
