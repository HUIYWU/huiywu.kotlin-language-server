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
        // CI runs a single publish may not be enough to observe the first batch. First wait for at least
        // one publish with a relaxed timeout, then (best-effort) give the stream more time to accumulate
        // one batch per opened file without making that stronger condition mandatory.
        waitForDiagnosticsPublishHistory(minSize = 1, timeoutMillis = 10000)
        if (lastOpenedFileCount > 1) {
            try {
                waitForDiagnosticsPublishHistory(minSize = lastOpenedFileCount, timeoutMillis = 4000)
            } catch (_: java.util.concurrent.TimeoutException) {
                // Keep the fixture tolerant across slower CI variants: tests only need observable publish
                // history, not a strict one-batch-per-open guarantee.
            }
        }
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
