package org.devpins.pihs.data.model.supplement

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a specific supplement product in a user's cabinet.
 * Corresponds to the 'products' table in Supabase.
 */
@Serializable
data class Product(
    @SerialName("id")
    val id: String, // UUID as string
    @SerialName("compound_id")
    val compoundId: String,
    @SerialName("vendor_id")
    val vendorId: String,
    @SerialName("name_on_bottle")
    val nameOnBottle: String,
    @SerialName("form_factor")
    val formFactor: String, // e.g., "capsule", "tablet", "powder"
    @SerialName("unit_dosage")
    val unitDosage: Double,
    @SerialName("unit_measure")
    val unitMeasure: String, // e.g., "mg", "g", "mcg"
    @SerialName("default_intake_form")
    val defaultIntakeForm: String? = "oral",
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("is_archived")
    val isArchived: Boolean? = false,
    // Joined data from related tables (not stored, fetched via foreign key join)
    @SerialName("compounds")
    val compound: Compound? = null,
    @SerialName("vendors")
    val vendor: Vendor? = null
)
