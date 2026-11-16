package com.skypass.feature.payment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skypass.core.domain.usecase.AddCardUseCase
import com.skypass.core.domain.usecase.CalculateBookingTotalUseCase
import com.skypass.core.domain.usecase.ValidateCardUseCase
import com.skypass.core.model.CardDraft
import com.skypass.core.ui.CardForm
import com.skypass.core.domain.usecase.CheckPromoUseCase
import com.skypass.core.domain.usecase.GetFlightOfferUseCase
import com.skypass.core.domain.usecase.GetPaymentMethodsUseCase
import com.skypass.core.domain.usecase.GetSeatMapUseCase
import com.skypass.core.domain.usecase.ObserveSessionUseCase
import com.skypass.core.domain.usecase.PayForTripUseCase
import com.skypass.core.model.AppError
import com.skypass.core.model.BookingError
import com.skypass.core.model.BookingQuote
import com.skypass.core.model.LegSelection
import com.skypass.core.model.Outcome
import com.skypass.core.model.PaymentMethod
import com.skypass.core.model.PriceBreakdown
import com.skypass.core.model.PromoCode
import com.skypass.core.model.TripOrder
import com.skypass.feature.payment.navigation.PaymentArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class PayState { Idle, Processing, Paid }

enum class PromoProblem { Invalid, Network }

data class PaymentUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val legs: List<LegSelection> = emptyList(),
    val passengers: Int = 1,
    val bags: Int = 0,
    val methods: List<PaymentMethod> = emptyList(),
    val selectedMethod: Int = 0,
    val promoInput: String = "",
    val promo: PromoCode? = null,
    val promoChecking: Boolean = false,
    val promoProblem: PromoProblem? = null,
    val price: PriceBreakdown = PriceBreakdown(),
    val payState: PayState = PayState.Idle,
    val payError: BookingError? = null,
    /** The "Add a card" sheet, while open. */
    val cardForm: CardForm? = null,
) {
    val method: PaymentMethod? get() = methods.getOrNull(selectedMethod)
    val seats: List<String> get() = legs.flatMap { leg -> leg.seats.map { it.code } }
    val canPay: Boolean get() = !loading && error == null && payState == PayState.Idle && method != null
}

sealed interface PaymentEvent {
    data class SelectMethod(val index: Int) : PaymentEvent
    data class PromoChanged(val text: String) : PaymentEvent
    data object ApplyPromo : PaymentEvent
    data object RemovePromo : PaymentEvent
    data object Pay : PaymentEvent
    data object Retry : PaymentEvent
    data object DismissPayError : PaymentEvent
    data object AddCard : PaymentEvent
    data class UpdateCard(val form: CardForm) : PaymentEvent
    data object SaveCard : PaymentEvent
    data object DismissAddCard : PaymentEvent
}

sealed interface PaymentEffect {
    data class Paid(val bookingIds: List<String>) : PaymentEffect
}

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getFlightOffer: GetFlightOfferUseCase,
    private val getSeatMap: GetSeatMapUseCase,
    private val getPaymentMethods: GetPaymentMethodsUseCase,
    private val observeSession: ObserveSessionUseCase,
    private val checkPromo: CheckPromoUseCase,
    private val calculateTotal: CalculateBookingTotalUseCase,
    private val payForTrip: PayForTripUseCase,
    private val addCard: AddCardUseCase,
    private val validateCard: ValidateCardUseCase,
) : ViewModel() {

    private val args = PaymentArgs(savedStateHandle)

    /** Stable across retries and process death, so paying again never double-books. */
    private val attemptId: String = savedStateHandle.get<String>(ATTEMPT) ?: UUID.randomUUID().toString().also { savedStateHandle[ATTEMPT] = it }

    private val _uiState = MutableStateFlow(PaymentUiState(passengers = args.passengerIds.size, bags = args.bags))
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    private val _effects = Channel<PaymentEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        load()
    }

    fun onEvent(event: PaymentEvent) {
        when (event) {
            is PaymentEvent.SelectMethod -> if (_uiState.value.payState == PayState.Idle) _uiState.update { it.copy(selectedMethod = event.index) }
            is PaymentEvent.PromoChanged -> _uiState.update { it.copy(promoInput = event.text.uppercase().filter(Char::isLetterOrDigit).take(16), promoProblem = null) }
            PaymentEvent.ApplyPromo -> applyPromo()
            PaymentEvent.RemovePromo -> reprice { it.copy(promo = null, promoInput = "") }
            PaymentEvent.Pay -> pay()
            PaymentEvent.Retry -> load()
            PaymentEvent.DismissPayError -> _uiState.update { it.copy(payError = null) }
            PaymentEvent.AddCard -> if (_uiState.value.payState == PayState.Idle) {
                // The first card a traveller adds becomes their default.
                _uiState.update { it.copy(cardForm = CardForm(CardDraft(makeDefault = it.methods.none { m -> m.cardId != null }))) }
            }
            is PaymentEvent.UpdateCard -> _uiState.update { it.copy(cardForm = event.form) }
            PaymentEvent.SaveCard -> saveCard()
            PaymentEvent.DismissAddCard -> _uiState.update { if (it.cardForm?.saving == true) it else it.copy(cardForm = null) }
        }
    }

    private fun load() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val legs = args.legs.map { leg ->
                async {
                    val offer = getFlightOffer(leg.instanceId, args.cabin, args.passengerIds.size)
                    val map = getSeatMap(leg.instanceId, args.cabin)
                    Triple(leg, offer, map)
                }
            }.map { it.await() }
            val failure = legs.firstNotNullOfOrNull { (_, o, m) -> (o as? Outcome.Failure)?.error ?: (m as? Outcome.Failure)?.error }
            if (failure != null) {
                _uiState.update { it.copy(loading = false, error = failure) }
                return@launch
            }
            val selections = legs.map { (leg, o, m) ->
                val map = (m as Outcome.Success).value
                // Seats were chosen on the previous screen; here we only need their fees.
                LegSelection((o as Outcome.Success).value, leg.seats.mapNotNull { code -> map.seat(code)?.copy(taken = false) })
            }
            val methods = getPaymentMethods(observeSession().first())
            reprice { it.copy(loading = false, legs = selections, methods = methods) }
        }
    }

    private fun applyPromo() {
        val code = _uiState.value.promoInput
        if (code.isBlank() || _uiState.value.promoChecking) return
        _uiState.update { it.copy(promoChecking = true, promoProblem = null) }
        viewModelScope.launch {
            when (val result = checkPromo(code)) {
                is Outcome.Success -> reprice {
                    val promo = result.value
                    it.copy(promoChecking = false, promo = promo, promoProblem = if (promo == null) PromoProblem.Invalid else null)
                }
                is Outcome.Failure -> _uiState.update { it.copy(promoChecking = false, promoProblem = PromoProblem.Network) }
            }
        }
    }

    private fun pay() {
        val s = _uiState.value
        val method = s.method ?: return
        if (!s.canPay) return
        _uiState.update { it.copy(payState = PayState.Processing, payError = null) }
        viewModelScope.launch {
            val order = TripOrder(
                legs = args.legs,
                cabin = args.cabin,
                passengerIds = args.passengerIds,
                bags = args.bags,
                promoCode = s.promo?.code,
                method = method.type,
                cardId = method.cardId,
            )
            when (val result = payForTrip(order, attemptId)) {
                is Outcome.Success -> {
                    _uiState.update { it.copy(payState = PayState.Paid) }
                    delay(PAID_BEAT_MS) // let "Paid ✓" land before the boarding pass prints
                    _effects.send(PaymentEffect.Paid(result.value.map { it.bookingId }))
                }
                is Outcome.Failure -> {
                    // A refused promo is dropped so the next tap succeeds at the full price.
                    _uiState.update {
                        it.copy(
                            payState = PayState.Idle,
                            payError = result.error,
                            promo = if (result.error == BookingError.InvalidPromo) null else it.promo,
                        )
                    }
                    if (result.error == BookingError.InvalidPromo) reprice { it }
                }
            }
        }
    }

    /** Saves the new card, then selects it so Pay uses it straight away. */
    private fun saveCard() {
        val form = _uiState.value.cardForm ?: return
        if (form.saving) return
        val problems = validateCard(form.draft)
        if (problems.isNotEmpty()) {
            _uiState.update { it.copy(cardForm = form.copy(problems = problems)) }
            return
        }
        _uiState.update { it.copy(cardForm = form.copy(saving = true, failed = false)) }
        viewModelScope.launch {
            when (val result = addCard(form.draft)) {
                is Outcome.Success -> {
                    val methods = getPaymentMethods(observeSession().first())
                    val index = methods.indexOfFirst { it.cardId == result.value.id }.coerceAtLeast(0)
                    _uiState.update { it.copy(cardForm = null, methods = methods, selectedMethod = index, payError = null) }
                }
                is Outcome.Failure -> _uiState.update { it.copy(cardForm = it.cardForm?.copy(saving = false, failed = true)) }
            }
        }
    }

    private fun reprice(change: (PaymentUiState) -> PaymentUiState) {
        _uiState.update { old ->
            val s = change(old)
            val price = if (s.legs.isEmpty()) PriceBreakdown() else calculateTotal(BookingQuote(s.legs, s.passengers, s.bags, s.promo))
            s.copy(price = price)
        }
    }

    private companion object {
        const val ATTEMPT = "payment_attempt"
        const val PAID_BEAT_MS = 650L
    }
}
