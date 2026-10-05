package com.noubli.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Accès aux comptes locaux. */
@Dao
interface UserDao {

    /** Échoue (SQLiteConstraintException) si le nom d'utilisateur existe déjà. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: UserEntity): Long

    /** Le nom doit déjà être normalisé (minuscules) par l'appelant. */
    @Query("SELECT * FROM app_user WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): UserEntity?

    @Query("SELECT * FROM app_user WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): UserEntity?
}
