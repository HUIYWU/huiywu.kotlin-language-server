package org.javacs.kt.diagnostic

import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.javacs.kt.util.CompilerMessageEntry
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import java.net.URI
import java.nio.file.Paths
import kotlin.math.max

fun convertCompilerMessage(entry: CompilerMessageEntry, fallbackUri: URI? = null): Pair<URI, Diagnostic>? =
    severity(entry.severity)?.let { diagnosticSeverity ->
        messageUri(entry.location, fallbackUri)?.let { uri ->
            Pair(uri, diagnostic(entry, diagnosticSeverity))
        }
    }

fun convertCompilerMessageOrFallback(entry: CompilerMessageEntry, fallbackUri: URI?): Pair<URI, Diagnostic>? =
    convertCompilerMessage(entry, fallbackUri)
        ?: fallbackUri?.let { fallback ->
            severity(entry.severity)?.let { diagnosticSeverity ->
                Pair(fallback, diagnostic(entry.copy(location = null), diagnosticSeverity))
            }
        }

private fun messageUri(location: CompilerMessageSourceLocation?, fallbackUri: URI?): URI? {
    val rawPath = location?.path?.takeIf { it.isNotBlank() }
    val fallbackFileName = fallbackUri
        ?.let { uri -> runCatching { Paths.get(uri).fileName?.toString() }.getOrNull() }
    val matchesFallbackFileName = rawPath != null && fallbackFileName != null && (
        rawPath == fallbackFileName ||
            rawPath.endsWith("/$fallbackFileName") ||
            rawPath.endsWith("\\$fallbackFileName")
        )
    val directUri = rawPath
        ?.takeUnless { matchesFallbackFileName }
        ?.let { path -> runCatching { Paths.get(path).toUri() }.getOrNull() }

    return directUri ?: fallbackUri
}

private fun diagnostic(entry: CompilerMessageEntry, severity: DiagnosticSeverity): Diagnostic =
    Diagnostic(
        messageRange(entry.location),
        entry.message,
        severity,
        "kotlin-compiler",
        "COMPILER_MESSAGE"
    )

private fun messageRange(location: CompilerMessageSourceLocation?): Range {
    val startLine = max((location?.line ?: 1) - 1, 0)
    val startChar = max((location?.column ?: 1) - 1, 0)
    return Range(
        Position(startLine, startChar),
        endPosition(location, startLine, startChar)
    )
}

private fun endPosition(location: CompilerMessageSourceLocation?, startLine: Int, startChar: Int): Position {
    val rawEndLine = location
        ?.takeIf { it.lineEnd > 0 }
        ?.let { max(it.lineEnd - 1, startLine) }
        ?: startLine
    val rawEndChar = location
        ?.takeIf { it.columnEnd > 0 }
        ?.let { max(it.columnEnd - 1, 0) }
        ?: startChar + 1

    return when {
        rawEndLine > startLine -> Position(rawEndLine, rawEndChar)
        rawEndChar > startChar -> Position(startLine, rawEndChar)
        else -> Position(startLine, startChar + 1)
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
