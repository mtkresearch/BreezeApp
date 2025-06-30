package com.mtkresearch.breezeapp.shared.contracts

import android.os.IBinder
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtkresearch.breezeapp.shared.contracts.model.AIRequest
import com.mtkresearch.breezeapp.shared.contracts.model.AIResponse
import com.mtkresearch.breezeapp.shared.contracts.model.BinaryData
import com.mtkresearch.breezeapp.shared.contracts.model.Configuration
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests that verify the AIDL interfaces can be correctly instantiated and used.
 * This validates the syntax of our contract.
 */
@RunWith(AndroidJUnit4::class)
class ContractSyntaxTest {

    /**
     * Verifies that the IAIRouterService interface can be used in code.
     */
    @Test
    fun testServiceInterfaceUsage() {
        // Create a stub implementation of the service
        val serviceStub = object : IAIRouterService.Stub() {
            override fun getApiVersion(): Int {
                return 2
            }
            
            override fun initialize(config: Configuration) {
                // No-op for test
            }

            override fun sendMessage(request: AIRequest) {
                // No-op for test
            }
            
            override fun cancelRequest(requestId: String): Boolean {
                return true
            }

            override fun registerListener(listener: IAIRouterListener) {
                // No-op for test
            }

            override fun unregisterListener(listener: IAIRouterListener) {
                // No-op for test
            }
            
            override fun hasCapability(capabilityName: String): Boolean {
                return capabilityName == "binary_data"
            }
        }
        
        // Verify we can cast it to the interface
        val service: IAIRouterService = serviceStub
        assertNotNull(service)
        
        // Verify we can access it as a Binder
        val binder: IBinder = serviceStub
        assertNotNull(binder)
    }

    /**
     * Verifies that the IAIRouterListener interface can be used in code.
     */
    @Test
    fun testListenerInterfaceUsage() {
        // Create a stub implementation of the listener
        val listenerStub = object : IAIRouterListener.Stub() {
            override fun onResponse(response: AIResponse) {
                // No-op for test
            }
        }
        
        // Verify we can cast it to the interface
        val listener: IAIRouterListener = listenerStub
        assertNotNull(listener)
        
        // Verify we can access it as a Binder
        val binder: IBinder = listenerStub
        assertNotNull(binder)
    }
    
    /**
     * Verifies that binary data can be properly passed through the interfaces.
     */
    @Test
    fun testBinaryDataUsage() {
        // Create a binary data object
        val binaryData = BinaryData(
            type = BinaryData.ContentType.IMAGE_JPEG,
            data = byteArrayOf(1, 2, 3, 4, 5),
            metadata = mapOf("test" to "value")
        )
        
        // Create a request with binary data
        val request = AIRequest(
            id = "test-id",
            text = "Test with binary data",
            sessionId = "session-123",
            timestamp = System.currentTimeMillis(),
            binaryAttachments = listOf(binaryData)
        )
        
        // Verify the request can be used in the service interface
        val serviceStub = object : IAIRouterService.Stub() {
            override fun getApiVersion(): Int = 1
            override fun initialize(config: Configuration) {}
            override fun sendMessage(req: AIRequest) {
                // Verify we can access the binary data
                val attachment = req.binaryAttachments.firstOrNull()
                assertNotNull(attachment)
            }
            override fun cancelRequest(requestId: String): Boolean = true
            override fun registerListener(listener: IAIRouterListener) {}
            override fun unregisterListener(listener: IAIRouterListener) {}
            override fun hasCapability(capabilityName: String): Boolean = true
        }
        
        // Call the method with our binary data
        serviceStub.sendMessage(request)
    }
} 