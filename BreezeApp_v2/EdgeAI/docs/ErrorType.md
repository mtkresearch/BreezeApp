# Error Handling
Here is a table summarizing the error types, error messages, descriptions, and sample error resonses for your local model endpoints (EdegAI.chat, EdegAI.tts, EdegAI.asr).

## 1. Error Types Table

| Error Type | Error Message | Description | Sample Error Response |
| :--- | :--- | :--- | :--- |
| InvalidInputException | "Input text exceeds maximum length (4096 characters)" | The input parameters are invalid, missing required fields, or exceed allowed limits. | throw new InvalidInputException("Input text exceeds maximum length (4096 characters)"); |
| ModelNotFoundException | "Model 'gpt-4o-mini-tts' not found" | The specified model does not exist or is not loaded. | throw new ModelNotFoundException("Model 'gpt-4o-mini-tts' not found"); |
|ModelInferenceException | "Model inference failed due to out-of-memory" | An error occurred during model inference | throw new ModelInferenceException("Model inference failed due to out-of-memory"); |
| AudioProcessingException | "Unsupported audio format: xyz" | The audio file is corrupted, in an unsupportd format, or cannot be decoded. | throw new AudioProcessingException("Unsupported audio format: .xyz"); |
| ResourceLimitException | "Insufficient GPU memory for inference" | The system does not have enough resources (CPU, GPU, RAM) to complete the request. | throw new ResourceLimitException("Insufficient GPU memory for inference"); |
| TimeoutException | "Inference timed out after 30 seconds" | The inference process took too long and was aborted. | throw new TimeoutException("Inference timed out after 30 seconds"); |
| NotSupportedException | "Parameter 'speed' is not supported by this model" | The requested feature, parameter, or format is not supported by the current model. | throw new NotSupportedException("Parameter 'speed' is not supported by this model"); |
| InternalErrorException | "Unexpected internal error occurred" | An unexpected internal error occurred. | throw new InternalErrorException("Unexpected internal error occurred"); |

Note:
- The error message is vailable via e.getMessage()
- The exception type allows you to distinquish the error category programmatically.