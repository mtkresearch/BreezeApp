package com.mtkresearch.breezeapp_kotlin.core.di

import com.mtkresearch.breezeapp_UI.data.repository.ChatRepositoryImpl
import com.mtkresearch.breezeapp_UI.domain.repository.ChatRepository
import com.mtkresearch.breezeapp_router.data.repository.AIRouterRepositoryImpl
import com.mtkresearch.breezeapp_router.domain.repository.AIRouterRepository
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

    @Binds
    @Singleton
    abstract fun bindAIRouterRepository(
        aiRouterRepositoryImpl: AIRouterRepositoryImpl
    ): AIRouterRepository
} 