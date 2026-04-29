package app.routemate.ui.ride

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
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
    rateTargetUserId: String? = null,
    onBack: () -> Unit,
    vm: RideDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(rideId, rateTargetUserId) { vm.load(rideId, rateTargetUserId) }
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    val locationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) vm.start()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 12.dp)) {
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
        Spacer(Modifier.height(2.dp))
        Text(
            "Status: ${ride.status} · ${ride.seats_available}/${ride.seats_total} seats · ₹${ride.price_per_seat}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(220.dp)) {
            RideMap(
                origin = ride.origin,
                destination = ride.destination,
                driver = state.driverPin,
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.weight(1f))

            if (state.isMyRide) {
                if (ride.status == "scheduled") {
                    OutlinedButton(
                        onClick = vm::cancelAsDriver,
                        enabled = !state.busy,
                    ) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
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
            } else if (state.canCancelAsRider) {
                OutlinedButton(
                    onClick = vm::cancelAsRider,
                    enabled = !state.busy,
                ) { Text("Cancel booking") }
            }
        }

        val driverUpi = ride.driver.upi_id
        if (!state.isMyRide
            && !driverUpi.isNullOrBlank()
            && (ride.status == "started" || ride.status == "completed")) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val driverName = ride.driver.name ?: "Route Mates driver"
                    val intent = Intent(Intent.ACTION_VIEW, buildUpiUri(
                        payee = driverUpi,
                        payeeName = driverName,
                        amount = ride.price_per_seat,
                        note = "Route Mates ride",
                    ))
                    runCatching { context.startActivity(intent) }
                        .onFailure {
                            if (it is ActivityNotFoundException) {
                                // No UPI app installed; show error inline via VM
                            }
                        }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Pay ₹${ride.price_per_seat} via UPI") }
        }

        if (state.canPromptForRating) {
            Spacer(Modifier.height(12.dp))
            RatingSheet(
                title = if (state.isMyRide)
                    "How was ${state.rateTargetName ?: "your rider"}?"
                else "How was your ride?",
                stars = state.ratingDraftStars,
                text = state.ratingDraftText,
                submitting = state.ratingSubmitting,
                onStars = vm::onRatingStarsChange,
                onText = vm::onRatingTextChange,
                onSubmit = vm::submitRating,
            )
        } else if (state.myRating != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Thanks — you rated this ride ★${state.myRating?.stars}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))
        ChatPanel(
            chat = state.chat,
            draft = state.draft,
            onDraft = vm::onDraftChange,
            onSend = vm::sendDraft,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

@Composable
private fun RatingSheet(
    title: String,
    stars: Int,
    text: String,
    submitting: Boolean,
    onStars: (Int) -> Unit,
    onText: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
            Row {
                for (i in 1..5) {
                    IconButton(onClick = { onStars(i) }) {
                        Icon(
                            imageVector = if (i <= stars) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                            contentDescription = "$i star",
                        )
                    }
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = onText,
                placeholder = { Text("Share a quick word (optional)") },
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onSubmit,
                enabled = !submitting && stars in 1..5,
            ) { Text(if (submitting) "Submitting…" else "Submit rating") }
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

@Composable
private fun ChatPanel(
    chat: List<ChatLine>,
    draft: String,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(chat.size) {
        if (chat.isNotEmpty()) listState.animateScrollToItem(chat.lastIndex)
    }

    Column(modifier) {
        Surface(
            tonalElevation = 1.dp,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            if (chat.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Coordinate pickup with chat once the ride is booked.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    items(chat, key = { it.id.ifBlank { it.at + it.from + it.body } }) { line ->
                        ChatBubble(line)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraft,
                placeholder = { Text("Message…") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onSend,
                enabled = draft.trim().isNotEmpty(),
            ) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun buildUpiUri(payee: String, payeeName: String, amount: String, note: String): Uri =
    Uri.Builder()
        .scheme("upi")
        .authority("pay")
        .appendQueryParameter("pa", payee)
        .appendQueryParameter("pn", payeeName)
        .appendQueryParameter("am", amount)
        .appendQueryParameter("cu", "INR")
        .appendQueryParameter("tn", note)
        .build()

@Composable
private fun ChatBubble(line: ChatLine) {
    val bubble = if (line.mine) MaterialTheme.colorScheme.primaryContainer
                 else MaterialTheme.colorScheme.surfaceVariant
    val align = if (line.mine) Alignment.End else Alignment.Start

    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .align(align)
                .clip(RoundedCornerShape(12.dp))
                .background(bubble)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(line.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
