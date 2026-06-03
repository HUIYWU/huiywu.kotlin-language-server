package org.javacs.kt

import org.hamcrest.Matchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Test

class StructuralMissingClosingBraceCapabilitySmokeTest : SingleFileTestFixture("diagnosticStructural", "MissingClosingBrace.kt") {
    @Test fun `open missing closing brace file publishes at least one non-empty diagnostics batch`() {
        languageServer.textDocumentService.debounceLint.waitForPendingTask()

        val publishesForFile = diagnosticsHistory.filter { it.uri == uri(file).toString() }
        val nonEmptyPublishes = publishesForFile.filter { it.diagnostics.isNotEmpty() }

        assertThat(languageServer.textDocumentService.lintCount >= 1, equalTo(true))
        assertThat(nonEmptyPublishes.isNotEmpty(), equalTo(true))
    }
}