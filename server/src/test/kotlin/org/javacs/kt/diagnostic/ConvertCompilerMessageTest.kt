package org.javacs.kt.diagnostic

import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.javacs.kt.util.CompilerMessageEntry
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.junit.Assert.assertThat
import org.junit.Test
import java.nio.file.Paths

class ConvertCompilerMessageTest {
    @Test fun `uses fallback uri when compiler message has no location`() {
        val fallbackUri = Paths.get("Fallback.kt").toUri()
        val converted = convertCompilerMessage(
            CompilerMessageEntry(
                CompilerMessageSeverity.ERROR,
                "Syntax error",
                null
            ),
            fallbackUri
        )

        assertThat(converted?.first, equalTo(fallbackUri))
        assertThat(converted?.second?.severity, equalTo(DiagnosticSeverity.Error))
        assertThat(converted?.second?.message, equalTo("Syntax error"))
    }

    @Test fun `uses fallback uri when compiler message path cannot be converted`() {
        val fallbackUri = Paths.get("Fallback.kt").toUri()
        val location = location(path = "\u0000bad-path", line = 3, column = 5, lineEnd = 3, columnEnd = 5)
        val converted = convertCompilerMessage(
            CompilerMessageEntry(
                CompilerMessageSeverity.ERROR,
                "Syntax error",
                location
            ),
            fallbackUri
        )

        assertThat(converted?.first, equalTo(fallbackUri))
        assertThat(converted?.second?.severity, equalTo(DiagnosticSeverity.Error))
    }

    @Test fun `uses fallback uri when compiler message path only matches fallback file name`() {
        val fallbackUri = Paths.get("Fallback.kt").toUri()
        val location = location(path = "Fallback.kt", line = 3, column = 5, lineEnd = 3, columnEnd = 9)
        val converted = convertCompilerMessage(
            CompilerMessageEntry(
                CompilerMessageSeverity.ERROR,
                "Syntax error",
                location
            ),
            fallbackUri
        )

        assertThat(converted?.first, equalTo(fallbackUri))
    }

    @Test fun `maps compiler message location to lsp range`() {
        val fallbackUri = Paths.get("Fallback.kt").toUri()
        val location = location(path = "", line = 4, column = 7, lineEnd = 4, columnEnd = 12)
        val converted = convertCompilerMessage(
            CompilerMessageEntry(
                CompilerMessageSeverity.WARNING,
                "Syntax warning",
                location
            ),
            fallbackUri
        )

        assertThat(converted?.second?.severity, equalTo(DiagnosticSeverity.Warning))
        assertThat(
            converted?.second?.range,
            equalTo(Range(Position(3, 6), Position(3, 11)))
        )
    }

    @Test fun `maps exception severity to error`() {
        val fallbackUri = Paths.get("Fallback.kt").toUri()
        val converted = convertCompilerMessage(
            CompilerMessageEntry(
                CompilerMessageSeverity.EXCEPTION,
                "Compiler exception",
                null
            ),
            fallbackUri
        )

        assertThat(converted?.second?.severity, equalTo(DiagnosticSeverity.Error))
    }

    @Test fun `maps strong warning severity to warning`() {
        val fallbackUri = Paths.get("Fallback.kt").toUri()
        val converted = convertCompilerMessage(
            CompilerMessageEntry(
                CompilerMessageSeverity.STRONG_WARNING,
                "Strong warning",
                null
            ),
            fallbackUri
        )

        assertThat(converted?.second?.severity, equalTo(DiagnosticSeverity.Warning))
    }

    @Test fun `uses one-character range when end position is missing`() {
        val fallbackUri = Paths.get("Fallback.kt").toUri()
        val location = location(path = "", line = 2, column = 4, lineEnd = 0, columnEnd = 0)
        val converted = convertCompilerMessage(
            CompilerMessageEntry(
                CompilerMessageSeverity.ERROR,
                "Syntax error",
                location
            ),
            fallbackUri
        )

        assertThat(
            converted?.second?.range,
            equalTo(Range(Position(1, 3), Position(1, 4)))
        )
    }

    @Test fun `clamps inverted end position to one-character range at start`() {
        val fallbackUri = Paths.get("Fallback.kt").toUri()
        val location = location(path = "", line = 5, column = 8, lineEnd = 4, columnEnd = 2)
        val converted = convertCompilerMessage(
            CompilerMessageEntry(
                CompilerMessageSeverity.ERROR,
                "Syntax error",
                location
            ),
            fallbackUri
        )

        assertThat(
            converted?.second?.range,
            equalTo(Range(Position(4, 7), Position(4, 8)))
        )
    }

    @Test fun `clamps zero line and column to zero-based origin`() {
        val fallbackUri = Paths.get("Fallback.kt").toUri()
        val location = location(path = "", line = 0, column = 0, lineEnd = 0, columnEnd = 0)
        val converted = convertCompilerMessage(
            CompilerMessageEntry(
                CompilerMessageSeverity.ERROR,
                "Syntax error",
                location
            ),
            fallbackUri
        )

        assertThat(
            converted?.second?.range,
            equalTo(Range(Position(0, 0), Position(0, 1)))
        )
    }

    @Test fun `clamps negative line and column to zero-based origin`() {
        val fallbackUri = Paths.get("Fallback.kt").toUri()
        val location = location(path = "", line = -3, column = -7, lineEnd = -1, columnEnd = -2)
        val converted = convertCompilerMessage(
            CompilerMessageEntry(
                CompilerMessageSeverity.ERROR,
                "Syntax error",
                location
            ),
            fallbackUri
        )

        assertThat(
            converted?.second?.range,
            equalTo(Range(Position(0, 0), Position(0, 1)))
        )
    }

    @Test fun `drops compiler messages that have no visible diagnostic severity`() {
        val fallbackUri = Paths.get("Fallback.kt").toUri()
        val converted = convertCompilerMessage(
            CompilerMessageEntry(
                CompilerMessageSeverity.INFO,
                "Compiler info",
                null
            ),
            fallbackUri
        )

        assertThat(converted, nullValue())
    }

    private fun location(
        path: String,
        line: Int,
        column: Int,
        lineEnd: Int,
        columnEnd: Int
    ) = object : CompilerMessageSourceLocation {
        override val path = path
        override val line = line
        override val column = column
        override val lineContent: String? = null
        override val lineEnd = lineEnd
        override val columnEnd = columnEnd
    }
}
