package org.javacs.kt

import org.eclipse.lsp4j.PublishDiagnosticsParams
open class MultiFileDiagnosticsObservationFixture(
    relativeWorkspaceRoot: String,
    config: Configuration = Configuration()
) : LanguageServerTestFixture(relativeWorkspaceRoot, config) {
    private var lastOpenedFileCount: Int = 0

    fun openFiles(vararg relativePaths: String) {
        lastOpenedFileCount = relativePaths.size
        relativePaths.forEach(::open)
    }
    fun waitForLint() {
        languageServer.textDocumentService.debounceLint.waitForPendingTask()
        // Multi-file open can publish diagnostics asynchronously per document; on slower Windows/Java 11
        // CI runs a single publish may not be enough to observe the first batch. Wait for at least as
        // many publishes as opened files, but never less than one.
        waitForDiagnosticsPublishHistory(minSize = maxOf(1, lastOpenedFileCount), timeoutMillis = 6000)
    }


    fun clearPublishedDiagnosticsHistory() {
        diagnosticsHistory.clear()
    }


    fun publishesFor(relativePath: String): List<PublishDiagnosticsParams> {
        val targetUri = uri(relativePath).toString()
        return diagnosticsHistory.filter { it.uri == targetUri }
    }

    fun latestPublishFor(relativePath: String): PublishDiagnosticsParams? =
        publishesFor(relativePath).lastOrNull()

    fun emptyPublishesFor(relativePath: String): List<PublishDiagnosticsParams> =
        publishesFor(relativePath).filter { it.diagnostics.isEmpty() }

    fun nonEmptyPublishesFor(relativePath: String): List<PublishDiagnosticsParams> =
        publishesFor(relativePath).filter { it.diagnostics.isNotEmpty() }

    fun publishedUris(): Set<String> =
        diagnosticsHistory.map { it.uri }.toSet()
}
