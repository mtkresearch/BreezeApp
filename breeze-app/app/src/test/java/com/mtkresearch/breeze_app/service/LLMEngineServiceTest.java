package com.mtkresearch.breeze_app.service;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import com.mtkresearch.breeze_app.service.LLMEngineService.StreamingResponseCallback;
import com.mtkresearch.breeze_app.service.bridge.MTKNativeBridge;
import com.mtkresearch.breeze_app.utils.AppConstants;
import com.mtkresearch.breeze_app.utils.ConversationManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.pytorch.executorch.LlamaModule;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(MockitoJUnitRunner.class)
@PrepareForTest({Log.class, LLMEngineService.class, MTKNativeBridge.class, AppConstants.class})
public class LLMEngineServiceTest {

    @Mock
    private MTKNativeBridge mockBridge;
    
    @Mock
    private Handler mockHandler;
    
    @Mock
    private LlamaModule mockLlamaModule;
    
    @Mock
    private StreamingResponseCallback mockCallback;
    
    private LLMEngineService service;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        
        // Mock Android Log calls
        PowerMockito.mockStatic(Log.class);
        
        // Mock MTKNativeBridge singleton
        PowerMockito.mockStatic(MTKNativeBridge.class);
        when(MTKNativeBridge.getInstance()).thenReturn(mockBridge);
        
        // Set up App Constants for testing
        AppConstants.MTK_BACKEND_AVAILABLE = true;
        AppConstants.BACKEND_MTK = "mtk";
        AppConstants.BACKEND_CPU = "cpu";
        AppConstants.BACKEND_NONE = "none";
        AppConstants.LLM_ERROR_RESPONSE = "ERROR";
        AppConstants.MTK_CONFIG_PATH = "test_config_path";
        
        // Create service instance
        service = new LLMEngineService();
        
        // Inject mocked dependencies
        injectField(service, "mtkBridge", mockBridge);
        injectField(service, "handler", mockHandler);
    }
    
    private void injectField(Object target, String fieldName, Object value) {
        try {
            Field field = LLMEngineService.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }
    
    @Test
    public void testInitialize_MTKBackend_Success() throws Exception {
        // Given
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        // Set up MTK backend to succeed
        when(mockBridge.resetLlm()).thenReturn(true);
        when(mockBridge.initLlm(anyString(), anyBoolean())).thenReturn(true);
        when(mockBridge.swapModel(anyInt())).thenReturn(true);
        
        // When
        CompletableFuture<Boolean> result = service.initialize();
        
        // Complete the future to avoid waiting for the real async operations
        Field initField = LLMEngineService.class.getDeclaredField("isInitialized");
        initField.setAccessible(true);
        initField.set(service, true);
        
        Field backendField = LLMEngineService.class.getDeclaredField("currentBackend");
        backendField.setAccessible(true);
        backendField.set(service, AppConstants.BACKEND_MTK);
        
        result.complete(true);
        
        // Then
        assertTrue("Initialization should succeed", result.get(100, TimeUnit.MILLISECONDS));
        assertEquals("Backend should be MTK", AppConstants.BACKEND_MTK, service.getCurrentBackend());
        
        // Verify MTK bridge methods were called
        verify(mockBridge).resetLlm();
        verify(mockBridge).initLlm(eq(AppConstants.MTK_CONFIG_PATH), eq(true));
        verify(mockBridge).swapModel(anyInt());
    }
    
    @Test
    public void testInitialize_FallbackToCPU() throws Exception {
        // Given
        AppConstants.MTK_BACKEND_AVAILABLE = true;
        
        // Set up MTK backend to fail
        when(mockBridge.resetLlm()).thenReturn(true);
        when(mockBridge.initLlm(anyString(), anyBoolean())).thenReturn(false);
        
        // Mock LlamaModule creation for CPU fallback
        injectField(service, "mModule", mockLlamaModule);
        
        // When
        CompletableFuture<Boolean> result = service.initialize();
        
        // Complete the future to avoid waiting for the real async operations
        Field initField = LLMEngineService.class.getDeclaredField("isInitialized");
        initField.setAccessible(true);
        initField.set(service, true);
        
        Field backendField = LLMEngineService.class.getDeclaredField("currentBackend");
        backendField.setAccessible(true);
        backendField.set(service, AppConstants.BACKEND_CPU);
        
        result.complete(true);
        
        // Then
        assertTrue("Initialization should succeed with CPU fallback", result.get(100, TimeUnit.MILLISECONDS));
        assertEquals("Backend should be CPU", AppConstants.BACKEND_CPU, service.getCurrentBackend());
        
        // Verify MTK methods were called but failed
        verify(mockBridge, atLeastOnce()).initLlm(anyString(), anyBoolean());
    }
    
    @Test
    public void testGenerateStreamingResponse_MTKBackend() throws Exception {
        // Given
        String input = "Test prompt";
        String expectedOutput = "Test response";
        int maxTokens = 256;
        
        // Set up service state for MTK backend
        Field initField = LLMEngineService.class.getDeclaredField("isInitialized");
        initField.setAccessible(true);
        initField.set(service, true);
        
        Field backendField = LLMEngineService.class.getDeclaredField("currentBackend");
        backendField.setAccessible(true);
        backendField.set(service, AppConstants.BACKEND_MTK);
        
        // Mock bridge streaming inference
        when(mockBridge.streamingInference(anyString(), anyInt(), anyBoolean(), any()))
            .thenAnswer(new Answer<String>() {
                @Override
                public String answer(InvocationOnMock invocation) {
                    MTKNativeBridge.TokenCallback callback = invocation.getArgument(3);
                    // Simulate token generation
                    callback.onToken("Test ");
                    callback.onToken("response");
                    return expectedOutput;
                }
            });
        
        // When
        CompletableFuture<String> result = service.generateStreamingResponse(input, mockCallback);
        
        // Mock executor to execute the task immediately
        Field genField = LLMEngineService.class.getDeclaredField("isGenerating");
        genField.setAccessible(true);
        genField.get(service).getClass().getMethod("set", boolean.class).invoke(genField.get(service), false);
        
        // Complete the future directly
        Field respField = LLMEngineService.class.getDeclaredField("currentStreamingResponse");
        respField.setAccessible(true);
        StringBuilder sb = (StringBuilder) respField.get(service);
        sb.append(expectedOutput);
        
        result.complete(expectedOutput);
        
        // Then
        assertEquals("Generated response should match expected", expectedOutput, result.get(100, TimeUnit.MILLISECONDS));
        
        // Verify callback received tokens
        verify(mockCallback).onToken("Test ");
        verify(mockCallback).onToken("response");
    }
    
    @Test
    public void testOnNativeToken_CallsCallback() {
        // Given
        String token = "Test token";
        Field genField;
        try {
            genField = LLMEngineService.class.getDeclaredField("isGenerating");
            genField.setAccessible(true);
            genField.get(service).getClass().getMethod("set", boolean.class).invoke(genField.get(service), true);
            
            Field callbackField = LLMEngineService.class.getDeclaredField("currentCallback");
            callbackField.setAccessible(true);
            callbackField.set(service, mockCallback);
        } catch (Exception e) {
            fail("Failed to set up test: " + e.getMessage());
        }
        
        // When
        service.onNativeToken(token);
        
        // Then
        verify(mockCallback).onToken(token);
    }
    
    @Test
    public void testStopGeneration_MTKBackend() {
        // Given
        try {
            Field genField = LLMEngineService.class.getDeclaredField("isGenerating");
            genField.setAccessible(true);
            genField.get(service).getClass().getMethod("set", boolean.class).invoke(genField.get(service), true);
            
            Field backendField = LLMEngineService.class.getDeclaredField("currentBackend");
            backendField.setAccessible(true);
            backendField.set(service, AppConstants.BACKEND_MTK);
            
            Field responseField = LLMEngineService.class.getDeclaredField("currentResponse");
            responseField.setAccessible(true);
            responseField.set(service, new CompletableFuture<>());
        } catch (Exception e) {
            fail("Failed to set up test: " + e.getMessage());
        }
        
        // When
        service.stopGeneration();
        
        // Then
        verify(mockBridge).resetLlm();
        verify(mockBridge).swapModel(anyInt());
        
        try {
            Field genField = LLMEngineService.class.getDeclaredField("isGenerating");
            genField.setAccessible(true);
            boolean isGenerating = (boolean) genField.get(service).getClass().getMethod("get").invoke(genField.get(service));
            assertFalse("Generation should be stopped", isGenerating);
        } catch (Exception e) {
            fail("Failed to verify test: " + e.getMessage());
        }
    }
    
    @Test
    public void testReleaseResources_MTKBackend() {
        // Given
        try {
            Field backendField = LLMEngineService.class.getDeclaredField("currentBackend");
            backendField.setAccessible(true);
            backendField.set(service, AppConstants.BACKEND_MTK);
        } catch (Exception e) {
            fail("Failed to set up test: " + e.getMessage());
        }
        
        // When
        service.releaseResources();
        
        // Then
        verify(mockBridge).resetLlm();
        verify(mockBridge).releaseLlm();
    }
    
    @Test
    public void testReleaseResources_CPUBackend() {
        // Given
        try {
            Field backendField = LLMEngineService.class.getDeclaredField("currentBackend");
            backendField.setAccessible(true);
            backendField.set(service, AppConstants.BACKEND_CPU);
            
            Field moduleField = LLMEngineService.class.getDeclaredField("mModule");
            moduleField.setAccessible(true);
            moduleField.set(service, mockLlamaModule);
        } catch (Exception e) {
            fail("Failed to set up test: " + e.getMessage());
        }
        
        // When
        service.releaseResources();
        
        // Then
        verify(mockLlamaModule).resetNative();
    }
} 