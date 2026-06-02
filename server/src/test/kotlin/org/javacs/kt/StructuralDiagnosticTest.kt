package org.javacs.kt

import org.eclipse.lsp4j.Diagnostic
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.not
import org.junit.Assert.assertThat
import org.junit.Ignore
import org.junit.Test

private fun assertStructuralDiagnosticsVisible(test: SingleFileTestFixture) {
    test.languageServer.textDocumentService.debounceLint.waitForPendingTask()

    assertThat(test.languageServer.textDocumentService.lintCount, greaterThanOrEqualTo(1))
    assertThat(test.diagnostics, not(hasSize<Diagnostic>(0)))
    assertThat(test.errors, not(hasSize<Diagnostic>(0)))
}

@Ignore("Structural diagnostics capability baseline for future KLS enhancement; current fwcd behavior is not yet stable enough across environments")
class StructuralDiagnosticsExtraClosingBraceTest : SingleFileTestFixture("diagnosticStructural", "ExtraClosingBrace.kt") {
    @Test fun `open structural file reports diagnostics for extra closing brace`() {
        assertStructuralDiagnosticsVisible(this)
    }
}

@Ignore("Structural diagnostics capability baseline for future KLS enhancement; current fwcd behavior is not yet stable enough across environments")
class StructuralDiagnosticsMissingClosingBraceTest : SingleFileTestFixture("diagnosticStructural", "MissingClosingBrace.kt") {
    @Test fun `open structural file reports diagnostics for missing closing brace`() {
        assertStructuralDiagnosticsVisible(this)
    }
}

@Ignore("Structural diagnostics capability baseline for future KLS enhancement; current fwcd behavior is not yet stable enough across environments")
class StructuralDiagnosticsMissingClosingParenTest : SingleFileTestFixture("diagnosticStructural", "MissingClosingParen.kt") {
    @Test fun `open structural file reports diagnostics for missing closing paren`() {
        assertStructuralDiagnosticsVisible(this)
    }
}

@Ignore("Structural diagnostics capability baseline for future KLS enhancement; current fwcd behavior is not yet stable enough across environments")
class StructuralDiagnosticsIncompleteFunctionBodyTest : SingleFileTestFixture("diagnosticStructural", "IncompleteFunctionBody.kt") {
    @Test fun `open structural file reports diagnostics for incomplete function body`() {
        assertStructuralDiagnosticsVisible(this)
    }
}

@Ignore("Structural diagnostics capability baseline for future KLS enhancement; current fwcd behavior is not yet stable enough across environments")
class StructuralDiagnosticsIncompleteExpressionTest : SingleFileTestFixture("diagnosticStructural", "IncompleteExpression.kt") {
    @Test fun `open structural file reports diagnostics for incomplete expression`() {
        assertStructuralDiagnosticsVisible(this)
    }
}

@Ignore("Structural diagnostics capability baseline for future KLS enhancement; current fwcd behavior is not yet stable enough across environments")
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
