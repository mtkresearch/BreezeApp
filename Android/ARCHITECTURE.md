# Breeze AI Chat - Architecture Guide

This document provides an overview of the architectural design choices made in the Breeze AI Chat application.

## 1. Architecture Overview

The application follows a modular, clean architecture approach, organized into separate concerns:

```
com.mtkresearch.breezeapp/
├── core/              - Foundational utilities and base classes
├── data/              - Data handling and repositories
├── features/          - Feature-specific modules for AI capabilities 
└── ui/                - User interface components
```

## 2. Design Patterns

### Service-Based Architecture

AI capabilities are implemented as Android Services, which:
- Allows for resource-intensive operations to run in the background
- Enables sharing of initialized AI models across different activities
- Provides a clean API for interacting with different AI features

### Repository Pattern

The application uses repositories to abstract data sources:
- `ConversationRepository` manages chat history and message formatting
- `ModelManager` handles downloading and management of AI model files

### ViewModel Pattern

ViewModels separate business logic from UI:
- `ChatViewModel` handles interaction between UI and services
- Manages conversation state and AI service communication
- Survives configuration changes for smooth user experience

### Observer Pattern

State updates are communicated through reactive flows:
- `StateFlow` objects publish updates to subscribers
- Services expose state through observable flows
- UI components observe and react to state changes

## 3. Key Components

### BaseEngineService

A common base class for all AI engine services that:
- Standardizes initialization and cleanup
- Provides consistent state management
- Simplifies service binding and lifecycle handling

### AI Feature Services

Each AI capability is implemented as a separate service:
- `LLMService`: Handles text generation
- `VLMService`: Processes images and provides descriptions
- `ASRService`: Converts speech to text
- `TTSService`: Converts text to speech

### Model Management

The `ModelManager` centralizes AI model handling:
- Downloads models from remote sources
- Tracks download progress
- Manages local model storage
- Provides available models by type

### Conversation Management

The `ConversationRepository` manages chat state:
- Stores and retrieves messages
- Formats conversation history for LLM context
- Handles system prompts and message formatting

## 4. Communication Flow

1. User interacts with UI components
2. UI events are forwarded to ViewModels
3. ViewModels coordinate with services and repositories
4. Services perform AI operations and emit results
5. ViewModels process results and update UI state
6. UI observes state changes and updates accordingly

## 5. Modularity and Integration

The application is designed for both full use and component extraction:

- **Loose coupling**: Services communicate through well-defined interfaces
- **Standalone modules**: Each AI feature can be used independently
- **Clean dependencies**: Feature modules depend only on core utilities
- **Consistent patterns**: Similar patterns across features for easier learning

## 6. Testing Strategy

The architecture supports comprehensive testing:

- **Unit Testing**: ViewModels, repositories, and utility classes
- **Integration Testing**: Service integration and communication
- **UI Testing**: Activity and fragment behavior

## 7. Future Extensibility

New AI capabilities can be added by:
1. Creating a new service that extends `BaseEngineService`
2. Implementing feature-specific functionality
3. Adding the service to the application manifest
4. Creating UI components that bind to the service

This modular approach ensures the codebase remains maintainable as new AI features are added. 