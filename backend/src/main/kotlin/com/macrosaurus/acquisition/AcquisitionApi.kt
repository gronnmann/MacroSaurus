package com.macrosaurus.acquisition

import com.macrosaurus.catalog.CatalogService
import com.macrosaurus.catalog.CreateFoodRequest
import com.macrosaurus.identity.UserContext
import com.macrosaurus.shared.BasisType
import com.macrosaurus.shared.ExternalServiceException
import com.macrosaurus.shared.InvalidOperationException
import com.macrosaurus.shared.JsonCodec
import com.macrosaurus.shared.NotFoundException
import com.macrosaurus.shared.SourceKind
import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.jooq.DSLContext
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID

@ConfigurationProperties("macrosaurus.open-food-facts")
data class OpenFoodFactsProperties(
    val baseUrl: String,
    val userAgent: String,
    val connectTimeout: Duration = Duration.ofSeconds(5),
    val readTimeout: Duration = Duration.ofSeconds(15),
)

@ConfigurationProperties("macrosaurus.open-router")
data class OpenRouterProperties(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val connectTimeout: Duration = Duration.ofSeconds(5),
    val readTimeout: Duration = Duration.ofSeconds(90),
)

data class BarcodeCandidate(
    val barcode: String,
    val name: String,
    val brand: String?,
    val source: SourceKind,
    val basisType: BasisType,
    val nutrients: Map<String, BigDecimal>,
    val externalId: String,
)

@Service
class OpenFoodFactsClient(
    properties: OpenFoodFactsProperties,
    private val mapper: ObjectMapper,
) {
    private val userAgent = properties.userAgent
    private val client = restClient(properties.baseUrl, properties.connectTimeout, properties.readTimeout)

    fun find(barcode: String): BarcodeCandidate? {
        return try {
            val payload =
                client
                    .get()
                    .uri("/api/v3/product/{barcode}.json?fields=code,product_name,brands,nutriments", barcode)
                    .header("User-Agent", userAgent)
                    .retrieve()
                    .body(String::class.java) ?: return null
            parseOpenFoodFactsCandidate(barcode, payload, mapper)
        } catch (error: Exception) {
            throw ExternalServiceException("Open Food Facts lookup failed", error)
        }
    }
}

internal fun parseOpenFoodFactsCandidate(
    barcode: String,
    payload: String,
    mapper: ObjectMapper,
): BarcodeCandidate? {
    val root = mapper.readTree(payload)
    val product = root.path("product").takeIf(JsonNode::isObject) ?: return null
    val name = product.path("product_name").asString("").ifBlank { return null }
    val nutriments = product.path("nutriments")

    fun decimal(key: String): BigDecimal? =
        nutriments
            .path(key)
            .takeUnless(JsonNode::isMissingNode)
            ?.takeUnless(JsonNode::isNull)
            ?.decimalValue()
    val nutrients =
        linkedMapOf<String, BigDecimal>().apply {
            decimal("energy-kcal_100g")?.let { put("energy_kcal", it) }
            decimal("proteins_100g")?.let { put("protein_g", it) }
            decimal("carbohydrates_100g")?.let { put("carbohydrate_g", it) }
            decimal("fat_100g")?.let { put("fat_g", it) }
            decimal("fiber_100g")?.let { put("fiber_g", it) }
            decimal("sugars_100g")?.let { put("sugars_g", it) }
            decimal("saturated-fat_100g")?.let { put("saturated_fat_g", it) }
            decimal("sodium_100g")?.multiply(BigDecimal("1000"))?.let { put("sodium_mg", it) }
        }
    return BarcodeCandidate(
        barcode,
        name,
        product.path("brands").asString("").ifBlank { null },
        SourceKind.OPEN_FOOD_FACTS,
        BasisType.PER_100_G,
        nutrients,
        barcode,
    )
}

@Service
class BarcodeService(
    private val catalog: CatalogService,
    private val off: OpenFoodFactsClient,
) {
    fun find(
        userId: String,
        rawBarcode: String,
    ): List<BarcodeCandidate> {
        val barcode = Barcode.normalizeAndValidate(rawBarcode)
        val local =
            catalog.search(userId, barcode, 20).filter { it.barcode == barcode }.map {
                BarcodeCandidate(barcode, it.name, it.brand, it.source, it.basisType, it.nutrients, it.id.toString())
            }
        return if (local.isNotEmpty()) local else listOfNotNull(off.find(barcode))
    }

    fun import(
        userId: String,
        rawBarcode: String,
    ): com.macrosaurus.catalog.FoodView {
        val barcode = Barcode.normalizeAndValidate(rawBarcode)
        catalog.search(userId, barcode, 20).firstOrNull { it.barcode == barcode }?.let { return it }
        val candidate = off.find(barcode) ?: throw NotFoundException("Barcode was not found")
        return catalog.create(
            userId,
            CreateFoodRequest(
                name = candidate.name,
                brand = candidate.brand,
                barcode = barcode,
                basisType = candidate.basisType,
                basisAmount = BigDecimal("100"),
                basisUnit = if (candidate.basisType == BasisType.PER_100_ML) "ml" else "g",
                nutrients = candidate.nutrients,
            ),
            candidate.source,
            candidate.externalId,
        )
    }
}

data class StartLabelScanRequest(
    @field:Pattern(regexp = "^data:image/(jpeg|png|webp);base64,.+")
    @field:Size(max = 12_000_000)
    val image: String,
    val barcode: String? = null,
    val localeHint: String? = null,
)

data class ExtractedNutrient(
    val code: String,
    val amount: BigDecimal,
    val unit: String,
    val confidence: BigDecimal,
)

data class LabelDraft(
    val name: String?,
    val brand: String?,
    val barcode: String?,
    val basisType: BasisType?,
    val basisAmount: BigDecimal?,
    val basisUnit: String?,
    val servingName: String?,
    val servingMassG: BigDecimal?,
    val servingVolumeMl: BigDecimal?,
    val nutrients: List<ExtractedNutrient>,
    val ingredients: String?,
    val allergens: List<String>,
    val warnings: List<String>,
)

data class ScanJobView(
    val id: UUID,
    val status: String,
    val draft: LabelDraft?,
    val errorMessage: String?,
    val expiresAt: OffsetDateTime,
)

@Service
class OpenRouterLabelExtractor(
    private val properties: OpenRouterProperties,
    private val mapper: ObjectMapper,
) {
    private val client = restClient(properties.baseUrl, properties.connectTimeout, properties.readTimeout)

    fun extract(request: StartLabelScanRequest): LabelDraft {
        if (properties.apiKey.isBlank()) throw InvalidOperationException("OPENROUTER_API_KEY is not configured")
        val content =
            mutableListOf<Map<String, Any>>(
                mapOf(
                    "type" to "text",
                    "text" to
                        "Extract the product identity and nutrition label. Preserve printed values and units. " +
                        "Do not guess missing values. Locale hint: ${request.localeHint ?: "unknown"}. " +
                        "Barcode hint: ${request.barcode ?: "none"}.",
                ),
            )
        content += mapOf("type" to "image_url", "image_url" to mapOf("url" to request.image))
        val schema =
            mapOf(
                "type" to "object",
                "additionalProperties" to false,
                "properties" to
                    mapOf(
                        "name" to nullableString(),
                        "brand" to nullableString(),
                        "barcode" to nullableString(),
                        "basisType" to mapOf("type" to listOf("string", "null"), "enum" to listOf("PER_100_G", "PER_100_ML", "PER_SERVING", null)),
                        "basisAmount" to nullableNumber(),
                        "basisUnit" to nullableString(),
                        "servingName" to nullableString(),
                        "servingMassG" to nullableNumber(),
                        "servingVolumeMl" to nullableNumber(),
                        "nutrients" to
                            mapOf(
                                "type" to "array",
                                "items" to
                                    mapOf(
                                        "type" to "object",
                                        "additionalProperties" to false,
                                        "properties" to
                                            mapOf(
                                                "code" to mapOf("type" to "string"),
                                                "amount" to mapOf("type" to "number"),
                                                "unit" to mapOf("type" to "string"),
                                                "confidence" to mapOf("type" to "number"),
                                            ),
                                        "required" to listOf("code", "amount", "unit", "confidence"),
                                    ),
                            ),
                        "ingredients" to nullableString(),
                        "allergens" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                        "warnings" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                    ),
                "required" to
                    listOf(
                        "name",
                        "brand",
                        "barcode",
                        "basisType",
                        "basisAmount",
                        "basisUnit",
                        "servingName",
                        "servingMassG",
                        "servingVolumeMl",
                        "nutrients",
                        "ingredients",
                        "allergens",
                        "warnings",
                    ),
            )
        val body =
            mapOf(
                "model" to properties.model,
                "messages" to listOf(mapOf("role" to "user", "content" to content)),
                "provider" to mapOf("require_parameters" to true, "data_collection" to "deny"),
                "response_format" to
                    mapOf(
                        "type" to "json_schema",
                        "json_schema" to
                            mapOf(
                                "name" to "nutrition_label",
                                "strict" to true,
                                "schema" to schema,
                            ),
                    ),
                "temperature" to 0,
            )
        val response =
            try {
                client
                    .post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer ${properties.apiKey}")
                    .header("HTTP-Referer", "https://macrosaurus.app")
                    .header("X-OpenRouter-Title", "Macrosaurus")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String::class.java)
            } catch (error: Exception) {
                throw ExternalServiceException("Label extraction failed", error)
            } ?: throw ExternalServiceException("Label extraction returned no response")
        return try {
            val contentJson =
                mapper
                    .readTree(response)
                    .path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asString()
            if (contentJson.isBlank()) {
                throw ExternalServiceException("Label extraction returned no structured content")
            }
            mapper.readValue(contentJson, LabelDraft::class.java)
        } catch (error: ExternalServiceException) {
            throw error
        } catch (error: Exception) {
            throw ExternalServiceException("Label extraction returned malformed structured content", error)
        }
    }

    private fun nullableString() = mapOf("type" to listOf("string", "null"))

    private fun nullableNumber() = mapOf("type" to listOf("number", "null"))
}

@Service
class ScanService(
    private val db: DSLContext,
    private val json: JsonCodec,
    private val extractor: OpenRouterLabelExtractor,
    private val catalog: CatalogService,
    private val clock: Clock,
) {
    fun start(
        userId: String,
        request: StartLabelScanRequest,
    ): ScanJobView {
        val id = UUID.randomUUID()
        val expires = OffsetDateTime.now(clock).plusHours(24)
        db.execute(
            "insert into scan_jobs(id, user_id, status, expires_at) values (?, ?, 'PROCESSING', cast(? as timestamptz))",
            id,
            userId,
            expires,
        )
        return try {
            val draft = extractor.extract(request)
            db.execute("update scan_jobs set status = 'REVIEW', result = cast(? as jsonb) where id = ?", json.write(draft), id)
            ScanJobView(id, "REVIEW", draft, null, expires)
        } catch (error: RuntimeException) {
            db.execute("update scan_jobs set status = 'FAILED', error_message = ? where id = ?", error.message?.take(500), id)
            throw error
        }
    }

    fun get(
        userId: String,
        id: UUID,
    ): ScanJobView {
        val record =
            db.fetchOne(
                "select id, status, result::text as result, error_message, expires_at from scan_jobs where id = ? and user_id = ?",
                id,
                userId,
            ) ?: throw NotFoundException("Scan was not found")
        return ScanJobView(
            id,
            record.get("status", String::class.java)!!,
            record.get("result", String::class.java)?.let { json.read(it, LabelDraft::class.java) },
            record.get("error_message", String::class.java),
            record.get("expires_at", OffsetDateTime::class.java)!!,
        )
    }

    @Transactional
    fun confirm(
        userId: String,
        id: UUID,
        request: CreateFoodRequest,
    ): com.macrosaurus.catalog.FoodView {
        val job = get(userId, id)
        if (job.status != "REVIEW") throw InvalidOperationException("Only scans awaiting review can be confirmed")
        val food = catalog.create(userId, request)
        db.execute("update scan_jobs set status = 'CONFIRMED', result = null where id = ? and user_id = ?", id, userId)
        return food
    }
}

private fun restClient(
    baseUrl: String,
    connectTimeout: Duration,
    readTimeout: Duration,
): RestClient {
    val requestFactory =
        SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(connectTimeout)
            setReadTimeout(readTimeout)
        }
    return RestClient
        .builder()
        .baseUrl(baseUrl)
        .requestFactory(requestFactory)
        .build()
}

@RestController
@RequestMapping("/api/v1")
class AcquisitionController(
    private val users: UserContext,
    private val barcodes: BarcodeService,
    private val scans: ScanService,
) {
    @GetMapping("/barcodes/{code}")
    fun barcode(
        @PathVariable code: String,
    ) = barcodes.find(users.userId(), code)

    @PostMapping("/barcodes/{code}/import")
    fun importBarcode(
        @PathVariable code: String,
    ) = barcodes.import(users.userId(), code)

    @PostMapping("/food-scans")
    fun scan(
        @Valid @RequestBody request: StartLabelScanRequest,
    ) = scans.start(users.userId(), request)

    @GetMapping("/food-scans/{id}")
    fun scan(
        @PathVariable id: UUID,
    ) = scans.get(users.userId(), id)

    @PostMapping("/food-scans/{id}/confirm")
    fun confirm(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CreateFoodRequest,
    ) = scans.confirm(users.userId(), id, request)
}
