package org.javacs.kt.hover

import org.eclipse.lsp4j.Hover
import org.javacs.kt.CompiledFile

fun hoverAt(file: CompiledFile, cursor: Int): Hover? =
    hoverAt(HoverContext.from(file, cursor))
