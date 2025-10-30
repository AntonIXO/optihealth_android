package org.devpins.pihs.data.model.supplement

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Parameters for the add_new_product RPC function.
 * Parameter names must match the p_... names in the SQL function.
 */
@Serializable
data class AddProductParams(
    @SerialName("p_user_id")
    val userId: String,
    @SerialName("p_compound_id")
    val compoundId: String,
    @SerialName("p_vendor_name")
    val vendorName: String,
    @SerialName("p_name_on_bottle")
    val nameOnBottle: String,
    @SerialName("p_form_factor")
    val formFactor: String, // e.g., "capsule", "tablet", "powder"
    @SerialName("p_unit_dosage")
    val unitDosage: Double,
    @SerialName("p_unit_measure")
    val unitMeasure: String // e.g., "mg", "g", "mcg"
)
