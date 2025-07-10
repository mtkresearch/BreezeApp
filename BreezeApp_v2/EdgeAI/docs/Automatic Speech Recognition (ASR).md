# Automatic Speech Recognition (ASR)

## 1. Description:

Transcribe audio into the input language using a specified model.

## 2. Method: 'EdgeAI.asr'

An OpenAI-style API for BreezeApp.

## 3. Request Body Parameter Description

| Parameter Name            | Type         | Required | Default | Description                                                                                                                                                                                                 |
| ------------------------- | ------------ | -------- | ------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| file                      | file         | Yes      |         | The audio file to be transcribed. Supported formats: flac, mp3, mp4, mpeg, mpga, m4a, ogg, wav, webm.                                                                                                       |
| model                     | string       | Yes      |         | The model ID to use.                                                                                                                                                                                        |
| language                  | string       | No       |         | The language of the input audio (ISO-639-1 code, e.g., "en"). Specifying this can improve accuracy and speed.                                                                                               |
| prompt                    | string       | No       |         | Optional prompt text to guide the model’s style or to continue from previous audio. Must match the audio language.                                                                                          |
| response_format           | string       | No       | json    | Output format. Options: json, text, srt, verbose_json, vtt.                                                                                                                                                 |
| include[]                 | array        | No       |         | Specify additional information to include in the response. "logprobs" returns log probabilities for each token (only supported by gpt-4o-transcribe/mini with response_format=json).                        |
| stream                    | boolean/null | No       | false   | Whether to return data as a stream (Server-Sent Events).                                                                                                                                                    |
| temperature               | number       | No       | 0       | Sampling temperature, range 0~1. Higher values (e.g., 0.8) produce more random output, lower values (e.g., 0.2) are more deterministic. When set to 0, the model will automatically adjust the temperature. |
| timestamp_granularities[] | array        | No       | segment | Specify the granularity of timestamps to generate. Must be used with response_format=verbose_json. Options: word, segment. "word" increases latency.                                                        |

## 4. Response Format

### 4.1 response_format: json (default)

```Json
{
  "text": "Transcribed text here."
}
```

### 4.2 response_format: text

```Json
Transcribed text here.
```

### 4.3 response_format: srt

```Json
1
00:00:00,000 --> 00:00:02,000
Transcribed text here.
```

### 4.4 response_format: vtt

```Json
WEBVTT

00:00:00.000 --> 00:00:02.000
Transcribed text here.
```

### 4.5 response_format: verbose_json

```Json
{
  "text": "Transcribed text here.",
  "segments": [
    {
      "id": 0,
      "seek": 0,
      "start": 0.0,
      "end": 2.0,
      "text": "Transcribed text here.",
      "tokens": [ ... ],
      "temperature": 0,
      "avg_logprob": -0.1,
      "compression_ratio": 1.2,
      "no_speech_prob": 0.01,
      "words": [
        {
          "word": "Transcribed",
          "start": 0.0,
          "end": 0.5
        }
        // ...
      ]
    }
  ],
  "language": "en"
}
```

- If `timestamp_granularities[]` is set to `word`, the `words` field will include timestamps for each word.

### 4.6 stream: true

The server returns response fragments (chunks) incrementally using Server-Sent Events (SSE).

Each chunk is a JSON object, usually containing only a part of the content (such as a short text segment).

A final `[DONE]` chunk indicates the end of the stream.

Streaming response example:

```Json
data: {"text": "Hello"}
data: {"text": " world"}
data: [DONE]
```

## 5. JAVA Examples

### 5.1 ASR Request Example (Non-Streaming)

```Java
import EdgeAI
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ASRExample {
    public static void main(String[] args) {
        // Build the request body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("file", new File("audio_sample.mp3"));
        requestBody.put("model", "gpt-4o-transcribe");
        requestBody.put("response_format", "json"); // Optional, default is json
        // You can add other parameters as needed, e.g. language, prompt, etc.

        // Call EdgeAI.asr method (assume it returns a Map for JSON/text, or String for plain text)
        try {
            Map<String, Object> response = EdgeAI.asr(requestBody);
            System.out.println(response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

### 5.2 ASR Streaming Example

```Java
import EdgeAI
import java.io.File;
import java.util.HashMap;
import java.util.Map;

// Define the callback interface for streaming responses
interface ASRStreamCallback {
    void onChunk(Map<String, Object> chunk);
    void onComplete();
    void onError(Exception e);
}

public class ASRStreamingExample {
    public static void main(String[] args) {
        // Build the request body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("file", new File("audio_sample.mp3"));
        requestBody.put("model", "gpt-4o-transcribe");
        requestBody.put("stream", true);
        requestBody.put("response_format", "json"); // Only json is supported for streaming

        // Call EdgeAI.asr with a streaming callback
        EdgeAI.asr(requestBody, new ASRStreamCallback() {
            @Override
            public void onChunk(Map<String, Object> chunk) {
                System.out.println("Received chunk: " + chunk);
            }

            @Override
            public void onComplete() {
                System.out.println("Streaming complete.");
            }

            @Override
            public void onError(Exception e) {
                e.printStackTrace();
            }
        });
    }
}
```

Notes:

- These examples assume `EdgeAI.asr(Map<String, Object> requestBody)` returns the full result for non-streaming, and `EdgeAI.asr(Map<String, Object> requestBody, ASRStreamCallback callback)` handles streaming.
- For streaming, each chunk is a partial JSON response as described in the API spec.
