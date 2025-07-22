package com.mtkresearch.breezeapp_kotlin.domain.usecase.settings

import com.mtkresearch.breezeapp_kotlin.domain.model.settings.FontSize
import com.mtkresearch.breezeapp_kotlin.domain.repository.AppSettingsRepository
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
 * UpdateFontSizeUseCase 單元測試
 *
 * 測試範圍：
 * - 字體大小更新功能
 * - Repository 方法調用驗證
 * - 異常處理測試
 * - 所有字體大小驗證
 * - 字體縮放比例驗證
 */
class UpdateFontSizeUseCaseTest {

    @Mock
    private lateinit var mockRepository: AppSettingsRepository

    private lateinit var updateUseCase: UpdateFontSizeUseCase

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        updateUseCase = UpdateFontSizeUseCase(mockRepository)
    }

    @Test
    fun `invoke should call repository updateFontSize with SMALL size`() = runTest {
        // Given
        val fontSize = FontSize.SMALL

        // When
        updateUseCase(fontSize)

        // Then
        verify(mockRepository).updateFontSize(fontSize)
    }

    @Test
    fun `invoke should call repository updateFontSize with MEDIUM size`() = runTest {
        // Given
        val fontSize = FontSize.MEDIUM

        // When
        updateUseCase(fontSize)

        // Then
        verify(mockRepository).updateFontSize(fontSize)
    }

    @Test
    fun `invoke should call repository updateFontSize with LARGE size`() = runTest {
        // Given
        val fontSize = FontSize.LARGE

        // When
        updateUseCase(fontSize)

        // Then
        verify(mockRepository).updateFontSize(fontSize)
    }

    @Test
    fun `invoke should handle all font sizes correctly`() = runTest {
        // Given
        val allFontSizes = listOf(FontSize.SMALL, FontSize.MEDIUM, FontSize.LARGE)

        // When & Then
        for (fontSize in allFontSizes) {
            updateUseCase(fontSize)
            verify(mockRepository).updateFontSize(fontSize)
        }

        verify(mockRepository, times(3)).updateFontSize(any())
    }

    @Test
    fun `invoke should verify font size scales are correct`() = runTest {
        // Given & When & Then
        assertEquals(0.8f, FontSize.SMALL.scale, "Small font should have scale 0.8")
        assertEquals(1.0f, FontSize.MEDIUM.scale, "Medium font should have scale 1.0")
        assertEquals(1.2f, FontSize.LARGE.scale, "Large font should have scale 1.2")

        // Test that UseCase works with all scales
        for (fontSize in FontSize.entries) {
            updateUseCase(fontSize)
            verify(mockRepository).updateFontSize(fontSize)
        }
    }

    @Test
    fun `invoke should propagate repository exception`() = runTest {
        // Given
        val fontSize = FontSize.MEDIUM
        val exception = RuntimeException("Repository update failed")
        whenever(mockRepository.updateFontSize(any())).thenThrow(exception)

        // When & Then
        assertThrows(RuntimeException::class.java) {
            runBlocking { updateUseCase(fontSize) }
        }
        verify(mockRepository).updateFontSize(fontSize)
    }

    @Test
    fun `invoke should handle IllegalArgumentException from repository`() = runTest {
        // Given
        val fontSize = FontSize.LARGE
        val exception = IllegalArgumentException("Invalid font size")
        whenever(mockRepository.updateFontSize(any())).thenThrow(exception)

        // When & Then
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { updateUseCase(fontSize) }
        }
        verify(mockRepository).updateFontSize(fontSize)
    }

    @Test
    fun `invoke should complete successfully when repository succeeds`() = runTest {
        // Given
        val fontSize = FontSize.MEDIUM
        // Repository method is suspend and returns Unit, so no need to stub

        // When
        updateUseCase(fontSize)

        // Then
        verify(mockRepository).updateFontSize(fontSize)
        // If we reach here without exception, the test passes
    }

    @Test
    fun `invoke should call repository exactly once per invocation`() = runTest {
        // Given
        val fontSize = FontSize.LARGE

        // When
        updateUseCase(fontSize)
        updateUseCase(fontSize)
        updateUseCase(fontSize)

        // Then
        verify(mockRepository, times(3)).updateFontSize(fontSize)
    }

    @Test
    fun `invoke should pass exact font size parameter to repository`() = runTest {
        // Given
        val smallFont = FontSize.SMALL
        val mediumFont = FontSize.MEDIUM
        val largeFont = FontSize.LARGE

        // When
        updateUseCase(smallFont)
        updateUseCase(mediumFont)
        updateUseCase(largeFont)

        // Then
        verify(mockRepository).updateFontSize(smallFont)
        verify(mockRepository).updateFontSize(mediumFont)
        verify(mockRepository).updateFontSize(largeFont)
        verify(mockRepository, times(3)).updateFontSize(any())
    }

    @Test
    fun `invoke should handle concurrent calls correctly`() = runTest {
        // Given
        val fontSize1 = FontSize.SMALL
        val fontSize2 = FontSize.LARGE

        // When
        val job1 = async { updateUseCase(fontSize1) }
        val job2 = async { updateUseCase(fontSize2) }
        job1.await()
        job2.await()

        // Then
        verify(mockRepository, atLeast(2)).updateFontSize(any())
    }

    @Test
    fun `invoke should maintain parameter integrity`() = runTest {
        // Given
        val originalFontSize = FontSize.LARGE

        // When
        updateUseCase(originalFontSize)

        // Then
        val argumentCaptor = argumentCaptor<FontSize>()
        verify(mockRepository).updateFontSize(argumentCaptor.capture())
        assertEquals(originalFontSize, argumentCaptor.firstValue, "Font size parameter should not be modified")
    }

    @Test
    fun `invoke should work with FontSize fromScale method`() = runTest {
        // Given - Test FontSize.fromScale integration
        val testScales = listOf(0.7f, 0.8f, 0.9f, 1.0f, 1.1f, 1.2f, 1.3f)
        
        // Reset mock before each verification
        reset(mockRepository)
        
        for (scale in testScales) {
            // When
            val fontSize = FontSize.fromScale(scale)
            updateUseCase(fontSize)
        }

        // Then - Verify total calls
        verify(mockRepository, times(testScales.size)).updateFontSize(any())
    }

    @Test
    fun `FontSize fromScale should return correct values`() {
        // Test the FontSize.fromScale method used by ViewModel
        assertEquals(FontSize.SMALL, FontSize.fromScale(0.8f), "Scale 0.8 should map to SMALL")
        assertEquals(FontSize.MEDIUM, FontSize.fromScale(1.0f), "Scale 1.0 should map to MEDIUM") 
        assertEquals(FontSize.LARGE, FontSize.fromScale(1.2f), "Scale 1.2 should map to LARGE")
        
        // Test edge cases - based on actual implementation using minByOrNull with absolute difference
        assertEquals(FontSize.SMALL, FontSize.fromScale(0.5f), "Low scale should map to SMALL")
        assertEquals(FontSize.LARGE, FontSize.fromScale(1.5f), "High scale should map to LARGE")
        // 0.9 is closer to 1.0 (MEDIUM) than 0.8 (SMALL): |0.9-1.0| = 0.1 vs |0.9-0.8| = 0.1, but SMALL comes first
        assertEquals(FontSize.SMALL, FontSize.fromScale(0.9f), "Scale 0.9 should map to SMALL (closest by minByOrNull)")
        // 1.1 is closer to 1.0 (MEDIUM) than 1.2 (LARGE): |1.1-1.0| = 0.1 vs |1.1-1.2| = 0.1, but MEDIUM comes first
        assertEquals(FontSize.MEDIUM, FontSize.fromScale(1.1f), "Scale 1.1 should map to MEDIUM (closest)")
    }
} 