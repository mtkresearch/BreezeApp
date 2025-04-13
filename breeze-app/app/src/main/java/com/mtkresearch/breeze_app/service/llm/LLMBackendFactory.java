package com.mtkresearch.breeze_app.service.llm;

import android.content.Context;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import com.mtkresearch.breeze_app.service.llm.backends.CPUBackend;
import com.mtkresearch.breeze_app.service.llm.backends.MTKBackend;
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
            String configPath = AppConstants.MTK_CONFIG_PATH;
            return new MTKBackend(configPath, executorService);
        }
        
        @Override
        public LLMBackend createCPUBackend(String modelPath) {
            return new CPUBackend(modelPath, executorService);
        }
        
        @Override
        public LLMBackend[] getAllBackends() {
            // Create all backends but don't initialize them
            return new LLMBackend[] {
                createMTKBackend(),
                createCPUBackend(AppConstants.getModelPath(context))
            };
        }
    }
} 