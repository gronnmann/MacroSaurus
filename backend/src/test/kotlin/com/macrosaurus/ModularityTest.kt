package com.macrosaurus

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

class ModularityTest {
    @Test
    fun `feature modules obey their declared boundaries`() {
        ApplicationModules.of(MacrosaurusApplication::class.java).verify()
    }
}
