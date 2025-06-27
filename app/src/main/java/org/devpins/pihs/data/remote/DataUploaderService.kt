package org.devpins.pihs.data.remote

import com.github.luben.zstd.Zstd
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions // Corrected: ensure this is the one for the client extension
import io.github.jan.supabase.gotrue.auth // Corrected: ensure this is the one for the client extension
import io.ktor.client.call.body // For response.body<Type>()
import io.ktor.client.statement.HttpResponse // For the response type
import io.ktor.http.HttpStatusCode // For response.status comparison
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.devpins.pihs.data.model.DataPoint
import org.devpins.pihs.data.remote.dto.UploadSuccessResponse
import javax.inject.Inject
import javax.inject.Singleton

sealed class UploadResult {
    data class Success(val response: UploadSuccessResponse) : UploadResult()
    data class Failure(val
                       errorMessage: String, val exception: Throwable? = null) : UploadResult()
}

@Singleton
class DataUploaderService @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val json: Json
) {

    suspend fun uploadDataPoints(dataPoints: List<DataPoint>): UploadResult {
        if (dataPoints.isEmpty()) {
            return UploadResult.Success(UploadSuccessResponse("No data points to upload.", 0, 0))
        }

        return try {
            // 1. Serialize to JSON string
            val jsonString = json.encodeToString(dataPoints)

            // 2. Convert JSON string to ByteArray
            val jsonDataBytes = jsonString.encodeToByteArray()

            // 3. Compress with Zstandard
            val compressedData = Zstd.compress(jsonDataBytes)

            // 4. Invoke the Supabase Edge Function
            // Ensure the user is authenticated before calling,
            // The edge function itself also checks authentication.
            // The Supabase client should automatically add the Authorization header if a user is logged in.
            if (supabaseClient.auth.currentUserOrNull() == null) {
                return UploadResult.Failure("User not authenticated. Cannot upload data.")
            }

            val response = supabaseClient.functions.invoke(
                function = "ingest-data",
                body = compressedData
                // Default Content-Type for ByteArray should be application/octet-stream
                // If issues arise, add: headers = mapOf("Content-Type" to "application/octet-stream")
            )

            // 5. Handle the Response
            if (response.status.isSuccess()) { // Use Ktor's isSuccess() for 2xx check
                try {
                    val successResponse = response.body<UploadSuccessResponse>() // Uses io.ktor.client.call.body
                    UploadResult.Success(successResponse)
                } catch (e: Exception) {
                    UploadResult.Failure("Successfully uploaded but failed to parse success response: ${e.message}", e)
                }
            } else {
                val errorBody = try { response.body<String>() } catch (e: Exception) { "Could not read error body." } // Uses io.ktor.client.call.body
                UploadResult.Failure("Upload failed with status ${response.status.value}. Error: $errorBody")
            }
        } catch (e: Exception) {
            // Catching ZstdException, SerializationException, network exceptions, etc.
            UploadResult.Failure("An error occurred during data upload: ${e.message}", e)
        }
    }
}
