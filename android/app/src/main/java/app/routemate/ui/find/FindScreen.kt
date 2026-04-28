package app.routemate.ui.find

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.routemate.R

@Composable
fun FindScreen(vm: FindViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = state.fromText,
            onValueChange = vm::onFromText,
            label = { Text(stringResource(R.string.hint_origin)) },
            modifier = Modifier.fillMaxWidth(1f),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.toText,
            onValueChange = vm::onToText,
            label = { Text(stringResource(R.string.hint_destination)) },
            modifier = Modifier.fillMaxWidth(1f),
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = vm::search, enabled = !state.busy) { Text("Search") }
        Spacer(Modifier.height(16.dp))

        LazyColumn {
            items(state.results, key = { it.id }) { ride ->
                Card(Modifier.padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${ride.origin_label}  →  ${ride.destination_label}")
                        Row { Text("at ${ride.depart_at}") }
                        Row {
                            Text("${ride.seats_available} seats · ₹${ride.price_per_seat}")
                        }
                    }
                }
            }
        }
        if (state.error != null) Text("Error: ${state.error}")
    }
}
