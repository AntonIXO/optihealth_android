package org.devpins.pihs.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a metric definition from the metric_definitions table
 */
@Serializable
data class MetricDefinition(
    @SerialName("id")
    val id: Long,
    @SerialName("metric_name")
    val metricName: String,
    @SerialName("category")
    val category: String,
    @SerialName("default_unit")
    val defaultUnit: String?,
    @SerialName("beautiful_name")
    val beautifulName: String?
) {
    fun getDisplayName(): String = beautifulName ?: metricName
}
