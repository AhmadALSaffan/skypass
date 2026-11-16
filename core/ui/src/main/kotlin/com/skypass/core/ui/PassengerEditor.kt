package com.skypass.core.ui

import com.skypass.core.domain.usecase.FieldProblem
import com.skypass.core.domain.usecase.PassengerField
import com.skypass.core.model.Gender
import com.skypass.core.model.Passenger
import com.skypass.core.model.PassportType
import java.time.LocalDate

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

/** [changed] with the errors it fixed removed: editing a field clears its own error. */
fun PassengerEditor.edit(changed: PassengerEditor): PassengerEditor {
    val stillWrong = problems.filterKeys { field -> field.valueIn(this) == field.valueIn(changed) }
    return changed.copy(problems = stillWrong)
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
