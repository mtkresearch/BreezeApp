package com.mtkresearch.breezeapp.domain.usecase.settings

import com.mtkresearch.breezeapp.domain.model.settings.ThemeMode
import com.mtkresearch.breezeapp.domain.repository.AppSettingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.*
import org.mockito.MockitoAnnotations
import org.junit.jupiter.api.Assertions.*

/**
 * UpdateThemeModeUseCase 單元測試
 *
 * 測試範圍：
 * - 主題模式更新功能
 * - Repository 方法調用驗證
 * - 異常處理測試
 * - 所有主題模式驗證
 * - 空值處理測試
 */
class UpdateThemeModeUseCaseTest {

    @Mock
    private lateinit var mockRepository: AppSettingsRepository

    private lateinit var updateUseCase: UpdateThemeModeUseCase

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        updateUseCase = UpdateThemeModeUseCase(mockRepository)
    }

    @Test
    fun `invoke should call repository updateThemeMode with LIGHT theme`() = runTest {
        // Given
        val themeMode = ThemeMode.LIGHT

        // When
        updateUseCase(themeMode)

        // Then
        verify(mockRepository).updateThemeMode(themeMode)
    }

    @Test
    fun `invoke should call repository updateThemeMode with DARK theme`() = runTest {
        // Given
        val themeMode = ThemeMode.DARK

        // When
        updateUseCase(themeMode)

        // Then
        verify(mockRepository).updateThemeMode(themeMode)
    }

    @Test
    fun `invoke should call repository updateThemeMode with SYSTEM theme`() = runTest {
        // Given
        val themeMode = ThemeMode.SYSTEM

        // When
        updateUseCase(themeMode)

        // Then
        verify(mockRepository).updateThemeMode(themeMode)
    }

    @Test
    fun `invoke should handle all theme modes correctly`() = runTest {
        // Given
        val allThemeModes = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)

        // When & Then
        for (themeMode in allThemeModes) {
            updateUseCase(themeMode)
            verify(mockRepository).updateThemeMode(themeMode)
        }

        verify(mockRepository, times(3)).updateThemeMode(any())
    }

    @Test
    fun `invoke should propagate repository exception`() = runTest {
        // Given
        val themeMode = ThemeMode.DARK
        val exception = RuntimeException("Repository update failed")
        whenever(mockRepository.updateThemeMode(any())).thenThrow(exception)

        // When & Then
        assertThrows(RuntimeException::class.java) {
            runBlocking { updateUseCase(themeMode) }
        }
        verify(mockRepository).updateThemeMode(themeMode)
    }

    @Test
    fun `invoke should handle IllegalArgumentException from repository`() = runTest {
        // Given
        val themeMode = ThemeMode.LIGHT
        val exception = IllegalArgumentException("Invalid theme mode")
        whenever(mockRepository.updateThemeMode(any())).thenThrow(exception)

        // When & Then
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { updateUseCase(themeMode) }
        }
        verify(mockRepository).updateThemeMode(themeMode)
    }

    @Test
    fun `invoke should complete successfully when repository succeeds`() = runTest {
        // Given
        val themeMode = ThemeMode.SYSTEM
        // Repository method is suspend and returns Unit, so no need to stub

        // When
        updateUseCase(themeMode)

        // Then
        verify(mockRepository).updateThemeMode(themeMode)
        // If we reach here without exception, the test passes
    }

    @Test
    fun `invoke should call repository exactly once per invocation`() = runTest {
        // Given
        val themeMode = ThemeMode.DARK

        // When
        updateUseCase(themeMode)
        updateUseCase(themeMode)
        updateUseCase(themeMode)

        // Then
        verify(mockRepository, times(3)).updateThemeMode(themeMode)
    }

    @Test
    fun `invoke should pass exact theme mode parameter to repository`() = runTest {
        // Given
        val lightTheme = ThemeMode.LIGHT
        val darkTheme = ThemeMode.DARK
        val systemTheme = ThemeMode.SYSTEM

        // When
        updateUseCase(lightTheme)
        updateUseCase(darkTheme)
        updateUseCase(systemTheme)

        // Then
        verify(mockRepository).updateThemeMode(lightTheme)
        verify(mockRepository).updateThemeMode(darkTheme)
        verify(mockRepository).updateThemeMode(systemTheme)
        verify(mockRepository, times(3)).updateThemeMode(any())
    }

    @Test
    fun `invoke should handle concurrent calls correctly`() = runTest {
        // Given
        val themeMode1 = ThemeMode.LIGHT
        val themeMode2 = ThemeMode.DARK

        // When
        val job1 = async { updateUseCase(themeMode1) }
        val job2 = async { updateUseCase(themeMode2) }
        job1.await()
        job2.await()

        // Then
        verify(mockRepository, atLeast(2)).updateThemeMode(any())
    }

    @Test
    fun `invoke should maintain parameter integrity`() = runTest {
        // Given
        val originalThemeMode = ThemeMode.DARK

        // When
        updateUseCase(originalThemeMode)

        // Then
        val argumentCaptor = argumentCaptor<ThemeMode>()
        verify(mockRepository).updateThemeMode(argumentCaptor.capture())
        assertEquals(originalThemeMode, argumentCaptor.firstValue, "Theme mode parameter should not be modified")
    }
} 