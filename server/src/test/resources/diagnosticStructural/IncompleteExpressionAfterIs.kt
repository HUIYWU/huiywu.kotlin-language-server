class IncompleteExpressionAfterIs {
    fun broken(foo: Any) {
        val ok = foo is
    }
}