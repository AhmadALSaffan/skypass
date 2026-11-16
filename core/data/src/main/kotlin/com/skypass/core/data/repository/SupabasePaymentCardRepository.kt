package com.skypass.core.data.repository

import com.skypass.core.domain.repository.PaymentCardRepository
import com.skypass.core.model.AppError
import com.skypass.core.model.CardBrand
import com.skypass.core.model.Outcome
import com.skypass.core.model.SavedCard
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

@Serializable
internal data class PaymentCardDto(
    val id: String? = null,
    val brand: String,
    val last4: String,
    @SerialName("exp_month") val expMonth: Int,
    @SerialName("exp_year") val expYear: Int,
    val holder: String,
    @SerialName("is_default") val isDefault: Boolean = false,
) {
    fun toModel(): SavedCard? = CardBrand.fromApi(brand)?.let { SavedCard(id.orEmpty(), it, last4, expMonth, expYear, holder, isDefault) }
}

/** A new row: the id, owner and default flag are filled in by the database. */
@Serializable
internal data class NewPaymentCardDto(
    val brand: String,
    val last4: String,
    @SerialName("exp_month") val expMonth: Int,
    @SerialName("exp_year") val expYear: Int,
    val holder: String,
)

/** `payment_cards`, row-level secured to the owner. Holds no full numbers and no security codes. */
internal class SupabasePaymentCardRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : PaymentCardRepository {

    override suspend fun cards(): Outcome<List<SavedCard>, AppError> = supabase.safeCall {
        supabase.from("payment_cards").select {
            order("is_default", Order.DESCENDING)
            order("created_at", Order.DESCENDING)
        }.decodeList<PaymentCardDto>().mapNotNull { it.toModel() }
    }

    override suspend fun add(
        brand: CardBrand,
        last4: String,
        expMonth: Int,
        expYear: Int,
        holder: String,
        makeDefault: Boolean,
    ): Outcome<SavedCard, AppError> = supabase.safeCall {
        // Saving the same card again just returns it (the table is unique on brand, last four and expiry).
        val row = supabase.from("payment_cards").upsert(NewPaymentCardDto(brand.apiValue, last4, expMonth, expYear, holder)) {
            onConflict = "user_id,brand,last4,exp_month,exp_year"
            select()
        }.decodeSingle<PaymentCardDto>()
        val first = supabase.from("payment_cards").select().decodeList<PaymentCardDto>().size == 1
        if (makeDefault || first) supabase.postgrest.rpc("set_default_card", buildJsonObject { put("p_card", row.id) })
        requireNotNull(row.copy(isDefault = row.isDefault || makeDefault || first).toModel())
    }

    override suspend fun delete(cardId: String): Outcome<Unit, AppError> = supabase.safeCall {
        supabase.from("payment_cards").delete { filter { eq("id", cardId) } }
        Unit
    }

    override suspend fun setDefault(cardId: String): Outcome<Unit, AppError> = supabase.safeCall {
        supabase.postgrest.rpc("set_default_card", buildJsonObject { put("p_card", cardId) })
        Unit
    }
}
