// Custom Runner example: implement and register a new runner
class MyCustomLLMRunner : BaseRunner {
    override fun load(config: ModelConfig): Boolean = true
    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult =
        InferenceResult.success(outputs = mapOf(InferenceResult.OUTPUT_TEXT to "Hello from custom runner!"))
    override fun unload() {}
    override fun getCapabilities(): List<CapabilityType> = listOf(CapabilityType.LLM)
    override fun isLoaded(): Boolean = true
    override fun getRunnerInfo(): RunnerInfo = RunnerInfo(
        name = "MyCustomLLMRunner",
        version = "1.0.0",
        capabilities = getCapabilities(),
        description = "A custom LLM runner",
        isMock = false
    )
}
// Register in runner_config.json:
// {
//   "name": "my_custom_llm",
//   "class": "com.yourpackage.MyCustomLLMRunner",
//   "capabilities": ["LLM"],
//   "priority": 50,
//   "is_real": true
// }
