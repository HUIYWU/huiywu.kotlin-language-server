annotation class Composable

@Composable
fun App() {
    Column {
        Text("hello")
        listOf("a", "b")
            .map { value -> value.uppercase() }
            .forEach { item ->
                Text(item)
            }
    }

    MissingComposable {
        Text("missing")
    }
}

@Composable
fun Column(content: () -> Unit) {
    content()
}

@Composable
fun Text(value: String) {
    println(value)
}
