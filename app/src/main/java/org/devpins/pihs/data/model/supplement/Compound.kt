package org.devpins.pihs.data.model.supplement

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a compound (active ingredient) in the supplement database.
 * Corresponds to the 'compounds' table in Supabase.
 */
@Serializable
data class Compound(
    @SerialName("id")
    val id: String, // UUID as string
    @SerialName("substance_id")
    val substanceId: String,
    @SerialName("name")
    val name: String,
    @SerialName("full_name")
    val fullName: String,
    @SerialName("description")
    val description: String? = null
)
