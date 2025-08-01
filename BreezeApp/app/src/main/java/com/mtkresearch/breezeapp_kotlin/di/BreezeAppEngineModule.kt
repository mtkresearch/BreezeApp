package com.mtkresearch.breezeapp_kotlin.di

import android.content.Context
import com.mtkresearch.breezeapp_kotlin.domain.usecase.breezeapp.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for BreezeApp Engine dependencies
 * 
 * This module provides all the UseCases needed for BreezeApp Engine integration.
 * It follows Clean Architecture principles by:
 * - Providing dependencies at the appropriate scope
 * - Being independent of external frameworks
 * - Having clear separation of concerns
 */
@Module
@InstallIn(SingletonComponent::class)
object BreezeAppEngineModule {
    
    /**
     * Provides ConnectionUseCase for managing BreezeApp Engine connection
     */
    @Provides
    @Singleton
    fun provideConnectionUseCase(
        @ApplicationContext context: Context
    ): ConnectionUseCase {
        return ConnectionUseCase(context.applicationContext as android.app.Application)
    }
    
    /**
     * Provides ChatUseCase for non-streaming chat requests
     */
    @Provides
    @Singleton
    fun provideChatUseCase(): ChatUseCase {
        return ChatUseCase()
    }
    
    /**
     * Provides StreamingChatUseCase for streaming chat requests
     */
    @Provides
    @Singleton
    fun provideStreamingChatUseCase(): StreamingChatUseCase {
        return StreamingChatUseCase()
    }
    
    /**
     * Provides TtsUseCase for text-to-speech requests
     */
    @Provides
    @Singleton
    fun provideTtsUseCase(): TtsUseCase {
        return TtsUseCase()
    }
    
    /**
     * Provides AsrFileUseCase for file-based speech recognition
     */
    @Provides
    @Singleton
    fun provideAsrFileUseCase(): AsrFileUseCase {
        return AsrFileUseCase()
    }
    
    /**
     * Provides AsrMicrophoneUseCase for microphone-based speech recognition
     */
    @Provides
    @Singleton
    fun provideAsrMicrophoneUseCase(): AsrMicrophoneUseCase {
        return AsrMicrophoneUseCase()
    }
    
    /**
     * Provides RequestCancellationUseCase for request cancellation
     */
    @Provides
    @Singleton
    fun provideRequestCancellationUseCase(): RequestCancellationUseCase {
        return RequestCancellationUseCase()
    }
}