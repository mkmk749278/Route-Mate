package app.routemate.ui.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.routemate.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SignInState {
    data class Idle(val phone: String = "", val busy: Boolean = false) : SignInState
    data class Error(val message: String) : SignInState
    data object Done : SignInState
}

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val authRepo: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<SignInState>(SignInState.Idle())
    val state: StateFlow<SignInState> = _state.asStateFlow()

    fun onPhoneChange(v: String) {
        val s = _state.value
        if (s is SignInState.Idle) _state.value = s.copy(phone = v)
    }

    fun back() { _state.value = SignInState.Idle() }

    fun submit() {
        val phone = (_state.value as? SignInState.Idle)?.phone?.trim().orEmpty()
        if (phone.length < 4) return
        _state.value = SignInState.Idle(phone, busy = true)

        viewModelScope.launch {
            runCatching { authRepo.devLogin(phone) }
                .onSuccess { _state.value = SignInState.Done }
                .onFailure { _state.value = SignInState.Error(it.message ?: "sign-in failed") }
        }
    }
}
