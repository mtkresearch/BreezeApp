package com.mtkresearch.breezeapp_kotlin.data.repository

import com.mtkresearch.breezeapp_kotlin.data.source.local.AppSettingsLocalDataSource
import com.mtkresearch.breezeapp_kotlin.domain.model.settings.AppSettings
import com.mtkresearch.breezeapp_kotlin.domain.model.settings.FontSize
import com.mtkresearch.breezeapp_kotlin.domain.model.settings.StorageLocation
import com.mtkresearch.breezeapp_kotlin.domain.model.settings.ThemeMode
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.*
import org.mockito.MockitoAnnotations
import org.junit.jupiter.api.Assertions.*

/**
 * AppSettingsRepositoryImpl 單元測試
 *
 * 測試範圍：
 * - Repository Pattern 實作
 * - LocalDataSource 整合
 * - Flow 資料流測試
 * - 設定讀取和寫入
 * - 異常處理測試
 * - 預設值處理
 */
class AppSettingsRepositoryImplTest {

    @Mock
    private lateinit var mockLocalDataSource: AppSettingsLocalDataSource

    private lateinit var repository: AppSettingsRepositoryImpl

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        repository = AppSettingsRepositoryImpl(mockLocalDataSource)
    }

    @Test
    fun `getAppSettings should return Flow from LocalDataSource`() = runTest {
        // Given
        val expectedSettings = createTestAppSettings()
        whenever(mockLocalDataSource.getAppSettings()).thenReturn(flowOf(expectedSettings))

        // When
        val resultFlow = repository.getAppSettings()
        val result = resultFlow.first()

        // Then
        assertEquals(expectedSettings, result, "Should return settings from LocalDataSource")
        verify(mockLocalDataSource).getAppSettings()
    }

    @Test
    fun `getAppSettings should return default settings when LocalDataSource returns default`() = runTest {
        // Given
        val defaultSettings = AppSettings()
        whenever(mockLocalDataSource.getAppSettings()).thenReturn(flowOf(defaultSettings))

        // When
        val resultFlow = repository.getAppSettings()
        val result = resultFlow.first()

        // Then
        assertEquals(defaultSettings, result, "Should return default settings")
        verify(mockLocalDataSource).getAppSettings()
    }

    @Test
    fun `updateThemeMode should call LocalDataSource updateThemeMode`() = runTest {
        // Given
        val themeMode = ThemeMode.DARK

        // When
        repository.updateThemeMode(themeMode)

        // Then
        verify(mockLocalDataSource).updateThemeMode(themeMode)
    }

    @Test
    fun `updateThemeMode should handle all theme modes`() = runTest {
        // Given
        val allThemeModes = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)

        // When & Then
        for (themeMode in allThemeModes) {
            repository.updateThemeMode(themeMode)
            verify(mockLocalDataSource).updateThemeMode(themeMode)
        }

        verify(mockLocalDataSource, times(3)).updateThemeMode(any())
    }

    @Test
    fun `updateFontSize should call LocalDataSource updateFontSize`() = runTest {
        // Given
        val fontSize = FontSize.LARGE

        // When
        repository.updateFontSize(fontSize)

        // Then
        verify(mockLocalDataSource).updateFontSize(fontSize)
    }

    @Test
    fun `updateFontSize should handle all font sizes`() = runTest {
        // Given
        val allFontSizes = listOf(FontSize.SMALL, FontSize.MEDIUM, FontSize.LARGE)

        // When & Then
        for (fontSize in allFontSizes) {
            repository.updateFontSize(fontSize)
            verify(mockLocalDataSource).updateFontSize(fontSize)
        }

        verify(mockLocalDataSource, times(3)).updateFontSize(any())
    }

    @Test
    fun `getAppSettings should propagate LocalDataSource exceptions`() = runTest {
        // Given
        val exception = RuntimeException("LocalDataSource error")
        whenever(mockLocalDataSource.getAppSettings()).thenThrow(exception)

        // When & Then
        assertThrows(RuntimeException::class.java) {
            repository.getAppSettings()
        }
        verify(mockLocalDataSource).getAppSettings()
    }

    @Test
    fun `updateThemeMode should propagate LocalDataSource exceptions`() = runTest {
        // Given
        val themeMode = ThemeMode.LIGHT
        val exception = RuntimeException("Update theme failed")
        whenever(mockLocalDataSource.updateThemeMode(any())).thenThrow(exception)

        // When & Then
        assertThrows(RuntimeException::class.java) {
            runBlocking { repository.updateThemeMode(themeMode) }
        }
        verify(mockLocalDataSource).updateThemeMode(themeMode)
    }

    @Test
    fun `updateFontSize should propagate LocalDataSource exceptions`() = runTest {
        // Given
        val fontSize = FontSize.MEDIUM
        val exception = RuntimeException("Update font failed")
        whenever(mockLocalDataSource.updateFontSize(any())).thenThrow(exception)

        // When & Then
        assertThrows(RuntimeException::class.java) {
            runBlocking { repository.updateFontSize(fontSize) }
        }
        verify(mockLocalDataSource).updateFontSize(fontSize)
    }

    @Test
    fun `repository should handle multiple concurrent reads`() = runTest {
        // Given
        val settings = createTestAppSettings()
        whenever(mockLocalDataSource.getAppSettings()).thenReturn(flowOf(settings))

        // When
        val flow1 = repository.getAppSettings()
        val flow2 = repository.getAppSettings()
        val flow3 = repository.getAppSettings()

        val result1 = flow1.first()
        val result2 = flow2.first()
        val result3 = flow3.first()

        // Then
        assertEquals(settings, result1, "First read should return correct settings")
        assertEquals(settings, result2, "Second read should return correct settings")
        assertEquals(settings, result3, "Third read should return correct settings")
        verify(mockLocalDataSource, times(3)).getAppSettings()
    }

    @Test
    fun `repository should handle multiple concurrent writes`() = runTest {
        // Given
        val theme1 = ThemeMode.LIGHT
        val theme2 = ThemeMode.DARK
        val font1 = FontSize.SMALL
        val font2 = FontSize.LARGE

        // When
        val job1 = async { repository.updateThemeMode(theme1) }
        val job2 = async { repository.updateThemeMode(theme2) }
        val job3 = async { repository.updateFontSize(font1) }
        val job4 = async { repository.updateFontSize(font2) }

        // Wait for all operations to complete
        job1.await()
        job2.await()
        job3.await()
        job4.await()

        // Then
        verify(mockLocalDataSource, times(2)).updateThemeMode(any())
        verify(mockLocalDataSource, times(2)).updateFontSize(any())
    }

    @Test
    fun `repository should maintain data integrity across operations`() = runTest {
        // Given
        val initialSettings = createDefaultAppSettings()
        whenever(mockLocalDataSource.getAppSettings()).thenReturn(flowOf(initialSettings))

        // When
        val initialResult = repository.getAppSettings().first()
        repository.updateThemeMode(ThemeMode.DARK)
        repository.updateFontSize(FontSize.LARGE)

        // Then
        assertEquals(initialSettings, initialResult, "Should return initial settings")
        verify(mockLocalDataSource).getAppSettings()
        verify(mockLocalDataSource).updateThemeMode(ThemeMode.DARK)
        verify(mockLocalDataSource).updateFontSize(FontSize.LARGE)
    }

    @Test
    fun `repository should handle null or empty responses from LocalDataSource`() = runTest {
        // Given - LocalDataSource returns default settings when no data exists
        val defaultSettings = AppSettings()
        whenever(mockLocalDataSource.getAppSettings()).thenReturn(flowOf(defaultSettings))

        // When
        val result = repository.getAppSettings().first()

        // Then
        assertEquals(defaultSettings, result, "Should handle default settings gracefully")
        assertNotNull(result, "Result should never be null")
        verify(mockLocalDataSource).getAppSettings()
    }

    @Test
    fun `repository should pass exact parameters to LocalDataSource`() = runTest {
        // Given
        val specificTheme = ThemeMode.DARK
        val specificFont = FontSize.LARGE

        // When
        repository.updateThemeMode(specificTheme)
        repository.updateFontSize(specificFont)

        // Then
        val themeCaptor = argumentCaptor<ThemeMode>()
        val fontCaptor = argumentCaptor<FontSize>()
        
        verify(mockLocalDataSource).updateThemeMode(themeCaptor.capture())
        verify(mockLocalDataSource).updateFontSize(fontCaptor.capture())
        
        assertEquals(specificTheme, themeCaptor.firstValue, "Should pass exact theme mode")
        assertEquals(specificFont, fontCaptor.firstValue, "Should pass exact font size")
    }

    @Test
    fun `repository should maintain consistent interface behavior`() = runTest {
        // Given
        val settings = createTestAppSettings()
        whenever(mockLocalDataSource.getAppSettings()).thenReturn(flowOf(settings))

        // When - Multiple operations in sequence
        val result1 = repository.getAppSettings().first()
        repository.updateThemeMode(ThemeMode.LIGHT)
        val result2 = repository.getAppSettings().first()
        repository.updateFontSize(FontSize.SMALL)

        // Then - All operations should complete successfully
        assertNotNull(result1, "First read should succeed")
        assertNotNull(result2, "Second read should succeed")
        verify(mockLocalDataSource, times(2)).getAppSettings()
        verify(mockLocalDataSource).updateThemeMode(ThemeMode.LIGHT)
        verify(mockLocalDataSource).updateFontSize(FontSize.SMALL)
    }

    @Test
    fun `repository should handle IllegalArgumentException from LocalDataSource`() = runTest {
        // Given
        val themeMode = ThemeMode.SYSTEM
        val exception = IllegalArgumentException("Invalid theme mode parameter")
        whenever(mockLocalDataSource.updateThemeMode(any())).thenThrow(exception)

        // When & Then
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.updateThemeMode(themeMode) }
        }
        verify(mockLocalDataSource).updateThemeMode(themeMode)
    }

    @Test
    fun `repository should handle different exception types appropriately`() = runTest {
        // Given
        val fontSize = FontSize.MEDIUM
        val securityException = SecurityException("Permission denied")
        whenever(mockLocalDataSource.updateFontSize(any())).thenThrow(securityException)

        // When & Then
        assertThrows(SecurityException::class.java) {
            runBlocking { repository.updateFontSize(fontSize) }
        }
        verify(mockLocalDataSource).updateFontSize(fontSize)
    }

    private fun createTestAppSettings(): AppSettings {
        return AppSettings(
            themeMode = ThemeMode.DARK,
            primaryColor = "#2196F3",
            fontSize = FontSize.LARGE,
            language = "zh-TW",
            enableNotifications = false,
            enableAnimations = false,
            storageLocation = StorageLocation.EXTERNAL,
            autoBackup = true
        )
    }

    private fun createDefaultAppSettings(): AppSettings {
        return AppSettings(
            themeMode = ThemeMode.SYSTEM,
            primaryColor = "#F99A1B",
            fontSize = FontSize.MEDIUM,
            language = "en",
            enableNotifications = true,
            enableAnimations = true,
            storageLocation = StorageLocation.INTERNAL,
            autoBackup = false
        )
    }
} 