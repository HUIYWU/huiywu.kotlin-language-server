class IncompleteExpressionAfterSafeCall {
    fun broken(foo: String?) {
        val value = foo?.
    }
}