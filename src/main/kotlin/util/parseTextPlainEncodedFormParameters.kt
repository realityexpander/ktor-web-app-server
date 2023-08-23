package util

fun String.parseTextPlainEncodedFormParameters(): Map<String, String> {
    val params = mutableMapOf<String, String>()
    this.lines().forEach { line ->
        val split = line.split("=")
        if (split.size == 2) {
            params[split[0]] = split[1]
        }
    }
    return params
}