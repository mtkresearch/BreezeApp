# Architecture

The application follows a service-based architecture where each AI capability (LLM, VLM, ASR, TTS) is implemented as an Android service. Each service supports multiple backends with graceful fallback:

1. Primary MediaTek backend (🚧 Still in development...)
2. ⭐️ Open-source alternatives (Executorch/Sherpa)
3. Android system defaults

Key components:
- `ChatActivity`: Main UI for text/voice interaction
- `AudioChatActivity`: Dedicated voice interface (🚧 Still in development...)
- `*EngineService`: Service implementations for each AI capability

## Known Issues

1. **VLM Support (Executorch)**: VLM features are currently non-functional due to limitations in Executorch's image processing capabilities. See [executorch#6189](https://github.com/pytorch/executorch/issues/6189) for updates.

2. **Audio Chat Interface**: The dedicated voice interface (`AudioChatActivity`) is still under development and may have limited functionality.

3. **MediaTek NPU Backend**: Support for MediaTek NPU acceleration is currently in development. Only CPU inference is fully supported at this time. 