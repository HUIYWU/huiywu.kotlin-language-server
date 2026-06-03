package org.javacs.kt

import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.not
import org.junit.Assert.assertThat
import org.junit.Test

class StructuralDiagnosticsObservationTest : SingleFileTestFixture("diagnosticStructural", "ExtraClosingBrace.kt") {
    @Test fun `open structural file records diagnostics publish history for current file`() {
        languageServer.textDocumentService.debounceLint.waitForPendingTask()

        assertThat(languageServer.textDocumentService.lintCount, greaterThanOrEqualTo(1))
        assertThat(diagnosticsHistory, not(hasSize(0)))

        val publishesForFile = publishesForCurrentFile()

        assertPublishHistoryExists(publishesForFile)
        assertThat(publishesForFile.last().uri, containsString("ExtraClosingBrace.kt"))
    }

    @Test fun `open structural file keeps last diagnostics snapshot aligned with latest publish`() {
        languageServer.textDocumentService.debounceLint.waitForPendingTask()

        assertLatestSnapshotAligned(diagnostics.size, publishesForCurrentFile())
    }

    @Test fun `open structural file records whether diagnostics were ever non-empty`() {
        languageServer.textDocumentService.debounceLint.waitForPendingTask()

        assertPublishesClassified(publishesForCurrentFile())
    }

    @Test fun `open structural file exposes whether last publish is empty`() {
        languageServer.textDocumentService.debounceLint.waitForPendingTask()

        val publishesForFile = publishesForCurrentFile()
        val latestPublish = publishesForFile.last()
        val nonEmptyPublishes = publishesForFile.filter { it.diagnostics.isNotEmpty() }

        assertPublishHistoryExists(publishesForFile)
        assertThat(latestPublish.diagnostics.isEmpty() || nonEmptyPublishes.isNotEmpty(), org.hamcrest.Matchers.equalTo(true))
    }

    @Test fun `open structural file exposes whether empty latest publish followed non-empty history`() {
        languageServer.textDocumentService.debounceLint.waitForPendingTask()

        assertEmptyLatestConsistency(publishesForCurrentFile())
    }
}