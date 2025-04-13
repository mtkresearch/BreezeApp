package com.mtkresearch.breeze_app.service.llm;

import android.content.Context;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import com.mtkresearch.breeze_app.service.llm.backends.CPUBackend;
import com.mtkresearch.breeze_app.service.llm.backends.MTKBackend;
import com.mtkresearch.breeze_app.service.bridge.MTKNativeBridge;
import com.mtkresearch.breeze_app.utils.AppConstants;

/**
 * Factory for creating different LLM backend implementations.
 */
public interface LLMBackendFactory {
    /**
     * Create an MTK native backend
     * @return An MTK backend instance
     */
    LLMBackend createMTKBackend();
    
    /**
     * Create a CPU backend using LlamaModule
     * @param modelPath Path to the model file
     * @return A CPU backend instance
     */
    LLMBackend createCPUBackend(String modelPath);
    
    /**
     * Get all available backends
     * @return Array of backend instances
     */
    LLMBackend[] getAllBackends();
    
    /**
     * Default implementation of the factory
     */
    class Default implements LLMBackendFactory {
        private final Context context;
        private final ExecutorService executorService;
        
        public Default(Context context) {
            this.context = context;
            this.executorService = Executors.newSingleThreadExecutor();
        }
        
        @Override
        public LLMBackend createMTKBackend() {
            // Check if MTK backend is available
            if (!AppConstants.isMTKBackendAvailable()) {
                android.util.Log.w("LLMBackendFactory", "MTK backend is not available, returning null");
                return null;
            }
            
            String configPath = AppConstants.getMtkConfigPath(context);
            android.util.Log.d("LLMBackendFactory", "Using MTK config path: " + configPath);
            
            String modelPath = AppConstants.getModelPath(context);
            // Get MTKNativeBridge instance
            MTKNativeBridge mtkBridge = MTKNativeBridge.getInstance();
            // Pass context to MTKBackend so it can show the download dialog if needed
            return new MTKBackend(mtkBridge, modelPath, configPath);
        }
        
        @Override
        public LLMBackend createCPUBackend(String modelPath) {
            android.util.Log.d("LLMBackendFactory", "Creating CPU backend with model path: " + modelPath);
            return new CPUBackend(modelPath, executorService, context);
        }
        
        @Override
        public LLMBackend[] getAllBackends() {
            // Try to use MTK backend first, fall back to CPU if not available
            java.util.List<LLMBackend> backends = new java.util.ArrayList<>();
            
            // Try MTK backend first if available
            if (AppConstants.isMTKBackendAvailable()) {
                LLMBackend mtkBackend = createMTKBackend();
                if (mtkBackend != null) {
                    backends.add(mtkBackend);
                }
            }
            
            // Always include CPU backend as fallback
            backends.add(createCPUBackend(AppConstants.getModelPath(context)));
            
            return backends.toArray(new LLMBackend[0]);
        }
    }
} 