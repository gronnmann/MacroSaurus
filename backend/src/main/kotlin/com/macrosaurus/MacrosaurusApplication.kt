package com.macrosaurus

import com.macrosaurus.catalog.CatalogImportApplication
import com.macrosaurus.catalog.CatalogImportCommand
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.modulith.Modulithic
import kotlin.system.exitProcess

@Modulithic
@ConfigurationPropertiesScan
@SpringBootApplication
class MacrosaurusApplication

fun main(args: Array<String>) {
    val catalogImport = args.any { it == "--macrosaurus.catalog-import.enabled=true" }
    if (!catalogImport) {
        runApplication<MacrosaurusApplication>(*args)
        return
    }

    val context =
        SpringApplicationBuilder(CatalogImportApplication::class.java)
            .web(WebApplicationType.NONE)
            .run(*args)
    val exitCode =
        try {
            context.getBean(CatalogImportCommand::class.java).execute(System.`in`, System.out)
            0
        } catch (error: Exception) {
            System.err.println("Catalog import failed: ${error.message ?: error.javaClass.simpleName}")
            1
        } finally {
            context.close()
        }
    exitProcess(exitCode)
}
