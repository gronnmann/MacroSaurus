package com.macrosaurus

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

class ArchitectureRulesTest {
    private val productionClasses = ClassFileImporter().importPackages("com.macrosaurus")

    @Test
    fun `database access stays in persistence adapters`() {
        noClasses()
            .that()
            .resideOutsideOfPackage("..persistence..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.jooq..")
            .check(productionClasses)
    }

    @Test
    fun `domain code stays framework independent`() {
        noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "org.jooq..", "tools.jackson..", "jakarta.validation..")
            .check(productionClasses)
    }

    @Test
    fun `http binding stays in web adapters`() {
        noClasses()
            .that()
            .resideOutsideOfPackage("..web..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework.web.bind.annotation..", "jakarta.validation..")
            .check(productionClasses)
    }
}
