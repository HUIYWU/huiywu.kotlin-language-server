package org.javacs.kt

import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan

internal fun assertMultiFilePublishHistoryExists(publishes: List<PublishDiagnosticsParams>) {
    assertThat(publishes.size, greaterThan(0))
}

internal fun assertPublishedUrisIncludeAnyOf(
    publishedUris: Set<String>,
    vararg expectedUris: String
) {
    assertThat(expectedUris.any { it in publishedUris }, equalTo(true))
}

internal fun assertLatestPublishIsEmpty(publish: PublishDiagnosticsParams?) {
    assertThat(publish != null, equalTo(true))
    assertThat(publish?.diagnostics?.isEmpty(), equalTo(true))
}

internal fun assertNoDuplicateDiagnostics(diagnostics: List<Diagnostic>) {
    val distinct = diagnostics.distinctBy {
        listOf(
            it.range.start.line,
            it.range.start.character,
            it.range.end.line,
            it.range.end.character,
            it.severity,
            it.message,
        )
    }
    assertThat(distinct.size, equalTo(diagnostics.size))
}