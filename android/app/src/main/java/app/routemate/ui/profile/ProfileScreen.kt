package app.routemate.ui.profile

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedTextField
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
import app.routemate.data.MePatch
import app.routemate.data.RouteMatesApi
import app.routemate.data.TrustedContact
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileState(
    val me: MeOut? = null,
    val nameDraft: String = "",
    val upiDraft: String = "",
    val contacts: List<TrustedContact> = emptyList(),
    val newContactName: String = "",
    val newContactPhone: String = "",
    val saving: Boolean = false,
    val savedAt: Long? = null,
    val error: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val api: RouteMatesApi,
    private val authRepo: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            runCatching { api.me() }
                .onSuccess { me ->
                    _state.value = _state.value.copy(
                        me = me,
                        nameDraft = me.name.orEmpty(),
                        upiDraft = me.upi_id.orEmpty(),
                        contacts = me.trusted_contacts,
                    )
                }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun onNameChange(v: String) { _state.value = _state.value.copy(nameDraft = v) }
    fun onUpiChange(v: String) { _state.value = _state.value.copy(upiDraft = v) }
    fun onContactNameChange(v: String) { _state.value = _state.value.copy(newContactName = v) }
    fun onContactPhoneChange(v: String) { _state.value = _state.value.copy(newContactPhone = v) }

    fun addContact() {
        val s = _state.value
        val name = s.newContactName.trim()
        val phone = s.newContactPhone.trim()
        if (name.isEmpty() || phone.length < 4) return
        _state.value = s.copy(
            contacts = s.contacts + TrustedContact(name, phone),
            newContactName = "",
            newContactPhone = "",
        )
    }

    fun removeContact(idx: Int) {
        val s = _state.value
        if (idx !in s.contacts.indices) return
        _state.value = s.copy(contacts = s.contacts.toMutableList().also { it.removeAt(idx) })
    }

    fun save() {
        val s = _state.value
        _state.value = s.copy(saving = true, error = null)
        viewModelScope.launch {
            runCatching {
                api.patchMe(
                    MePatch(
                        name = s.nameDraft.trim().ifBlank { null },
                        upi_id = s.upiDraft.trim(),
                        trusted_contacts = s.contacts,
                    )
                )
            }
                .onSuccess {
                    _state.value = _state.value.copy(
                        me = it,
                        nameDraft = it.name.orEmpty(),
                        upiDraft = it.upi_id.orEmpty(),
                        contacts = it.trusted_contacts,
                        saving = false,
                        savedAt = System.currentTimeMillis(),
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(saving = false, error = it.message)
                }
        }
    }

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
    val state by vm.state.collectAsState()
    val me = state.me

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = me?.phone ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Rating ★ ${"%.2f".format(me?.rating_avg ?: 0.0)} (${me?.rating_count ?: 0})",
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = state.nameDraft,
            onValueChange = vm::onNameChange,
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.upiDraft,
            onValueChange = vm::onUpiChange,
            label = { Text("UPI ID (e.g. yourname@bank)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            supportingText = {
                Text(
                    "Riders use this to pay you after the ride. Leave empty to disable.",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
        )

        Spacer(Modifier.height(20.dp))
        Text(
            "Trusted contacts",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            "We'll alert these numbers if you trigger the in-ride Emergency button.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        state.contacts.forEachIndexed { idx, c ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    "${c.name} · ${c.phone}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                androidx.compose.material3.TextButton(onClick = { vm.removeContact(idx) }) {
                    Text("Remove")
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.newContactName,
                onValueChange = vm::onContactNameChange,
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.newContactPhone,
                onValueChange = vm::onContactPhoneChange,
                label = { Text("Phone") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        androidx.compose.material3.TextButton(onClick = vm::addContact) {
            Text("Add contact")
        }

        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = vm::save,
                enabled = !state.saving,
            ) { Text(if (state.saving) "Saving…" else "Save") }
            if (state.savedAt != null && !state.saving) {
                Text(
                    "Saved ✓",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        }
        if (state.error != null) {
            Spacer(Modifier.height(8.dp))
            Text("Error: ${state.error}", color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = { vm.signOut(onSignedOut) }) { Text("Sign out") }
    }
}
