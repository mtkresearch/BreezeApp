# ❗ EdgeAI SDK Error Handling

The EdgeAI SDK provides a detailed exception hierarchy to help you diagnose and handle errors gracefully. All SDK-specific exceptions inherit from the base `EdgeAIException`.

## Exception Hierarchy

```
EdgeAIException
├── ServiceConnectionException
├── InvalidInputException
├── ModelNotFoundException
├── ModelInferenceException
├── AudioProcessingException
└── TimeoutException
```

## Exception Details

### `ServiceConnectionException`
-   **When it happens**: This is one of the most common errors. It occurs when the SDK fails to establish or maintain a connection with the `BreezeApp Router` service.
-   **Common Causes**:
    -   The `BreezeApp Router` application is not installed on the device.
    -   The `BreezeApp Router` service is disabled or has crashed.
    -   Your application does not have the necessary permissions to query and bind to other services (rare, but possible on custom Android builds).
-   **Suggested Handling**:
    -   Catch this exception during `EdgeAI.initializeAndWait()`.
    -   Display a user-friendly dialog or message prompting the user to install or enable the `BreezeApp Router`. You can even guide them to the Google Play Store.

---

### `InvalidInputException`
-   **When it happens**: The request you sent to the API failed validation checks.
-   **Common Causes**:
    -   A required field (like `model` or `messages`) is missing.
    -   A parameter is out of its valid range (e.g., `temperature = 3.0f`).
    -   The input text for TTS is longer than the 4096-character limit.
-   **Suggested Handling**: This is typically a developer error. Log the detailed error message during development to fix the request creation logic. You should generally not see this in a production environment if your code is correct.

---

### `ModelNotFoundException`
-   **When it happens**: The `model` ID you specified in a request is not available in the `BreezeApp Router`.
-   **Common Causes**:
    -   A typo in the model name (e.g., `"breeze-2"` instead of `"breeze2"`).
    -   The model has not been downloaded or enabled in the `BreezeApp Router` settings.
-   **Suggested Handling**: Provide a mechanism for the user to select from a list of available models, which could be fetched from the Router if such an API exists. Otherwise, show an error message indicating the model is unavailable.

---

### `ModelInferenceException`
-   **When it happens**: An error occurred within the AI model itself during processing.
-   **Common Causes**:
    -   The model ran out of memory on the device.
    -   The input was malformed in a way that the model could not handle.
    -   A rare, unexpected crash within the native inference engine.
-   **Suggested Handling**: This is usually a non-recoverable error for that specific request. Log the error for diagnostics and inform the user that their request could not be completed. You might suggest they try rephrasing their request.

---

### `AudioProcessingException`
-   **When it happens**: Specific to ASR, this error occurs when the provided audio data is invalid.
-   **Common Causes**:
    -   The audio file format is unsupported.
    -   The audio data is corrupted or empty.
-   **Suggested Handling**: Inform the user that the audio file is invalid and ask them to try recording again or using a different file.

---

### `TimeoutException`
-   **When it happens**: A response was not received from the `BreezeApp Router` within the expected time frame.
-   **Common Causes**:
    -   The device is under very heavy load, and the AI inference is taking too long.
    -   A deadlock or an unresponsive state within the `BreezeApp Router` service.
-   **Suggested Handling**: Inform the user that the request timed out and that they can try again. This is often a transient issue.

---

### `ResourceLimitException`
-   **When it happens**: The system lacks sufficient resources (CPU, GPU, RAM) to process the request.
-   **Common Causes**:
    -   Running a large model on a low-memory device.
    -   Multiple apps consuming resources in the background.
-   **Suggested Handling**: This is a difficult error to recover from. Suggest that the user close other applications or try again later. For developers, this might indicate that a smaller model variant is needed for certain devices.

---

### `NotSupportedException`
-   **When it happens**: The request used a parameter or feature that is not supported by the specified model.
-   **Common Causes**:
    -   Sending a `speed` parameter to a TTS model that does not allow speed adjustments.
    -   Requesting a specific response format that the model cannot generate.
-   **Suggested Handling**: This is typically a developer error. The client application should be aware of the capabilities of the models it interacts with. Log the error and adjust the request logic.

---

### `InternalErrorException`
-   **When it happens**: An unexpected and unrecoverable error occurred within the `BreezeApp Router` or the underlying inference engine.
-   **Common Causes**:
    -   This is a catch-all for rare and unforeseen issues.
-   **Suggested Handling**: Log the error with as much detail as possible for debugging. Inform the user that a critical error occurred and that the request could not be completed. 