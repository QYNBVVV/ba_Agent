package com.baam.mobile.data.history

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TaskRunEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class BaamDatabase : RoomDatabase() {
    abstract fun taskRunDao(): TaskRunDao
}
