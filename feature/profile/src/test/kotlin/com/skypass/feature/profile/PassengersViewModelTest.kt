package com.skypass.feature.profile

import com.skypass.core.domain.usecase.DeletePassengerUseCase
import com.skypass.core.domain.usecase.FieldProblem
import com.skypass.core.domain.usecase.GetPassengersUseCase
import com.skypass.core.domain.usecase.PassengerField
import com.skypass.core.domain.usecase.SavePassengerUseCase
import com.skypass.core.domain.usecase.ValidatePassengerUseCase
import com.skypass.core.model.Gender
import com.skypass.core.testing.FakePassengerRepository
import com.skypass.core.testing.MainDispatcherExtension
import com.skypass.core.testing.TestPassengers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PassengersViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val clock = Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneId.of("UTC"))
    private val repo = FakePassengerRepository(listOf(TestPassengers.TINA))

    private fun viewModel() = PassengersViewModel(
        getPassengers = GetPassengersUseCase(repo),
        savePassenger = SavePassengerUseCase(repo),
        deletePassenger = DeletePassengerUseCase(repo),
        validatePassenger = ValidatePassengerUseCase(clock),
        clock = clock,
    )

    @Test
    fun `adding a traveller checks every passport detail before saving`() {
        val vm = viewModel()
        vm.onEvent(PassengersEvent.Add)
        assertFalse(vm.uiState.value.editor!!.isPrimary) // Tina already is
        vm.onEvent(PassengersEvent.Save)
        assertEquals(FieldProblem.Required, vm.uiState.value.editor!!.problems[PassengerField.FirstName])

        val sam = vm.uiState.value.editor!!.copy(
            firstName = "Sam", lastName = "Anderson", gender = Gender.Male, passportNumber = "LA7654321",
            issueDate = LocalDate.of(2023, 1, 10), expiryDate = LocalDate.of(2033, 1, 10),
            phone = "+64 21 555 0100", email = "sam@example.com",
        )
        vm.onEvent(PassengersEvent.UpdateEditor(sam))
        // Editing clears the errors it fixed.
        assertNull(vm.uiState.value.editor!!.problems[PassengerField.FirstName])
        vm.onEvent(PassengersEvent.Save)
        assertNull(vm.uiState.value.editor)
        assertEquals(listOf("Tina Anderson", "Sam Anderson"), vm.uiState.value.passengers.map { it.fullName })
        assertEquals(2, repo.stored.size)
    }

    @Test
    fun `removing asks first, and the primary traveller stays`() {
        repo.stored += TestPassengers.TINA.copy(id = "p-2", firstName = "Sam", isPrimary = false)
        val vm = viewModel()
        vm.onEvent(PassengersEvent.AskDelete("p-2"))
        assertEquals("p-2", vm.uiState.value.confirmingDelete)
        vm.onEvent(PassengersEvent.CancelDelete)
        assertEquals(2, vm.uiState.value.passengers.size)

        vm.onEvent(PassengersEvent.AskDelete("p-2"))
        vm.onEvent(PassengersEvent.ConfirmDelete)
        assertEquals(listOf("p-1"), vm.uiState.value.passengers.map { it.id })
        assertTrue(repo.stored.none { it.id == "p-2" })
    }

    @Test
    fun `a refused delete puts the traveller back`() {
        repo.stored += TestPassengers.TINA.copy(id = "p-2", firstName = "Sam", isPrimary = false)
        val vm = viewModel()
        repo.failSaves = true
        vm.onEvent(PassengersEvent.AskDelete("p-2"))
        vm.onEvent(PassengersEvent.ConfirmDelete)
        assertEquals(2, vm.uiState.value.passengers.size)
    }
}
