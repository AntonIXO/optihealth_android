package org.devpins.pihs.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Represents an event for ingestion into the events table.
 * This matches the structure of the PostgreSQL events table.
 */
@Serializable
data class Event(
    @SerialName("event_name")
    val eventName: String,
    @SerialName("start_timestamp")
    val startTimestamp: String, // ISO 8601 format (e.g., "2023-04-01T22:00:00Z")
    @SerialName("end_timestamp")
    val endTimestamp: String?, // ISO 8601 format, nullable for ongoing events
    @SerialName("description")
    val description: String?,
    @SerialName("properties")
    val properties: JsonElement? // JSONB properties
)
