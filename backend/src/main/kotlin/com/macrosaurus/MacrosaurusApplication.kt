package com.macrosaurus

import com.macrosaurus.catalog.CatalogImportCommand
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.modulith.Modulithic
import kotlin.system.exitProcess

@Modulithic
@ConfigurationPropertiesScan
@SpringBootApplication
class MacrosaurusApplication

fun main(args: Array<String>) {
    val context = runApplication<MacrosaurusApplication>(*args)
    if (args.none { it == "--macrosaurus.catalog-import.enabled=true" }) return

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
