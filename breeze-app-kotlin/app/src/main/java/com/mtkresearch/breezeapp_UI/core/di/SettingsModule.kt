package com.mtkresearch.breezeapp_kotlin.core.di

import com.mtkresearch.breezeapp_kotlin.data.repository.AppSettingsRepositoryImpl
import com.mtkresearch.breezeapp_kotlin.domain.repository.AppSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {

    @Binds
    @Singleton
    abstract fun bindAppSettingsRepository(
        appSettingsRepositoryImpl: AppSettingsRepositoryImpl
    ): AppSettingsRepository

} 