package com.skypass.core.data.repository

import com.skypass.core.data.remote.SavedItemDto
import com.skypass.core.model.AppError
import com.skypass.core.model.Outcome
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update

/**
 * One kind of `saved_items` row (flight, hotel…), kept in memory for instant feedback and written
 * through to Supabase. Reloads when a different user signs in.
 */
internal class SavedItemsStore(private val supabase: SupabaseClient, private val kind: String) {

    private val ids = MutableStateFlow<Set<String>>(emptySet())
    private var loadedFor: String? = null

    fun ids(): Flow<Set<String>> = flow {
        val user = supabase.auth.currentUserOrNull()?.id
        if (user != null && user != loadedFor) {
            val loaded = safeCall {
                supabase.from("saved_items").select { filter { eq("kind", kind) } }
                    .decodeList<SavedItemDto>()
                    .map { it.itemId }
                    .toSet()
            }
            if (loaded is Outcome.Success) {
                ids.value = loaded.value
                loadedFor = user
            }
        }
        emitAll(ids)
    }

    suspend fun setSaved(id: String, saved: Boolean): Outcome<Unit, AppError> {
        // Optimistic: the icon fills immediately; roll back if the server refuses.
        ids.update { if (saved) it + id else it - id }
        val result = safeCall {
            if (saved) {
                supabase.from("saved_items").upsert(SavedItemDto(kind, id))
            } else {
                supabase.from("saved_items").delete {
                    filter {
                        eq("kind", kind)
                        eq("item_id", id)
                    }
                }
            }
            Unit
        }
        if (result is Outcome.Failure) ids.update { if (saved) it - id else it + id }
        return result
    }
}
