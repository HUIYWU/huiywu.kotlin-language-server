package org.javacs.kt

import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.not
import org.junit.Assert.assertThat
import org.junit.Test

class StructuralDiagnosticsEditObservationTest : SingleFileTestFixture("diagnosticStructural", "StructuralBaseline.kt") {
    @Test fun `change to extra closing brace records publish history for current file`() {
        val historyBefore = publishesForCurrentFile().size

        replace(file, 5, 2, "", "\n}")
        languageServer.textDocumentService.debounceLint.waitForPendingTask()

        val publishesForFile = publishesForCurrentFile()

        assertThat(languageServer.textDocumentService.lintCount, greaterThanOrEqualTo(2))
        assertPublishHistoryExists(publishesForFile)
        assertThat(publishesForFile.size, greaterThan(historyBefore))
        assertLatestSnapshotAligned(diagnostics.size, publishesForFile)
    }

    @Test fun `change to missing closing paren records publish history for current file`() {
        val historyBefore = publishesForCurrentFile().size

        replace(file, 3, 21, ")", "")
        languageServer.textDocumentService.debounceLint.waitForPendingTask()

        val publishesForFile = publishesForCurrentFile()

        assertThat(languageServer.textDocumentService.lintCount, greaterThanOrEqualTo(2))
        assertPublishHistoryExists(publishesForFile)
        assertThat(publishesForFile.size, greaterThan(historyBefore))
        assertPublishesClassified(publishesForFile)
        assertLatestSnapshotAligned(diagnostics.size, publishesForFile)
    }

    @Test fun `change to missing closing paren exposes whether empty latest publish followed non-empty history`() {
        replace(file, 3, 21, ")", "")
        languageServer.textDocumentService.debounceLint.waitForPendingTask()

        assertEmptyLatestConsistency(publishesForCurrentFile())
    }
}