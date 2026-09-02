#!/usr/bin/env node

import { createHash } from 'node:crypto'
import { readFile, writeFile } from 'node:fs/promises'
import { pathToFileURL } from 'node:url'

const MATVARE_URLS = {
    en: 'https://www.matvaretabellen.no/api/en/foods.json',
    nb: 'https://www.matvaretabellen.no/api/nb/foods.json',
}

const MATVARE_NUTRIENTS = {
    Fett: ['fat_g', 'g'],
    Mettet: ['saturated_fat_g', 'g'],
    Trans: ['trans_fat_g', 'g'],
    Enumet: ['monounsaturated_fat_g', 'g'],
    Flerum: ['polyunsaturated_fat_g', 'g'],
    Kolest: ['cholesterol_mg', 'mg'],
    Karbo: ['carbohydrate_g', 'g'],
    'Mono+Di': ['sugars_g', 'g'],
    Fiber: ['fiber_g', 'g'],
    Protein: ['protein_g', 'g'],
    Na: ['sodium_mg', 'mg'],
    Ca: ['calcium_mg', 'mg'],
    Fe: ['iron_mg', 'mg'],
    K: ['potassium_mg', 'mg'],
    'Vit C': ['vitamin_c_mg', 'mg'],
    'Vit D': ['vitamin_d_ug', 'ug'],
    'Vit A': ['vitamin_a_ug', 'ug'],
    'Vit E': ['vitamin_e_mg', 'mg'],
    VITK1: ['vitamin_k_ug', 'ug'],
    'Vit B1': ['thiamin_mg', 'mg'],
    'Vit B2': ['riboflavin_mg', 'mg'],
    Niacin: ['niacin_mg', 'mg'],
    'Vit B6': ['vitamin_b6_mg', 'mg'],
    Folat: ['folate_ug', 'ug'],
    'Vit B12': ['vitamin_b12_ug', 'ug'],
    Mg: ['magnesium_mg', 'mg'],
    P: ['phosphorus_mg', 'mg'],
    Zn: ['zinc_mg', 'mg'],
    Cu: ['copper_mg', 'mg'],
    Mn: ['manganese_mg', 'mg'],
    Se: ['selenium_ug', 'ug'],
}

const USDA_NUTRIENTS = [
    ['energy_kcal', 'kcal', [1008, 2047, 2048], /^energy/],
    ['protein_g', 'g', [1003], /^protein$/],
    ['carbohydrate_g', 'g', [1005], /^carbohydrate/],
    ['fat_g', 'g', [1004], /total lipid|total fat/],
    ['fiber_g', 'g', [1079], /fiber,? total dietary/],
    ['sugars_g', 'g', [2000, 1063], /sugars?,? total/],
    ['saturated_fat_g', 'g', [1258], /fatty acids,? total saturated/],
    ['trans_fat_g', 'g', [1257], /fatty acids,? total trans/],
    ['monounsaturated_fat_g', 'g', [1292], /fatty acids,? total monounsaturated/],
    ['polyunsaturated_fat_g', 'g', [1293], /fatty acids,? total polyunsaturated/],
    ['cholesterol_mg', 'mg', [1253], /^cholesterol$/],
    ['sodium_mg', 'mg', [1093], /^sodium/],
    ['calcium_mg', 'mg', [1087], /^calcium/],
    ['iron_mg', 'mg', [1089], /^iron/],
    ['potassium_mg', 'mg', [1092], /^potassium/],
    ['vitamin_c_mg', 'mg', [1162], /vitamin c/],
    ['vitamin_d_ug', 'ug', [1114], /vitamin d.*(?:d2 \+ d3|total)/],
    ['vitamin_a_ug', 'ug', [1106], /vitamin a,? rae/],
    ['vitamin_e_mg', 'mg', [1109], /vitamin e.*alpha/],
    ['vitamin_k_ug', 'ug', [1185], /vitamin k.*phylloquinone/],
    ['thiamin_mg', 'mg', [1165], /^thiamin/],
    ['riboflavin_mg', 'mg', [1166], /^riboflavin/],
    ['niacin_mg', 'mg', [1167], /^niacin/],
    ['vitamin_b6_mg', 'mg', [1175], /vitamin b-?6/],
    ['folate_ug', 'ug', [1177], /folate,? total/],
    ['vitamin_b12_ug', 'ug', [1178], /vitamin b-?12/],
    ['magnesium_mg', 'mg', [1090], /^magnesium/],
    ['phosphorus_mg', 'mg', [1091], /^phosphorus/],
    ['zinc_mg', 'mg', [1095], /^zinc/],
    ['copper_mg', 'mg', [1098], /^copper/],
    ['manganese_mg', 'mg', [1101], /^manganese/],
    ['selenium_ug', 'ug', [1103], /^selenium/],
]

function finiteNumber(value) {
    if (value === null || value === undefined || value === '') return null
    const parsed = Number(value)
    return Number.isFinite(parsed) && parsed >= 0 ? parsed : null
}

function normalizedUnit(unit) {
    return String(unit || '')
        .trim()
        .toLowerCase()
        .replaceAll('µ', 'u')
        .replaceAll('μ', 'u')
        .replace('-re', '')
}

function convertAmount(value, fromUnit, toUnit) {
    const amount = finiteNumber(value)
    if (amount === null) return null
    const from = normalizedUnit(fromUnit)
    const to = normalizedUnit(toUnit)
    if (from === to) return amount
    const grams = { g: 1, mg: 0.001, ug: 0.000001 }
    if (!(from in grams) || !(to in grams)) return null
    return (amount * grams[from]) / grams[to]
}

function cleanText(value) {
    const cleaned = String(value || '').replace(/\s+/g, ' ').trim()
    return cleaned || null
}

function normalizedPortions(portions, mapper) {
    const mapped = (Array.isArray(portions) ? portions : []).map(mapper).filter(Boolean)
    return mapped.map((portion, index) => ({ ...portion, default: index === 0 }))
}

function matvarePortion(portion) {
    const grams = convertAmount(portion?.quantity, portion?.unit, 'g')
    if (grams === null || grams <= 0) return null
    const name = cleanText(portion.portionName) || cleanText(portion.portionUnit)
    if (!name) return null
    return { name, gramWeight: grams }
}

function matvareNutrients(food) {
    const result = {}
    const calories = convertAmount(food?.calories?.quantity, food?.calories?.unit, 'kcal')
    if (calories !== null) result.energy_kcal = calories
    for (const constituent of Array.isArray(food.constituents) ? food.constituents : []) {
        const mapping = MATVARE_NUTRIENTS[constituent.nutrientId]
        if (!mapping) continue
        const [code, unit] = mapping
        const amount = convertAmount(constituent.quantity, constituent.unit, unit)
        if (amount !== null) result[code] = amount
    }
    if (result.sodium_mg === undefined) {
        const salt = (food.constituents || []).find((item) => item.nutrientId === 'NaCl')
        const saltGrams = convertAmount(salt?.quantity, salt?.unit, 'g')
        if (saltGrams !== null) result.sodium_mg = saltGrams * 400
    }
    return result
}

export function prepareMatvareRelease(englishDocument, norwegianDocument, releaseKey, checksum = 'fixture') {
    const englishFoods = englishDocument?.foods
    const norwegianFoods = norwegianDocument?.foods
    if (!Array.isArray(englishFoods) || !Array.isArray(norwegianFoods)) {
        throw new Error('Matvaretabellen exports must contain a foods array')
    }
    const norwegianById = new Map(norwegianFoods.map((food) => [String(food.foodId), food]))
    const foods = englishFoods.map((food) => {
        const externalId = cleanText(food.foodId)
        const name = cleanText(food.foodName)
        if (!externalId || !name) throw new Error('Matvaretabellen food is missing foodId or foodName')
        const norwegianName = cleanText(norwegianById.get(externalId)?.foodName)
        const aliases = norwegianName && norwegianName !== name ? { nb: norwegianName } : {}
        return {
            externalId,
            name,
            locale: 'en',
            aliases,
            basisType: 'PER_100_G',
            basisAmount: 100,
            basisUnit: 'g',
            nutrients: matvareNutrients(food),
            portions: normalizedPortions(food.portions, matvarePortion),
        }
    })
    return { source: 'MATVARETABELLEN', releaseKey, checksum, foods }
}

function usdaNutrientDetails(item) {
    const nutrient = item?.nutrient || {}
    return {
        id: Number(nutrient.id ?? item?.nutrientId),
        name: String(nutrient.name ?? item?.nutrientName ?? '').trim().toLowerCase(),
        unit: nutrient.unitName ?? item?.unitName,
        amount: item?.amount ?? item?.value,
    }
}

function usdaNutrients(food) {
    const available = (Array.isArray(food.foodNutrients) ? food.foodNutrients : []).map(usdaNutrientDetails)
    const result = {}
    for (const [code, unit, ids, namePattern] of USDA_NUTRIENTS) {
        const match = available.find((item) => ids.includes(item.id)) || available.find((item) => namePattern.test(item.name))
        if (!match) continue
        const amount = convertAmount(match.amount, match.unit, unit)
        if (amount !== null) result[code] = amount
    }
    return result
}

function usdaPortion(portion) {
    const grams = finiteNumber(portion?.gramWeight)
    if (grams === null || grams <= 0) return null
    const amount = finiteNumber(portion.amount) || 1
    const descriptor =
        cleanText(portion.portionDescription) ||
        cleanText(portion.modifier) ||
        cleanText(portion.measureUnit?.abbreviation) ||
        cleanText(portion.measureUnit?.name) ||
        'serving'
    const prefix = amount === 1 ? '1' : String(amount)
    const name = descriptor.startsWith(prefix) ? descriptor : `${prefix} ${descriptor}`
    return { name, gramWeight: grams }
}

export function prepareUsdaRelease(document, source, releaseKey, checksum = 'fixture') {
    const rootKey = source === 'USDA_FOUNDATION' ? 'FoundationFoods' : source === 'USDA_SR_LEGACY' ? 'SRLegacyFoods' : null
    if (!rootKey) throw new Error(`Unsupported USDA source: ${source}`)
    const foods = document?.[rootKey]
    if (!Array.isArray(foods)) throw new Error(`USDA ${source} export must contain ${rootKey}`)
    return {
        source,
        releaseKey,
        checksum,
        // FoodData Central exports can contain trailing null placeholders.
        foods: foods.filter((food) => food !== null).map((food) => {
            const externalId = cleanText(food.fdcId)
            const name = cleanText(food.description)
            if (!externalId || !name) throw new Error(`USDA ${source} food is missing fdcId or description`)
            return {
                externalId,
                name,
                locale: 'en',
                aliases: {},
                basisType: 'PER_100_G',
                basisAmount: 100,
                basisUnit: 'g',
                nutrients: usdaNutrients(food),
                portions: normalizedPortions(food.foodPortions, usdaPortion),
            }
        }),
    }
}

function parseOptions(argumentsList) {
    const options = {}
    for (let index = 0; index < argumentsList.length; index += 2) {
        const key = argumentsList[index]
        const value = argumentsList[index + 1]
        if (!key?.startsWith('--') || value === undefined) throw new Error(`Invalid option: ${key || '<missing>'}`)
        options[key.slice(2)] = value
    }
    return options
}

function checksumOf(...buffers) {
    const hash = createHash('sha256')
    for (const buffer of buffers) {
        hash.update(String(buffer.length))
        hash.update(':')
        hash.update(buffer)
    }
    return `sha256:${hash.digest('hex')}`
}

async function fetchBuffer(url) {
    const response = await fetch(url, { headers: { 'User-Agent': 'MacroSaurus catalog importer' } })
    if (!response.ok) throw new Error(`Could not download ${url}: HTTP ${response.status}`)
    return Buffer.from(await response.arrayBuffer())
}

async function main() {
    const [kind, ...argumentList] = process.argv.slice(2)
    const options = parseOptions(argumentList)
    if (!kind || !options.release || !options.output) {
        throw new Error(
            'Usage: prepare-catalog-release.mjs matvaretabellen --release 2026 --output release.json\n' +
                '   or: prepare-catalog-release.mjs usda-foundation|usda-sr-legacy --release <release> --input <download.json> --output release.json',
        )
    }

    let release
    if (kind === 'matvaretabellen') {
        const [english, norwegian] = await Promise.all([fetchBuffer(MATVARE_URLS.en), fetchBuffer(MATVARE_URLS.nb)])
        release = prepareMatvareRelease(JSON.parse(english), JSON.parse(norwegian), options.release, checksumOf(english, norwegian))
    } else if (kind === 'usda-foundation' || kind === 'usda-sr-legacy') {
        if (!options.input) throw new Error(`${kind} requires --input <extracted USDA JSON file>`)
        const raw = await readFile(options.input)
        const source = kind === 'usda-foundation' ? 'USDA_FOUNDATION' : 'USDA_SR_LEGACY'
        release = prepareUsdaRelease(JSON.parse(raw), source, options.release, checksumOf(raw))
    } else {
        throw new Error(`Unknown source: ${kind}`)
    }

    await writeFile(options.output, `${JSON.stringify(release)}\n`)
    console.log(`Prepared ${release.foods.length} ${release.source} foods in ${options.output}`)
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    main().catch((error) => {
        console.error(error.message)
        process.exitCode = 1
    })
}
