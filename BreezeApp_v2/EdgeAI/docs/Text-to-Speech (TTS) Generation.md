# Text-to-Speech (TTS) Generation

## 1. Description:

Generate text-to-speech (TTS) audio from provided text using a specified model.

## 2. Method: 'EdgeAI.tts'

An OpenAI-style API for BreezeApp.

## 3. Request Body Parameter Description:

| Parameter Name  | Type   | Required | Default | Description                                                                                                         |
| --------------- | ------ | -------- | ------- | ------------------------------------------------------------------------------------------------------------------- |
| input           | string | Yes      |         | The text to be converted to speech. Maximum length: 4096 characters.                                                |
| model           | string | Yes      |         | TTS model name                                                                                                      |
| voice           | string | Yes      |         | Voice style. Supported values: alloy, ash, ballad, coral, echo, fable, onyx, nova, sage, shimmer, verse.            |
| instructions    | string | No       |         | Additional instructions to control voice style. Supported only by gpt-4o-mini-tts; not supported by tts-1/tts-1-hd. |
| response_format | string | No       | mp3     | Output audio format. Supported values: mp3, opus, aac, flac, wav, pcm.                                              |
| speed           | number | No       | 1       | Playback speed, range: 0.25~4.0, default is 1.                                                                      |

## 4. Response Format

- The response returns the generated audio file content.
- The `Content-Type` is determined by the `response_format` parameter (e.g., `mp3`, `wav`, etc.).
- There is no JSON wrapper; the response is a raw binary audio stream.

## 5. JAVA Examples

### 5.1 Text-to-Speech (TTS) Request Example

```Java
import EdgeAI
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class TTSExample {
    public static void main(String[] args) {
        // Build the request body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("input", "Hello, this is a text-to-speech test.");
        requestBody.put("model", "tts-1");
        requestBody.put("voice", "alloy");
        requestBody.put("response_format", "mp3"); // Optional, default is mp3

        // Call EdgeAI.tts method (assume it returns InputStream for audio)
        try (InputStream audioStream = EdgeAI.tts(requestBody);
             FileOutputStream out = new FileOutputStream("output.mp3")) {

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = audioStream.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            System.out.println("Audio file saved as output.mp3");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

Notes:

- This example assumes `EdgeAI.tts(Map<String, Object> requestBody)` returns an `InputStream` containing the raw audio data.
- The audio is saved as `output.mp3`. Change the file extension if you use a different `response_format`.
- You can add other parameters (e.g., `instructions`, `speed`) to the `requestBody` as needed.
