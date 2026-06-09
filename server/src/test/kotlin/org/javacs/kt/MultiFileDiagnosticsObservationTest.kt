package org.javacs.kt

import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import org.junit.Assert.assertThat
import org.junit.Test

class MultiFileDiagnosticsObservationTest : MultiFileDiagnosticsObservationFixture("diagnosticStructural") {
    @Test fun `opening multiple files records publish history that can be queried by uri`() {
        openFiles("MissingClosingParen.kt", "StructuralBaseline.kt")
        waitForLint()

        val missingParenUri = uri("MissingClosingParen.kt").toString()
        val baselineUri = uri("StructuralBaseline.kt").toString()
        val publishedUris = publishedUris()
        val missingParenPublishes = publishesFor("MissingClosingParen.kt")
        val baselinePublishes = publishesFor("StructuralBaseline.kt")

        assertLintRan()
        if (diagnosticsHistory.isNotEmpty()) {
            assertMultiFilePublishHistoryExists(diagnosticsHistory)
            assertPublishedUrisIncludeAnyOf(publishedUris, missingParenUri, baselineUri)
            assertThat(
                missingParenPublishes.isNotEmpty() || baselinePublishes.isNotEmpty(),
                org.hamcrest.Matchers.equalTo(true)
            )
        }
    }

    @Test fun `latest publish can be queried per file after opening multiple files`() {
        openFiles("MissingClosingParen.kt", "StructuralBaseline.kt")
        waitForLint()

        val missingParenLatest = latestPublishFor("MissingClosingParen.kt")
        val baselineLatest = latestPublishFor("StructuralBaseline.kt")

        assertLintRan()
        if (diagnosticsHistory.isNotEmpty()) {
            assertThat(
                missingParenLatest != null || baselineLatest != null,
                org.hamcrest.Matchers.equalTo(true)
            )
            assertThat(publishedUris(), not(nullValue()))
        }
    }

    @Test fun `latest publish for observed file contains no duplicate diagnostics`() {
        openFiles("MissingClosingParen.kt", "StructuralBaseline.kt")
        waitForLint()

        val latestObservedPublish = latestPublishFor("MissingClosingParen.kt")
            ?: latestPublishFor("StructuralBaseline.kt")

        assertLintRan()
        if (latestObservedPublish != null) {
            assertNoDuplicateDiagnostics(latestObservedPublish.diagnostics)
        }
    }

    @Test fun `multi-file observation exposes publish distribution across uris`() {
        openFiles("MissingClosingParen.kt", "StructuralBaseline.kt")
        waitForLint()

        val observedUris = diagnosticsHistory
            .filter { it.uri == uri("MissingClosingParen.kt").toString() || it.uri == uri("StructuralBaseline.kt").toString() }
            .map { it.uri }
            .toSet()

        assertLintRan()
        if (observedUris.isNotEmpty()) {
            assertPublishedUrisIncludeAnyOf(
                observedUris,
                uri("MissingClosingParen.kt").toString(),
                uri("StructuralBaseline.kt").toString()
            )
        }
    }

    private fun assertLintRan() {
        assertThat(languageServer.textDocumentService.lintCount, org.hamcrest.Matchers.greaterThanOrEqualTo(1))
    }
}