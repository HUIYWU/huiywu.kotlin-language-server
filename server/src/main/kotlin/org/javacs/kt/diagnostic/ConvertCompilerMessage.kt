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

fun convertCompilerMessage(entry: CompilerMessageEntry, fallbackUri: URI? = null): Pair<URI, Diagnostic>? {
    val severity = severity(entry.severity) ?: return null
    val uri = messageUri(entry.location, fallbackUri) ?: return null
    return Pair(uri, diagnostic(entry, severity))
}

fun convertCompilerMessageOrFallback(entry: CompilerMessageEntry, fallbackUri: URI?): Pair<URI, Diagnostic>? {
    val converted = convertCompilerMessage(entry, fallbackUri)
    if (converted != null) return converted

    val fallback = fallbackUri ?: return null
    val severity = severity(entry.severity) ?: return null
    return Pair(fallback, diagnostic(entry.copy(location = null), severity))
}

private fun messageUri(location: CompilerMessageSourceLocation?, fallbackUri: URI?): URI? {
    val rawPath = location?.path?.takeIf { it.isNotBlank() }
    val directUri = rawPath?.let { path -> runCatching { Paths.get(path).toUri() }.getOrNull() }
    if (directUri != null) return directUri

    val fallback = fallbackUri ?: return null
    val fallbackPath = runCatching { Paths.get(fallback) }.getOrNull()
    val fileName = fallbackPath?.fileName?.toString()

    return when {
        rawPath == null -> fallback
        rawPath == fileName -> fallback
        rawPath.endsWith("/$fileName") || rawPath.endsWith("\\$fileName") -> fallback
        else -> fallback
    }
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
