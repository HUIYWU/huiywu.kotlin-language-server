class IncompleteBooleanExpressionAfterAnd {
    fun broken(foo: Boolean) {
        if (foo &&
        ) {
            println("ok")
        }
    }
}