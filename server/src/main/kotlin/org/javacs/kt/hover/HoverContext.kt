package org.javacs.kt.hover

import com.intellij.psi.PsiElement
import org.javacs.kt.CompiledFile
import org.javacs.kt.util.findParent
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.resolve.scopes.LexicalScope

/**
 * Shared hover resolution context.
 *
 * The long-term hover pipeline should prefer information from the original parsed file and the
 * existing whole-file BindingContext before falling back to synthetic expression analysis. Keeping
 * these values centralized makes each hover provider explicit about which layer it consumes.
 */
class HoverContext private constructor(
    val file: CompiledFile,
    val cursor: Int,
    private val original: OriginalElements,
) {
    val originalElement: PsiElement?
        get() = original.element

    val originalKtElement: KtElement?
        get() = original.ktElement

    val originalExpression: KtExpression?
        get() = original.expression

    val originalDeclaration: KtNamedDeclaration?
        get() = original.declaration

    val originalReference: KtReferenceExpression?
        get() = original.reference

    val scope: LexicalScope? by lazy { file.scopeAtPoint(cursor) }

    companion object {
        fun from(file: CompiledFile, cursor: Int): HoverContext {
            val originalElement = file.originalElementAtPoint(cursor)
            val original = OriginalElements(
                element = originalElement,
                ktElement = originalElement?.findParent<KtElement>(),
                expression = originalElement?.findParent<KtExpression>(),
                declaration = originalElement?.findParent<KtNamedDeclaration>(),
                reference = originalElement?.findParent<KtReferenceExpression>(),
            )
            return HoverContext(file = file, cursor = cursor, original = original)
        }
    }
}

internal data class OriginalElements(
    val element: PsiElement?,
    val ktElement: KtElement?,
    val expression: KtExpression?,
    val declaration: KtNamedDeclaration?,
    val reference: KtReferenceExpression?,
)

fun HoverContext.originalDeclarationOnName(): KtNamedDeclaration? {
    val declaration = originalDeclaration
    val nameRange = declaration?.nameIdentifier?.textRange
    return declaration?.takeIf { nameRange?.contains(cursor) == true }
}
