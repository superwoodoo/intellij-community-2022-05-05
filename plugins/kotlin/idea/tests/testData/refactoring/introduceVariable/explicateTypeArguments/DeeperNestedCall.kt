// COMPILER_ARGUMENTS: -XXLanguage:-NewInference
// WITH_STDLIB
import java.util.ArrayList

fun f() {
    val list: List<List<List<Int>>> = ArrayList(<selection>ArrayList(listOf())</selection>)
}
