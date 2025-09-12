# Code Refactoring and Simplification Suggestions

This document contains a list of potential code improvements identified during the project-wide documentation update. These suggestions aim to improve code clarity, reduce complexity, and enhance maintainability.

---

## 1. `EdgeAI.kt` - Simplify Response Conversion

-   **Location:** `BreezeApp-engine/android/EdgeAI/src/main/java/com/mtkresearch/breezeapp/edgeai/EdgeAI.kt`
-   **Files:** `convertAIResponseToChatResponse`, `convertAIResponseToTTSResponse`, etc.

-   **Issue:** The `EdgeAI` object currently contains several private `convert...` functions to transform the generic `AIResponse` into specific response types (`ChatResponse`, `TTSResponse`, etc.). This adds responsibility to the `EdgeAI` class that could be better encapsulated elsewhere.

-   **Suggestion:** Create extension functions or dedicated mapper classes for these conversions. This would make the `EdgeAI` class cleaner and more focused on its primary role of service communication.

    **Example (Extension Function):**

    ```kotlin
    // In a new file, e.g., `ResponseMappers.kt`
    fun AIResponse.toChatResponse(isStreaming: Boolean, modelName: String?): ChatResponse {
        // ... conversion logic ...
    }

    // In EdgeAI.kt, the call becomes cleaner:
    val chatResponse = aiResponse.toChatResponse(request.stream ?: false, request.model)
    ```

---

## 2. `BreezeAppEngineService.kt` - Delegate Model Readiness Checks

-   **Location:** `BreezeApp-engine/android/breeze-app-engine/src/main/java/com/mtkresearch/breezeapp/engine/BreezeAppEngineService.kt`
-   **Files:** `isModelReadyForInference()`, `isCategoryReadyForInference()`

-   **Issue:** The `BreezeAppEngineService` currently has methods to check the status of models. While the logic is delegated to the `ModelManager`, this still adds a layer of responsibility to the Service that is not strictly related to its Android lifecycle duties.

-   **Suggestion:** Components that need to check model status (like the `BreezeAppRouter` or specific `Runners`) should query the `ModelManager` singleton directly. This further purifies the `Service` class, keeping it focused only on Android component lifecycle management.

---

## 3. `ChatViewModel.kt` - Extract Request Creation Logic

-   **Location:** `BreezeApp-client/android/breeze-app-client/src/main/java/com/mtkresearch/breezeapp/engine/client/presentation/viewmodel/ChatViewModel.kt`
-   **Files:** `runGuardrailCheck`, `sendChatRequest`

-   **Issue:** The `ChatViewModel` contains logic for building `GuardrailRequest` and `ChatRequest` objects directly within its methods. This mixes the "what" (sending a request) with the "how" (creating the specific request object).

-   **Suggestion:** Create a dedicated factory or builder class, or even private helper functions, to encapsulate the creation of these request objects. This makes the primary ViewModel logic easier to read and separates the concern of request construction.

    **Example (Private Helper):**

    ```kotlin
    private fun createChatRequest(userInput: String): ChatRequest {
        return ChatRequest(
            model = "Breeze2-3B-8W16A-250630-npu",
            messages = listOf(
                ChatRequest.Message(role = "system", content = "You are a helpful assistant."),
                ChatRequest.Message(role = "user", content = userInput)
            ),
            stream = true
        )
    }

    // In sendChatRequest method:
    val chatRequest = createChatRequest(userInput)
    EdgeAI.chat(chatRequest).onEach { ... }.launchIn(viewModelScope)
    ```

---

## 4. `BreezeAppRouter.kt` - Simplify Error Handling

-   **Location:** `BreezeApp-engine/android/breeze-app-engine/src/main/java/com/mtkresearch/breezeapp/engine/core/BreezeAppRouter.kt`
-   **File:** `processRequest`

-   **Issue:** The `processRequest` function has multiple `catch` blocks for different exception types (`RunnerNotFoundException`, `RunnerNotReadyException`, `Exception`). This can be simplified.

-   **Suggestion:** Consolidate the exception handling into a single `catch` block and use a `when` statement to differentiate between exception types. This reduces code duplication for creating the `AIResponse` object.

    **Example:**

    ```kotlin
    } catch (e: Exception) {
        Log.e(TAG, "Error processing request: ${request.requestId}", e)
        val errorMessage = when (e) {
            is RunnerNotFoundException -> "No runner available for this request type."
            is RunnerNotReadyException -> "The required AI model is not ready. Please try again later."
            else -> "An unexpected error occurred: ${e.message}"
        }
        clientListener.onResponse(
            AIResponse(
                requestId = request.requestId,
                state = ResponseState.ERROR,
                error = errorMessage,
                isComplete = true
            )
        )
    }
    ```
