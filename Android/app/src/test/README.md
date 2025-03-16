# Unit Tests for BreezeApp

This directory contains unit tests for the various components of the BreezeApp.

## Test Organization

Tests are organized in a structure mirroring the main app's package structure:

- `com.mtkresearch.breezeapp.ui` - Tests for UI components like activities, fragments, and adapters
- `com.mtkresearch.breezeapp.message` - Tests for message-related models and utility classes
- `com.mtkresearch.breezeapp.data` - Tests for data repositories and storage
- `com.mtkresearch.breezeapp.util` - Tests for utility classes
- `com.mtkresearch.breezeapp.features` - Tests for specific app features

## Current Test Status

The current test implementations included in this repository are structured but require additional work to resolve compilation issues:

1. **Missing Dependencies**: Some test dependencies like Robolectric and AndroidX Test libraries need to be properly configured in the build.gradle.kts file

2. **Implementation Gaps**: Test implementations might need access to actual application classes they're testing, which may require exposing certain functionality or creating test alternatives

3. **Mocks & Stubs**: Each test includes appropriate mock objects setup, but some need implementation details to match your application's actual classes

4. **Resolution Required**: To complete test implementation, you'll need to:
   - Ensure all necessary test dependencies are added to build.gradle.kts
   - Update test class implementations to match your application's architecture
   - Expose any required internal functionality for testing via interfaces or test helper methods

## Running Tests

### From Android Studio

1. Open the project in Android Studio
2. Navigate to a test file
3. Right-click and select "Run Test" or click the green play button in the gutter
4. To run all tests, right-click on the `test` directory and select "Run Tests"

### From Command Line

Run all tests:
```
./gradlew test
```

Run tests for a specific module:
```
./gradlew app:testDebugUnitTest
```

Run a specific test class:
```
./gradlew app:testDebugUnitTest --tests "com.mtkresearch.breezeapp.ui.chat.DrawerLayoutTest"
```

## Test Dependencies

The project uses the following test libraries:

- JUnit 4 - Core testing framework
- Mockito - Mocking framework
- Mockito-Kotlin - Kotlin-friendly extensions for Mockito
- Robolectric - Android framework simulation for unit tests (needs to be added)
- AndroidX Test - Android testing utilities (needs to be added)
- Turbine - Testing utilities for Flow
- Coroutines Test - Testing utilities for coroutines

## Writing New Tests

When writing new tests:

1. Follow the AAA pattern (Arrange, Act, Assert)
2. Use descriptive test method names with back-ticks (e.g., `` `test description` ``)
3. Mock external dependencies
4. Test one behavior per test method
5. Prefer unit tests over integration tests where appropriate

## Coverage

To generate test coverage reports:

```
./gradlew app:testDebugUnitTestCoverage
```

The report will be generated in `app/build/reports/coverage/test/debug/` 