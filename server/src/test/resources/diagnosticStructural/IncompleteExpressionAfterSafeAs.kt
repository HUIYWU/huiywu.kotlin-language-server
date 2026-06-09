class IncompleteExpressionAfterSafeAs {
    fun broken(foo: Any) {
        val value = foo as?
    }
}