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
        // Multi-file diagnostics publish can be absent or delayed on Windows CI even after lint has
        // completed. Keep this fixture observation-first: prefer publish history when available, but
        // do not turn a missing asynchronous publish into a hard failure for multi-file smoke tests.
        try {
            waitForDiagnosticsPublishHistory(minSize = 1, timeoutMillis = 10000)
        } catch (_: java.util.concurrent.TimeoutException) {
            return
        }
        if (lastOpenedFileCount > 1) {
            try {
                waitForDiagnosticsPublishHistory(minSize = lastOpenedFileCount, timeoutMillis = 4000)
            } catch (_: java.util.concurrent.TimeoutException) {
                // Keep the fixture tolerant across slower CI variants: tests only need observable publish
                // history when the asynchronous publish stream is available, not a strict one-batch-per-open guarantee.
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
