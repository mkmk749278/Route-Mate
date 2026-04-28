package app.routemate.ui.find

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.routemate.data.GeocodeHit

@Composable
fun FindScreen(vm: FindViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = state.fromText,
            onValueChange = { vm.onTextChange(FindField.From, it) },
            label = { Text("From") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Suggestions(state.fromHits) { vm.onPickHit(FindField.From, it) }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.toText,
            onValueChange = { vm.onTextChange(FindField.To, it) },
            label = { Text("To") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Suggestions(state.toHits) { vm.onPickHit(FindField.To, it) }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = vm::search,
            enabled = state.canSearch && !state.busy,
        ) { Text("Search") }
        Spacer(Modifier.height(16.dp))

        if (state.error != null) {
            Text("Error: ${state.error}", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        if (state.results.isEmpty() && state.canSearch && !state.busy) {
            Text(
                "No rides match yet. Try a different time window or wider radius.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn {
            items(state.results, key = { it.id }) { ride ->
                Card(Modifier.padding(vertical = 4.dp).fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "${ride.origin_label}  →  ${ride.destination_label}",
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row {
                            Text(
                                "${ride.seats_available}/${ride.seats_total} seats · ₹${ride.price_per_seat}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
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
