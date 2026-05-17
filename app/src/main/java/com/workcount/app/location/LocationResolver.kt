package com.workcount.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

interface LocationResolver {
    suspend fun getCurrentAddress(): String?
}

class AndroidLocationResolver(
    context: Context,
) : LocationResolver {
    private val appContext = context.applicationContext
    private val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)
    private val geocoder = Geocoder(appContext, Locale.getDefault())

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentAddress(): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        val location = runCatching { fusedClient.lastLocation.await() }.getOrNull() ?: return@withContext null
        reverseGeocode(location.latitude, location.longitude)
    }

    private suspend fun reverseGeocode(latitude: Double, longitude: Double): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                    continuation.resume(addresses.firstOrNull()?.toSingleLine())
                }
            }
        } else {
            runCatching {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1)
                    ?.firstOrNull()
                    ?.toSingleLine()
            }.getOrElse { error ->
                if (error is IOException) null else throw error
            }
        }
    }
}

private fun Address.toSingleLine(): String? {
    val directLine = getAddressLine(0)
    if (!directLine.isNullOrBlank()) return directLine

    return listOfNotNull(
        thoroughfare,
        subThoroughfare,
        locality,
        adminArea,
    ).joinToString(", ").ifBlank { null }
}
