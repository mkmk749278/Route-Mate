package app.routemate.ui.find

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.routemate.data.LatLng
import app.routemate.data.RideOut
import app.routemate.data.RideRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FindState(
    val fromText: String = "",
    val toText: String = "",
    val from: LatLng? = null,
    val to: LatLng? = null,
    val results: List<RideOut> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class FindViewModel @Inject constructor(
    private val rides: RideRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FindState())
    val state: StateFlow<FindState> = _state.asStateFlow()

    fun onFromText(v: String) { _state.value = _state.value.copy(fromText = v) }
    fun onToText(v: String) { _state.value = _state.value.copy(toText = v) }

    fun search() {
        val from = _state.value.from ?: LatLng(0.0, 0.0)
        val to = _state.value.to ?: LatLng(0.0, 0.0)
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            runCatching { rides.search(from = from, to = to) }
                .onSuccess { _state.value = _state.value.copy(results = it, busy = false) }
                .onFailure { _state.value = _state.value.copy(error = it.message, busy = false) }
        }
    }
}
