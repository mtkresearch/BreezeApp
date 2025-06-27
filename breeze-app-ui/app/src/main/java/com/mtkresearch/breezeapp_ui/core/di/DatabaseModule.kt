package com.mtkresearch.breezeapp_ui.core.di

import android.content.Context
import androidx.room.Room
import com.mtkresearch.breezeapp_ui.data.source.local.dao.ChatDao
import com.mtkresearch.breezeapp_ui.data.source.local.database.BreezeDatabase
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
    fun provideBreezeDatabase(@ApplicationContext context: Context): BreezeDatabase {
        return Room.databaseBuilder(
            context,
            BreezeDatabase::class.java,
            BreezeDatabase.DATABASE_NAME
        )
        .fallbackToDestructiveMigration() // Not recommended for production
        .build()
    }

    @Provides
    @Singleton
    fun provideChatDao(database: BreezeDatabase): ChatDao {
        return database.chatDao()
    }
} 