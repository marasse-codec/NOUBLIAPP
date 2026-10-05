package com.noubli.app.data

import android.database.sqlite.SQLiteConstraintException
import com.noubli.app.data.db.UserDao
import com.noubli.app.data.db.UserEntity
import com.noubli.app.domain.PasswordHasher
import com.noubli.app.domain.Validators
import com.noubli.app.domain.model.AuthError
import com.noubli.app.domain.model.AuthResult
import com.noubli.app.domain.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Comptes locaux : inscription, connexion, déconnexion.
 * Tout reste sur l'appareil ; aucun serveur n'est contacté.
 */
class AuthRepository(
    private val userDao: UserDao,
    private val session: SessionStore,
    private val hasher: PasswordHasher = PasswordHasher(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    /** Crée un compte puis connecte immédiatement l'utilisateur. */
    suspend fun register(rawUsername: String, password: String): AuthResult {
        val username = Validators.normalizeUsername(rawUsername)
        if (Validators.usernameError(username) != null) return AuthResult.Failure(AuthError.INVALID_USERNAME)
        if (Validators.passwordError(password) != null) return AuthResult.Failure(AuthError.WEAK_PASSWORD)
        if (userDao.findByUsername(username) != null) return AuthResult.Failure(AuthError.USERNAME_TAKEN)

        val salt = hasher.newSalt()
        // PBKDF2 est volontairement lent : on le sort du thread principal.
        val hash = withContext(Dispatchers.Default) { hasher.hash(password, salt) }
        val id = try {
            userDao.insert(
                UserEntity(username = username, passwordHash = hash, salt = salt, createdAt = clock())
            )
        } catch (e: SQLiteConstraintException) {
            // Deux inscriptions simultanées avec le même nom : l'index unique tranche.
            return AuthResult.Failure(AuthError.USERNAME_TAKEN)
        }
        session.setUser(id)
        return AuthResult.Success(User(id, username))
    }

    /** Vérifie les identifiants ; le message d'erreur ne dit pas lequel des deux est faux. */
    suspend fun login(rawUsername: String, password: String): AuthResult {
        val username = Validators.normalizeUsername(rawUsername)
        val user = userDao.findByUsername(username)
            ?: return AuthResult.Failure(AuthError.WRONG_CREDENTIALS)
        val ok = withContext(Dispatchers.Default) {
            hasher.matches(password, user.salt, user.passwordHash)
        }
        if (!ok) return AuthResult.Failure(AuthError.WRONG_CREDENTIALS)
        session.setUser(user.id)
        return AuthResult.Success(User(user.id, user.username))
    }

    /** Déconnecte l'utilisateur ; le service de surveillance s'arrête de lui-même. */
    fun logout() = session.setUser(null)
}
