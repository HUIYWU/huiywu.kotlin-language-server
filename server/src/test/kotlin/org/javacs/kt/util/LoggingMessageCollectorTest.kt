package org.javacs.kt.util

import org.hamcrest.Matchers.equalTo
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.junit.After
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LoggingMessageCollectorTest {
    @Before fun setUp() {
        LoggingMessageCollector.clear()
    }

    @After fun tearDown() {
        LoggingMessageCollector.clear()
    }

    @Test fun `reports structural messages when collector has no compiler errors`() {
        val structuralEntries = listOf(
            CompilerMessageEntry(
                severity = CompilerMessageSeverity.ERROR,
                message = "Structural error",
                location = null
            )
        )

        LoggingMessageCollector.reportStructuralMessages(structuralEntries)

        assertThat(LoggingMessageCollector.drain(), equalTo(structuralEntries))
    }

    @Test fun `skips structural messages when compiler error already exists`() {
        LoggingMessageCollector.report(CompilerMessageSeverity.ERROR, "Native compiler error", null)
        LoggingMessageCollector.reportStructuralMessages(
            listOf(
                CompilerMessageEntry(
                    severity = CompilerMessageSeverity.ERROR,
                    message = "Structural error",
                    location = null
                )
            )
        )

        val drained = LoggingMessageCollector.drain()

        assertThat(drained.map { it.message }, equalTo(listOf("Native compiler error")))
    }

    @Test fun `allows structural messages when collector only has warnings`() {
        LoggingMessageCollector.report(CompilerMessageSeverity.WARNING, "Native compiler warning", null)
        LoggingMessageCollector.reportStructuralMessages(
            listOf(
                CompilerMessageEntry(
                    severity = CompilerMessageSeverity.ERROR,
                    message = "Structural error",
                    location = null
                )
            )
        )

        val drained = LoggingMessageCollector.drain()

        assertThat(
            drained.map { it.message },
            equalTo(listOf("Native compiler warning", "Structural error"))
        )
        assertTrue(drained.any { it.severity == CompilerMessageSeverity.ERROR })
    }
}
