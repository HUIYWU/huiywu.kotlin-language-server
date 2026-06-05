package org.javacs.kt.hover

import com.intellij.psi.PsiDocCommentBase
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.MarkupContent
import org.javacs.kt.util.findParent
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtExpression

internal fun originalTypeHoverAt(context: HoverContext): Hover? {
    val expression = context.originalExpression
    val hoverText = expression?.let {
        suppressHoverAnalysisFailure("originalType", context) {
            renderTypeFromOriginalContext(it, context)
        }
    }
    return hoverOrNull(expression, hoverText)
}

internal fun syntheticTypeHoverAt(context: HoverContext): Hover? {
    val file = context.file
    val expression = file.parseAtPoint(context.cursor)?.findParent<KtExpression>()
    val scope = context.scope
    val hoverText = if (expression != null && expression !is KtDeclaration && scope != null) {
        suppressHoverAnalysisFailure("syntheticType", context) {
            renderTypeOf(expression, file.bindingContextOf(expression, scope), context)
        }
    } else {
        null
    }
    val safeExpression = expression.takeUnless { it is KtDeclaration }
    return hoverOrNull(safeExpression, hoverText)
}

private fun hoverOrNull(expression: KtExpression?, hoverText: String?): Hover? {
    return if (expression != null && hoverText != null) {
        typeHover(expression, hoverText)
    } else {
        null
    }
}

private fun typeHover(expression: KtExpression, hoverText: String): Hover {
    val javaDoc = expression.children
        .mapNotNull { child -> (child as? PsiDocCommentBase)?.text }
        .map(::renderJavaDoc)
        .firstOrNull()
        ?: ""
    val hover = MarkupContent(
        "markdown",
        listOf("```kotlin\n$hoverText\n```", javaDoc)
            .filter { content -> content.isNotEmpty() }
            .joinToString("\n---\n"),
    )
    return Hover(hover)
}
