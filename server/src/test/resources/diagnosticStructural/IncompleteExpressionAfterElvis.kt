class IncompleteExpressionAfterElvis {
    fun broken(foo: String?): String {
        val value = foo ?:
        return value
    }
}