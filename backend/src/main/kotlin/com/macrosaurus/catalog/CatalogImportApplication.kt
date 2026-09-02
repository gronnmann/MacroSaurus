package com.macrosaurus.catalog

import com.macrosaurus.catalog.application.CatalogImportCommandHandler
import com.macrosaurus.catalog.application.CatalogService
import com.macrosaurus.catalog.persistence.JooqCatalogRepository
import com.macrosaurus.shared.JsonCodec
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = ["macrosaurus.catalog-import.enabled"], havingValue = "true")
@EnableAutoConfiguration
@Import(
    CatalogImportCommandHandler::class,
    CatalogService::class,
    JooqCatalogRepository::class,
    JsonCodec::class,
)
class CatalogImportApplication
