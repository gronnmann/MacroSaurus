package com.macrosaurus.catalog.application

import com.macrosaurus.catalog.CatalogImportResult
import com.macrosaurus.catalog.CatalogImporter
import com.macrosaurus.catalog.ImportedFood
import com.macrosaurus.catalog.SourceKind
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class CatalogImportCommandTest {
    private val mapper = jacksonObjectMapper()
    private val importer = RecordingImporter()
    private val command = CatalogImportCommandHandler(mapper, importer)

    @Test
    fun `reads a normalized release from stdin and writes the import result`() {
        val input =
            """
            {
              "source": "MATVARETABELLEN",
              "releaseKey": "2026",
              "checksum": "sha256:test",
              "foods": [{
                "externalId": "food-1",
                "name": "Oatmeal",
                "locale": "en",
                "aliases": {"nb": "Havregrøt"},
                "basisType": "PER_100_G",
                "basisAmount": 100,
                "basisUnit": "g",
                "nutrients": {"energy_kcal": 71, "protein_g": 2.5},
                "portions": [{"name": "1 bowl", "gramWeight": 250, "default": true}]
              }]
            }
            """.trimIndent()
        val output = ByteArrayOutputStream()

        command.execute(ByteArrayInputStream(input.toByteArray()), output)

        assertThat(importer.source).isEqualTo(SourceKind.MATVARETABELLEN)
        assertThat(importer.releaseKey).isEqualTo("2026")
        assertThat(importer.checksum).isEqualTo("sha256:test")
        val food = importer.foods.single()
        assertThat(food.externalId).isEqualTo("food-1")
        assertThat(food.aliases).containsEntry("nb", "Havregrøt")
        assertThat(food.nutrients["protein_g"]).isEqualByComparingTo("2.5")
        val portion = food.portions.single()
        assertThat(portion.name).isEqualTo("1 bowl")
        assertThat(portion.gramWeight).isEqualByComparingTo("250")
        assertThat(portion.default).isTrue()
        val result = mapper.readTree(output.toString())
        assertThat(result["source"].stringValue()).isEqualTo("MATVARETABELLEN")
        assertThat(result["importedCount"].intValue()).isEqualTo(1)
        assertThat(output.toString()).endsWith("\n")
    }

    @Test
    fun `rejects malformed input before importing`() {
        assertThatThrownBy {
            command.execute(ByteArrayInputStream("not-json".toByteArray()), ByteArrayOutputStream())
        }.isInstanceOf(Exception::class.java)

        assertThat(importer.foods).isEmpty()
    }

    private class RecordingImporter : CatalogImporter {
        var source: SourceKind? = null
        var releaseKey: String? = null
        var checksum: String? = null
        var foods: List<ImportedFood> = emptyList()

        override fun importRelease(
            source: SourceKind,
            releaseKey: String,
            checksum: String,
            foods: List<ImportedFood>,
        ): CatalogImportResult {
            this.source = source
            this.releaseKey = releaseKey
            this.checksum = checksum
            this.foods = foods
            return CatalogImportResult(source, releaseKey, foods.size, false)
        }
    }
}
