package com.mtkresearch.breezeapp.shared.contracts.model

import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ParcelableTest {

    // Extension function for testing to write BinaryData to Parcel
    private fun BinaryData.writeToParcelForTest(parcel: Parcel) {
        parcel.writeString(type)
        parcel.writeInt(data.size)
        parcel.writeByteArray(data)
        parcel.writeInt(metadata.size)
        metadata.forEach { (key, value) ->
            parcel.writeString(key)
            parcel.writeString(value)
        }
    }

    @Test
    fun configuration_isParcelable() {
        val original = Configuration(
            apiVersion = 2,
            logLevel = 3,
            preferredRuntime = Configuration.RuntimeBackend.GPU,
            runnerConfigurations = mapOf(
                Configuration.AITaskType.TEXT_GENERATION to Configuration.RunnerType.EXECUTORCH,
                Configuration.AITaskType.IMAGE_ANALYSIS to Configuration.RunnerType.ONNX,
                Configuration.AITaskType.SPEECH_RECOGNITION to Configuration.RunnerType.SYSTEM
            ),
            defaultModelName = "gpt-3.5-turbo",
            languagePreference = "zh-TW",
            timeoutMs = 45000,
            maxTokens = 2048,
            temperature = 0.8f,
            additionalSettings = mapOf(
                Configuration.SettingKeys.ENABLE_LOGGING to "true",
                Configuration.SettingKeys.CACHE_MODELS to "true",
                Configuration.SettingKeys.MODEL_PATH_PREFIX + "text_generation" to "/data/local/tmp/models/llm.bin",
                Configuration.SettingKeys.RUNNER_CONFIG_PREFIX + "executorch" to "optimize_for_mobile=true"
            )
        )

        val parcel = Parcel.obtain()
        original.writeToParcel(parcel, original.describeContents())
        parcel.setDataPosition(0)

        val createdFromParcel = Configuration.createFromParcel(parcel)
        assertEquals(original.apiVersion, createdFromParcel.apiVersion)
        assertEquals(original.logLevel, createdFromParcel.logLevel)
        assertEquals(original.preferredRuntime, createdFromParcel.preferredRuntime)
        assertEquals(original.defaultModelName, createdFromParcel.defaultModelName)
        assertEquals(original.languagePreference, createdFromParcel.languagePreference)
        assertEquals(original.timeoutMs, createdFromParcel.timeoutMs)
        assertEquals(original.maxTokens, createdFromParcel.maxTokens)
        assertEquals(original.temperature, createdFromParcel.temperature)
        assertEquals(original.additionalSettings, createdFromParcel.additionalSettings)
        
        // Verify runner configurations
        assertEquals(
            original.runnerConfigurations[Configuration.AITaskType.TEXT_GENERATION],
            createdFromParcel.runnerConfigurations[Configuration.AITaskType.TEXT_GENERATION]
        )
        assertEquals(
            original.runnerConfigurations[Configuration.AITaskType.IMAGE_ANALYSIS],
            createdFromParcel.runnerConfigurations[Configuration.AITaskType.IMAGE_ANALYSIS]
        )
        assertEquals(
            original.runnerConfigurations[Configuration.AITaskType.SPEECH_RECOGNITION],
            createdFromParcel.runnerConfigurations[Configuration.AITaskType.SPEECH_RECOGNITION]
        )

        parcel.recycle()
    }

    @Test
    fun aiRequest_isParcelable() {
        val binaryData = BinaryData(
            type = BinaryData.ContentType.IMAGE_JPEG,
            data = byteArrayOf(1, 2, 3, 4, 5),
            metadata = mapOf("width" to "800", "height" to "600")
        )
        
        val original = AIRequest(
            id = "test-id",
            text = "Analyze this image",
            sessionId = "session-123",
            timestamp = 1234567890L,
            apiVersion = 2,
            binaryAttachments = listOf(binaryData),
            options = mapOf(
                AIRequest.OptionKeys.REQUEST_TYPE to AIRequest.RequestType.IMAGE_ANALYSIS,
                AIRequest.OptionKeys.MODEL_NAME to "vision-model"
            )
        )

        val parcel = Parcel.obtain()
        original.writeToParcel(parcel, original.describeContents())
        parcel.setDataPosition(0)

        val createdFromParcel = AIRequest.createFromParcel(parcel)
        assertEquals(original.id, createdFromParcel.id)
        assertEquals(original.text, createdFromParcel.text)
        assertEquals(original.sessionId, createdFromParcel.sessionId)
        assertEquals(original.timestamp, createdFromParcel.timestamp)
        assertEquals(original.apiVersion, createdFromParcel.apiVersion)
        assertEquals(original.options, createdFromParcel.options)
        
        // Verify binary data
        assertEquals(1, createdFromParcel.binaryAttachments.size)
        val parsedBinaryData = createdFromParcel.binaryAttachments[0]
        assertEquals(binaryData.type, parsedBinaryData.type)
        assertTrue(binaryData.data.contentEquals(parsedBinaryData.data))
        assertEquals(binaryData.metadata, parsedBinaryData.metadata)

        parcel.recycle()
    }

    @Test
    fun aiResponse_isParcelable() {
        val binaryData = BinaryData(
            type = BinaryData.ContentType.IMAGE_PNG,
            data = byteArrayOf(5, 4, 3, 2, 1),
            metadata = mapOf("generated" to "true")
        )
        
        val original = AIResponse(
            requestId = "request-456",
            text = "This is a response.",
            isComplete = true,
            state = AIResponse.ResponseState.COMPLETED,
            apiVersion = 2,
            binaryAttachments = listOf(binaryData),
            metadata = mapOf(
                AIResponse.MetadataKeys.MODEL_NAME to "gpt-4",
                AIResponse.MetadataKeys.PROCESSING_TIME_MS to "1200"
            ),
            error = null
        )

        val parcel = Parcel.obtain()
        original.writeToParcel(parcel, original.describeContents())
        parcel.setDataPosition(0)

        val createdFromParcel = AIResponse.createFromParcel(parcel)
        assertEquals(original.requestId, createdFromParcel.requestId)
        assertEquals(original.text, createdFromParcel.text)
        assertEquals(original.isComplete, createdFromParcel.isComplete)
        assertEquals(original.state, createdFromParcel.state)
        assertEquals(original.apiVersion, createdFromParcel.apiVersion)
        assertEquals(original.metadata, createdFromParcel.metadata)
        assertEquals(original.error, createdFromParcel.error)
        
        // Verify binary data
        assertEquals(1, createdFromParcel.binaryAttachments.size)
        val parsedBinaryData = createdFromParcel.binaryAttachments[0]
        assertEquals(binaryData.type, parsedBinaryData.type)
        assertTrue(binaryData.data.contentEquals(parsedBinaryData.data))
        assertEquals(binaryData.metadata, parsedBinaryData.metadata)

        parcel.recycle()
    }
    
    @Test
    fun binaryData_isParcelable() {
        val original = BinaryData(
            type = BinaryData.ContentType.AUDIO_WAV,
            data = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
            metadata = mapOf(
                "duration" to "120",
                "sampleRate" to "44100"
            )
        )

        val parcel = Parcel.obtain()
        // Use our test extension function to write to parcel
        original.writeToParcelForTest(parcel)
        parcel.setDataPosition(0)

        // Use the BinaryDataParceler to read the object from parcel
        val createdFromParcel = BinaryDataParceler.create(parcel)
        assertEquals(original.type, createdFromParcel.type)
        assertTrue(original.data.contentEquals(createdFromParcel.data))
        assertEquals(original.metadata, createdFromParcel.metadata)

        parcel.recycle()
    }
} 