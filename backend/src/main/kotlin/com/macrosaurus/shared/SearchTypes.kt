package com.macrosaurus.shared

import java.util.UUID

enum class SearchStage { WORDS, PREFIX, TYPO }

/** Lightweight result used to rank across modules before loading nutrition and ingredients. */
data class SearchHit(
    val id: UUID,
    val revisionId: UUID,
    val name: String,
    val rank: Int,
    val similarity: Double,
    val sortName: String,
) {
    companion object {
        val relevance: Comparator<SearchHit> =
            compareBy<SearchHit> { it.rank }
                .thenByDescending { it.similarity }
                .thenBy { it.sortName }
                .thenBy { it.id.toString() }
    }
}
