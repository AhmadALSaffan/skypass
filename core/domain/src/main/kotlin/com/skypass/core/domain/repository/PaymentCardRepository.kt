package com.skypass.core.domain.repository

import com.skypass.core.model.AppError
import com.skypass.core.model.CardBrand
import com.skypass.core.model.Outcome
import com.skypass.core.model.SavedCard

/** Cards saved on the account. Only the brand, last four digits, expiry and holder are stored. */
interface PaymentCardRepository {
    suspend fun cards(): Outcome<List<SavedCard>, AppError>

    suspend fun add(brand: CardBrand, last4: String, expMonth: Int, expYear: Int, holder: String, makeDefault: Boolean): Outcome<SavedCard, AppError>

    suspend fun delete(cardId: String): Outcome<Unit, AppError>

    suspend fun setDefault(cardId: String): Outcome<Unit, AppError>
}
