package com.mtkresearch.breezeapp_ui.data.source.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mtkresearch.breezeapp_ui.data.source.local.converter.ZonedDateTimeConverter
import com.mtkresearch.breezeapp_ui.data.source.local.dao.ChatDao
import com.mtkresearch.breezeapp_ui.data.source.local.entity.ChatMessageEntity
import com.mtkresearch.breezeapp_ui.data.source.local.entity.ChatSessionEntity

@Database(
    entities = [
        ChatSessionEntity::class,
        ChatMessageEntity::class
    ],
    version = 1,
    exportSchema = false // In a real app, this should be true and managed with schema migrations.
)
@TypeConverters(ZonedDateTimeConverter::class)
abstract class BreezeDatabase : RoomDatabase() {

    abstract fun chatDao(): ChatDao

    companion object {
        const val DATABASE_NAME = "breeze_app_ui.db"
    }
} 