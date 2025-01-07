| Model Type | Local CPU | MTK NPU | Default |
|-----------|-----------|---------|----------|
| LLM       | ✅         | ✅       | ❌       |
| VLM       | ❌         | ❌       | ❌       |
| ASR       | ✅         | ❌       | ✅       |
| TTS       | ✅         | ❌       | ✅       |


### Testing Prompt:
- 請問台灣有哪些知名夜市？
- 請介紹聯發科以及聯發創新基地


### TTS model download
https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/index.html

```bash
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-melo-tts-zh_en.tar.bz2
tar xvf vits-melo-tts-zh_en.tar.bz2
rm vits-melo-tts-zh_en.tar.bz2


### ASR model download
https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/tree/main/test_wavs

```bash
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2

tar xvf sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
rm sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2