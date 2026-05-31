package org.javacs.kt.diagnostic

import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.javacs.kt.util.CompilerMessageEntry
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import java.net.URI
import java.nio.file.Paths
import kotlin.math.max

fun convertCompilerMessage(entry: CompilerMessageEntry): Pair<URI, Diagnostic>? {
    val location = entry.location
    val path = location?.path
    val severity = severity(entry.severity)

    return if (location != null && severity != null && !path.isNullOrBlank()) {
        runCatching { Paths.get(path).toUri() }
            .getOrNull()
            ?.let { uri ->
                val startLine = max(location.line - 1, 0)
                val startChar = max(location.column - 1, 0)

                val rawEndLine = if (location.lineEnd > 0) max(location.lineEnd - 1, startLine) else startLine
                val rawEndChar = if (location.columnEnd > 0) max(location.columnEnd - 1, 0) else startChar + 1

                val end = when {
                    rawEndLine > startLine -> Position(rawEndLine, rawEndChar)
                    rawEndChar > startChar -> Position(startLine, rawEndChar)
                    else -> Position(startLine, startChar + 1)
                }

                Pair(
                    uri,
                    Diagnostic(
                        Range(Position(startLine, startChar), end),
                        entry.message,
                        severity,
                        "kotlin-compiler",
                        "COMPILER_MESSAGE"
                    )
                )
            }
    } else {
        null
    }
}

private fun severity(severity: CompilerMessageSeverity): DiagnosticSeverity? =
    when (severity) {
        CompilerMessageSeverity.ERROR,
        CompilerMessageSeverity.EXCEPTION -> DiagnosticSeverity.Error
        CompilerMessageSeverity.WARNING,
        CompilerMessageSeverity.STRONG_WARNING -> DiagnosticSeverity.Warning
        else -> null
    }
