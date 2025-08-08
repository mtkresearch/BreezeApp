package com.mtkresearch.breezeapp.di

import android.content.Context
import com.mtkresearch.breezeapp.data.repository.RuntimeSettingsRepository
import com.mtkresearch.breezeapp.domain.usecase.settings.LoadRuntimeSettingsUseCase
import com.mtkresearch.breezeapp.domain.usecase.settings.SaveRuntimeSettingsUseCase
import com.mtkresearch.breezeapp.domain.usecase.settings.UpdateRuntimeParameterUseCase
import com.mtkresearch.breezeapp.domain.usecase.settings.ValidateRuntimeSettingsUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RuntimeSettingsModule {

    @Provides
    @Singleton
    fun provideRuntimeSettingsRepository(
        @ApplicationContext context: Context
    ): RuntimeSettingsRepository = RuntimeSettingsRepository(context)

    @Provides
    @Singleton
    fun provideValidateRuntimeSettingsUseCase(): ValidateRuntimeSettingsUseCase =
        ValidateRuntimeSettingsUseCase()

    @Provides
    @Singleton
    fun provideLoadRuntimeSettingsUseCase(
        repository: RuntimeSettingsRepository
    ): LoadRuntimeSettingsUseCase = LoadRuntimeSettingsUseCase(repository)

    @Provides
    @Singleton
    fun provideSaveRuntimeSettingsUseCase(
        repository: RuntimeSettingsRepository,
        validateRuntimeSettingsUseCase: ValidateRuntimeSettingsUseCase
    ): SaveRuntimeSettingsUseCase = SaveRuntimeSettingsUseCase(repository, validateRuntimeSettingsUseCase)

    @Provides
    @Singleton
    fun provideUpdateRuntimeParameterUseCase(
        validateRuntimeSettingsUseCase: ValidateRuntimeSettingsUseCase
    ): UpdateRuntimeParameterUseCase = UpdateRuntimeParameterUseCase(validateRuntimeSettingsUseCase)
}
