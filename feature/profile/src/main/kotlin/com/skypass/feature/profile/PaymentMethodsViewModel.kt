package com.skypass.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skypass.core.domain.usecase.AddCardUseCase
import com.skypass.core.domain.usecase.DeleteCardUseCase
import com.skypass.core.domain.usecase.GetCardsUseCase
import com.skypass.core.domain.usecase.SetDefaultCardUseCase
import com.skypass.core.domain.usecase.ValidateCardUseCase
import com.skypass.core.model.AppError
import com.skypass.core.model.CardDraft
import com.skypass.core.model.Outcome
import com.skypass.core.model.SavedCard
import com.skypass.core.ui.CardForm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class PaymentMethodsUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val cards: List<SavedCard> = emptyList(),
    val cardForm: CardForm? = null,
    /** The card waiting for "Remove?" to be confirmed. */
    val confirmingDelete: String? = null,
    val today: LocalDate = LocalDate.now(),
)

sealed interface PaymentMethodsEvent {
    data object AddCard : PaymentMethodsEvent
    data class UpdateCard(val form: CardForm) : PaymentMethodsEvent
    data object SaveCard : PaymentMethodsEvent
    data object DismissAddCard : PaymentMethodsEvent
    data class MakeDefault(val card: SavedCard) : PaymentMethodsEvent
    data class AskDelete(val cardId: String) : PaymentMethodsEvent
    data object CancelDelete : PaymentMethodsEvent
    data object ConfirmDelete : PaymentMethodsEvent
    data object Retry : PaymentMethodsEvent
}

/** Payment methods: saved cards (add, make default, remove) and Google Pay. */
@HiltViewModel
class PaymentMethodsViewModel @Inject constructor(
    private val getCards: GetCardsUseCase,
    private val addCard: AddCardUseCase,
    private val validateCard: ValidateCardUseCase,
    private val deleteCard: DeleteCardUseCase,
    private val setDefault: SetDefaultCardUseCase,
    clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentMethodsUiState(today = LocalDate.now(clock)))
    val uiState: StateFlow<PaymentMethodsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: PaymentMethodsEvent) {
        when (event) {
            PaymentMethodsEvent.AddCard -> _uiState.update { it.copy(cardForm = CardForm(CardDraft(makeDefault = it.cards.isEmpty()))) }
            is PaymentMethodsEvent.UpdateCard -> _uiState.update { it.copy(cardForm = event.form) }
            PaymentMethodsEvent.SaveCard -> save()
            PaymentMethodsEvent.DismissAddCard -> _uiState.update { if (it.cardForm?.saving == true) it else it.copy(cardForm = null) }
            is PaymentMethodsEvent.MakeDefault -> makeDefault(event.card)
            is PaymentMethodsEvent.AskDelete -> _uiState.update { it.copy(confirmingDelete = event.cardId) }
            PaymentMethodsEvent.CancelDelete -> _uiState.update { it.copy(confirmingDelete = null) }
            PaymentMethodsEvent.ConfirmDelete -> delete()
            PaymentMethodsEvent.Retry -> load()
        }
    }

    private fun load(quiet: Boolean = false) {
        if (!quiet) _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = getCards(_uiState.value.today)) {
                is Outcome.Success -> _uiState.update { it.copy(loading = false, error = null, cards = result.value) }
                is Outcome.Failure -> _uiState.update { it.copy(loading = false, error = result.error) }
            }
        }
    }

    private fun save() {
        val form = _uiState.value.cardForm ?: return
        if (form.saving) return
        val problems = validateCard(form.draft)
        if (problems.isNotEmpty()) {
            _uiState.update { it.copy(cardForm = form.copy(problems = problems)) }
            return
        }
        _uiState.update { it.copy(cardForm = form.copy(saving = true, failed = false)) }
        viewModelScope.launch {
            when (addCard(form.draft)) {
                is Outcome.Success -> {
                    _uiState.update { it.copy(cardForm = null) }
                    load(quiet = true)
                }
                is Outcome.Failure -> _uiState.update { it.copy(cardForm = it.cardForm?.copy(saving = false, failed = true)) }
            }
        }
    }

    private fun makeDefault(card: SavedCard) {
        if (card.isDefault || card.isExpired(_uiState.value.today)) return
        val before = _uiState.value.cards
        _uiState.update { s -> s.copy(cards = s.cards.map { it.copy(isDefault = it.id == card.id) }.sortedByDescending { it.isDefault }) }
        viewModelScope.launch {
            if (setDefault(card.id) is Outcome.Failure) _uiState.update { it.copy(cards = before) }
        }
    }

    private fun delete() {
        val id = _uiState.value.confirmingDelete ?: return
        val before = _uiState.value.cards
        _uiState.update { s -> s.copy(confirmingDelete = null, cards = s.cards.filterNot { it.id == id }) }
        viewModelScope.launch {
            if (deleteCard(id) is Outcome.Failure) _uiState.update { it.copy(cards = before) }
        }
    }
}
