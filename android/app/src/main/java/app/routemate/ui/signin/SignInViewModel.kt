package app.routemate.ui.signin

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.routemate.data.AuthRepository
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface SignInState {
    data class PhoneEntry(val phone: String = "", val busy: Boolean = false) : SignInState
    data class OtpEntry(val code: String = "", val busy: Boolean = false) : SignInState
    data class Error(val message: String) : SignInState
    data object Done : SignInState
}

@HiltViewModel
class SignInViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val authRepo: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<SignInState>(SignInState.PhoneEntry())
    val state: StateFlow<SignInState> = _state.asStateFlow()

    private var verificationId: String? = null

    fun onPhoneChange(v: String) {
        val s = _state.value
        if (s is SignInState.PhoneEntry) _state.value = s.copy(phone = v)
    }

    fun onCodeChange(v: String) {
        val s = _state.value
        if (s is SignInState.OtpEntry) _state.value = s.copy(code = v)
    }

    fun back() { _state.value = SignInState.PhoneEntry() }

    fun sendOtp() {
        val phone = (_state.value as? SignInState.PhoneEntry)?.phone?.trim().orEmpty()
        if (phone.isEmpty()) return
        _state.value = SignInState.PhoneEntry(phone, busy = true)

        val activity = (ctx as? Activity)
            ?: run {
                _state.value = SignInState.Error("Internal: no activity context")
                return
            }
        val options = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(c: PhoneAuthCredential) {
                    signInWith(c)
                }
                override fun onVerificationFailed(e: FirebaseException) {
                    _state.value = SignInState.Error(e.message ?: "verification failed")
                }
                override fun onCodeSent(id: String, t: PhoneAuthProvider.ForceResendingToken) {
                    verificationId = id
                    _state.value = SignInState.OtpEntry()
                }
            }).build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyOtp() {
        val s = _state.value as? SignInState.OtpEntry ?: return
        val id = verificationId ?: return
        _state.value = s.copy(busy = true)
        val cred = PhoneAuthProvider.getCredential(id, s.code.trim())
        signInWith(cred)
    }

    private fun signInWith(cred: PhoneAuthCredential) {
        viewModelScope.launch {
            runCatching {
                FirebaseAuth.getInstance().signInWithCredential(cred).await()
                authRepo.exchangeFirebaseToken()
            }.onSuccess { _state.value = SignInState.Done }
              .onFailure { _state.value = SignInState.Error(it.message ?: "sign-in failed") }
        }
    }
}
