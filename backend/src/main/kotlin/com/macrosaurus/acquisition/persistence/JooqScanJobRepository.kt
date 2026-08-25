package com.macrosaurus.acquisition.persistence

import com.macrosaurus.acquisition.application.LabelDraft
import com.macrosaurus.acquisition.application.ScanJob
import com.macrosaurus.shared.JsonCodec
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository
internal class JooqScanJobRepository(
    private val db: DSLContext,
    private val json: JsonCodec,
) {
    fun insertProcessing(
        id: UUID,
        userId: String,
        expiresAt: OffsetDateTime,
    ) {
        db.execute(
            "insert into scan_jobs(id, user_id, status, expires_at) values (?, ?, 'PROCESSING', cast(? as timestamptz))",
            id,
            userId,
            expiresAt,
        )
    }

    fun markReview(
        id: UUID,
        draft: LabelDraft,
    ) {
        db.execute("update scan_jobs set status = 'REVIEW', result = cast(? as jsonb) where id = ?", json.write(draft), id)
    }

    fun markFailed(
        id: UUID,
        message: String?,
    ) {
        db.execute("update scan_jobs set status = 'FAILED', error_message = ? where id = ?", message, id)
    }

    fun find(
        userId: String,
        id: UUID,
    ): ScanJob? {
        val record =
            db.fetchOne(
                "select id, status, result::text as result, error_message, expires_at from scan_jobs where id = ? and user_id = ?",
                id,
                userId,
            ) ?: return null
        return ScanJob(
            id,
            record.get("status", String::class.java)!!,
            record.get("result", String::class.java)?.let { json.read(it, LabelDraft::class.java) },
            record.get("error_message", String::class.java),
            record.get("expires_at", OffsetDateTime::class.java)!!,
        )
    }

    fun markConfirmed(
        userId: String,
        id: UUID,
    ) {
        db.execute("update scan_jobs set status = 'CONFIRMED', result = null where id = ? and user_id = ?", id, userId)
    }
}
