package org.javacs.kt.hover

import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.renderer.ClassifierNamePolicy
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.renderer.RenderingFormat
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import org.jetbrains.kotlin.utils.IDEAPluginsCompatibilityAPI

private const val JAVA_DOC_START = "/**"
private const val JAVA_DOC_END = "*/"
private const val JAVA_DOC_LINE_PREFIX = '*'

internal val TYPE_RENDERER: DescriptorRenderer by lazy { DescriptorRenderer.COMPACT.withOptions {
    textFormat = RenderingFormat.PLAIN
    classifierNamePolicy = object: ClassifierNamePolicy {
        override fun renderClassifier(classifier: ClassifierDescriptor, renderer: DescriptorRenderer): String {
            if (DescriptorUtils.isAnonymousObject(classifier)) {
                return "<anonymous object>"
            }
            return ClassifierNamePolicy.SHORT.renderClassifier(classifier, renderer)
        }
    }
} }

internal fun renderJavaDoc(text: String): String {
    val split = text.split('\n')
    return split.mapIndexed { index, line ->
        when (index) {
            0 -> line.substring(line.indexOf(JAVA_DOC_START) + JAVA_DOC_START.length)
            split.size - 1 -> line.substring(line.indexOf(JAVA_DOC_END) + JAVA_DOC_END.length)
            else -> line.substring(line.indexOf(JAVA_DOC_LINE_PREFIX) + 1)
        }
    }.joinToString("\n")
}

@OptIn(IDEAPluginsCompatibilityAPI::class)
internal fun renderTypeFromOriginalContext(element: KtExpression, context: HoverContext): String? =
    suppressHoverAnalysisFailure("originalType.render", context) {
        if (element is KtCallableDeclaration) {
            val descriptor = context.file.descriptorForDeclaration(element)
            if (descriptor is CallableDescriptor) {
                return@suppressHoverAnalysisFailure descriptor.returnType?.let(TYPE_RENDERER::renderType)
            }
        }

        context.file.originalTypeOfExpression(element)?.let(TYPE_RENDERER::renderType)
    }

@OptIn(IDEAPluginsCompatibilityAPI::class)
internal fun renderTypeOf(element: KtExpression, bindingContext: BindingContext, context: HoverContext): String? =
    suppressHoverAnalysisFailure("syntheticType.render", context) {
        if (element is KtCallableDeclaration) {
            val descriptor = bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, element]
            if (descriptor != null) {
                when (descriptor) {
                    is CallableDescriptor -> return@suppressHoverAnalysisFailure descriptor.returnType?.let(TYPE_RENDERER::renderType)
                }
            }
        }

        val expressionType = bindingContext[BindingContext.EXPRESSION_TYPE_INFO, element]?.type ?: element.getType(bindingContext)
        val result = expressionType?.let { TYPE_RENDERER.renderType(it) } ?: return@suppressHoverAnalysisFailure null

        val smartCast = bindingContext[BindingContext.SMARTCAST, element]
        if (smartCast != null && element is KtReferenceExpression) {
            val declaredType = (bindingContext[BindingContext.REFERENCE_TARGET, element] as? CallableDescriptor)?.returnType
            if (declaredType != null) {
                return@suppressHoverAnalysisFailure result + " (smart cast from " + TYPE_RENDERER.renderType(declaredType) + ")"
            }
        }
        result
    }
