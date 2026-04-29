package app.routemate.ui.offer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import app.routemate.data.GeocodeHit

@Composable
fun OfferScreen(vm: OfferViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val scroll = rememberScrollState()
    val context = LocalContext.current

    val locationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) vm.useCurrentLocation(OfferField.Origin)
    }

    fun useMyLocation() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            vm.useCurrentLocation(OfferField.Origin)
        } else {
            locationPermLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    if (state.postedRideId != null) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Ride posted ✓", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Riders nearby can now find and book your ride.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = vm::resetPosted) { Text("Post another") }
        }
        return
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(scroll),
    ) {
        OutlinedTextField(
            value = state.originText,
            onValueChange = { vm.onTextChange(OfferField.Origin, it) },
            label = { Text("From") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = ::useMyLocation) {
                Icon(Icons.Outlined.MyLocation, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Use my location")
            }
        }
        Suggestions(state.originHits) { vm.onPickHit(OfferField.Origin, it) }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.destinationText,
            onValueChange = { vm.onTextChange(OfferField.Destination, it) },
            label = { Text("To") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Suggestions(state.destinationHits) { vm.onPickHit(OfferField.Destination, it) }

        Spacer(Modifier.height(20.dp))

        Text("Depart in: ${state.departInMinutes} min", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = state.departInMinutes.toFloat(),
            onValueChange = { vm.onDepartInMinutesChange(it.toInt()) },
            valueRange = 5f..240f,
            steps = 23,
        )

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Seats", modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { vm.onSeatsChange(-1) }) { Text("−") }
            Text(
                "  ${state.seats}  ",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            OutlinedButton(onClick = { vm.onSeatsChange(+1) }) { Text("+") }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.pricePerSeat,
            onValueChange = vm::onPriceChange,
            label = { Text("Price per seat (₹)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(Modifier.height(16.dp))
        Text("Repeat on", style = MaterialTheme.typography.bodyMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            val labels = listOf("M", "T", "W", "T", "F", "S", "S")
            labels.forEachIndexed { idx, label ->
                FilterChip(
                    selected = (state.recurrenceDays shr idx) and 1 == 1,
                    onClick = { vm.toggleRecurrenceDay(idx) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(),
                )
            }
        }
        if (state.recurrenceDays != 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                "After this ride completes, the next matching weekday's ride is auto-posted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = vm::post,
            enabled = state.canPost && !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state.busy) "Posting…" else "Post ride") }

        if (state.error != null) {
            Spacer(Modifier.height(8.dp))
            Text("Error: ${state.error}", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun Suggestions(hits: List<GeocodeHit>, onPick: (GeocodeHit) -> Unit) {
    if (hits.isEmpty()) return
    Card(Modifier.padding(top = 4.dp).fillMaxWidth().heightIn(max = 220.dp)) {
        LazyColumn {
            items(hits) { hit ->
                Text(
                    text = hit.label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(hit) }
                        .padding(12.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                HorizontalDivider()
            }
        }
    }
}
