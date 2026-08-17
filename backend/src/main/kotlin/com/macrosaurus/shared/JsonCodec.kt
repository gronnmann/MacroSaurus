package com.macrosaurus.shared

import org.springframework.stereotype.Component
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal

@Component
class JsonCodec(
    private val objectMapper: ObjectMapper,
) {
    fun nutrients(json: String): NutrientValues =
        NutrientValues(
            objectMapper.readValue(json, object : TypeReference<Map<String, BigDecimal>>() {}),
        )

    fun writeNutrients(values: NutrientValues): String = objectMapper.writeValueAsString(values.values)

    fun write(value: Any): String = objectMapper.writeValueAsString(value)

    fun <T> read(
        json: String,
        type: Class<T>,
    ): T = objectMapper.readValue(json, type)
}
