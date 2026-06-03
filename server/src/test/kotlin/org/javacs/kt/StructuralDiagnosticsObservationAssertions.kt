package org.javacs.kt

import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.not
import org.junit.Assert.assertThat

internal fun SingleFileTestFixture.publishesForCurrentFile(): List<PublishDiagnosticsParams> {
    val currentFileUri = uri(file).toString()
    return diagnosticsHistory.filter { it.uri == currentFileUri }
}

internal fun assertPublishHistoryExists(publishesForFile: List<PublishDiagnosticsParams>) {
    assertThat(publishesForFile, not(hasSize(0)))
}

internal fun assertLatestSnapshotAligned(
    diagnosticsSize: Int,
    publishesForFile: List<PublishDiagnosticsParams>
) {
    assertPublishHistoryExists(publishesForFile)
    assertThat(diagnosticsSize, equalTo(publishesForFile.last().diagnostics.size))
}

internal fun assertPublishesClassified(publishesForFile: List<PublishDiagnosticsParams>) {
    assertPublishHistoryExists(publishesForFile)
    val nonEmptyPublishes = publishesForFile.filter { it.diagnostics.isNotEmpty() }
    val emptyPublishes = publishesForFile.filter { it.diagnostics.isEmpty() }
    assertThat(nonEmptyPublishes.size + emptyPublishes.size, equalTo(publishesForFile.size))
}

internal fun assertEmptyLatestConsistency(publishesForFile: List<PublishDiagnosticsParams>) {
    assertPublishHistoryExists(publishesForFile)
    val latestPublish = publishesForFile.last()
    val nonEmptyPublishes = publishesForFile.filter { it.diagnostics.isNotEmpty() }

    if (latestPublish.diagnostics.isEmpty()) {
        assertThat(hasClearAfterNonEmpty(publishesForFile), equalTo(nonEmptyPublishes.isNotEmpty()))
    }
}

internal fun hasClearAfterNonEmpty(publishes: List<PublishDiagnosticsParams>): Boolean {
    var seenNonEmpty = false
    publishes.forEach { publish ->
        if (publish.diagnostics.isNotEmpty()) {
            seenNonEmpty = true
        }
        if (seenNonEmpty && publish.diagnostics.isEmpty()) {
            return true
        }
    }
    return false
}
