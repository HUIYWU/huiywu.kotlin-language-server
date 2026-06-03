package org.javacs.kt

import org.hamcrest.Matchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Test

class StructuralDiagnosticsCapabilitySmokeTest : SingleFileTestFixture("diagnosticStructural", "ExtraClosingBrace.kt") {
    @Test fun `open extra closing brace file publishes at least one non-empty diagnostics batch`() {
        languageServer.textDocumentService.debounceLint.waitForPendingTask()

        val publishesForFile = publishesForCurrentFile()
        val nonEmptyPublishes = publishesForFile.filter { it.diagnostics.isNotEmpty() }

        assertThat(languageServer.textDocumentService.lintCount >= 1, equalTo(true))
        assertThat(nonEmptyPublishes.isNotEmpty(), equalTo(true))
    }

    private fun publishesForCurrentFile() = diagnosticsHistory.filter { it.uri == uri(file).toString() }
}
