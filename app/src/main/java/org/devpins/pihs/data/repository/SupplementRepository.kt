package org.devpins.pihs.data.repository

import android.util.Log
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.devpins.pihs.data.model.supplement.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrapper for deserializing supplement_logs with joined product data.
 */
@Serializable
private data class SupplementLogWithProduct(
    @SerialName("products")
    val product: Product?
)

/**
 * Repository for supplement-related database operations.
 * Handles all interactions with the Supabase backend for the supplement tracking system.
 */
@Singleton
class SupplementRepository @Inject constructor(
    private val postgrest: Postgrest
) {
    companion object {
        private const val TAG = "SupplementRepository"
    }

    /**
     * Search for compounds by name.
     * Used in Step 1 of the Add Product Wizard.
     */
    suspend fun searchCompounds(searchTerm: String): Result<List<Compound>> {
        return try {
            val compounds = postgrest["compounds"]
                .select {
                    filter {
                        ilike("full_name", "%${searchTerm}%")
                    }
                    limit(10)
                }
                .decodeList<Compound>()
            
            Log.d(TAG, "Found ${compounds.size} compounds for query: $searchTerm")
            Result.success(compounds)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching compounds", e)
            Result.failure(e)
        }
    }

    /**
     * Search for vendors by name.
     * Used in Step 2 of the Add Product Wizard.
     */
    suspend fun searchVendors(searchTerm: String): Result<List<Vendor>> {
        return try {
            val vendors = postgrest["vendors"]
                .select {
                    filter {
                        ilike("name", "%${searchTerm}%")
                    }
                    limit(10)
                }
                .decodeList<Vendor>()
            
            Log.d(TAG, "Found ${vendors.size} vendors for query: $searchTerm")
            Result.success(vendors)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching vendors", e)
            Result.failure(e)
        }
    }

    /**
     * Get all products in the user's cabinet.
     * Derives cabinet from distinct products the user has logged.
     * Includes joined data from compounds and vendors tables.
     * Optimized: Filters archived products server-side and uses distinct to minimize data transfer.
     */
    suspend fun getUserProducts(userId: String): Result<List<Product>> {
        return try {
            // Query supplement_logs to find distinct products user has logged
            // Note: Supabase Postgrest doesn't support SELECT DISTINCT on joined tables directly,
            // so we still need client-side distinctBy, but we filter archived products server-side
            val logsWithProducts = postgrest["supplement_logs"]
                .select(
                    columns = Columns.raw(
                        """
                        products!inner (
                            id,
                            compound_id,
                            vendor_id,
                            name_on_bottle,
                            form_factor,
                            unit_dosage,
                            unit_measure,
                            default_intake_form,
                            created_at,
                            updated_at,
                            is_archived,
                            compounds ( id, substance_id, name, full_name, description ),
                            vendors ( id, name, website_url )
                        )
                        """.trimIndent()
                    )
                ) {
                    filter {
                        eq("user_id", userId)
                        // Filter archived products on the server side
                        eq("products.is_archived", false)
                    }
                }
                .decodeList<SupplementLogWithProduct>()

            // Extract distinct products using a Set for O(1) lookups instead of distinctBy
            val productSet = mutableSetOf<String>()
            val products = logsWithProducts.mapNotNull { logWithProduct ->
                logWithProduct.product?.let { product ->
                    if (productSet.add(product.id)) product else null
                }
            }

            Log.d(TAG, "Fetched ${products.size} products for user cabinet")
            Result.success(products)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user products", e)
            Result.failure(e)
        }
    }

    /**
     * Get distinct products from user's cabinet for the quick-log widget.
     * Returns products the user has logged recently (last 90 days).
     * Optimized: Filters archived products server-side and uses Set for efficient deduplication.
     */
    suspend fun getCabinetProducts(userId: String): Result<List<Product>> {
        return try {
            // Get products logged in the last 90 days for quick access
            val cutoffDate = Instant.now().minus(90, java.time.temporal.ChronoUnit.DAYS)
            val cutoffDateStr = cutoffDate.toString()

            val logsWithProducts = postgrest["supplement_logs"]
                .select(
                    columns = Columns.raw(
                        """
                        products!inner (
                            id,
                            compound_id,
                            vendor_id,
                            name_on_bottle,
                            form_factor,
                            unit_dosage,
                            unit_measure,
                            default_intake_form,
                            is_archived,
                            compounds ( id, substance_id, name, full_name, description )
                        )
                        """.trimIndent()
                    )
                ) {
                    filter {
                        eq("user_id", userId)
                        gte("timestamp", cutoffDateStr)
                        // Filter archived products on the server side
                        eq("products.is_archived", false)
                    }
                }
                .decodeList<SupplementLogWithProduct>()

            // Extract distinct products using a Set for O(1) lookups
            val productSet = mutableSetOf<String>()
            val products = logsWithProducts.mapNotNull { logWithProduct ->
                logWithProduct.product?.let { product ->
                    if (productSet.add(product.id)) product else null
                }
            }

            Log.d(TAG, "Fetched ${products.size} cabinet products")
            Result.success(products)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching cabinet products", e)
            Result.failure(e)
        }
    }

    /**
     * Get today's supplement logs for the user.
     * Includes joined product data.
     */
    suspend fun getTodaysLogs(userId: String): Result<List<SupplementLog>> {
        return try {
            // Get start of today in ISO format
            val now = Instant.now()
            val todayStart = now.atZone(ZoneId.systemDefault())
                .toLocalDate()
                .atStartOfDay(ZoneId.of("UTC"))
                .format(DateTimeFormatter.ISO_INSTANT)
            
            val logs = postgrest["supplement_logs"]
                .select(
                    columns = Columns.raw(
                        """
                        id,
                        timestamp,
                        dosage_amount,
                        dosage_unit,
                        intake_form,
                        calculated_dosage_mg,
                        notes,
                        products ( 
                            id,
                            compound_id,
                            vendor_id,
                            name_on_bottle,
                            form_factor,
                            unit_dosage,
                            unit_measure,
                            compounds ( id, substance_id, name, full_name )
                        )
                        """.trimIndent()
                    )
                ) {
                    filter {
                        eq("user_id", userId)
                        gte("timestamp", todayStart)
                    }
                    order("timestamp", order = Order.DESCENDING)
                }
                .decodeList<SupplementLog>()
            
            Log.d(TAG, "Fetched ${logs.size} logs for today")
            Result.success(logs)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching today's logs", e)
            Result.failure(e)
        }
    }

    /**
     * Log a supplement intake (Job 2: "3-Tap Log").
     * The database trigger automatically calculates the dosage in mg.
     */
    suspend fun logSupplement(entry: NewSupplementLog): Result<Unit> {
        return try {
            postgrest["supplement_logs"]
                .insert(entry)
            
            Log.d(TAG, "Successfully logged supplement")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error logging supplement", e)
            Result.failure(e)
        }
    }

    /**
     * Add a new product to the user's cabinet using the RPC function (Job 1).
     * This calls the add_new_product PostgreSQL function which handles:
     * - Creating vendor if it doesn't exist
     * - Checking for duplicate products
     * - Inserting the new product
     */
    suspend fun addNewProduct(params: AddProductParams): Result<Product> {
        return try {
            val rpcParams = buildJsonObject {
                put("p_user_id", params.userId)
                put("p_compound_id", params.compoundId)
                put("p_vendor_name", params.vendorName)
                put("p_name_on_bottle", params.nameOnBottle)
                put("p_form_factor", params.formFactor)
                put("p_unit_dosage", params.unitDosage)
                put("p_unit_measure", params.unitMeasure)
            }
            
            val product = postgrest.rpc(
                function = "add_new_product",
                parameters = rpcParams
            ).decodeSingle<Product>()
            
            Log.d(TAG, "Successfully added new product: ${product.nameOnBottle}")
            Result.success(product)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding new product", e)
            Result.failure(e)
        }
    }

    /**
     * Archive a product globally (admin/creator function).
     * Note: This archives the product for ALL users, not just the current user.
     * In a real app, you'd want to add a user_hidden_products table for per-user hiding.
     */
    suspend fun archiveProduct(productId: String): Result<Unit> {
        return try {
            postgrest["products"]
                .update(
                    {
                        set("is_archived", true)
                    }
                ) {
                    filter {
                        eq("id", productId)
                    }
                }
            
            Log.d(TAG, "Successfully archived product")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error archiving product", e)
            Result.failure(e)
        }
    }

    /**
     * Get supplement logs within a date range.
     */
    suspend fun getLogsInRange(
        userId: String,
        startDate: String,
        endDate: String
    ): Result<List<SupplementLog>> {
        return try {
            val logs = postgrest["supplement_logs"]
                .select(
                    columns = Columns.raw(
                        """
                        id,
                        timestamp,
                        dosage_amount,
                        dosage_unit,
                        intake_form,
                        calculated_dosage_mg,
                        notes,
                        products ( 
                            id,
                            compound_id,
                            vendor_id,
                            name_on_bottle,
                            form_factor,
                            unit_dosage,
                            unit_measure,
                            compounds ( id, substance_id, name, full_name )
                        )
                        """.trimIndent()
                    )
                ) {
                    filter {
                        eq("user_id", userId)
                        gte("timestamp", startDate)
                        lte("timestamp", endDate)
                    }
                    order("timestamp", order = Order.DESCENDING)
                }
                .decodeList<SupplementLog>()
            
            Log.d(TAG, "Fetched ${logs.size} logs in range")
            Result.success(logs)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching logs in range", e)
            Result.failure(e)
        }
    }
}
