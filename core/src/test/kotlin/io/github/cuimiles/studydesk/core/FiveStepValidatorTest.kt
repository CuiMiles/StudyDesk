package io.github.cuimiles.studydesk.core

import io.github.cuimiles.studydesk.core.fivestep.FiveStepPayload
import io.github.cuimiles.studydesk.core.fivestep.FiveStepValidator
import org.junit.Assert.*
import org.junit.Test

class FiveStepValidatorTest {

    @Test
    fun testValidPayload() {
        val payload = FiveStepPayload(
            concreteImage = "A sturdy stone embankment holding back turbulent storm floodwaters from village houses.",
            synonymsComparison = "1. mitigate: softens the impact without total removal.\n2. alleviate: eases suffering or pain.\n3. lessen: general reduction of quantity or severity.",
            registerAndContexts = "Formal / Academic context; municipal disaster mitigation planning.",
            collocations = "mitigate the risk, mitigate damage, mitigate climate impact",
            associations = "climate change, flood barriers, emergency response",
            integratedExample = "When severe rainfall triggered flash floods across the rural valley, community leaders worked through the night to reinforce the reservoir walls. Their timely intervention helped mitigate the damage to local schools and homes. Engineers later presented a comprehensive review explaining why proper drainage infrastructure remains critical for long-term regional resilience and flood protection.",
            integratedExampleMapping = "Step 1: sandbags. Step 2: reduce harm. Step 3: report."
        )

        val res = FiveStepValidator.validate(payload)
        assertTrue(res.reason, res.isValid)
    }

    @Test
    fun testMissingField() {
        val payload = FiveStepPayload(
            concreteImage = "",
            synonymsComparison = "a, b, c",
            registerAndContexts = "formal",
            collocations = "c1, c2",
            associations = "a1, a2",
            integratedExample = "word ".repeat(60)
        )
        val res = FiveStepValidator.validate(payload)
        assertFalse(res.isValid)
        assertTrue(res.reason.contains("concrete_image 不能为空"))
    }

    @Test
    fun testWordCountBounds() {
        val shortPayload = FiveStepPayload(
            concreteImage = "Image",
            synonymsComparison = "1. mitigate 2. alleviate 3. lessen contrast",
            registerAndContexts = "formal",
            collocations = "mitigate damage, mitigate loss",
            associations = "flood, danger, safety",
            integratedExample = "Too short example."
        )
        val resShort = FiveStepValidator.validate(shortPayload)
        assertFalse(resShort.isValid)
        assertTrue(resShort.reason.contains("词数"))
    }
}
