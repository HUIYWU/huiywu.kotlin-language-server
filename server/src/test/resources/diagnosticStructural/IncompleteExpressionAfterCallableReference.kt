class IncompleteExpressionAfterCallableReference {
    fun broken(foo: Any) {
        val ref = foo::
    }
}