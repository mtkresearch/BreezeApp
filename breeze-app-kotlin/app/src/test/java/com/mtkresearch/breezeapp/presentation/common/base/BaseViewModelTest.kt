package com.mtkresearch.breezeapp.presentation.common.base

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class BaseViewModelTest {

    // 測試用的ViewModel實現
    private class TestViewModel : BaseViewModel() {
        fun testSetLoading(isLoading: Boolean) = setLoading(isLoading)
        fun testSetError(message: String, throwable: Throwable? = null) = setError(message, throwable)
        fun testSetSuccess(message: String = "") = setSuccess(message)
        fun testSetIdle() = setIdle()
        
        fun testLaunchSafely(
            showLoading: Boolean = true,
            onError: ((Throwable) -> Unit)? = null,
            block: suspend () -> Unit
        ) = launchSafely(showLoading, onError, block)
        
        fun testLaunchWithResult(
            showLoading: Boolean = true,
            onSuccess: (String) -> Unit,
            onError: ((Throwable) -> Unit)? = null,
            block: suspend () -> String
        ) = launchWithResult(showLoading, onSuccess, onError, block)
        
        fun testHandleError(throwable: Throwable) = handleError(throwable)
        fun testValidateInput(condition: Boolean, errorMessage: String) = validateInput(condition, errorMessage)
    }

    private lateinit var viewModel: TestViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = TestViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setLoading should update loading state correctly`() = runTest {
        // 初始狀態應該是 false
        assertFalse(viewModel.isLoading.first())
        assertEquals(UiState.IDLE, viewModel.uiState.first().state)

        // 設置 loading = true
        viewModel.testSetLoading(true)
        advanceUntilIdle()

        assertTrue(viewModel.isLoading.first())
        assertEquals(UiState.LOADING, viewModel.uiState.first().state)
        assertTrue(viewModel.uiState.first().isLoading)

        // 設置 loading = false
        viewModel.testSetLoading(false)
        advanceUntilIdle()

        assertFalse(viewModel.isLoading.first())
        assertEquals(UiState.IDLE, viewModel.uiState.first().state)
        assertFalse(viewModel.uiState.first().isLoading)
    }

    @Test
    fun `setError should update error state correctly`() = runTest {
        val errorMessage = "Test error message"
        val exception = RuntimeException("Test exception")

        viewModel.testSetError(errorMessage, exception)
        advanceUntilIdle()

        assertEquals(errorMessage, viewModel.error.first())
        assertFalse(viewModel.isLoading.first())
        
        val uiState = viewModel.uiState.first()
        assertEquals(UiState.ERROR, uiState.state)
        assertEquals(errorMessage, uiState.message)
        assertFalse(uiState.isLoading)
        assertEquals(exception, uiState.error)
    }

    @Test
    fun `setSuccess should update success state correctly`() = runTest {
        val successMessage = "Operation completed successfully"

        viewModel.testSetSuccess(successMessage)
        advanceUntilIdle()

        assertEquals(successMessage, viewModel.successMessage.first())
        assertFalse(viewModel.isLoading.first())
        assertNull(viewModel.error.first())
        
        val uiState = viewModel.uiState.first()
        assertEquals(UiState.SUCCESS, uiState.state)
        assertEquals(successMessage, uiState.message)
        assertFalse(uiState.isLoading)
        assertNull(uiState.error)
    }

    @Test
    fun `setIdle should reset all states`() = runTest {
        // 先設置一些狀態
        viewModel.testSetError("Error")
        viewModel.testSetLoading(true)
        advanceUntilIdle()

        // 重置為閒置狀態
        viewModel.testSetIdle()
        advanceUntilIdle()

        assertFalse(viewModel.isLoading.first())
        assertNull(viewModel.error.first())
        assertNull(viewModel.successMessage.first())
        
        val uiState = viewModel.uiState.first()
        assertEquals(UiState.IDLE, uiState.state)
        assertEquals("", uiState.message)
        assertFalse(uiState.isLoading)
        assertNull(uiState.error)
    }

    @Test
    fun `clearError should clear error state`() = runTest {
        // 設置錯誤狀態
        viewModel.testSetError("Test error")
        advanceUntilIdle()
        
        assertNotNull(viewModel.error.first())
        assertEquals(UiState.ERROR, viewModel.uiState.first().state)

        // 清除錯誤
        viewModel.clearError()
        advanceUntilIdle()

        assertNull(viewModel.error.first())
        assertEquals(UiState.IDLE, viewModel.uiState.first().state)
    }

    @Test
    fun `clearSuccessMessage should clear success state`() = runTest {
        // 設置成功狀態
        viewModel.testSetSuccess("Success message")
        advanceUntilIdle()
        
        assertNotNull(viewModel.successMessage.first())
        assertEquals(UiState.SUCCESS, viewModel.uiState.first().state)

        // 清除成功訊息
        viewModel.clearSuccessMessage()
        advanceUntilIdle()

        assertNull(viewModel.successMessage.first())
        assertEquals(UiState.IDLE, viewModel.uiState.first().state)
    }

    @Test
    fun `launchSafely should handle success correctly`() = runTest {
        var executionCompleted = false

        viewModel.testLaunchSafely {
            executionCompleted = true
        }
        advanceUntilIdle()

        assertTrue(executionCompleted)
        assertFalse(viewModel.isLoading.first())
        assertNull(viewModel.error.first())
    }

    @Test
    fun `launchSafely should handle exceptions correctly`() = runTest {
        val exception = RuntimeException("Test exception")

        viewModel.testLaunchSafely {
            throw exception
        }
        advanceUntilIdle()

        assertFalse(viewModel.isLoading.first())
        assertNotNull(viewModel.error.first())
        assertEquals(UiState.ERROR, viewModel.uiState.first().state)
    }

    @Test
    fun `launchWithResult should handle success correctly`() = runTest {
        val expectedResult = "Test result"
        var actualResult: String? = null

        viewModel.testLaunchWithResult(
            onSuccess = { result -> actualResult = result }
        ) {
            expectedResult
        }
        advanceUntilIdle()

        assertEquals(expectedResult, actualResult)
        assertFalse(viewModel.isLoading.first())
        assertNull(viewModel.error.first())
    }

    @Test
    fun `handleError should format different exception types correctly`() = runTest {
        // 測試 IllegalArgumentException
        viewModel.testHandleError(IllegalArgumentException("Invalid argument"))
        advanceUntilIdle()
        assertTrue(viewModel.error.first()!!.startsWith("參數錯誤"))

        // 測試 IOException
        viewModel.testHandleError(IOException("Network error"))
        advanceUntilIdle()
        assertTrue(viewModel.error.first()!!.startsWith("網路錯誤"))

        // 測試未知錯誤
        viewModel.testHandleError(RuntimeException("Unknown error"))
        advanceUntilIdle()
        assertEquals("Unknown error", viewModel.error.first())
    }

    @Test
    fun `validateInput should return correct results`() = runTest {
        // 有效輸入
        assertTrue(viewModel.testValidateInput(true, "Error message"))
        assertNull(viewModel.error.first())

        // 無效輸入
        assertFalse(viewModel.testValidateInput(false, "Validation failed"))
        assertEquals("Validation failed", viewModel.error.first())
    }

    @Test
    fun `retry should clear error state`() = runTest {
        // 設置錯誤狀態
        viewModel.testSetError("Test error")
        advanceUntilIdle()
        assertNotNull(viewModel.error.first())

        // 執行重試
        viewModel.retry()
        advanceUntilIdle()

        assertNull(viewModel.error.first())
    }
} 