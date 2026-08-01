package com.baam.mobile.di

import android.content.Context
import androidx.room.Room
import com.baam.mobile.data.history.BaamDatabase
import com.baam.mobile.data.history.TaskRunDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BaamDatabase =
        Room.databaseBuilder(context, BaamDatabase::class.java, "baam.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideTaskRunDao(db: BaamDatabase): TaskRunDao = db.taskRunDao()
}
