package org.javacs.kt

import org.eclipse.lsp4j.Diagnostic
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.not
import org.javacs.kt.diagnostic.STRUCTURAL_DIAGNOSTIC_SOURCE
import org.javacs.kt.diagnostic.STRUCTURAL_INCOMPLETE_EXPRESSION
import org.javacs.kt.diagnostic.STRUCTURAL_MISMATCHED_DELIMITER
import org.javacs.kt.diagnostic.STRUCTURAL_UNCLOSED_DELIMITER
import org.javacs.kt.diagnostic.STRUCTURAL_UNMATCHED_CLOSING
import org.javacs.kt.diagnostic.STRUCTURAL_UNTERMINATED_BACKTICK_IDENTIFIER
import org.javacs.kt.diagnostic.STRUCTURAL_UNTERMINATED_BLOCK_COMMENT
import org.javacs.kt.diagnostic.STRUCTURAL_UNTERMINATED_CHAR_LITERAL
import org.javacs.kt.diagnostic.STRUCTURAL_UNTERMINATED_RAW_STRING_LITERAL
import org.javacs.kt.diagnostic.STRUCTURAL_UNTERMINATED_STRING_LITERAL
import org.javacs.kt.diagnostic.structuralDiagnostics
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Test

private fun assertStructuralDiagnosticsVisible(test: SingleFileTestFixture) {
    test.languageServer.textDocumentService.debounceLint.waitForPendingTask()

    assertThat(test.languageServer.textDocumentService.lintCount, greaterThanOrEqualTo(1))
    assertThat(test.diagnostics, not(hasSize<Diagnostic>(0)))
    assertThat(test.errors, not(hasSize<Diagnostic>(0)))
}

private fun diagnosticCode(diagnostic: Diagnostic): String? = diagnostic.code?.left

private fun structuralDiagnosticsFor(test: SingleFileTestFixture): List<Diagnostic> {
    val publishedStructuralDiagnostics = test.diagnostics.filter { it.source == STRUCTURAL_DIAGNOSTIC_SOURCE }
    if (publishedStructuralDiagnostics.isNotEmpty()) return publishedStructuralDiagnostics

    val content = test.workspaceRoot.resolve(test.file).toFile().readText()
    return structuralDiagnostics(test.uri(test.file), content).map { it.second }
}

private fun assertHasDiagnosticCode(test: SingleFileTestFixture, code: String) {
    test.languageServer.textDocumentService.debounceLint.waitForPendingTask()

    assertThat(structuralDiagnosticsFor(test).map(::diagnosticCode), hasItem(code))
}

private fun assertNoDiagnosticMessage(test: SingleFileTestFixture, messageFragment: String) {
    assertTrue(
        "Expected no diagnostic containing '$messageFragment', actual messages=${test.diagnostics.map { it.message }}",
        test.diagnostics.none { it.message.contains(messageFragment, ignoreCase = true) }
    )
}

private fun assertHasDiagnosticMessage(test: SingleFileTestFixture, messageFragment: String) {
    test.languageServer.textDocumentService.debounceLint.waitForPendingTask()

    val diagnostics = structuralDiagnosticsFor(test)
    assertTrue(
        "Expected diagnostic containing '$messageFragment', actual messages=${diagnostics.map { it.message }}",
        diagnostics.any { it.message.contains(messageFragment, ignoreCase = true) }
    )
}

class StructuralDiagnosticsExtraClosingBraceTest : SingleFileTestFixture("diagnosticStructural", "ExtraClosingBrace.kt") {
    @Test fun `open structural file reports diagnostics for extra closing brace`() {
        assertStructuralDiagnosticsVisible(this)
    }

    @Test fun `open extra closing brace reports unexpected closing brace`() {
        assertHasDiagnosticCode(this, STRUCTURAL_UNMATCHED_CLOSING)
        assertHasDiagnosticMessage(this, "Unexpected closing '}'")
    }
}

class StructuralDiagnosticsMissingClosingBraceTest : SingleFileTestFixture("diagnosticStructural", "MissingClosingBrace.kt") {
    @Test fun `open structural file reports diagnostics for missing closing brace`() {
        assertStructuralDiagnosticsVisible(this)
    }

    @Test fun `open missing closing brace reports unclosed brace`() {
        assertHasDiagnosticCode(this, STRUCTURAL_UNCLOSED_DELIMITER)
        assertHasDiagnosticMessage(this, "Unclosed '{'; expected '}'")
    }
}

class StructuralDiagnosticsMissingClosingParenTest : SingleFileTestFixture("diagnosticStructural", "MissingClosingParen.kt") {
    @Test fun `open structural file reports diagnostics for missing closing paren`() {
        assertStructuralDiagnosticsVisible(this)
    }

    @Test fun `open missing closing paren reports unclosed paren`() {
        assertHasDiagnosticCode(this, STRUCTURAL_UNCLOSED_DELIMITER)
        assertHasDiagnosticMessage(this, "Unclosed '('; expected ')'")
    }
}

class StructuralDiagnosticsMismatchedClosingDelimiterTest : SingleFileTestFixture("diagnosticStructural", "MismatchedClosingDelimiter.kt") {
    @Test fun `open mismatched closing delimiter reports expected delimiter`() {
        assertHasDiagnosticCode(this, STRUCTURAL_MISMATCHED_DELIMITER)
        assertHasDiagnosticMessage(this, "Mismatched closing ')'; expected '}' for '{'")
    }
}

class StructuralDiagnosticsIncompleteFunctionBodyTest : SingleFileTestFixture("diagnosticStructural", "IncompleteFunctionBody.kt") {
    @Test fun `open structural file reports diagnostics for incomplete function body`() {
        assertStructuralDiagnosticsVisible(this)
    }
}

class StructuralDiagnosticsIncompleteExpressionTest : SingleFileTestFixture("diagnosticStructural", "IncompleteExpression.kt") {
    @Test fun `open structural file reports diagnostics for incomplete expression`() {
        assertStructuralDiagnosticsVisible(this)
    }

    @Test fun `open incomplete expression prefers equals diagnostic over secondary warnings`() {
        assertHasDiagnosticCode(this, STRUCTURAL_INCOMPLETE_EXPRESSION)
        assertNoDiagnosticMessage(this, "unused")
        assertNoDiagnosticMessage(this, "type annotation")
    }
}

class StructuralDiagnosticsIncompleteExpressionAfterOperatorTest : SingleFileTestFixture("diagnosticStructural", "IncompleteExpressionAfterOperator.kt") {
    @Test fun `open incomplete expression after trailing operator reports operator diagnostic`() {
        assertHasDiagnosticCode(this, STRUCTURAL_INCOMPLETE_EXPRESSION)
        assertHasDiagnosticMessage(this, "Incomplete expression after '+'")
        assertNoDiagnosticMessage(this, "type annotation")
    }
}

class StructuralDiagnosticsIncompleteExpressionAfterPrefixOperatorTest : SingleFileTestFixture("diagnosticStructural", "IncompleteExpressionAfterPrefixOperator.kt") {
    @Test fun `open incomplete expression after prefix operator reports operator diagnostic`() {
        assertHasDiagnosticCode(this, STRUCTURAL_INCOMPLETE_EXPRESSION)
        assertHasDiagnosticMessage(this, "Incomplete expression after '+'")
        assertNoDiagnosticMessage(this, "type annotation")
    }
}

class StructuralDiagnosticsIncompleteExpressionAfterCallableReferenceTest : SingleFileTestFixture("diagnosticStructural", "IncompleteExpressionAfterCallableReference.kt") {
    @Test fun `open incomplete expression after callable reference reports operator diagnostic`() {
        assertHasDiagnosticCode(this, STRUCTURAL_INCOMPLETE_EXPRESSION)
        assertHasDiagnosticMessage(this, "Incomplete expression after '::'")
    }
}

class StructuralDiagnosticsIncompleteExpressionAfterAsTest : SingleFileTestFixture("diagnosticStructural", "IncompleteExpressionAfterAs.kt") {
    @Test fun `open incomplete expression after as reports keyword diagnostic`() {
        assertHasDiagnosticCode(this, STRUCTURAL_INCOMPLETE_EXPRESSION)
        assertHasDiagnosticMessage(this, "Incomplete expression after 'as'")
    }
}

class StructuralDiagnosticsIncompleteExpressionAfterSafeAsTest : SingleFileTestFixture("diagnosticStructural", "IncompleteExpressionAfterSafeAs.kt") {
    @Test fun `open incomplete expression after safe as reports keyword diagnostic`() {
        assertHasDiagnosticCode(this, STRUCTURAL_INCOMPLETE_EXPRESSION)
        assertHasDiagnosticMessage(this, "Incomplete expression after 'as?'")
    }
}

class StructuralDiagnosticsIncompleteExpressionAfterIsTest : SingleFileTestFixture("diagnosticStructural", "IncompleteExpressionAfterIs.kt") {
    @Test fun `open incomplete expression after is reports keyword diagnostic`() {
        assertHasDiagnosticCode(this, STRUCTURAL_INCOMPLETE_EXPRESSION)
        assertHasDiagnosticMessage(this, "Incomplete expression after 'is'")
    }
}

class StructuralDiagnosticsIncompleteExpressionAfterElvisTest : SingleFileTestFixture("diagnosticStructural", "IncompleteExpressionAfterElvis.kt") {
    @Test fun `open incomplete expression after elvis reports operator diagnostic`() {
        assertHasDiagnosticCode(this, STRUCTURAL_INCOMPLETE_EXPRESSION)
        assertHasDiagnosticMessage(this, "Incomplete expression after '?:'")
    }
}

class StructuralDiagnosticsIncompleteExpressionAfterSafeCallTest : SingleFileTestFixture("diagnosticStructural", "IncompleteExpressionAfterSafeCall.kt") {
    @Test fun `open incomplete expression after safe call reports operator diagnostic`() {
        assertHasDiagnosticCode(this, STRUCTURAL_INCOMPLETE_EXPRESSION)
        assertHasDiagnosticMessage(this, "Incomplete expression after '?.'")
    }
}

class StructuralDiagnosticsIncompleteReturnExpressionAfterOperatorTest : SingleFileTestFixture("diagnosticStructural", "IncompleteReturnExpressionAfterOperator.kt") {
    @Test fun `open incomplete return expression after trailing operator reports operator diagnostic`() {
        assertHasDiagnosticCode(this, STRUCTURAL_INCOMPLETE_EXPRESSION)
        assertHasDiagnosticMessage(this, "Incomplete expression after '+'")
    }
}

class StructuralDiagnosticsIncompleteBooleanExpressionAfterAndTest : SingleFileTestFixture("diagnosticStructural", "IncompleteBooleanExpressionAfterAnd.kt") {
    @Test fun `open incomplete boolean expression after and reports operator diagnostic`() {
        assertHasDiagnosticCode(this, STRUCTURAL_INCOMPLETE_EXPRESSION)
        assertHasDiagnosticMessage(this, "Incomplete expression after '&&'")
    }
}

class StructuralDiagnosticsUnterminatedBacktickIdentifierTest : SingleFileTestFixture("diagnosticStructural", "UnterminatedBacktickIdentifier.kt") {
    @Test fun `open unterminated backtick identifier reports precise diagnostic`() {
        assertHasDiagnosticCode(this, STRUCTURAL_UNTERMINATED_BACKTICK_IDENTIFIER)
        assertNoDiagnosticMessage(this, "Function declaration must have a name")
    }
}

class StructuralDiagnosticsUnterminatedStringLiteralTest : SingleFileTestFixture("diagnosticStructural", "UnterminatedStringLiteral.kt") {
    @Test fun `open unterminated string literal suppresses cascading delimiter diagnostics`() {
        assertHasDiagnosticCode(this, STRUCTURAL_UNTERMINATED_STRING_LITERAL)
        assertNoDiagnosticMessage(this, "Incomplete function body")
        assertNoDiagnosticMessage(this, "Unclosed '{'")
    }
}

class StructuralDiagnosticsUnterminatedRawStringLiteralTest : SingleFileTestFixture("diagnosticStructural", "UnterminatedRawStringLiteral.kt") {
    @Test fun `open unterminated raw string literal suppresses cascading delimiter diagnostics`() {
        assertHasDiagnosticCode(this, STRUCTURAL_UNTERMINATED_RAW_STRING_LITERAL)
        assertNoDiagnosticMessage(this, "Incomplete function body")
        assertNoDiagnosticMessage(this, "Unclosed '{'")
    }
}

class StructuralDiagnosticsUnterminatedCharLiteralTest : SingleFileTestFixture("diagnosticStructural", "UnterminatedCharLiteral.kt") {
    @Test fun `open unterminated char literal suppresses cascading delimiter diagnostics`() {
        assertHasDiagnosticCode(this, STRUCTURAL_UNTERMINATED_CHAR_LITERAL)
        assertNoDiagnosticMessage(this, "Incomplete function body")
        assertNoDiagnosticMessage(this, "Unclosed '{'")
    }
}

class StructuralDiagnosticsUnterminatedBlockCommentTest : SingleFileTestFixture("diagnosticStructural", "UnterminatedBlockComment.kt") {
    @Test fun `open unterminated block comment suppresses cascading delimiter diagnostics`() {
        assertHasDiagnosticCode(this, STRUCTURAL_UNTERMINATED_BLOCK_COMMENT)
        assertNoDiagnosticMessage(this, "Incomplete function body")
        assertNoDiagnosticMessage(this, "Unclosed '{'")
    }
}

class StructuralDiagnosticsEditFlowTest : SingleFileTestFixture("diagnosticStructural", "StructuralBaseline.kt") {
    @Test fun `change introduces extra closing brace and reports diagnostics`() {
        replace(file, 5, 2, "", "\n}")
        assertStructuralDiagnosticsVisible(this)
    }

    @Test fun `change introduces missing closing paren and reports diagnostics`() {
        replace(file, 3, 21, ")", "")
        assertStructuralDiagnosticsVisible(this)
    }

    @Test fun `change introduces incomplete expression and save still reports diagnostics`() {
        replace(file, 3, 9, "println(\"ok\")", "val value =")
        languageServer.textDocumentService.debounceLint.waitForPendingTask()
        save(file)
        assertStructuralDiagnosticsVisible(this)
    }
}