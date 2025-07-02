# BreezeApp v2 Architecture

This document provides a high-level overview of the BreezeApp v2 architecture, focusing on the core design principles, component interactions, and communication patterns.

## Core Architecture Principles

BreezeApp v2 follows these key architectural principles:

1. **Separation of Concerns**: Clearly separated modules with well-defined responsibilities
2. **Process Isolation**: AI processing runs in a separate process from UI
3. **Contract-First Design**: AIDL interfaces define clear communication contracts
4. **Clean Architecture**: Each module follows MVVM + Clean Architecture patterns
5. **Extensibility**: Strategy pattern for pluggable AI backends

## System Components

The system is divided into three main components:

```
┌────────────────────┐      ┌───────────────────┐      ┌────────────────────┐
│                    │      │                   │      │                    │
│  Client Apps       │◄────►│  Shared Contracts │◄────►│  AI Router Service │
│  (UI Process)      │      │  (AIDL)           │      │  (Service Process) │
│                    │      │                   │      │                    │
└────────────────────┘      └───────────────────┘      └────────────────────┘
```

### Client Apps

Client applications (including `breeze-app-router-client`) are responsible for:

- Providing user interface
- Managing connection to the AI Router Service
- Sending requests and handling responses
- Implementing proper error handling and reconnection logic

For details on implementing a client, see the [Client Integration Guide](../breeze-app-router-client/docs/README.md).

### Shared Contracts

The `shared-contracts` module defines the communication interface between clients and the service:

- AIDL interfaces for service methods and callbacks
- Parcelable data models for request/response
- Configuration options for service initialization

For API details, see the [Shared Contracts API Documentation](../shared-contracts/docs/api.md).

### AI Router Service

The `breeze-app-router` service is responsible for:

- Managing AI capabilities through pluggable runners
- Handling client connections and requests
- Orchestrating AI tasks across different backends
- Managing resources and model loading

For service implementation details, see the [Router Service Documentation](../breeze-app-router/docs/README.md).

## Communication Flow

The communication between components follows this pattern:

```
┌──────────┐     ┌─────────────┐     ┌───────────────┐     ┌────────────┐
│          │     │             │     │               │     │            │
│   UI     │────►│  ViewModel  │────►│  Repository   │────►│  Service   │
│          │     │             │     │               │     │            │
└──────────┘     └─────────────┘     └───────────────┘     └────────────┘
     ▲                                                           │
     │                                                           │
     └───────────────────────────────────────────────────────────┘
                          Response Callback
```

1. User interacts with the UI
2. ViewModel processes the interaction and calls the Repository
3. Repository communicates with the Service via AIDL
4. Service processes the request and returns results via callback
5. Repository updates the ViewModel (typically via Flow)
6. ViewModel updates the UI state

## Security Model

The AI Router Service is protected by a signature-level permission, ensuring that only authorized applications can connect. This security model has several important aspects:

- **Permission Declaration**: The service declares a signature-level permission
- **Permission Enforcement**: The service checks this permission on bind attempts
- **Client Requirements**: Clients must declare the permission in their manifest
- **Signature Matching**: For production use, clients must be signed with the same key

## Error Handling

The architecture includes comprehensive error handling:

- **Connection Failures**: Clients implement reconnection strategies
- **Remote Exceptions**: All IPC calls are wrapped in try-catch blocks
- **Structured Errors**: AIResponse includes error fields for explicit error handling
- **Fallback Mechanisms**: The service implements runner fallbacks when primary runners fail

## Threading Model

To ensure responsive UIs and efficient processing:

- **Client-Side**: All IPC calls are performed on background threads (Dispatchers.IO)
- **Service-Side**: Long-running tasks are offloaded to dedicated coroutine scopes
- **Callback Handling**: Responses are received on binder threads and dispatched to appropriate threads

## Extension Points

The architecture provides several extension points:

- **New AI Capabilities**: Add new runners to the AI Router Service
- **Alternative Implementations**: Swap runner implementations without changing the API
- **Client Variations**: Create specialized clients for specific use cases

## Further Reading

- [Refactoring Plan](reference/refactoring_plan.md) - Detailed implementation plan
- [Router Service Architecture](../breeze-app-router/docs/ARCHITECTURE.md) - Service internal design
- [Client Integration Guide](../breeze-app-router-client/docs/README.md) - How to integrate with the service 