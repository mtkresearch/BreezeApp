# Error Handling

Here is a table summarizing the error types, error messages, descriptions, and sample error responses for your local model endpoints (`EdgeAI.chat`, `EdgeAI.tts`, `EdgeAI.asr`).



## 1. Error Types Table

| Error Type               | Error Message Example                                 | Description                                                                          | Sample Error Response (Java Exception)                                                    |
| ------------------------ | ----------------------------------------------------- | ------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------- |
| InvalidInputException    | "Input text exceeds maximum length (4096 characters)" | The input parameters are invalid, missing required fields, or exceed allowed limits. | `throw new InvalidInputException("Input text exceeds maximum length (4096 characters)");` |
| ModelNotFoundException   | "Model 'gpt-4o-mini-tts' not found"                   | The specified model does not exist or is not loaded.                                 | `throw new ModelNotFoundException("Model 'gpt-4o-mini-tts' not found");`                  |
| ModelInferenceException  | "Model inference failed due to out-of-memory"         | An error occurred during model inference.                                            | `throw new ModelInferenceException("Model inference failed due to out-of-memory");`       |
| AudioProcessingException | "Unsupported audio format: .xyz"                      | The audio file is corrupted, in an unsupported format, or cannot be decoded.         | `throw new AudioProcessingException("Unsupported audio format: .xyz");`                   |
| ResourceLimitException   | "Insufficient GPU memory for inference"               | The system does not have enough resources (CPU, RAM, GPU) to complete the request.   | `throw new ResourceLimitException("Insufficient GPU memory for inference");`              |
| TimeoutException         | "Inference timed out after 30 seconds"                | The inference process took too long and was aborted.                                 | `throw new TimeoutException("Inference timed out after 30 seconds");`                     |
| NotSupportedException    | "Parameter 'speed' is not supported by this model"    | The requested feature, parameter, or format is not supported by the current model.   | `throw new NotSupportedException("Parameter 'speed' is not supported by this model");`    |
| InternalErrorException   | "Unexpected internal error occurred"                  | An unexpected internal error occurred.                                               | `throw new InternalErrorException("Unexpected internal error occurred");`                 |



## 2. Sample Error Response (Java Exception)

All errors are thrown as Java exceptions. Here is a sample code snippet for catching and handling these errors:

```java
try {
    Map<String, Object> response = EdgeAI.chat(requestBody);
} catch (InvalidInputException e) {
    System.err.println("Invalid input: " + e.getMessage());
} catch (ModelNotFoundException e) {
    System.err.println("Model not found: " + e.getMessage());
} catch (ModelInferenceException e) {
    System.err.println("Inference error: " + e.getMessage());
} catch (AudioProcessingException e) {
    System.err.println("Audio processing error: " + e.getMessage());
} catch (ResourceLimitException e) {
    System.err.println("Resource limit: " + e.getMessage());
} catch (TimeoutException e) {
    System.err.println("Timeout: " + e.getMessage());
} catch (NotSupportedException e) {
    System.err.println("Not supported: " + e.getMessage());
} catch (InternalErrorException e) {
    System.err.println("Internal error: " + e.getMessage());
}

```

**Note**:

- The error message is available via `e.getMessage()`.
- The exception type allows you to distinguish the error category programmatically.
