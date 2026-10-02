package com.smartspend.util

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.SearchByTextRequest
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import com.smartspend.BuildConfig
import com.smartspend.data.models.TransactionCategory
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume

data class NearbyPlace(
    val name: String,
    val placeId: String,
    val address: String?,
    val types: List<String>,
    val distanceMeters: Float?,
    val category: TransactionCategory
)

object LocationHelper {

    private const val TAG = "SmartSpend_Location"

    suspend fun getCurrentLocation(context: Context): Location? {
        Log.d(TAG, "━━━ getCurrentLocation ━━━")

        val fineOk = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseOk = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        Log.d(TAG, "FINE=$fineOk  COARSE=$coarseOk")
        if (!fineOk && !coarseOk) { Log.e(TAG, "❌ No location permission"); return null }

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        Log.d(TAG, "GPS=${lm.isProviderEnabled(LocationManager.GPS_PROVIDER)}  " +
                "Network=${lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)}")

        val client = LocationServices.getFusedLocationProviderClient(context)
        return try {
            val last = client.lastLocation.await()
            if (last != null && !isStale(last) && isAccurateEnough(last)) {
                Log.d(TAG, "✅ lastLocation lat=${last.latitude} lng=${last.longitude} " +
                        "accuracy=${last.accuracy}m age=${(System.currentTimeMillis()-last.time)/1000}s")
                last
            } else {
                Log.d(TAG, "Cached fix missing/stale/inaccurate " +
                        "(accuracy=${last?.accuracy}m) — requesting fresh high-accuracy fix…")
                val fresh = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                when {
                    fresh != null -> Log.d(TAG, "✅ Fresh: lat=${fresh.latitude} lng=${fresh.longitude} accuracy=${fresh.accuracy}m")
                    else -> Log.e(TAG, "❌ Fresh location null — no GPS fix")
                }
                // Fall back to the stale/coarse last-known fix rather than nothing at all —
                // an approximate location is still better than skipping place lookup entirely.
                fresh ?: last
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Location error: ${e.message}")
            null
        }
    }

    /** A fix worse than this is treated as unreliable for pinpointing a specific venue. */
    private const val MAX_ACCEPTABLE_ACCURACY_METERS = 50f

    private fun isAccurateEnough(location: Location) =
        !location.hasAccuracy() || location.accuracy <= MAX_ACCEPTABLE_ACCURACY_METERS

    // Escalating search radii (meters). We start tight so a well-matched venue right next to you
    // wins, but widen automatically if nothing is found — e.g. sparse areas, or a place whose
    // registered Google lat/lng sits a bit off from your GPS fix (rooftop geocoding, large campus,
    // mall interior, etc). Previously this was hardcoded to 10m, which routinely excluded the
    // venue you were actually standing in.
    private val SEARCH_RADII_METERS = listOf(75.0, 150.0, 350.0, 750.0)

    // Each shared significant word between the SMS merchant hint and a candidate's name is
    // "worth" this many meters when ranking. It lets a strong name match win over a slightly
    // closer, unrelated place, but can't override actual proximity by a large margin — e.g. a
    // supermarket 400m away can no longer outrank the coffee shop you're standing inside just
    // because its name shares a word with the SMS hint.
    private const val METERS_PER_MATCHED_WORD = 20f

    suspend fun findNearbyPlaces(
        context: Context,
        location: Location,
        merchantHint: String?
    ): List<NearbyPlace> {
        Log.d(TAG, "━━━ findNearbyPlaces ━━━  hint=$merchantHint")

        if (!hasLocationPermission(context)) {
            Log.e(TAG, "❌ No fine location permission")
            return emptyList()
        }

        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(context, BuildConfig.PLACES_API_KEY)
        }

        val placesClient = Places.createClient(context)

        for ((index, radius) in SEARCH_RADII_METERS.withIndex()) {
            val results = searchNearbyOnce(placesClient, location, merchantHint, radius)
            if (results.isNotEmpty()) {
                results.firstOrNull()?.let {
                    Log.d(TAG, "Best @ ${radius.toInt()}m radius: ${it.name}  cat=${it.category}  dist=${it.distanceMeters?.toInt()}m")
                }
                return results
            }
            Log.d(TAG, "No places within ${radius.toInt()}m" +
                    if (index < SEARCH_RADII_METERS.lastIndex) " — widening search…" else " — giving up.")
        }
        return emptyList()
    }

    private suspend fun searchNearbyOnce(
        placesClient: com.google.android.libraries.places.api.net.PlacesClient,
        location: Location,
        merchantHint: String?,
        radiusMeters: Double
    ): List<NearbyPlace> {
        // Use only fields confirmed to exist in places:3.5.0
        val placeFields = listOf(
            Place.Field.NAME,
            Place.Field.ID,
            Place.Field.ADDRESS,
            Place.Field.LAT_LNG,
            Place.Field.TYPES
        )

        val circle = CircularBounds.newInstance(
            LatLng(location.latitude, location.longitude), radiusMeters
        )

        val request = SearchNearbyRequest
            .builder(circle, placeFields)
            // Without this, the API defaults to ranking by POPULARITY, which can push the venue
            // you're actually standing in out of the top results in favor of a "bigger name"
            // place slightly farther away. We want the closest match, not the most famous one.
            .setRankPreference(SearchNearbyRequest.RankPreference.DISTANCE)
            .setMaxResultCount(20)
            .build()

        Log.d(TAG, "Sending SearchNearbyRequest within ${radiusMeters.toInt()}m…")

        return suspendCancellableCoroutine { cont ->
            placesClient.searchNearby(request)
                .addOnSuccessListener { response ->
                    val places = response.places
                    Log.d(TAG, "✅ Got ${places.size} places within ${radiusMeters.toInt()}m")

                    val results = places.map { place ->
                        // Safely extract types — works across SDK versions
                        val types = safeGetTypes(place)

                        val distMeters = place.latLng?.let { latlng ->
                            val out = FloatArray(1)
                            Location.distanceBetween(
                                location.latitude, location.longitude,
                                latlng.latitude, latlng.longitude,
                                out
                            )
                            out[0]
                        }

                        Log.d(TAG, "  → name=${place.name}  types=$types  dist=${distMeters?.toInt()}m")

                        NearbyPlace(
                            name           = place.name ?: "Unknown",
                            placeId        = place.id ?: "",
                            address        = place.address,
                            types          = types,
                            distanceMeters = distMeters,
                            category       = TransactionCategory.fromPlaceType(types)
                        )
                    }.sortedBy { p ->
                        val dist = p.distanceMeters ?: Float.MAX_VALUE
                        val bonus = if (merchantHint != null) matchScore(p.name, merchantHint) * METERS_PER_MATCHED_WORD else 0f
                        (dist - bonus).coerceAtLeast(0f)
                    }

                    cont.resume(results)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ SearchNearby failed: ${e.message}")
                    cont.resume(emptyList())
                }
        }
    }

    /**
     * Free-text merchant/place search (Places "Search by Text"), used by the manual "Search"
     * option next to the Merchant field so the user isn't limited to whatever was auto-detected
     * nearby — e.g. the transaction happened at a mall food court and the GPS-nearby list picked
     * the wrong outlet, or location wasn't available at all.
     *
     * [biasLocation], when available, nudges results toward the transaction's location without
     * strictly limiting to it — a manual search should still be able to find a place a bit
     * further away if that's genuinely what the user is looking for.
     */
    suspend fun searchPlacesByText(
        context: Context,
        query: String,
        biasLocation: Location? = null
    ): List<NearbyPlace> {
        if (query.isBlank()) return emptyList()

        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(context, BuildConfig.PLACES_API_KEY)
        }
        val placesClient = Places.createClient(context)

        val placeFields = listOf(
            Place.Field.NAME,
            Place.Field.ID,
            Place.Field.ADDRESS,
            Place.Field.LAT_LNG,
            Place.Field.TYPES
        )

        val requestBuilder = SearchByTextRequest.builder(query, placeFields)
            .setMaxResultCount(10)

        if (biasLocation != null) {
            // Wide (20km) soft bias — prefer results near the transaction, don't exclude
            // everything else the way a hard location *restriction* would.
            requestBuilder.setLocationBias(
                CircularBounds.newInstance(LatLng(biasLocation.latitude, biasLocation.longitude), 20_000.0)
            )
        }

        Log.d(TAG, "Sending SearchByTextRequest for query=\"$query\" bias=${biasLocation != null}")

        return suspendCancellableCoroutine { cont ->
            placesClient.searchByText(requestBuilder.build())
                .addOnSuccessListener { response ->
                    val results = response.places.map { place ->
                        val types = safeGetTypes(place)
                        val distMeters = biasLocation?.let { loc ->
                            place.latLng?.let { latlng ->
                                val out = FloatArray(1)
                                Location.distanceBetween(
                                    loc.latitude, loc.longitude,
                                    latlng.latitude, latlng.longitude,
                                    out
                                )
                                out[0]
                            }
                        }
                        NearbyPlace(
                            name = place.name ?: "Unknown",
                            placeId = place.id ?: "",
                            address = place.address,
                            types = types,
                            distanceMeters = distMeters,
                            category = TransactionCategory.fromPlaceType(types)
                        )
                    }
                    Log.d(TAG, "✅ SearchByText got ${results.size} result(s) for \"$query\"")
                    cont.resume(results)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ SearchByText failed: ${e.message}")
                    cont.resume(emptyList())
                }
        }
    }

    /** Extracts place types safely regardless of SDK version */
    private fun safeGetTypes(place: Place): List<String> {
        // Try place.types (new SDK)
        return try {
            place.types?.map { it.toString().lowercase() } ?: emptyList()
        } catch (e1: Exception) {
            // Fallback: reflection for older SDKs
            try {
                val m = place.javaClass.getMethod("getPlaceTypes")
                @Suppress("UNCHECKED_CAST")
                (m.invoke(place) as? List<*>)
                    ?.mapNotNull { it?.toString()?.lowercase() }
                    ?: emptyList()
            } catch (e2: Exception) {
                emptyList()
            }
        }
    }

    fun pickBestMerchant(places: List<NearbyPlace>, merchantHint: String?): NearbyPlace? {
        if (places.isEmpty()) return null
        // `places` is already ranked by combined distance + name-match score (see
        // searchNearbyOnce), so the best candidate is simply the first one. We previously
        // re-scanned the whole list here for *any* name match regardless of its rank, which
        // could resurface a farther-away place just because it shared a word with the SMS hint —
        // the exact "wrong nearby place picked" symptom. Now we only skip past results that
        // clearly aren't a real venue (roads, localities, etc).
        return places.firstOrNull { p ->
            p.types.none { it in listOf("route", "locality", "political", "street_address") }
        } ?: places.first()
    }

    private fun matchScore(placeName: String, hint: String): Int {
        val p = placeName.uppercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()
        val h = hint.uppercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()
        return p.intersect(h).size
    }

    private fun hasLocationPermission(context: Context) =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun isStale(location: Location) =
        System.currentTimeMillis() - location.time > 5 * 60 * 1000

    /**
     * Opens the given coordinates in a maps app (falls back to a browser if none is installed).
     * Used to jump from a transaction's saved location straight to Google Maps / any maps app.
     */
    fun openInMaps(context: Context, latitude: Double, longitude: Double, label: String? = null) {
        val encodedLabel = Uri.encode(label?.takeIf { it.isNotBlank() } ?: "Transaction location")
        val geoUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($encodedLabel)")
        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)

        try {
            if (mapIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapIntent)
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "geo intent resolve failed: ${e.message}")
        }

        // Fallback: open in browser via Google Maps web URL
        val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$latitude,$longitude")
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No app available to open maps: ${e.message}")
        }
    }
}