package com.skypass.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skypass.core.domain.usecase.DeletePassengerUseCase
import com.skypass.core.domain.usecase.GetPassengersUseCase
import com.skypass.core.domain.usecase.SavePassengerUseCase
import com.skypass.core.domain.usecase.ValidatePassengerUseCase
import com.skypass.core.model.AppError
import com.skypass.core.model.Outcome
import com.skypass.core.model.Passenger
import com.skypass.core.ui.PassengerEditor
import com.skypass.core.ui.edit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class PassengersUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val passengers: List<Passenger> = emptyList(),
    val editor: PassengerEditor? = null,
    /** The traveller waiting for "Remove?" to be confirmed. */
    val confirmingDelete: String? = null,
    val saveFailed: Boolean = false,
    val today: LocalDate = LocalDate.now(),
)

sealed interface PassengersEvent {
    data object Add : PassengersEvent
    data class Edit(val passenger: Passenger) : PassengersEvent
    data class UpdateEditor(val editor: PassengerEditor) : PassengersEvent
    data object Save : PassengersEvent
    data object DismissEditor : PassengersEvent
    data class AskDelete(val passengerId: String) : PassengersEvent
    data object CancelDelete : PassengersEvent
    data object ConfirmDelete : PassengersEvent
    data object Retry : PassengersEvent
}

/** "Passengers & travel documents": the travellers booking reuses, kept up to date here. */
@HiltViewModel
class PassengersViewModel @Inject constructor(
    private val getPassengers: GetPassengersUseCase,
    private val savePassenger: SavePassengerUseCase,
    private val deletePassenger: DeletePassengerUseCase,
    private val validatePassenger: ValidatePassengerUseCase,
    clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PassengersUiState(today = LocalDate.now(clock)))
    val uiState: StateFlow<PassengersUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: PassengersEvent) {
        when (event) {
            // The first traveller saved becomes the primary one (the account holder).
            PassengersEvent.Add -> _uiState.update { it.copy(editor = PassengerEditor(isPrimary = it.passengers.isEmpty()), saveFailed = false) }
            is PassengersEvent.Edit -> _uiState.update { it.copy(editor = PassengerEditor.from(event.passenger), saveFailed = false) }
            is PassengersEvent.UpdateEditor -> _uiState.update { s -> s.copy(editor = s.editor?.edit(event.editor)) }
            PassengersEvent.Save -> save()
            PassengersEvent.DismissEditor -> _uiState.update { if (it.editor?.saving == true) it else it.copy(editor = null) }
            is PassengersEvent.AskDelete -> _uiState.update { it.copy(confirmingDelete = event.passengerId) }
            PassengersEvent.CancelDelete -> _uiState.update { it.copy(confirmingDelete = null) }
            PassengersEvent.ConfirmDelete -> delete()
            PassengersEvent.Retry -> load()
        }
    }

    private fun load() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = getPassengers()) {
                is Outcome.Success -> _uiState.update { it.copy(loading = false, passengers = result.value) }
                is Outcome.Failure -> _uiState.update { it.copy(loading = false, error = result.error) }
            }
        }
    }

    private fun save() {
        val editor = _uiState.value.editor ?: return
        if (editor.saving) return
        // Outside a booking there's no trip end: documents only need to be valid today.
        val problems = validatePassenger(editor.toPassenger(), tripEnds = null)
        if (problems.isNotEmpty()) {
            _uiState.update { it.copy(editor = editor.copy(problems = problems)) }
            return
        }
        _uiState.update { it.copy(editor = editor.copy(saving = true), saveFailed = false) }
        viewModelScope.launch {
            when (val result = savePassenger(editor.toPassenger())) {
                is Outcome.Success -> _uiState.update { s ->
                    val saved = result.value
                    val list = if (s.passengers.any { it.id == saved.id }) s.passengers.map { if (it.id == saved.id) saved else it } else s.passengers + saved
                    s.copy(editor = null, passengers = list)
                }
                is Outcome.Failure -> _uiState.update { it.copy(editor = it.editor?.copy(saving = false), saveFailed = true) }
            }
        }
    }

    private fun delete() {
        val id = _uiState.value.confirmingDelete ?: return
        val before = _uiState.value.passengers
        // Optimistic: the card goes at once and comes back if the server refuses.
        _uiState.update { s -> s.copy(confirmingDelete = null, passengers = s.passengers.filterNot { it.id == id }) }
        viewModelScope.launch {
            if (deletePassenger(id) is Outcome.Failure) _uiState.update { it.copy(passengers = before) }
        }
    }
}
