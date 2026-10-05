package com.noubli.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base SQLite locale de l'application (fichier « noubli.db » sur l'appareil).
 * Room active les clés étrangères automatiquement.
 *
 * MVP : version 1 sans export de schéma. Dès qu'une table change, il faudra
 * incrémenter [version], activer exportSchema et écrire une Migration.
 */
@Database(
    entities = [
        UserEntity::class,
        ZoneEntity::class,
        ReminderItemEntity::class,
        AlertEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NoubliDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun zoneDao(): ZoneDao
    abstract fun alertEventDao(): AlertEventDao

    companion object {
        private const val DB_NAME = "noubli.db"

        /** Construit la base (l'ouverture réelle du fichier est paresseuse). */
        fun create(context: Context): NoubliDatabase =
            Room.databaseBuilder(context.applicationContext, NoubliDatabase::class.java, DB_NAME)
                .build()
    }
}
