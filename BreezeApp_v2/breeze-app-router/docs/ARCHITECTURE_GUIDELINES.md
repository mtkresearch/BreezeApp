# Architecture Guidelines

## 🏗️ Clean Architecture Implementation

### Layer Structure
```
┌─────────────────────────────────────────┐
│                UI Layer                 │
│  Activities, ViewModels (MVVM)         │
├─────────────────────────────────────────┤
│            Application Layer            │
│  Use Cases, Service Coordinators       │
├─────────────────────────────────────────┤
│              Domain Layer               │
│  Entities, Interfaces, Business Rules  │
├─────────────────────────────────────────┤
│           Infrastructure Layer          │
│  Repositories, External APIs, Storage  │
└─────────────────────────────────────────┘
```

### MVVM + Use Case Pattern

**When to Use MVVM:**
- ✅ Activities with complex UI state
- ✅ Components that need reactive data
- ❌ Simple launcher activities (keep lightweight)
- ❌ Service classes (they're not UI)

**Use Case Guidelines:**
- One Use Case per business operation
- Use Cases should be stateless
- Inject dependencies via constructor
- Return domain models, not framework types

### Code Organization Rules

1. **Package by Feature, then by Layer**
   ```
   com.mtkresearch.breezeapp.router/
   ├── domain/           # Business logic
   ├── data/            # Data sources
   ├── core/            # Core router infrastructure
   ├── system/          # System integration
   ├── ui/              # Presentation layer
   └── injection/       # DI configuration
   ```

2. **Dependency Rules**
   - Domain layer has NO dependencies on other layers
   - Application layer depends only on Domain
   - Infrastructure layer implements Domain interfaces
   - UI layer depends on Application and Domain only

3. **Naming Conventions**
   - Use Cases: `VerbNounUseCase` (e.g., `ProcessChatRequestUseCase`)
   - ViewModels: `FeatureViewModel` (e.g., `RouterLauncherViewModel`)
   - Repositories: `NounRepository` (e.g., `ModelRepository`)
   - Domain Models: Clear business names (e.g., `ServiceState`, `InferenceRequest`)

### Testing Strategy

- **Unit Tests**: Domain models and Use Cases
- **Integration Tests**: Repository implementations
- **UI Tests**: Critical user flows only
- **Service Tests**: AIDL interface behavior

### Performance Guidelines

- Use `lazy` initialization for expensive objects
- Prefer `StateFlow` over `LiveData` for reactive state
- Keep ViewModels lightweight
- Use coroutines for async operations