package org.devpins.pihs.data.model.supplement

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a supplement vendor/manufacturer.
 * Corresponds to the 'vendors' table in Supabase.
 */
@Serializable
data class Vendor(
    @SerialName("id")
    val id: String, // UUID as string
    @SerialName("name")
    val name: String,
    @SerialName("website_url")
    val websiteUrl: String? = null
)
