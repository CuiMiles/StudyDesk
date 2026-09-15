package io.github.cuimiles.studydesk.core.fivestep

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FiveStepPayload(
    @SerialName("concrete_image")
    val concreteImage: String,

    @SerialName("synonyms_comparison")
    val synonymsComparison: String,

    @SerialName("register_and_contexts")
    val registerAndContexts: String,

    @SerialName("collocations")
    val collocations: String,

    @SerialName("associations")
    val associations: String,

    @SerialName("integrated_example")
    val integratedExample: String,

    @SerialName("integrated_example_mapping")
    val integratedExampleMapping: String = "",

    @SerialName("chinese_explanation")
    val chineseExplanation: String = ""
)

data class ValidationResult(
    val isValid: Boolean,
    val reason: String
)

object FiveStepValidator {

    fun countWords(text: String): Int {
        val pattern = Regex("[A-Za-z0-9]+(?:'[A-Za-z0-9]+)?")
        return pattern.findAll(text).count()
    }

    fun validate(payload: FiveStepPayload): ValidationResult {
        if (payload.concreteImage.isBlank()) return ValidationResult(false, "concrete_image 不能为空")
        if (payload.synonymsComparison.isBlank()) return ValidationResult(false, "synonyms_comparison 不能为空")
        if (payload.registerAndContexts.isBlank()) return ValidationResult(false, "register_and_contexts 不能为空")
        if (payload.collocations.isBlank()) return ValidationResult(false, "collocations 不能为空")
        if (payload.associations.isBlank()) return ValidationResult(false, "associations 不能为空")
        if (payload.integratedExample.isBlank()) return ValidationResult(false, "integrated_example 不能为空")

        // Synonyms check: at least 3 synonyms
        val words = Regex("[A-Za-z]{3,}").findAll(payload.synonymsComparison).count()
        val items = Regex("(?:^|\\n|\\d+\\.|\\*|-)\\s*([A-Za-z -]{2,})").findAll(payload.synonymsComparison).count()
        if (items < 3 && words < 6) {
            return ValidationResult(false, "synonyms_comparison 必须对比至少3个近义词")
        }

        // Example word count check: 50-100 words (strict bounds 50-100)
        val wc = countWords(payload.integratedExample)
        if (wc < 50 || wc > 100) {
            return ValidationResult(false, "integrated_example 词数 ($wc) 不在 50–100 词范围内")
        }

        return ValidationResult(true, "ok")
    }
}
