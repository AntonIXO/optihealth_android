package org.devpins.pihs.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UploadSuccessResponse(
    @SerialName("message")
    val message: String,
    @SerialName("received_count")
    val receivedCount: Int,
    @SerialName("inserted_count")
    val insertedCount: Int
)
