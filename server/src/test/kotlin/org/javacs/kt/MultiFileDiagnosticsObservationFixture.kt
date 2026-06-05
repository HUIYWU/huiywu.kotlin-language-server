package org.javacs.kt

import org.eclipse.lsp4j.PublishDiagnosticsParams

open class MultiFileDiagnosticsObservationFixture(
    relativeWorkspaceRoot: String,
    config: Configuration = Configuration()
) : LanguageServerTestFixture(relativeWorkspaceRoot, config) {
    fun openFiles(vararg relativePaths: String) {
        relativePaths.forEach(::open)
    }
    fun waitForLint() {
        languageServer.textDocumentService.debounceLint.waitForPendingTask()
        waitForDiagnosticsPublishHistory()
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
