package org.javacs.kt

import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.not
import org.junit.Assert.assertThat
import org.junit.Test

class StructuralDiagnosticsSaveObservationTest : SingleFileTestFixture("diagnosticStructural", "StructuralBaseline.kt") {
    @Test fun `save after incomplete expression records publish history for current file`() {
        val historyBefore = publishesForCurrentFile().size

        replace(file, 3, 9, "println(\"ok\")", "val value =")
        languageServer.textDocumentService.debounceLint.waitForPendingTask()
        save(file)
        languageServer.textDocumentService.debounceLint.waitForPendingTask()

        val publishesForFile = publishesForCurrentFile()

        assertThat(languageServer.textDocumentService.lintCount, greaterThanOrEqualTo(2))
        assertPublishHistoryExists(publishesForFile)
        assertThat(publishesForFile.size, greaterThan(historyBefore))
        assertPublishesClassified(publishesForFile)
        assertLatestSnapshotAligned(diagnostics.size, publishesForFile)
    }

    @Test fun `save after extra closing brace keeps current file publish stream observable`() {
        val historyBefore = publishesForCurrentFile().size

        replace(file, 5, 2, "", "\n}")
        languageServer.textDocumentService.debounceLint.waitForPendingTask()
        save(file)
        languageServer.textDocumentService.debounceLint.waitForPendingTask()

        val publishesForFile = publishesForCurrentFile()

        assertThat(languageServer.textDocumentService.lintCount, greaterThanOrEqualTo(2))
        assertPublishHistoryExists(publishesForFile)
        assertThat(publishesForFile.size, greaterThan(historyBefore))
        assertLatestSnapshotAligned(diagnostics.size, publishesForFile)
    }

    @Test fun `save after incomplete expression exposes whether empty latest publish followed non-empty history`() {
        replace(file, 3, 9, "println(\"ok\")", "val value =")
        languageServer.textDocumentService.debounceLint.waitForPendingTask()
        save(file)
        languageServer.textDocumentService.debounceLint.waitForPendingTask()

        assertEmptyLatestConsistency(publishesForCurrentFile())
    }
}
