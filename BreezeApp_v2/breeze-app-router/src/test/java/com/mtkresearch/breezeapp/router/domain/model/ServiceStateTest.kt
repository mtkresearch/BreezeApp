package com.mtkresearch.breezeapp.router.domain.model

import com.mtkresearch.breezeapp.router.R
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for ServiceState domain model.
 * 
 * Tests state behavior, display text generation, notification properties,
 * and state transitions to ensure correct domain logic.
 */
class ServiceStateTest {

    @Test
    fun `Ready state should have correct properties`() {
        // Given
        val state = ServiceState.Ready
        
        // Then
        assertEquals("AI Router Ready", state.getDisplayText())
        assertEquals(R.drawable.ic_home, state.getIcon())
        assertTrue("Ready state should be ongoing", state.isOngoing())
        assertFalse("Ready state should not show progress", state.showProgress())
        assertFalse("Ready state should not be active", state.isActive())
        assertEquals(NotificationPriority.LOW, state.getNotificationPriority())
    }

    @Test
    fun `Processing state should have correct properties`() {
        // Given
        val singleRequest = ServiceState.Processing(1)
        val multipleRequests = ServiceState.Processing(3)
        
        // Then - Single request
        assertEquals("Processing 1 AI request", singleRequest.getDisplayText())
        assertEquals(R.drawable.ic_refresh, singleRequest.getIcon())
        assertTrue("Processing state should be ongoing", singleRequest.isOngoing())
        assertTrue("Processing state should show progress", singleRequest.showProgress())
        assertTrue("Processing state should be active", singleRequest.isActive())
        assertTrue("Processing should be indeterminate", singleRequest.isIndeterminate())
        assertEquals(NotificationPriority.DEFAULT, singleRequest.getNotificationPriority())
        
        // Then - Multiple requests
        assertEquals("Processing 3 AI requests", multipleRequests.getDisplayText())
        assertTrue("Multiple requests should use plural form", 
            multipleRequests.getDisplayText().contains("requests"))
    }

    @Test
    fun `Downloading state should have correct properties`() {
        // Given
        val downloadingWithoutSize = ServiceState.Downloading("llama-3.2-1b", 45)
        val downloadingWithSize = ServiceState.Downloading("llama-3.2-1b", 75, "2.1 GB")
        
        // Then - Without size
        assertEquals("Downloading llama-3.2-1b: 45%", downloadingWithoutSize.getDisplayText())
        assertEquals(R.drawable.ic_cloud_off, downloadingWithoutSize.getIcon())
        assertTrue("Downloading state should be ongoing", downloadingWithoutSize.isOngoing())
        assertTrue("Downloading state should show progress", downloadingWithoutSize.showProgress())
        assertTrue("Downloading state should be active", downloadingWithoutSize.isActive())
        assertFalse("Downloading should not be indeterminate", downloadingWithoutSize.isIndeterminate())
        assertEquals(45, downloadingWithoutSize.getProgressValue())
        assertEquals(100, downloadingWithoutSize.getProgressMax())
        assertEquals(NotificationPriority.DEFAULT, downloadingWithoutSize.getNotificationPriority())
        
        // Then - With size
        assertEquals("Downloading llama-3.2-1b: 75% (2.1 GB)", downloadingWithSize.getDisplayText())
        assertTrue("Size info should be included", 
            downloadingWithSize.getDisplayText().contains("(2.1 GB)"))
    }

    @Test
    fun `Error state should have correct properties`() {
        // Given
        val recoverableError = ServiceState.Error("Network timeout", true)
        val nonRecoverableError = ServiceState.Error("Model corrupted", false)
        
        // Then - Recoverable error
        assertEquals("AI Router Error: Network timeout", recoverableError.getDisplayText())
        assertEquals(R.drawable.ic_error, recoverableError.getIcon())
        assertFalse("Error state should not be ongoing", recoverableError.isOngoing())
        assertFalse("Error state should not show progress", recoverableError.showProgress())
        assertFalse("Error state should not be active", recoverableError.isActive())
        assertTrue("Error should be recoverable", recoverableError.isRecoverable)
        assertEquals(NotificationPriority.HIGH, recoverableError.getNotificationPriority())
        
        // Then - Non-recoverable error
        assertFalse("Error should not be recoverable", nonRecoverableError.isRecoverable)
    }

    @Test
    fun `isActive should correctly identify active states`() {
        // Given
        val activeStates = listOf(
            ServiceState.Processing(1),
            ServiceState.Downloading("model", 50)
        )
        val inactiveStates = listOf(
            ServiceState.Ready,
            ServiceState.Error("Test error")
        )
        
        // Then
        activeStates.forEach { state ->
            assertTrue("$state should be active", state.isActive())
        }
        
        inactiveStates.forEach { state ->
            assertFalse("$state should not be active", state.isActive())
        }
    }

    @Test
    fun `notification priorities should be correctly assigned`() {
        // Given & Then
        assertEquals(NotificationPriority.LOW, ServiceState.Ready.getNotificationPriority())
        assertEquals(NotificationPriority.DEFAULT, ServiceState.Processing(1).getNotificationPriority())
        assertEquals(NotificationPriority.DEFAULT, ServiceState.Downloading("model", 50).getNotificationPriority())
        assertEquals(NotificationPriority.HIGH, ServiceState.Error("error").getNotificationPriority())
    }

    @Test
    fun `progress values should be correct for different states`() {
        // Given
        val ready = ServiceState.Ready
        val processing = ServiceState.Processing(2)
        val downloading = ServiceState.Downloading("model", 75)
        val error = ServiceState.Error("error")
        
        // Then - Progress values
        assertEquals(0, ready.getProgressValue())
        assertEquals(0, processing.getProgressValue())
        assertEquals(75, downloading.getProgressValue())
        assertEquals(0, error.getProgressValue())
        
        // Then - Progress max
        assertEquals(100, ready.getProgressMax())
        assertEquals(100, processing.getProgressMax())
        assertEquals(100, downloading.getProgressMax())
        assertEquals(100, error.getProgressMax())
        
        // Then - Indeterminate
        assertFalse(ready.isIndeterminate())
        assertTrue(processing.isIndeterminate())
        assertFalse(downloading.isIndeterminate())
        assertFalse(error.isIndeterminate())
    }

    @Test
    fun `Processing state should handle edge cases`() {
        // Given
        val zeroRequests = ServiceState.Processing(0)
        val manyRequests = ServiceState.Processing(100)
        
        // Then
        assertEquals("Processing 0 AI requests", zeroRequests.getDisplayText())
        assertEquals("Processing 100 AI requests", manyRequests.getDisplayText())
    }

    @Test
    fun `Downloading state should handle edge cases`() {
        // Given
        val zeroProgress = ServiceState.Downloading("model", 0)
        val fullProgress = ServiceState.Downloading("model", 100)
        val emptyModelName = ServiceState.Downloading("", 50)
        
        // Then
        assertEquals("Downloading model: 0%", zeroProgress.getDisplayText())
        assertEquals("Downloading model: 100%", fullProgress.getDisplayText())
        assertEquals("Downloading : 50%", emptyModelName.getDisplayText())
    }

    @Test
    fun `Error state should handle empty messages`() {
        // Given
        val emptyError = ServiceState.Error("")
        val nullishError = ServiceState.Error("   ")
        
        // Then
        assertEquals("AI Router Error: ", emptyError.getDisplayText())
        assertEquals("AI Router Error:    ", nullishError.getDisplayText())
    }
}