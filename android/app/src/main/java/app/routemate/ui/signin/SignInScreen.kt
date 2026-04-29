package app.routemate.ui.signin

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.routemate.R

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    vm: SignInViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val activity = LocalContext.current.findActivity()

    LaunchedEffect(state) {
        if (state is SignInState.Done) onSignedIn()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            if (vm.firebaseEnabled) "Sign in with phone OTP" else "Quick dev sign-in",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        when (val s = state) {
            is SignInState.PhoneEntry -> {
                OutlinedTextField(
                    value = s.phone,
                    onValueChange = vm::onPhoneChange,
                    label = { Text(stringResource(R.string.hint_phone)) },
                    enabled = !s.busy,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { vm.submitPhone(activity) },
                    enabled = !s.busy && s.phone.trim().length >= 4,
                ) {
                    Text(if (vm.firebaseEnabled) "Send code" else stringResource(R.string.action_continue))
                }
            }
            is SignInState.OtpEntry -> {
                OutlinedTextField(
                    value = s.code,
                    onValueChange = vm::onCodeChange,
                    label = { Text(stringResource(R.string.hint_otp)) },
                    enabled = !s.busy,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = vm::verifyOtp,
                    enabled = !s.busy && s.code.trim().length >= 4,
                ) { Text(stringResource(R.string.action_sign_in)) }
                TextButton(onClick = vm::back) { Text("Use a different number") }
            }
            is SignInState.Error -> {
                Text(s.message, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = vm::back) { Text("Try again") }
            }
            SignInState.Done -> Unit
        }
    }
}
