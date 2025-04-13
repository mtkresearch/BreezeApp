package com.mtkresearch.breeze_app.service.bridge;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.util.Log;

import com.mtkresearch.breeze_app.service.LLMEngineService;
import com.mtkresearch.breeze_app.utils.AppConstants;
import com.mtkresearch.gai_android.service.LLMEngineService.TokenCallback;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;

import java.lang.reflect.Field;

@RunWith(MockitoJUnitRunner.class)
@PrepareForTest({Log.class, System.class, MTKNativeBridge.class, AppConstants.class})
public class MTKNativeBridgeTest {

    @Mock
    private com.mtkresearch.gai_android.service.LLMEngineService mockBridgeInstance;

    @Mock
    private LLMEngineService mockService;

    @Mock
    private MTKNativeBridge.TokenCallback mockCallback;

    private MTKNativeBridge bridge;

    @Before
    public void setUp() throws Exception {
        // Mock Android Log calls
        PowerMockito.mockStatic(Log.class);
        
        // Mock System.loadLibrary to avoid actual loading of native libraries
        PowerMockito.mockStatic(System.class);
        
        // Reset static fields in MTKNativeBridge for clean testing
        resetSingleton(MTKNativeBridge.class, "instance");
        
        // Set bridge instance for testing
        Field bridgeInstanceField = MTKNativeBridge.class.getDeclaredField("bridgeInstance");
        bridgeInstanceField.setAccessible(true);
        bridgeInstanceField.set(null, mockBridgeInstance);
        
        // Create a real instance of MTKNativeBridge (now with mocked dependencies)
        bridge = MTKNativeBridge.getInstance();
    }

    /**
     * Helper method to reset singleton instances for testing
     */
    private void resetSingleton(Class<?> clazz, String fieldName) {
        try {
            Field instance = clazz.getDeclaredField(fieldName);
            instance.setAccessible(true);
            instance.set(null, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testInitialize_Success() {
        // Given
        when(mockBridgeInstance.nativeResetLlm()).thenReturn(true);
        
        // When
        boolean result = MTKNativeBridge.initialize();
        
        // Then
        assertTrue("Initialization should succeed", result);
        assertTrue("MTK_BACKEND_AVAILABLE should be true", AppConstants.MTK_BACKEND_AVAILABLE);
        verify(mockBridgeInstance).nativeResetLlm();
    }

    @Test
    public void testInitialize_FailedNativeCall() {
        // Given
        when(mockBridgeInstance.nativeResetLlm()).thenThrow(new UnsatisfiedLinkError("Test error"));
        
        // When
        boolean result = MTKNativeBridge.initialize();
        
        // Then
        assertFalse("Initialization should fail", result);
        assertFalse("MTK_BACKEND_AVAILABLE should be false", AppConstants.MTK_BACKEND_AVAILABLE);
        verify(mockBridgeInstance).nativeResetLlm();
    }

    @Test
    public void testRegisterService_Success() {
        // When
        boolean result = bridge.registerService(mockService);
        
        // Then
        assertTrue("Registration should succeed", result);
    }

    @Test
    public void testInitLlm_Success() {
        // Given
        when(mockBridgeInstance.nativeInitLlm(anyString(), anyBoolean())).thenReturn(true);
        
        // When
        boolean result = bridge.initLlm("test_path", true);
        
        // Then
        assertTrue("Init LLM should succeed", result);
        verify(mockBridgeInstance).nativeInitLlm("test_path", true);
    }

    @Test
    public void testInitLlm_Failure() {
        // Given
        when(mockBridgeInstance.nativeInitLlm(anyString(), anyBoolean())).thenReturn(false);
        
        // When
        boolean result = bridge.initLlm("test_path", true);
        
        // Then
        assertFalse("Init LLM should fail", result);
        verify(mockBridgeInstance).nativeInitLlm("test_path", true);
    }

    @Test
    public void testStreamingInference_Success() {
        // Given
        String expectedResponse = "Test response";
        when(mockBridgeInstance.nativeStreamingInference(anyString(), anyInt(), anyBoolean(), any()))
                .thenReturn(expectedResponse);
        
        // When
        String result = bridge.streamingInference("input", 100, false, mockCallback);
        
        // Then
        assertEquals("Response should match expected", expectedResponse, result);
        verify(mockBridgeInstance).nativeStreamingInference(eq("input"), eq(100), eq(false), any(TokenCallback.class));
    }

    @Test
    public void testResetLlm_Success() {
        // Given
        when(mockBridgeInstance.nativeResetLlm()).thenReturn(true);
        
        // When
        boolean result = bridge.resetLlm();
        
        // Then
        assertTrue("Reset LLM should succeed", result);
        verify(mockBridgeInstance).nativeResetLlm();
    }

    @Test
    public void testSwapModel_Success() {
        // Given
        when(mockBridgeInstance.nativeSwapModel(anyInt())).thenReturn(true);
        
        // When
        boolean result = bridge.swapModel(128);
        
        // Then
        assertTrue("Swap model should succeed", result);
        verify(mockBridgeInstance).nativeSwapModel(128);
    }
    
    @Test
    public void testError_BridgeInstanceNull() throws Exception {
        // Given
        Field bridgeInstanceField = MTKNativeBridge.class.getDeclaredField("bridgeInstance");
        bridgeInstanceField.setAccessible(true);
        bridgeInstanceField.set(null, null);
        
        // Create a new bridge with null bridgeInstance
        bridge = MTKNativeBridge.getInstance();
        
        // When/Then
        assertFalse("InitLlm should fail with null bridge", bridge.initLlm("test", true));
        assertEquals("Inference should return error with null bridge", 
                AppConstants.LLM_ERROR_RESPONSE, bridge.inference("test", 10, false));
        assertEquals("StreamingInference should return error with null bridge",
                AppConstants.LLM_ERROR_RESPONSE, bridge.streamingInference("test", 10, false, mockCallback));
        assertFalse("ResetLlm should fail with null bridge", bridge.resetLlm());
        assertFalse("SwapModel should fail with null bridge", bridge.swapModel(128));
    }
} 