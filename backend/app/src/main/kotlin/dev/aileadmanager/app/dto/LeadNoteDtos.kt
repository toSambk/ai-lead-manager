package dev.aileadmanager.app.dto

import dev.aileadmanager.core.LeadMessage
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class AddLeadNoteRequest(
    @field:NotBlank
    @field:Size(max = 2000)
    val body: String,
)

data class LeadNoteResponse(
    val id: Long,
    val authorId: Long?,
    val body: String,
    val createdAt: Instant,
) {
    companion object {
        fun from(note: LeadMessage) = LeadNoteResponse(
            id = checkNotNull(note.id) { "Stored lead note has no ID" },
            authorId = note.senderId,
            body = note.body,
            createdAt = checkNotNull(note.createdAt) { "Stored lead note has no creation time" },
        )
    }
}
