package app.routemate.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
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
import app.routemate.data.AuthRepository
import app.routemate.data.MeOut
import app.routemate.data.RouteMatesApi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val api: RouteMatesApi,
    private val authRepo: AuthRepository,
) : ViewModel() {

    private val _me = MutableStateFlow<MeOut?>(null)
    val me: StateFlow<MeOut?> = _me.asStateFlow()

    fun load() { viewModelScope.launch { runCatching { api.me() }.onSuccess { _me.value = it } } }

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch { authRepo.signOut(); onDone() }
    }
}

@Composable
fun ProfileScreen(
    onSignedOut: () -> Unit,
    vm: ProfileViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { vm.load() }
    val me by vm.me.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = me?.name ?: "(no name)")
        Text(text = me?.phone ?: "")
        Spacer(Modifier.height(8.dp))
        Text(text = "Rating ★ ${"%.2f".format(me?.rating_avg ?: 0.0)} (${me?.rating_count ?: 0})")
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = { vm.signOut(onSignedOut) }) { Text("Sign out") }
    }
}
