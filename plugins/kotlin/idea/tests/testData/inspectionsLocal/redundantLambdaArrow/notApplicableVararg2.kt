// COMPILER_ARGUMENTS: -XXLanguage:-NewInference
// PROBLEM: none
// WITH_STDLIB

fun main() {
    registerHandler(handlers = *arrayOf(
        { _ -> },
        { it<caret> -> }
    ))
}

fun registerHandler(vararg handlers: (String) -> Unit) {
    handlers.forEach { it.invoke("hello") }
}