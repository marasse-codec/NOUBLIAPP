package com.noubli.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base SQLite locale de l'application (fichier « noubli.db » sur l'appareil).
 * Room active les clés étrangères automatiquement.
 *
 * Historique des versions :
 *  - 1 : utilisateurs, zones, objets, alertes ;
 *  - 2 : trajets enregistrés (tables `trace` et `trace_point`), voir [MIGRATION_1_2].
 *
 * Aucune donnée existante n'est jamais supprimée : chaque évolution passe par une Migration.
 */
@Database(
    entities = [
        UserEntity::class,
        ZoneEntity::class,
        ReminderItemEntity::class,
        AlertEventEntity::class,
        TraceEntity::class,
        TracePointEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class NoubliDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun zoneDao(): ZoneDao
    abstract fun alertEventDao(): AlertEventDao
    abstract fun traceDao(): TraceDao

    companion object {
        private const val DB_NAME = "noubli.db"

        /**
         * 1 -> 2 : ajoute les trajets. Le SQL reproduit exactement ce que Room génère pour
         * [TraceEntity] et [TracePointEntity] (Room vérifie ce schéma à l'ouverture de la base).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `trace` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`zone_id` INTEGER NOT NULL, `started_at` INTEGER NOT NULL, `simulated` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`zone_id`) REFERENCES `zone`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trace_zone_id` ON `trace` (`zone_id`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `trace_point` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`trace_id` INTEGER NOT NULL, `time_ms` INTEGER NOT NULL, `latitude` REAL NOT NULL, " +
                        "`longitude` REAL NOT NULL, `sigma_m` REAL NOT NULL, `distance_m` REAL NOT NULL, " +
                        "`path_m` REAL NOT NULL, " +
                        "FOREIGN KEY(`trace_id`) REFERENCES `trace`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trace_point_trace_id` ON `trace_point` (`trace_id`)")
            }
        }

        /** Construit la base (l'ouverture réelle du fichier est paresseuse). */
        fun create(context: Context): NoubliDatabase =
            Room.databaseBuilder(context.applicationContext, NoubliDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
