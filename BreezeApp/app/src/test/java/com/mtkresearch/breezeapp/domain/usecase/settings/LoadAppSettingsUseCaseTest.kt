package com.mtkresearch.breezeapp.domain.usecase.settings

import com.mtkresearch.breezeapp.domain.model.settings.AppSettings
import com.mtkresearch.breezeapp.domain.model.settings.FontSize
import com.mtkresearch.breezeapp.domain.model.settings.StorageLocation
import com.mtkresearch.breezeapp.domain.model.settings.ThemeMode
import com.mtkresearch.breezeapp.domain.repository.AppSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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
 * LoadAppSettingsUseCase 單元測試
 *
 * 測試範圍：
 * - 正常設定載入流程
 * - Flow 資料流測試
 * - Repository 異常處理
 * - 預設值驗證
 * - 資料完整性檢查
 */
class LoadAppSettingsUseCaseTest {

    @Mock
    private lateinit var mockRepository: AppSettingsRepository

    private lateinit var loadUseCase: LoadAppSettingsUseCase

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        loadUseCase = LoadAppSettingsUseCase(mockRepository)
    }

    @Test
    fun `invoke should return Flow from repository`() = runTest {
        // Given
        val expectedSettings = createTestAppSettings()
        whenever(mockRepository.getAppSettings()).thenReturn(flowOf(expectedSettings))

        // When
        val resultFlow = loadUseCase()
        val result = resultFlow.first()

        // Then
        assertEquals(expectedSettings, result, "Should return settings from repository")
        verify(mockRepository).getAppSettings()
    }

    @Test
    fun `invoke should return default settings when repository returns default`() = runTest {
        // Given
        val defaultSettings = AppSettings()
        whenever(mockRepository.getAppSettings()).thenReturn(flowOf(defaultSettings))

        // When
        val resultFlow = loadUseCase()
        val result = resultFlow.first()

        // Then
        assertEquals(defaultSettings, result, "Should return default settings")
        assertEquals(ThemeMode.SYSTEM, result.themeMode, "Should have system theme mode by default")
        assertEquals(FontSize.MEDIUM, result.fontSize, "Should have medium font size by default")
        assertEquals("#F99A1B", result.primaryColor, "Should have default primary color")
        assertEquals("en-US", result.language, "Should have English as default language")
        assertTrue(result.enableNotifications, "Should have notifications enabled by default")
        assertTrue(result.enableAnimations, "Should have animations enabled by default")
        assertEquals(StorageLocation.INTERNAL, result.storageLocation, "Should use internal storage by default")
        assertFalse(result.autoBackup, "Should have auto backup disabled by default")
        verify(mockRepository).getAppSettings()
    }

    @Test
    fun `invoke should handle repository exception gracefully`() = runTest {
        // Given
        val exception = RuntimeException("Repository error")
        whenever(mockRepository.getAppSettings()).thenReturn(flow { throw exception })

        // When & Then
        assertThrows(RuntimeException::class.java) {
            runBlocking { loadUseCase().first() }
        }
        verify(mockRepository).getAppSettings()
    }

    @Test
    fun `invoke should handle multiple emissions from repository`() = runTest {
        // Given
        val initialSettings = AppSettings()
        val updatedSettings = createTestAppSettings()
        whenever(mockRepository.getAppSettings()).thenReturn(flow {
            emit(initialSettings)
            emit(updatedSettings)
        })

        // When
        val resultFlow = loadUseCase()
        val results = mutableListOf<AppSettings>()
        resultFlow.collect { results.add(it) }

        // Then
        assertEquals(2, results.size, "Should receive both emissions")
        assertEquals(initialSettings, results[0], "First emission should be initial settings")
        assertEquals(updatedSettings, results[1], "Second emission should be updated settings")
        verify(mockRepository).getAppSettings()
    }

    @Test
    fun `invoke should validate all theme modes`() = runTest {
        // Test all theme modes
        val themeModes = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)
        
        for (themeMode in themeModes) {
            // Given
            val settings = AppSettings(themeMode = themeMode)
            whenever(mockRepository.getAppSettings()).thenReturn(flowOf(settings))

            // When
            val result = loadUseCase().first()

            // Then
            assertEquals(themeMode, result.themeMode, "Should preserve theme mode: $themeMode")
        }
    }

    @Test
    fun `invoke should validate all font sizes`() = runTest {
        // Test all font sizes
        val fontSizes = listOf(FontSize.SMALL, FontSize.MEDIUM, FontSize.LARGE)
        
        for (fontSize in fontSizes) {
            // Given
            val settings = AppSettings(fontSize = fontSize)
            whenever(mockRepository.getAppSettings()).thenReturn(flowOf(settings))

            // When
            val result = loadUseCase().first()

            // Then
            assertEquals(fontSize, result.fontSize, "Should preserve font size: $fontSize")
        }
    }

    @Test
    fun `invoke should validate all storage locations`() = runTest {
        // Test all storage locations
        val storageLocations = listOf(StorageLocation.INTERNAL, StorageLocation.EXTERNAL)
        
        for (storageLocation in storageLocations) {
            // Given
            val settings = AppSettings(storageLocation = storageLocation)
            whenever(mockRepository.getAppSettings()).thenReturn(flowOf(settings))

            // When
            val result = loadUseCase().first()

            // Then
            assertEquals(storageLocation, result.storageLocation, "Should preserve storage location: $storageLocation")
        }
    }

    @Test
    fun `invoke should create new Flow on each call`() = runTest {
        // Given
        val settings = createTestAppSettings()
        whenever(mockRepository.getAppSettings()).thenReturn(flowOf(settings))

        // When
        loadUseCase().first() // First call
        loadUseCase().first() // Second call

        // Then
        verify(mockRepository, times(2)).getAppSettings()
    }

    @Test
    fun `invoke should handle custom primary colors`() = runTest {
        // Given
        val customColors = listOf("#FF5722", "#2196F3", "#4CAF50", "#FFC107")
        
        for (color in customColors) {
            // Given
            val settings = AppSettings(primaryColor = color)
            whenever(mockRepository.getAppSettings()).thenReturn(flowOf(settings))

            // When
            val result = loadUseCase().first()

            // Then
            assertEquals(color, result.primaryColor, "Should preserve custom color: $color")
        }
    }

    @Test
    fun `invoke should handle different languages`() = runTest {
        // Given
        val languages = listOf("zh-TW", "zh-CN", "ja-JP", "ko-KR")
        
        for (language in languages) {
            // Given
            val settings = AppSettings(language = language)
            whenever(mockRepository.getAppSettings()).thenReturn(flowOf(settings))

            // When
            val result = loadUseCase().first()

            // Then
            assertEquals(language, result.language, "Should preserve language: $language")
        }
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
} 