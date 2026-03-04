package com.axolync.android.properties

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/**
 * JUnit property-style sanity test (kept intentionally small for quick CI signal).
 */
class PropertyTestExample {

    @Test
    fun `addition remains commutative over random samples`() {
        val random = Random(9001)
        repeat(500) {
            val a = random.nextInt()
            val b = random.nextInt()
            assertEquals(a + b, b + a)
        }
    }
}
