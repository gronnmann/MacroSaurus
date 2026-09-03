package com.macrosaurus

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.modulith.Modulithic

@Modulithic
@ConfigurationPropertiesScan
@SpringBootApplication
class MacrosaurusApplication

fun main(args: Array<String>) = runApplication<MacrosaurusApplication>(*args).let { Unit }
