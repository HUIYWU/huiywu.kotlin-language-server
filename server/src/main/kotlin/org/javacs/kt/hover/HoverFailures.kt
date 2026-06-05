package org.javacs.kt.hover

import org.javacs.kt.LOG
import org.javacs.kt.util.KotlinLSException
import org.jetbrains.kotlin.resolve.lazy.NoDescriptorForDeclarationException
import org.jetbrains.kotlin.util.KotlinFrontEndException

/**
 * Hover-specific safety boundary.
 *
 * Hover is an optional UI affordance; analysis failures in a single provider should make that
 * provider decline the hover instead of surfacing as a JSON-RPC internal error. Non-hover callers
 * should keep their own failure policy and must not depend on this helper implicitly.
 */
internal fun <T> runHoverProvider(provider: String, context: HoverContext, block: () -> T?): T? =
    try {
        block()
    } catch (e: NoDescriptorForDeclarationException) {
        logHoverProviderFailure(provider, context, e)
        null
    } catch (e: KotlinLSException) {
        logHoverProviderFailure(provider, context, e)
        null
    } catch (e: KotlinFrontEndException) {
        logHoverProviderFailure(provider, context, e)
        null
    } catch (e: UnsupportedOperationException) {
        if (e.message == "Should not be called") {
            logHoverProviderFailure(provider, context, e)
            null
        } else {
            throw e
        }
    }

internal fun <T> suppressHoverAnalysisFailure(provider: String, context: HoverContext, block: () -> T?): T? =
    try {
        block()
    } catch (e: NoDescriptorForDeclarationException) {
        logHoverProviderFailure(provider, context, e)
        null
    } catch (e: KotlinLSException) {
        logHoverProviderFailure(provider, context, e)
        null
    } catch (e: KotlinFrontEndException) {
        logHoverProviderFailure(provider, context, e)
        null
    } catch (e: UnsupportedOperationException) {
        if (e.message == "Should not be called") {
            logHoverProviderFailure(provider, context, e)
            null
        } else {
            throw e
        }
    }

internal inline fun <T> suppressHoverAnalysisFailure(block: () -> T?): T? =
    try {
        block()
    } catch (_: NoDescriptorForDeclarationException) {
        null
    } catch (_: KotlinLSException) {
        null
    } catch (_: KotlinFrontEndException) {
        null
    } catch (e: UnsupportedOperationException) {
        if (e.message == "Should not be called") null else throw e
    }

private fun logHoverProviderFailure(provider: String, context: HoverContext, error: Throwable) {
    LOG.debug(
        "Hover provider {} failed: cursor={} file={} originalPsi={} expressionPsi={} error={} message={}",
        provider,
        context.cursor,
        context.file.parse.name,
        context.originalKtElement?.javaClass?.simpleName ?: context.originalElement?.javaClass?.simpleName ?: "null",
        context.originalExpression?.javaClass?.simpleName ?: "null",
        error.javaClass.simpleName,
        error.message ?: ""
    )
}
