package io.github.cuimiles.studydesk.core.vocabulary

object ExampleText {
    fun matches(text: String, forms: List<String>): List<IntRange> {
        val terms = forms.filter { it.isNotBlank() }.distinct().sortedByDescending { it.length }
        if (terms.isEmpty()) return emptyList()
        val regex = Regex("(?<![\\p{L}\\p{N}])(?:" + terms.joinToString("|") { Regex.escape(it) } + ")(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
        return regex.findAll(text).map { it.range }.toList()
    }
}
