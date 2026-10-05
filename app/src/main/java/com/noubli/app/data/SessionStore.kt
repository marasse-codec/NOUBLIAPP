package com.noubli.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Mémorise l'utilisateur connecté (son identifiant uniquement) dans les
 * SharedPreferences, pour rester connecté entre deux lancements.
 * [userId] est un flux observable : l'UI et le service réagissent à la déconnexion.
 */
class SessionStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _userId = MutableStateFlow(readStoredId())

    /** Identifiant de l'utilisateur connecté, ou null si personne n'est connecté. */
    val userId: StateFlow<Long?> = _userId.asStateFlow()

    /** Connecte l'utilisateur [id], ou déconnecte si [id] est null. */
    fun setUser(id: Long?) {
        if (id == null) {
            prefs.edit().remove(KEY_USER_ID).apply()
        } else {
            prefs.edit().putLong(KEY_USER_ID, id).apply()
        }
        _userId.value = id
    }

    private fun readStoredId(): Long? =
        if (prefs.contains(KEY_USER_ID)) prefs.getLong(KEY_USER_ID, -1L) else null

    private companion object {
        const val PREFS_NAME = "noubli_session"
        const val KEY_USER_ID = "user_id"
    }
}
