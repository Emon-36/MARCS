package com.example.marcs

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.location.Geocoder
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView as OsmdroidMapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.IOException

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapView(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var searchText by remember { mutableStateOf("") }
    val chittagongGeoPoint = remember { GeoPoint(22.3569, 91.7832) }
    val mapViewState = remember { mutableStateOf<OsmdroidMapView?>(null) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    // Store the user's last known location to use as the trajectory start point
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }


    // --- Location Permission Handling ---
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)
            ) {
                hasLocationPermission = true
                Log.d("MapView", "Location permission granted.")
            } else {
                Log.d("MapView", "Location permission denied.")
            }
        }
    )

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Map View") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (hasLocationPermission) {
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                        .addOnSuccessListener { location ->
                            if (location != null) {
                                val userGeoPoint = GeoPoint(location.latitude, location.longitude)
                                userLocation = userGeoPoint // *** Store user location
                                mapViewState.value?.let { map ->
                                    addOrUpdateMarker(map, userGeoPoint, context, isSearchedLocation = false)
                                    map.controller.animateTo(userGeoPoint, 18.0, 1000L)
                                }
                            } else {
                                Log.d("MapView", "Could not get current location.")
                            }
                        }
                } else {
                    locationPermissionLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                }
            }) {
                Icon(Icons.Filled.MyLocation, contentDescription = "Center on me")
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    OsmdroidMapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(14.0)
                        controller.setCenter(chittagongGeoPoint)

                        // *** NEW: Add tap event listener for trajectory ***
                        val eventsReceiver = object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                                p?.let { targetPoint ->
                                    // FIX: Correctly get map center from the MapView instance
                                    val startPoint = userLocation ?: this@apply.mapCenter as GeoPoint
                                    drawTrajectory(this@apply, startPoint, targetPoint)
                                }
                                return true
                            }

                            override fun longPressHelper(p: GeoPoint?): Boolean {
                                return false
                            }
                        }
                        overlays.add(MapEventsOverlay(eventsReceiver))
                        // ***********************************************

                        mapViewState.value = this
                    }
                },
                update = { /* No updates on recomposition */ }
            )

            // --- Search and Navigate Bar ---
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Search Location") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search Icon") },
                    singleLine = true
                )
                Button(onClick = {
                    if (searchText.isNotBlank()) {
                        coroutineScope.launch {
                            geocodeAndNavigate(searchText, context, mapViewState.value)
                        }
                    }
                }) {
                    Text("Go")
                }
            }
        }
    }
}

private fun addOrUpdateMarker(map: OsmdroidMapView, geoPoint: GeoPoint, context: Context, isSearchedLocation: Boolean) {
    val markerId = if (isSearchedLocation) "searched_location" else "current_location"
    val markerTitle = if (isSearchedLocation) "Searched Location" else "My Location"

    map.overlays.filterIsInstance<Marker>().find { it.id == markerId }?.let {
        map.overlays.remove(it)
    }

    val marker = Marker(map).apply {
        id = markerId
        position = geoPoint
        title = markerTitle
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        icon = ContextCompat.getDrawable(context, if (isSearchedLocation) org.osmdroid.library.R.drawable.marker_default else org.osmdroid.library.R.drawable.person)
    }
    map.overlays.add(marker)
    map.invalidate()
}

private suspend fun geocodeAndNavigate(searchQuery: String, context: Context, map: OsmdroidMapView?) {
    if (map == null) return
    val geocoder = Geocoder(context)
    try {
        val addresses = withContext(Dispatchers.IO) {
            geocoder.getFromLocationName(searchQuery, 1)
        }
        if (addresses?.isNotEmpty() == true) {
            val location = addresses[0]
            val geoPoint = GeoPoint(location.latitude, location.longitude)
            addOrUpdateMarker(map, geoPoint, context, isSearchedLocation = true)
            // FIX: Correctly get map center from the map object
            drawTrajectory(map, map.mapCenter as GeoPoint, geoPoint) // Also draw trajectory on search
            map.controller.animateTo(geoPoint, 16.0, 1000L)
        } else {
            Log.w("MapView", "Search returned no results for: $searchQuery")
        }
    } catch (e: IOException) {
        Log.e("MapView", "Geocoding failed", e)
    }
}

// *** MODIFIED: Function to calculate and draw the trajectory with VISIBLE coordinate markers ***
private fun drawTrajectory(map: OsmdroidMapView, start: GeoPoint, end: GeoPoint) {
    // 1. Remove any previous trajectory and coordinate markers
    map.overlays.removeAll(map.overlays.filterIsInstance<Polyline>().filter { it.id == "trajectory" })
    map.overlays.removeAll(map.overlays.filterIsInstance<Marker>().filter { it.id?.startsWith("traj_marker_") == true })


    // 2. Calculate points for the arc
    val arcPoints = mutableListOf<GeoPoint>()
    val numPoints = 100 // The more points, the smoother the curve
    val numMarkers = 10 // INCREASED: Show more coordinate markers along the path

    // Calculate a "height" for the arc directly in latitude degrees.
    val latDistance = kotlin.math.abs(start.latitude - end.latitude)
    val lonDistance = kotlin.math.abs(start.longitude - end.longitude)
    val arcHeight = (latDistance + lonDistance) * 0.25 // Adjust this factor for a higher/lower arc

    for (i in 0..numPoints) {
        val fraction = i.toFloat() / numPoints
        // Linear interpolation for lat and lon
        val lat = start.latitude + fraction * (end.latitude - start.latitude)
        val lon = start.longitude + fraction * (end.longitude - start.longitude)

        // Add a curve using a sine function for a nice arc shape
        val curveOffset = (arcHeight * kotlin.math.sin(fraction * Math.PI))
        val arcLat = lat + curveOffset
        val currentPoint = GeoPoint(arcLat, lon, curveOffset * 10000) // Store altitude in meters (approx)

        arcPoints.add(currentPoint)

        // Add a coordinate marker at specified intervals. Skip the very first and last to avoid clutter.
        if (i > 0 && i < numPoints && i % (numPoints / numMarkers) == 0) {
            val infoMarker = Marker(map).apply {
                id = "traj_marker_$i"
                position = currentPoint
                title = "Lat: ${String.format("%.4f", currentPoint.latitude)}\nLon: ${String.format("%.4f", currentPoint.longitude)}\nAlt: ${String.format("%.0f", currentPoint.altitude)}m"
                // MODIFIED: Anchor the info window to appear neatly above the dot
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_TOP)
                // Use a small, unobtrusive icon for the info markers
                icon = ContextCompat.getDrawable(map.context, org.osmdroid.library.R.drawable.ic_menu_mylocation)
            }
            map.overlays.add(infoMarker)
            // NEW: Show the info window by default
            infoMarker.showInfoWindow()
        }
    }

    // 3. Create and add the Polyline
    val polyline = Polyline().apply {
        id = "trajectory"
        setPoints(arcPoints)
        outlinePaint.color = Color.parseColor("#FF0077") // A bright pink color
        outlinePaint.strokeWidth = 10f
    }

    map.overlays.add(polyline)
    map.invalidate() // Refresh the map to show the line and markers
}
