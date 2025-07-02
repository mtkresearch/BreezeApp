# BreezeApp v2: AI Router Architecture

## Overview

BreezeApp v2 is a modular Android application that implements a decoupled AI service architecture. The system is divided into separate modules with clear responsibilities, allowing for better maintainability, testability, and scalability.

## Project Structure

The project consists of the following key modules:

### 1. [breeze-app-router](../breeze-app-router/docs/README.md)

The core AI service provider that hosts various AI capabilities (LLM, ASR, VLM, etc.) and exposes them through a secure AIDL interface. This service runs as a standalone Android application and can be accessed by authorized client applications.

### 2. [shared-contracts](../shared-contracts/docs/api.md)

The contract module that defines the AIDL interfaces and data models for communication between clients and the router service. This module serves as the single source of truth for the API contract.

### 3. [breeze-app-router-client](../breeze-app-router-client/README.md)

A reference implementation and demonstration app that showcases how to properly integrate with the AI Router Service. This module serves as both a functional testing tool and comprehensive documentation for third-party developers.

## Getting Started

For new developers, we recommend starting with the following resources:

1. [Quick Start Guide](quick-start.md) - Basic setup and first steps
2. [Architecture Overview](ARCHITECTURE.md) - Comprehensive system architecture
3. [Contributing Guide](CONTRIBUTING.md) - How to contribute to the project

## Integration Options for Third-Party Developers

If you're developing a third-party application that needs to integrate with the BreezeApp AI Router, see our [Client Integration Guide](../breeze-app-router-client/docs/integration-guide.md).

## Project Status

The project has completed all planned milestones as outlined in our [Refactoring Plan](reference/refactoring_plan.md).

## Future Plans

- Publish the `shared-contracts` module as a standalone dependency via Maven/JitPack
- Implement production runners with actual AI models
- Expand test coverage with more edge cases and performance scenarios
- Develop additional client examples for specific use cases

## Documentation Index

For a complete list of all available documentation, please visit our [Documentation Index](index.md).

## 🎯 Quick Navigation

| For... | Read This |
|--------|-----------|
| **New Developers** | Start with [Quick Start Guide](quick-start.md) ⚡ |
| **Understanding Architecture** | See [Architecture Overview](ARCHITECTURE.md) 🏗️ |
| **UI Development** | Check [Client API Spec](client-api-spec.md) 🎨 |
| **API Integration** | Explore [API Reference](api-reference.md) 🔗 |
| **Testing Implementation** | Read [Testing Guide](testing-guide.md) 🧪 |
| **In-Depth Development** | Study [Developer Guide](developer-guide.md) 📖 |
| **Contributing** | Follow the [Contributing Guide](CONTRIBUTING.md) 👥 |

---

## 🚀 Quick Examples

### Basic Chat Integration
```kotlin
val aiClient = AIRouterClient.Builder(context).build()
lifecycleScope.launch {
    aiClient.connect()
    val response = aiClient.generateText("Hello AI")
    textView.text = response.text
}
```

### Configuration Setup
```kotlin
val client = AIRouterClient.Builder(context)
    .setConnectionListener { state ->
        when (state) {
            ConnectionState.CONNECTED -> Log.d(TAG, "Connected")
            ConnectionState.DISCONNECTED -> Log.d(TAG, "Disconnected")
        }
    }
    .setLogLevel(LogLevel.DEBUG)
    .build()
```

---

*Last Updated: 2024-12-20*  
*Version: 2.1* 