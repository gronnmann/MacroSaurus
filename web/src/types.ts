export type Nutrients = Record<string, number>

export type BasisType = 'PER_100_G' | 'PER_100_ML' | 'PER_SERVING'
export type FoodSource = 'USDA' | 'OPEN_FOOD_FACTS' | 'USER'
export type Meal = 'BREAKFAST' | 'LUNCH' | 'DINNER' | 'SNACK' | 'OTHER'

export interface ProblemDetails {
    type?: string
    title?: string
    status: number
    detail: string
    errors?: Record<string, string | string[]>
}

export interface NutrientDefinition {
    code: string
    displayName: string
    category: string
    unit: string
    sortOrder: number
}

export interface Portion {
    id: string
    name: string
    quantity: number
    gramWeight?: number
    milliliterVolume?: number
    default: boolean
}

export interface PortionInput {
    name: string
    quantity: number
    gramWeight?: number | null
    milliliterVolume?: number | null
    default: boolean
}

export interface Food {
    id: string
    revisionId: string
    revision: number
    name: string
    brand?: string
    barcode?: string
    source: FoodSource
    basisType: BasisType
    basisAmount: number
    basisUnit: string
    densityGPerMl?: number
    nutrients: Nutrients
    portions: Portion[]
    createdAt: string
}

export interface FoodInput {
    name: string
    brand?: string | null
    barcode?: string | null
    basisType: BasisType
    basisAmount: number
    basisUnit: string
    densityGPerMl?: number | null
    nutrients: Nutrients
    portions: PortionInput[]
}

export interface ResolvedFoodAmount {
    foodRevisionId: string
    displayName: string
    quantity: number
    unit: string
    resolvedGrams?: number
    nutrients: Nutrients
}

export interface DiaryEntry {
    id: string
    localDate: string
    consumedAt: string
    meal: Meal
    displayName: string
    entryType: 'FOOD' | 'RECIPE' | 'QUICK'
    sourceRevisionId?: string
    quantity?: number
    unit?: string
    portionId?: string
    nutrients: Nutrients
}

export interface DiaryDay {
    date: string
    entries: DiaryEntry[]
    totals: Nutrients
}

export interface RecipeIngredient {
    id: string
    foodRevisionId: string
    name: string
    quantity: number
    unit: string
    portionId?: string
    resolvedGrams?: number
    nutrients: Nutrients
}

export interface Recipe {
    id: string
    revisionId: string
    revision: number
    name: string
    servings: number
    explicitYieldG?: number
    estimatedYieldG?: number
    totalNutrients: Nutrients
    nutrientsPerServing: Nutrients
    nutrientsPer100G?: Nutrients
    ingredients: RecipeIngredient[]
    createdAt: string
}

export interface RecipeInput {
    name: string
    servings: number
    finishedWeightG?: number | null
    ingredients: Array<{
        foodRevisionId: string
        quantity: number
        unit: string
        portionId?: string | null
    }>
}

export interface Profile {
    userId: string
    displayName: string
    locale: string
    timezone: string
    unitSystem: 'METRIC'
    birthDate?: string
    heightCm?: number
    formulaSex?: 'MALE' | 'FEMALE'
    activityMultiplier: number
}

export interface NutrientTarget {
    nutrientCode: string
    displayName: string
    unit: string
    targetAmount?: number
    minimumAmount?: number
    maximumAmount?: number
}

export interface Weight {
    id: string
    weightKg: number
    measuredAt: string
    note?: string
}

export interface EnergyEstimate {
    date: string
    baselineKcal?: number
    adaptiveKcal?: number
    suggestedKcal?: number
    confidence: string
    adaptiveEligible: boolean
    algorithmVersion: string
    explanation: string[]
    requirements: Record<string, number>
    lowerKcal?: number
    upperKcal?: number
    trendWeightKg?: number
    trendWeightLowerKg?: number
    trendWeightUpperKg?: number
    modelState: 'BASELINE' | 'UPDATING' | 'HOLDING' | 'INSUFFICIENT'
}

export type WeightGoalType = 'LOSS' | 'MAINTAIN' | 'GAIN'
export type ProgramStyle = 'COACHED' | 'MANUAL'

export interface WeightGoal {
    id: string
    type: WeightGoalType
    startingWeightKg: number
    targetWeightKg?: number
    weeklyRatePercent: number
    status: 'ACTIVE' | 'COMPLETED' | 'ARCHIVED'
    startedOn: string
    endedOn?: string
}

export interface NutritionProgram {
    id: string
    goalId?: string
    style: ProgramStyle
    effectiveFrom: string
    effectiveTo?: string
    energyKcal?: number
    proteinG?: number
    carbohydrateG?: number
    fatG?: number
    proteinGPerKg?: number
    fatEnergyPercent?: number
    expenditureKcal?: number
    expenditureLowerKcal?: number
    expenditureUpperKcal?: number
    algorithmVersion?: string
    source: 'ONBOARDING' | 'CHECK_IN' | 'PROFILE_RERUN'
}

export interface CoachingStatus {
    setupComplete: boolean
    goal?: WeightGoal
    program?: NutritionProgram
    nextCheckInDate?: string
    checkInDue: boolean
}

export interface CoachingSetupDraft {
    currentStep: number
    displayName?: string
    locale: string
    timezone: string
    birthDate?: string
    heightCm?: number
    formulaSex?: 'MALE' | 'FEMALE'
    activityMultiplier: number
    weightKg?: number
    goalType?: WeightGoalType
    targetWeightKg?: number
    weeklyRatePercent?: number
    programStyle?: ProgramStyle
    proteinGPerKg: number
    fatEnergyPercent: number
    manualEnergyKcal?: number
    manualProteinG?: number
    manualCarbohydrateG?: number
    manualFatG?: number
}

export interface SetupPreview {
    expenditure: EnergyEstimate
    energyKcal: number
    proteinG: number
    carbohydrateG: number
    fatG: number
    estimatedCompletionDate?: string
    warnings: string[]
}

export type NutritionDayStatus = 'CONFIRMED_COMPLETE' | 'ESTIMATED_TOTAL' | 'EXCLUDED' | 'FASTING'

export interface NutritionDayReview {
    date: string
    status: NutritionDayStatus
    estimatedTotalKcal?: number
}

export interface NutritionReviewCandidate {
    date: string
    loggedEnergyKcal?: number
    entryCount: number
    reason: 'MISSING' | 'POSSIBLE_PARTIAL'
    review?: NutritionDayReview
}

export interface CheckInProposal {
    estimate: EnergyEstimate
    previousEnergyKcal?: number
    proposedEnergyKcal?: number
    proposedProteinG?: number
    proposedCarbohydrateG?: number
    proposedFatG?: number
    targetUpdateAvailable: boolean
    warnings: string[]
}

export interface CheckIn {
    due: boolean
    id?: string
    weekStart?: string
    periodFrom?: string
    periodTo?: string
    status?: 'DRAFT' | 'ACCEPTED' | 'SKIPPED'
    candidates: NutritionReviewCandidate[]
    needsWeight: boolean
    proposal?: CheckInProposal
}

export interface ProgressSeriesPoint {
    date: string
    measuredWeightKg?: number
    expenditure: EnergyEstimate
}

export interface Trackable {
    type: 'FOOD' | 'RECIPE'
    id: string
    revisionId: string
    name: string
    brand?: string
    servingLabel: string
    nutrients: Nutrients
}

export interface LastTrackedAmount {
    quantity: number
    unit: string
    portionId?: string
}

export interface TimeOfDaySuggestions {
    anchorHour: number
    items: Trackable[]
}

export type EnergyGoalMode = 'FIXED' | 'MAINTENANCE' | 'KCAL_DELTA' | 'PERCENT_DELTA'
export type MacroGoalMode = 'GUIDED' | 'CUSTOM_GRAMS' | 'PERCENT_SPLIT'
export type GoalWeightBasis = 'LATEST_WEIGHT' | 'MANUAL_WEIGHT'

export interface GoalSettings {
    configured: boolean
    energyMode?: EnergyGoalMode
    energyValue?: number
    macroMode?: MacroGoalMode
    proteinGPerKg?: number
    fatEnergyPercent?: number
    weightBasis?: GoalWeightBasis
    manualWeightKg?: number
    proteinTargetG?: number
    carbohydrateTargetG?: number
    fatTargetG?: number
    proteinEnergyPercent?: number
    carbohydrateEnergyPercent?: number
}

export interface ResolvedGoal {
    date: string
    energyKcal?: number
    proteinG?: number
    carbohydrateG?: number
    fatG?: number
    expenditureKcal?: number
    energyRule?: EnergyGoalMode
    warnings: string[]
}

export interface UpdateDiaryEntryInput {
    localDate: string
    consumedAt: string
    meal: Meal
    quantity?: number
    unit?: string
    portionId?: string | null
    name?: string
    calories?: number
    proteinG?: number
    carbohydrateG?: number
    fatG?: number
    fiberG?: number | null
}

export interface BarcodeCandidate {
    barcode: string
    name: string
    brand?: string
    source: FoodSource
    basisType: BasisType
    nutrients: Nutrients
    externalId: string
}

export interface ExtractedNutrient {
    code: string
    amount: number
    unit: string
    confidence: number
}
export interface LabelDraft {
    name?: string
    brand?: string
    barcode?: string
    basisType?: BasisType
    basisAmount?: number
    basisUnit?: string
    servingName?: string
    servingMassG?: number
    servingVolumeMl?: number
    nutrients: ExtractedNutrient[]
    ingredients?: string
    allergens: string[]
    warnings: string[]
}
export interface ScanJob {
    id: string
    status: string
    draft?: LabelDraft
    errorMessage?: string
    expiresAt: string
}

export interface CreatedShare {
    id: string
    urlToken: string
    resourceType: 'FOOD' | 'RECIPE'
    expiresAt?: string
    createdAt: string
}

export interface SharedSnapshot {
    resourceType: 'FOOD' | 'RECIPE'
    snapshot: Food | Recipe
    expiresAt?: string
}
