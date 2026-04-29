package app.routemate.ui.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.routemate.data.RideBooking
import app.routemate.data.RideOut

@Composable
fun TripsScreen(
    onOpenRide: (rideId: String) -> Unit,
    vm: TripsViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { vm.load() }
    val state by vm.state.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (state.error != null) {
            Text("Error: ${state.error}", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }
        if (state.isEmpty && !state.busy) {
            Spacer(Modifier.height(64.dp))
            Text(
                "No upcoming trips. Post a ride from Offer or find one from Find.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }

        LazyColumn {
            if (state.driving.isNotEmpty()) {
                item { SectionHeader("Driving") }
                items(state.driving, key = { "d-${it.id}" }) { ride ->
                    DrivingCard(ride) { onOpenRide(ride.id) }
                }
            }
            if (state.riding.isNotEmpty()) {
                item { SectionHeader("Riding") }
                items(state.riding, key = { "r-${it.booking.id}" }) { rb ->
                    RidingCard(rb) { onOpenRide(rb.ride.id) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun DrivingCard(ride: RideOut, onClick: () -> Unit) {
    Card(
        Modifier.padding(vertical = 4.dp).fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${ride.origin_label}  →  ${ride.destination_label}",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(ride.status)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${ride.seats_total - ride.seats_available}/${ride.seats_total} booked · ₹${ride.price_per_seat}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RidingCard(rb: RideBooking, onClick: () -> Unit) {
    Card(
        Modifier.padding(vertical = 4.dp).fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${rb.ride.origin_label}  →  ${rb.ride.destination_label}",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusChip("${rb.booking.status} · ${rb.ride.status}")
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${rb.booking.seats} seat(s) · ₹${rb.ride.price_per_seat}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val container = when {
        status.contains("started") -> MaterialTheme.colorScheme.tertiaryContainer
        status.contains("accepted") -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(color = container, shape = RoundedCornerShape(50)) {
        Text(
            status,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
