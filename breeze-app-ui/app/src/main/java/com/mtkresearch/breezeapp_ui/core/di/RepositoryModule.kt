package com.mtkresearch.breezeapp_ui.core.di

import com.mtkresearch.breezeapp_ui.data.repository.ChatRepositoryImpl
import com.mtkresearch.breezeapp_ui.domain.repository.ChatRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindChatRepository(
        chatRepositoryImpl: ChatRepositoryImpl
    ): ChatRepository
} 