package app.routemate.ui.offer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.routemate.R

@Composable
fun OfferScreen() {
    var origin by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var seats by remember { mutableStateOf("3") }
    var price by remember { mutableStateOf("50") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(value = origin, onValueChange = { origin = it },
            label = { Text(stringResource(R.string.hint_origin)) })
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = destination, onValueChange = { destination = it },
            label = { Text(stringResource(R.string.hint_destination)) })
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = seats, onValueChange = { seats = it.filter(Char::isDigit) },
            label = { Text("Seats") })
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = price, onValueChange = { price = it.filter(Char::isDigit) },
            label = { Text("Price per seat (₹)") })
        Spacer(Modifier.height(16.dp))
        Button(onClick = { /* wire to OfferViewModel */ }) {
            Text(stringResource(R.string.action_post_ride))
        }
    }
}
