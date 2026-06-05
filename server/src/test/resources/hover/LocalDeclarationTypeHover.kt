fun outer() {
    class LocalType {
        fun value(): String = "ok"
    }

    val local = LocalType()
    println(local.value())
}
