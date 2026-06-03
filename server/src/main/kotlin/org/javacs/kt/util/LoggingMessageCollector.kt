package org.javacs.kt.util

import org.javacs.kt.LOG
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector

data class CompilerMessageEntry(
    val severity: CompilerMessageSeverity,
    val message: String,
    val location: CompilerMessageSourceLocation?
)

object LoggingMessageCollector : MessageCollector {
    private val messages = mutableListOf<CompilerMessageEntry>()

    override fun clear() {
        synchronized(messages) {
            messages.clear()
        }
    }

    override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageSourceLocation?) {
        synchronized(messages) {
            messages.add(CompilerMessageEntry(severity, message, location))
        }
        LOG.debug("Kotlin compiler: [{}] {} @ {}", severity, message, location)
    }

    fun reportStructuralFallbackMessages(entries: Collection<CompilerMessageEntry>) {
        if (entries.isEmpty()) return
        synchronized(messages) {
            val hasCompilerErrors = messages.any { it.severity == CompilerMessageSeverity.ERROR || it.severity == CompilerMessageSeverity.EXCEPTION }
            if (!hasCompilerErrors) {
                messages.addAll(entries)
            }
        }
    }

    override fun hasErrors() = synchronized(messages) {
        messages.any { it.severity == CompilerMessageSeverity.ERROR || it.severity == CompilerMessageSeverity.EXCEPTION }
    }

    fun drain(): List<CompilerMessageEntry> = synchronized(messages) {
        val snapshot = messages.toList()
        messages.clear()
        snapshot
    }
}
