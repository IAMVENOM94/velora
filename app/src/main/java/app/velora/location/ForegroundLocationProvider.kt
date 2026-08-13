package app.velora.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.core.content.ContextCompat
import app.velora.domain.model.ProfileLocation
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

sealed interface LocationResult {
    data class Located(val value: ProfileLocation) : LocationResult
    data object PermissionDenied : LocationResult
    data object Unavailable : LocationResult
}

interface ForegroundLocationProvider {
    suspend fun current(): LocationResult
    fun manual(country: String?, city: String): ProfileLocation = ProfileLocation(
        country = country?.trim()?.ifBlank { null },
        city = city.trim(),
        locationSource = "manual"
    )
}

@Singleton
class AndroidForegroundLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fused: FusedLocationProviderClient,
) : ForegroundLocationProvider {
    @SuppressLint("MissingPermission")
    override suspend fun current(): LocationResult {
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!coarse && !fine) return LocationResult.PermissionDenied
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(6 * 60 * 60 * 1_000L)
            .setDurationMillis(12_000L)
            .build()
        val cancellation = CancellationTokenSource()
        val location = suspendCancellableCoroutine<android.location.Location?> { continuation ->
            fused.getCurrentLocation(request, cancellation.token)
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resume(null) }
            continuation.invokeOnCancellation { cancellation.cancel() }
        } ?: return LocationResult.Unavailable
        val address = reverse(location.latitude, location.longitude) ?: return LocationResult.Unavailable
        val city = address.locality ?: address.subAdminArea ?: address.adminArea
            ?: return LocationResult.Unavailable
        return LocationResult.Located(
            ProfileLocation(
                country = address.countryName,
                region = address.adminArea,
                city = city,
                latitude = location.latitude,
                longitude = location.longitude,
                locationSource = "gps",
                accuracyMeters = location.accuracy.toInt().coerceAtLeast(0),
            )
        )
    }

    @Suppress("DEPRECATION")
    private suspend fun reverse(latitude: Double, longitude: Double) = withContext(Dispatchers.IO) {
        runCatching { Geocoder(context, Locale.getDefault()).getFromLocation(latitude, longitude, 1)?.firstOrNull() }
            .getOrNull()
    }
}
