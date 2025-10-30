package org.devpins.pihs.data.model.supplement

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a supplement intake log entry.
 * Corresponds to the 'supplement_logs' table in Supabase.
 */
@Serializable
data class SupplementLog(
    @SerialName("id")
    val id: String? = null, // UUID as string, nullable for inserts
    @SerialName("user_id")
    val userId: String? = null, // Set by backend RLS
    @SerialName("product_id")
    val productId: String,
    @SerialName("timestamp")
    val timestamp: String, // ISO 8601 format
    @SerialName("dosage_amount")
    val dosageAmount: Double,
    @SerialName("dosage_unit")
    val dosageUnit: String, // e.g., "capsules", "tablets"
    @SerialName("intake_form")
    val intakeForm: String, // e.g., "oral", "sublingual"
    @SerialName("calculated_dosage_mg")
    val calculatedDosageMg: Double? = null, // Computed by database trigger
    @SerialName("notes")
    val notes: String? = null,
    // Joined data from products table
    @SerialName("products")
    val product: Product? = null
)

/**
 * DTO for creating a new supplement log entry.
 * Used for INSERT operations to avoid sending read-only fields.
 */
@Serializable
data class NewSupplementLog(
    @SerialName("product_id")
    val productId: String,
    @SerialName("dosage_amount")
    val dosageAmount: Double,
    @SerialName("dosage_unit")
    val dosageUnit: String,
    @SerialName("intake_form")
    val intakeForm: String,
    @SerialName("timestamp")
    val timestamp: String? = null, // Defaults to now() in database if not provided
    @SerialName("notes")
    val notes: String? = null
)
