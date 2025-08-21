package org.devpins.pihs.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Represents a single data point for ingestion.
 * This class structure matches the `data_point_insert_type` PostgreSQL type
 * and the JSON structure expected by the Supabase Edge Function.
 */
@Serializable
data class DataPoint(
    @SerialName("metric_source_id")
    val metricSourceId: Long?, // Nullable to allow for cases where it might not be applicable or available yet
    @SerialName("timestamp")
    val timestamp: String, // ISO 8601 format expected (e.g., "2023-04-01T10:00:00Z")
    @SerialName("metric_name")
    val metricName: String,
    @SerialName("value_numeric")
    val valueNumeric: Double?,
    @SerialName("value_text")
    val valueText: String?,
    @SerialName("value_json")
    val valueJson: JsonElement?,
    @SerialName("unit")
    val unit: String?,
    @SerialName("tags")
    val tags: JsonElement?
)
