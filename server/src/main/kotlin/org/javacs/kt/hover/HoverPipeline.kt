package org.javacs.kt.hover

import org.eclipse.lsp4j.Hover

internal fun hoverAt(context: HoverContext): Hover? =
    runHoverProvider("symbol", context) { symbolHoverAt(context) }
        ?: runHoverProvider("originalType", context) { originalTypeHoverAt(context) }
        ?: runHoverProvider("syntheticType", context) { syntheticTypeHoverAt(context) }
