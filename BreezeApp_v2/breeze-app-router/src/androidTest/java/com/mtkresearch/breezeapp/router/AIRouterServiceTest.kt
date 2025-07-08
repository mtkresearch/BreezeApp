package com.mtkresearch.breezeapp.router

import android.content.Intent
import android.os.IBinder
import android.os.RemoteException
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import com.mtkresearch.breezeapp.edgeai.IAIRouterListener
import com.mtkresearch.breezeapp.edgeai.IAIRouterService
import com.mtkresearch.breezeapp.edgeai.model.AIRequest
import com.mtkresearch.breezeapp.edgeai.model.AIResponse
import com.mtkresearch.breezeapp.edgeai.model.RequestPayload
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class AIRouterServiceTest {

    @get:Rule
    val serviceTestRule = ServiceTestRule()

    private lateinit var service: IAIRouterService

    @Before
    fun setUp() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), AIRouterService::class.java)
        // Use the test rule to bind to the service, which correctly handles its lifecycle.
        val binder = serviceTestRule.bindService(intent)
        service = IAIRouterService.Stub.asInterface(binder)
    }

    @Test
    fun serviceBinding_returnsValidBinder() {
        assertNotNull("Service should bind successfully", service)
        val apiVersion = service.apiVersion
        assertTrue("API version should be greater than 0", apiVersion > 0)
    }

    @Test
    fun listener_receivesResponse_andUnregistersCorrectly() {
        val responseLatch = CountDownLatch(1)
        val responseCounter = AtomicInteger(0)
        var receivedResponse: AIResponse? = null

        val listener = object : IAIRouterListener.Stub() {
            override fun onResponse(response: AIResponse?) {
                receivedResponse = response
                responseCounter.incrementAndGet()
                responseLatch.countDown()
            }
        }

        // 1. Register listener and send a message via the public AIDL interface
        service.registerListener(listener)
        val request = AIRequest(payload = RequestPayload.TextChat(prompt = "Test prompt", modelName = "mock-llm"))
        service.sendMessage(request)

        // 2. Verify the listener received the response
        assertTrue("Listener should receive a response within 5 seconds", responseLatch.await(5, TimeUnit.SECONDS))
        assertNotNull("Received response should not be null", receivedResponse)
        assertEquals("Request ID should match", request.id, receivedResponse?.requestId)
        assertEquals("Response counter should be 1", 1, responseCounter.get())
        assertTrue("Response should be marked as complete", receivedResponse?.isComplete == true)

        // 3. Unregister the listener
        service.unregisterListener(listener)

        // 4. Send another message and verify the listener is NOT called again
        val secondRequest = AIRequest(payload = RequestPayload.TextChat(prompt = "Second prompt", modelName = "mock-llm"))
        service.sendMessage(secondRequest)

        // Give some time for the message to be (not) processed by the old listener
        SystemClock.sleep(1000)

        // The counter should remain 1, proving the listener was not called a second time
        assertEquals("Response counter should still be 1 after unregistering", 1, responseCounter.get())
    }
} 