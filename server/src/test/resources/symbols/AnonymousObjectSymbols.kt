class SymbolContainerWithAnonymous {
    val anonymous = object {
        fun insideAnonymousObject() = Unit
    }
}
