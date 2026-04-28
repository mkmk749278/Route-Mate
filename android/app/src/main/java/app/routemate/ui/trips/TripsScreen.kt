package app.routemate.ui.trips

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.routemate.data.BookingOut
import app.routemate.data.RideRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class TripsViewModel @Inject constructor(
    private val rides: RideRepository,
) : ViewModel() {
    private val _bookings = MutableStateFlow<List<BookingOut>>(emptyList())
    val bookings: StateFlow<List<BookingOut>> = _bookings.asStateFlow()

    fun load() {
        viewModelScope.launch { runCatching { rides.myBookings() }.onSuccess { _bookings.value = it } }
    }
}

@Composable
fun TripsScreen(vm: TripsViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { vm.load() }
    val bookings by vm.bookings.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn {
            items(bookings, key = { it.id }) { b ->
                Card(Modifier.padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Ride ${b.ride_id.take(8)}…")
                        Text("Status: ${b.status}")
                        Text("Seats: ${b.seats}")
                    }
                }
            }
        }
    }
}
