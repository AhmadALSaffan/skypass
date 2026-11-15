package com.skypass.feature.booking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skypass.core.domain.usecase.CalculateBookingTotalUseCase
import com.skypass.core.domain.usecase.FieldProblem
import com.skypass.core.domain.usecase.GetFlightOfferUseCase
import com.skypass.core.domain.usecase.GetPassengersUseCase
import com.skypass.core.domain.usecase.GetSeatMapUseCase
import com.skypass.core.domain.usecase.ObserveSessionUseCase
import com.skypass.core.domain.usecase.PassengerField
import com.skypass.core.domain.usecase.SavePassengerUseCase
import com.skypass.core.domain.usecase.SuggestSeatsUseCase
import com.skypass.core.domain.usecase.ValidatePassengerUseCase
import com.skypass.core.model.AppError
import com.skypass.core.model.BookingQuote
import com.skypass.core.model.Cabin
import com.skypass.core.model.FlightOffer
import com.skypass.core.model.Gender
import com.skypass.core.model.LegOrder
import com.skypass.core.model.LegSelection
import com.skypass.core.model.Outcome
import com.skypass.core.model.Passenger
import com.skypass.core.model.PassportType
import com.skypass.core.model.PriceBreakdown
import com.skypass.core.model.Seat
import com.skypass.core.model.SeatMap
import com.skypass.feature.booking.navigation.BookingArgs
import com.skypass.feature.booking.navigation.PaymentRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** One leg: the departure, the whole cabin, and which seat each traveller sits in. */
data class LegState(
    val instanceId: String,
    val flight: FlightOffer? = null,
    val map: SeatMap? = null,
    /** Traveller index → seat code. */
    val assignments: Map<Int, String> = emptyMap(),
) {
    fun seatOf(traveller: Int): Seat? = assignments[traveller]?.let { map?.seat(it) }
    fun travellerIn(seat: String): Int? = assignments.entries.firstOrNull { it.value == seat }?.key
}

/** The passenger form (all nine details the airline needs). */
data class PassengerEditor(
    val id: String? = null,
    val firstName: String = "",
    val lastName: String = "",
    val gender: Gender? = null,
    val passportType: PassportType? = PassportType.Ordinary,
    val passportNumber: String = "",
    val issueDate: LocalDate? = null,
    val expiryDate: LocalDate? = null,
    val phone: String = "",
    val email: String = "",
    val isPrimary: Boolean = false,
    val problems: Map<PassengerField, FieldProblem> = emptyMap(),
    val saving: Boolean = false,
) {
    fun toPassenger() = Passenger(
        id = id,
        firstName = firstName,
        lastName = lastName,
        passportType = passportType,
        passportNumber = passportNumber.uppercase().filter(Char::isLetterOrDigit).ifBlank { null },
        passportIssueDate = issueDate,
        passportExpiryDate = expiryDate,
        gender = gender,
        phone = phone.ifBlank { null },
        email = email.ifBlank { null },
        isPrimary = isPrimary,
    )

    companion object {
        fun from(p: Passenger) = PassengerEditor(
            id = p.id,
            firstName = p.firstName,
            lastName = p.lastName,
            gender = p.gender,
            passportType = p.passportType ?: PassportType.Ordinary,
            passportNumber = p.passportNumber.orEmpty(),
            issueDate = p.passportIssueDate,
            expiryDate = p.passportExpiryDate,
            phone = p.phone.orEmpty(),
            email = p.email.orEmpty(),
            isPrimary = p.isPrimary,
        )
    }
}

data class BookingUiState(
    val adults: Int,
    val cabin: Cabin,
    val legs: List<LegState>,
    val activeLeg: Int = 0,
    /** Which traveller the next seat tap is for. */
    val activeTraveller: Int = 0,
    val loading: Boolean = true,
    val error: AppError? = null,
    /** Saved travellers, the account holder first. The first [adults] fly. */
    val passengers: List<Passenger> = emptyList(),
    /** Travellers whose details still need work (index → problems). */
    val incomplete: Set<Int> = emptySet(),
    val bags: Int = 1,
    val price: PriceBreakdown = PriceBreakdown(),
    val editor: PassengerEditor? = null,
) {
    val isRoundTrip: Boolean get() = legs.size == 2
    val travellers: List<Passenger> get() = passengers.take(adults)
    val missingTravellers: Int get() = (adults - travellers.size).coerceAtLeast(0)
    val allSeated: Boolean get() = legs.all { leg -> (0 until adults).all { it in leg.assignments } }
    val canContinue: Boolean
        get() = !loading && error == null && missingTravellers == 0 && incomplete.isEmpty() && allSeated &&
            legs.all { it.flight != null }
}

sealed interface BookingEvent {
    data class SelectLeg(val index: Int) : BookingEvent
    data class SelectTraveller(val index: Int) : BookingEvent
    data class SelectSeat(val code: String) : BookingEvent
    data object AddBag : BookingEvent
    data object RemoveBag : BookingEvent
    data class EditPassenger(val passenger: Passenger?) : BookingEvent
    data class UpdateEditor(val editor: PassengerEditor) : BookingEvent
    data object SavePassenger : BookingEvent
    data object DismissEditor : BookingEvent
    data object Continue : BookingEvent
    data object Retry : BookingEvent
}

sealed interface BookingEffect {
    data class OpenPayment(val request: PaymentRequest) : BookingEffect
    data object PassengerSaveFailed : BookingEffect
}

@HiltViewModel
class BookingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getFlightOffer: GetFlightOfferUseCase,
    private val getSeatMap: GetSeatMapUseCase,
    private val getPassengers: GetPassengersUseCase,
    private val savePassenger: SavePassengerUseCase,
    private val validatePassenger: ValidatePassengerUseCase,
    private val suggestSeats: SuggestSeatsUseCase,
    private val calculateTotal: CalculateBookingTotalUseCase,
    private val observeSession: ObserveSessionUseCase,
) : ViewModel() {

    private val args = BookingArgs(savedStateHandle)

    private val _uiState = MutableStateFlow(
        BookingUiState(
            adults = args.adults,
            cabin = args.cabin,
            legs = listOfNotNull(args.outboundInstanceId, args.returnInstanceId).map { LegState(it) },
        ),
    )
    val uiState: StateFlow<BookingUiState> = _uiState.asStateFlow()

    private val _effects = Channel<BookingEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        load()
    }

    fun onEvent(event: BookingEvent) {
        when (event) {
            is BookingEvent.SelectLeg -> _uiState.update { it.copy(activeLeg = event.index.coerceIn(0, it.legs.lastIndex)) }
            is BookingEvent.SelectTraveller -> _uiState.update { it.copy(activeTraveller = event.index.coerceIn(0, it.adults - 1)) }
            is BookingEvent.SelectSeat -> selectSeat(event.code)
            BookingEvent.AddBag -> reprice { it.copy(bags = (it.bags + 1).coerceAtMost(CalculateBookingTotalUseCase.MAX_BAGS)) }
            BookingEvent.RemoveBag -> reprice { it.copy(bags = (it.bags - 1).coerceAtLeast(0)) }
            is BookingEvent.EditPassenger -> _uiState.update { s ->
                s.copy(editor = event.passenger?.let(PassengerEditor::from) ?: PassengerEditor(isPrimary = s.passengers.isEmpty()))
            }
            is BookingEvent.UpdateEditor -> _uiState.update { s ->
                // Editing a field clears its error.
                val old = s.editor ?: return@update s
                val changed = event.editor
                val stillWrong = old.problems.filterKeys { field -> field.valueIn(old) == field.valueIn(changed) }
                s.copy(editor = changed.copy(problems = stillWrong))
            }
            BookingEvent.SavePassenger -> save()
            BookingEvent.DismissEditor -> _uiState.update { it.copy(editor = null) }
            BookingEvent.Continue -> proceed()
            BookingEvent.Retry -> load()
        }
    }

    private fun load() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val results = _uiState.value.legs.map { leg ->
                async { Triple(leg, getFlightOffer(leg.instanceId, args.cabin, args.adults), getSeatMap(leg.instanceId, args.cabin)) }
            }.map { it.await() }
            val failure = results.firstNotNullOfOrNull { (_, offer, map) ->
                (offer as? Outcome.Failure)?.error ?: (map as? Outcome.Failure)?.error
            }
            if (failure != null) {
                _uiState.update { it.copy(loading = false, error = failure) }
                return@launch
            }
            val legs = results.map { (leg, offer, map) ->
                val seatMap = (map as Outcome.Success).value
                val kept = leg.assignments.filterValues { code -> seatMap.seat(code)?.taken == false }
                val assignments = if (kept.size == args.adults) {
                    kept
                } else {
                    suggestSeats(seatMap, args.adults).mapIndexed { i, seat -> i to seat.code }.toMap()
                }
                leg.copy(flight = (offer as Outcome.Success).value, map = seatMap, assignments = assignments)
            }
            val passengers = loadPassengers()
            reprice { it.copy(loading = false, legs = legs, passengers = passengers) }
        }
    }

    /** Saved travellers; the first time, the account holder becomes the primary one (details to finish). */
    private suspend fun loadPassengers(): List<Passenger> {
        val saved = (getPassengers() as? Outcome.Success)?.value.orEmpty()
        if (saved.isNotEmpty()) return saved
        val session = observeSession().first() ?: return emptyList()
        val name = session.fullName?.trim()?.split(Regex("\\s+")) ?: return emptyList()
        val created = savePassenger(
            Passenger(
                id = null,
                firstName = name.first(),
                lastName = name.drop(1).joinToString(" "),
                email = session.email,
                isPrimary = true,
            ),
        )
        return listOfNotNull((created as? Outcome.Success)?.value)
    }

    /**
     * Seat tap: the active traveller takes the seat. If a fellow traveller had it, they swap. Then
     * the next traveller without a seat becomes active, so a family is seated in a few taps.
     */
    private fun selectSeat(code: String) {
        val s = _uiState.value
        val leg = s.legs.getOrNull(s.activeLeg) ?: return
        val seat = leg.map?.seat(code) ?: return
        if (seat.taken) return
        val active = s.activeTraveller
        val holder = leg.travellerIn(code)
        if (holder == active) return
        val updated = leg.assignments.toMutableMap()
        val previous = updated[active]
        updated[active] = code
        if (holder != null) {
            if (previous != null) updated[holder] = previous else updated.remove(holder)
        }
        val next = (0 until s.adults).firstOrNull { it != active && it !in updated } ?: active
        reprice { st ->
            st.copy(
                legs = st.legs.mapIndexed { i, l -> if (i == st.activeLeg) l.copy(assignments = updated) else l },
                activeTraveller = next,
            )
        }
    }

    private fun save() {
        val editor = _uiState.value.editor ?: return
        val problems = validatePassenger(editor.toPassenger(), tripEnds())
        if (problems.isNotEmpty()) {
            _uiState.update { it.copy(editor = editor.copy(problems = problems)) }
            return
        }
        _uiState.update { it.copy(editor = editor.copy(saving = true)) }
        viewModelScope.launch {
            when (val result = savePassenger(editor.toPassenger())) {
                is Outcome.Success -> reprice { s ->
                    val saved = result.value
                    val list = if (s.passengers.any { it.id == saved.id }) {
                        s.passengers.map { if (it.id == saved.id) saved else it }
                    } else {
                        s.passengers + saved
                    }
                    s.copy(passengers = list.sortedByDescending { it.isPrimary }, editor = null)
                }
                is Outcome.Failure -> {
                    _uiState.update { it.copy(editor = editor.copy(saving = false)) }
                    _effects.send(BookingEffect.PassengerSaveFailed)
                }
            }
        }
    }

    private fun proceed() {
        val s = _uiState.value
        if (!s.canContinue) return
        val request = PaymentRequest(
            legs = s.legs.map { leg -> LegOrder(leg.instanceId, (0 until s.adults).map { checkNotNull(leg.assignments[it]) }) },
            passengerIds = s.travellers.mapNotNull { it.id },
            cabin = s.cabin,
            bags = s.bags,
        )
        viewModelScope.launch { _effects.send(BookingEffect.OpenPayment(request)) }
    }

    /** The last day of the trip, for the passport validity check. */
    private fun tripEnds(): LocalDate? = _uiState.value.legs.mapNotNull { it.flight?.arrival?.toLocalDate() }.maxOrNull()

    /** Apply a change, then recompute completeness and the price. */
    private fun reprice(change: (BookingUiState) -> BookingUiState) {
        _uiState.update { old ->
            val s = change(old)
            val end = s.legs.mapNotNull { it.flight?.arrival?.toLocalDate() }.maxOrNull()
            val incomplete = s.travellers.indices.filter { validatePassenger(s.travellers[it], end).isNotEmpty() }.toSet()
            val legs = s.legs.mapNotNull { leg ->
                leg.flight?.let { f -> LegSelection(f, (0 until s.adults).mapNotNull { leg.seatOf(it) }) }
            }
            val price = if (legs.size == s.legs.size) calculateTotal(BookingQuote(legs, s.adults, s.bags)) else PriceBreakdown()
            s.copy(price = price, incomplete = incomplete)
        }
    }
}

/** The form value behind each field, to know which errors an edit fixes. */
private fun PassengerField.valueIn(e: PassengerEditor): Any? = when (this) {
    PassengerField.FirstName -> e.firstName
    PassengerField.LastName -> e.lastName
    PassengerField.PassportType -> e.passportType
    PassengerField.PassportNumber -> e.passportNumber
    PassengerField.IssueDate -> e.issueDate
    PassengerField.ExpiryDate -> e.expiryDate to e.issueDate
    PassengerField.Gender -> e.gender
    PassengerField.Phone -> e.phone
    PassengerField.Email -> e.email
}
