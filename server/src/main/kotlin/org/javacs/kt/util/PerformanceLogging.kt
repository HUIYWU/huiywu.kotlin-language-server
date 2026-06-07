package org.javacs.kt.util

import java.util.Locale
import org.javacs.kt.LOG

private const val PERF_LOG_AS_INFO = false
private const val NANOS_PER_MILLISECOND = 1_000_000.0

internal inline fun <T> logPerf(operation: String, details: String = "", block: () -> T): T {
    val startedAt = System.nanoTime()
    return try {
        block()
    } finally {
        val elapsedMs = (System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND
        val elapsedMsText = String.format(Locale.ROOT, "%.2f", elapsedMs)
        if (details.isBlank()) {
            if (PERF_LOG_AS_INFO) {
                LOG.info("PERF {} took {} ms", operation, elapsedMsText)
            } else {
                LOG.debug("PERF {} took {} ms", operation, elapsedMsText)
            }
        } else {
            if (PERF_LOG_AS_INFO) {
                LOG.info("PERF {} took {} ms ({})", operation, elapsedMsText, details)
            } else {
                LOG.debug("PERF {} took {} ms ({})", operation, elapsedMsText, details)
            }
        }
    }
}
