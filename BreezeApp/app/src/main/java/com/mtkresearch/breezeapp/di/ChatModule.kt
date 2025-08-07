package com.mtkresearch.breezeapp.di

import com.mtkresearch.breezeapp.data.repository.ChatRepositoryImpl
import com.mtkresearch.breezeapp.domain.repository.ChatRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module for Chat-related dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ChatModule {
    
    @Binds
    @Singleton
    abstract fun bindChatRepository(
        chatRepositoryImpl: ChatRepositoryImpl
    ): ChatRepository
}