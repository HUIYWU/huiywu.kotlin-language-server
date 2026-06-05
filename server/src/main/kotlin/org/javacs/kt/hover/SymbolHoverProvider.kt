package org.javacs.kt.hover

import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.Range
import org.javacs.kt.CompiledFile
import org.javacs.kt.completion.DECL_RENDERER
import org.javacs.kt.position.position
import org.javacs.kt.signaturehelp.getDocString
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.psi.KtNamedDeclaration

internal fun symbolHoverAt(context: HoverContext): Hover? =
    referenceHoverAt(context) ?: declarationHoverAt(context)

private fun referenceHoverAt(context: HoverContext): Hover? {
    val file = context.file
    val refTarget = file.referenceTargetAtPoint(context.cursor) ?: return null
    val (ref, target) = refTarget
    val javaDoc = getDocString(file, context.cursor)
    val location = ref.textRange
    val hoverText = DECL_RENDERER.render(target)
    val hover = MarkupContent(
        "markdown",
        listOf("```kotlin\n$hoverText\n```", javaDoc)
            .filter { content -> content.isNotEmpty() }
            .joinToString("\n---\n"),
    )
    val range = Range(
        position(file.content, location.startOffset),
        position(file.content, location.endOffset),
    )
    return Hover(hover, range)
}

private fun declarationHoverAt(context: HoverContext): Hover? {
    val declaration = context.originalDeclarationOnName()
    val file = context.file
    val descriptor = declaration?.let {
        suppressHoverAnalysisFailure("symbol.declaration", context) {
            file.descriptorForDeclaration(it)
        }
    }
    return declarationHoverOrNull(file, declaration, descriptor)
}

private fun declarationHoverOrNull(
    file: CompiledFile,
    declaration: KtNamedDeclaration?,
    descriptor: DeclarationDescriptor?,
): Hover? {
    return if (declaration != null && descriptor != null) {
        declarationHover(file, declaration, descriptor)
    } else {
        null
    }
}

private fun declarationHover(
    file: CompiledFile,
    declaration: KtNamedDeclaration,
    descriptor: DeclarationDescriptor,
): Hover {
    val nameRange = requireNotNull(declaration.nameIdentifier?.textRange)
    val hoverText = DECL_RENDERER.render(descriptor)
    val hover = MarkupContent("markdown", "```kotlin\n$hoverText\n```")
    val range = Range(
        position(file.content, nameRange.startOffset),
        position(file.content, nameRange.endOffset),
    )
    return Hover(hover, range)
}
