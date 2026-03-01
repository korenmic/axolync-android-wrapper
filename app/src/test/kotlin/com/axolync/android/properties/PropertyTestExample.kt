package com.axolync.android.properties

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

/**
 * Example property-based test using Kotest.
 * Property tests will be added in subsequent tasks.
 */
class PropertyTestExample : StringSpec({
    "addition is commutative" {
        checkAll(100, Arb.int(), Arb.int()) { a, b ->
            (a + b) shouldBe (b + a)
        }
    }
})
