# Contributing to BreezeApp

Thank you for your interest in contributing to BreezeApp! This document provides guidelines and instructions for contributing to the project.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Workflow](#development-workflow)
- [Coding Standards](#coding-standards)
- [Documentation Guidelines](#documentation-guidelines)
- [Pull Request Process](#pull-request-process)
- [Issue Reporting](#issue-reporting)

## Code of Conduct

We expect all contributors to follow our [Code of Conduct](CODE_OF_CONDUCT.md). Please be respectful and constructive in all interactions.

## Getting Started

1. **Fork the repository** on GitHub
2. **Clone your fork**:
   ```bash
   git clone https://github.com/your-username/BreezeApp.git
   cd BreezeApp
   ```
3. **Set up the development environment** following the [Quick Start Guide](quick-start.md)
4. **Create a new branch** for your feature or bugfix:
   ```bash
   git checkout -b feature/your-feature-name
   ```

## Development Workflow

We follow a modular development approach with clear separation of concerns:

1. **Identify the module** your change belongs to:
   - `breeze-app-router` - AI Router Service
   - `shared-contracts` - AIDL interfaces and data models
   - `breeze-app-router-client` - Reference client implementation

2. **Make your changes** following the [Coding Standards](#coding-standards)

3. **Write tests** for your changes:
   - Unit tests for business logic
   - Integration tests for component interactions
   - UI tests for user interface changes

4. **Update documentation** as needed:
   - Code comments (KDoc/Javadoc)
   - Module-specific documentation
   - High-level architecture documentation if applicable

## Coding Standards

We follow the official [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) with the following additions:

### General Guidelines

- Use meaningful variable and function names
- Keep functions small and focused on a single responsibility
- Prefer immutability when possible
- Use coroutines for asynchronous operations
- Follow MVVM + Clean Architecture patterns

### Android-Specific Guidelines

- Use ViewBinding for view access
- Use StateFlow/SharedFlow for reactive UI updates
- Handle configuration changes appropriately
- Perform all long-running operations off the main thread
- Use dependency injection for testability

### AIDL Guidelines

- Keep interfaces backward compatible when possible
- Document all interface methods and parameters
- Handle RemoteExceptions in client code
- Use appropriate thread handling for callbacks

## Documentation Guidelines

Documentation is a critical part of the project. Please follow these guidelines:

### Documentation Structure

- **High-level concepts** go in the main `/docs` directory
- **Module-specific details** go in the module's `/docs` directory
- **API references** should be generated from code comments

### Documentation Standards

- Use clear, concise language
- Include code examples for complex concepts
- Use diagrams for visual explanation when helpful
- Link to related documentation rather than duplicating content
- Keep documentation up-to-date with code changes

### Documentation Files

Each module should have at least:
- A `README.md` explaining the module's purpose and usage
- An `ARCHITECTURE.md` for internal design details (if applicable)
- API documentation generated from code comments

## Pull Request Process

1. **Ensure your code passes all tests**:
   ```bash
   ./gradlew test
   ./gradlew connectedAndroidTest
   ```

2. **Update documentation** to reflect any changes

3. **Submit a pull request** with a clear title and description:
   - What changes were made
   - Why the changes were made
   - How the changes were implemented
   - Any testing considerations

4. **Address review feedback** promptly

5. **Once approved**, your pull request will be merged by a maintainer

## Issue Reporting

If you find a bug or have a feature request:

1. Check if the issue already exists in the [Issue Tracker](https://github.com/your-org/BreezeApp/issues)
2. If not, create a new issue with:
   - A clear title
   - A detailed description
   - Steps to reproduce (for bugs)
   - Expected and actual behavior (for bugs)
   - Screenshots if applicable

Thank you for contributing to BreezeApp! 