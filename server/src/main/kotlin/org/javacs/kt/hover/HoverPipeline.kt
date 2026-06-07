package org.javacs.kt.hover

import org.eclipse.lsp4j.Hover
import org.javacs.kt.util.logPerf

internal fun hoverAt(context: HoverContext): Hover? =
    logPerf("hover.pipeline", "file=${context.file.parse.name}, cursor=${context.cursor}") {
        runHoverProvider("symbol", context) { symbolHoverAt(context) }
            ?: runHoverProvider("originalType", context) { originalTypeHoverAt(context) }
            ?: runHoverProvider("syntheticType", context) { syntheticTypeHoverAt(context) }
    }
