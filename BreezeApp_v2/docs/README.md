# BreezeApp v2.0 Documentation

This directory contains comprehensive documentation for BreezeApp v2.0's AI Router architecture.

## 📚 Documentation Index

### Getting Started
- **[Quick Start Guide](quick-start.md)** - Get up and running in 5 minutes ⚡
- **[Developer Guide](developer-guide.md)** - Comprehensive development guide 🛠️

### Architecture & Design
- **[Refactoring Plan](refactoring_plan.md)** - Complete roadmap and implementation milestones
- **[Client API Specification](client_api_spec.md)** - UI layer architecture and API design

### API References
- **[Shared Contracts API](../shared-contracts/docs/api.md)** - AIDL interfaces and data models

### Testing & Quality
- **[Testing Guide](testing-guide.md)** - Comprehensive testing strategies 🧪

### Specifications
- **[AI Router Specification](ai_router_spec/)** - Detailed technical specifications
  - [Project Overview](ai_router_spec/00-Overview/)
  - [Architecture](ai_router_spec/01-Architecture/)
  - [Interfaces](ai_router_spec/02-Interfaces/)
  - [Models](ai_router_spec/03-Models/)
  - [Runtime](ai_router_spec/04-Runtime/)
  - [Error Handling](ai_router_spec/05-Error-Handling/)
  - [Testing](ai_router_spec/06-Testing/)
  - [Implementation](ai_router_spec/07-Implementation/)
  - [Diagrams](ai_router_spec/08-Diagrams/)

---

## 🎯 Quick Navigation

| For... | Read This |
|--------|-----------|
| **New Developers** | Start with [Quick Start Guide](quick-start.md) ⚡ |
| **Understanding Architecture** | See [Refactoring Plan](refactoring_plan.md) 🏗️ |
| **UI Development** | Check [Client API Spec](client_api_spec.md) 🎨 |
| **API Integration** | Explore [Shared Contracts API](../shared-contracts/docs/api.md) 🔗 |
| **Testing Implementation** | Read [Testing Guide](testing-guide.md) 🧪 |
| **In-Depth Development** | Study [Developer Guide](developer-guide.md) 📖 |

---

## 🚀 Quick Examples

### Basic Chat Integration
```kotlin
val aiClient = SimpleAIClient(context)
aiClient.connect()
aiClient.sendMessage("Hello AI") { response ->
    println("AI Response: $response")
}
```

### Configuration Setup
```kotlin
val config = Configuration(
    apiVersion = 2,
    maxTokens = 1024,
    temperature = 0.7f
)
```

---

*Last Updated: 2024-12-19*  
*Version: 2.0* 