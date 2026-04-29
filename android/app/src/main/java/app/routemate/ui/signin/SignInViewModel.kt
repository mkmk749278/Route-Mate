package app.routemate.ui.signin

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.routemate.data.AuthRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface SignInState {
    /** Single-field phone entry. Used in BOTH dev-login and OTP-stage-1. */
    data class PhoneEntry(val phone: String = "", val busy: Boolean = false) : SignInState
    /** OTP code entry, after Firebase has dispatched the SMS. */
    data class OtpEntry(val code: String = "", val busy: Boolean = false) : SignInState
    data class Error(val message: String) : SignInState
    data object Done : SignInState
}

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val authRepo: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<SignInState>(SignInState.PhoneEntry())
    val state: StateFlow<SignInState> = _state.asStateFlow()

    private var verificationId: String? = null

    /** True when this build was assembled with a real google-services.json
     *  AND Firebase initialised at app start. False on local dev-login builds. */
    val firebaseEnabled: Boolean
        get() = runCatching { FirebaseApp.getInstance() }.isSuccess

    fun onPhoneChange(v: String) {
        val s = _state.value
        if (s is SignInState.PhoneEntry) _state.value = s.copy(phone = v)
    }

    fun onCodeChange(v: String) {
        val s = _state.value
        if (s is SignInState.OtpEntry) _state.value = s.copy(code = v)
    }

    fun back() { _state.value = SignInState.PhoneEntry() }

    /** Branches on firebaseEnabled. Activity is required for the Firebase
     *  reCAPTCHA fallback path; on dev-login it's unused. */
    fun submitPhone(activity: Activity?) {
        val raw = (_state.value as? SignInState.PhoneEntry)?.phone.orEmpty()
        val phone = normalizePhone(raw) ?: run {
            _state.value = SignInState.Error("Enter a valid 10-digit phone number")
            return
        }
        _state.value = SignInState.PhoneEntry(raw, busy = true)

        if (firebaseEnabled && activity != null) {
            sendOtpFirebase(phone, activity)
        } else {
            devLogin(phone)
        }
    }

    /** Coerce raw input to E.164. Defaults missing country code to India (+91).
     *  Accepts "9876543210", "09876543210", "+919876543210", "91 98765 43210". */
    private fun normalizePhone(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        val e164 = when {
            raw.trim().startsWith("+") -> "+$digits"
            digits.length == 10 -> "+91$digits"
            digits.length == 11 && digits.startsWith("0") -> "+91${digits.drop(1)}"
            digits.length == 12 && digits.startsWith("91") -> "+$digits"
            else -> return null
        }
        // E.164: + followed by 8–15 digits.
        return e164.takeIf { it.length in 9..16 }
    }

    fun verifyOtp() {
        val s = _state.value as? SignInState.OtpEntry ?: return
        val id = verificationId ?: return
        _state.value = s.copy(busy = true)
        val cred = PhoneAuthProvider.getCredential(id, s.code.trim())
        signInWithCredential(cred)
    }

    private fun sendOtpFirebase(phone: String, activity: Activity) {
        val options = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(c: PhoneAuthCredential) {
                    signInWithCredential(c)
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

    private fun signInWithCredential(cred: PhoneAuthCredential) {
        viewModelScope.launch {
            runCatching {
                FirebaseAuth.getInstance().signInWithCredential(cred).await()
                authRepo.exchangeFirebaseToken()
            }
                .onSuccess { _state.value = SignInState.Done }
                .onFailure { _state.value = SignInState.Error(it.message ?: "sign-in failed") }
        }
    }

    private fun devLogin(phone: String) {
        viewModelScope.launch {
            runCatching { authRepo.devLogin(phone) }
                .onSuccess { _state.value = SignInState.Done }
                .onFailure { _state.value = SignInState.Error(it.message ?: "sign-in failed") }
        }
    }
}
