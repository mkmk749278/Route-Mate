package app.routemate.ui.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.routemate.R

@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    vm: SignInViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()

    if (state is SignInState.Done) {
        onSignedIn()
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(R.string.app_name))
        Spacer(Modifier.height(24.dp))

        when (val s = state) {
            is SignInState.PhoneEntry -> {
                OutlinedTextField(
                    value = s.phone,
                    onValueChange = vm::onPhoneChange,
                    label = { Text(stringResource(R.string.hint_phone)) },
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { vm.sendOtp() }, enabled = !s.busy) {
                    Text(stringResource(R.string.action_continue))
                }
            }
            is SignInState.OtpEntry -> {
                OutlinedTextField(
                    value = s.code,
                    onValueChange = vm::onCodeChange,
                    label = { Text(stringResource(R.string.hint_otp)) },
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { vm.verifyOtp() }, enabled = !s.busy) {
                    Text(stringResource(R.string.action_sign_in))
                }
                TextButton(onClick = { vm.back() }) { Text("Use a different number") }
            }
            is SignInState.Error -> {
                Text(s.message)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { vm.back() }) { Text("Try again") }
            }
            SignInState.Done -> Unit
        }
    }
}
