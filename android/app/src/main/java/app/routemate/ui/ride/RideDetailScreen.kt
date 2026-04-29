package app.routemate.ui.ride

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import app.routemate.data.LatLng
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun RideDetailScreen(
    rideId: String,
    onBack: () -> Unit,
    vm: RideDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(rideId) { vm.load(rideId) }
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    val locationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) vm.start()
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (state.error != null) {
            Text("Error: ${state.error}", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        val ride = state.ride
        if (ride == null) {
            Text("Loading…")
            return
        }

        Text(
            "${ride.origin_label}  →  ${ride.destination_label}",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Status: ${ride.status} · ${ride.seats_available}/${ride.seats_total} seats · ₹${ride.price_per_seat}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(360.dp)) {
            RideMap(
                origin = ride.origin,
                destination = ride.destination,
                driver = state.driverPin,
            )
        }
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.weight(1f))

            if (state.isMyRide) {
                if (ride.status == "scheduled") {
                    Button(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACCESS_FINE_LOCATION,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) vm.start() else locationPermLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                )
                            )
                        },
                        enabled = !state.busy,
                    ) { Text("Start") }
                } else if (ride.status == "started") {
                    Button(onClick = vm::complete, enabled = !state.busy) { Text("Complete") }
                }
            }
        }
    }
}

@Composable
private fun RideMap(origin: LatLng, destination: LatLng, driver: LatLng?) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { mapView },
        update = { map ->
            map.overlays.clear()
            val o = GeoPoint(origin.lat, origin.lng)
            val d = GeoPoint(destination.lat, destination.lng)

            map.overlays += Marker(map).apply {
                position = o
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Pickup"
            }
            map.overlays += Marker(map).apply {
                position = d
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Drop"
            }
            driver?.let { dp ->
                map.overlays += Marker(map).apply {
                    position = GeoPoint(dp.lat, dp.lng)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = "Driver"
                }
            }

            val center = driver?.let { GeoPoint(it.lat, it.lng) } ?: GeoPoint(
                (origin.lat + destination.lat) / 2.0,
                (origin.lng + destination.lng) / 2.0,
            )
            map.controller.setZoom(14.0)
            map.controller.setCenter(center)
            map.invalidate()
        },
    )
}
