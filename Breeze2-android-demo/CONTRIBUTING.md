# Contributing to Android AI Assistant

First off, thank you for considering contributing to Android AI Assistant! It's people like you that make this project better.

## Code of Conduct

By participating in this project, you are expected to uphold our Code of Conduct:

- Use welcoming and inclusive language
- Be respectful of differing viewpoints and experiences
- Gracefully accept constructive criticism
- Focus on what is best for the community
- Show empathy towards other community members

## How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check the issue list as you might find out that you don't need to create one. When you are creating a bug report, please include as many details as possible:

* Use a clear and descriptive title
* Describe the exact steps which reproduce the problem
* Provide specific examples to demonstrate the steps
* Describe the behavior you observed after following the steps
* Explain which behavior you expected to see instead and why
* Include screenshots if possible
* Include your environment details:
  - Android version
  - Device model
  - RAM size
  - Model versions being used

### Suggesting Enhancements

If you have a suggestion for the project, we'd love to hear it. Enhancement suggestions are tracked as GitHub issues. When creating an enhancement suggestion, please include:

* A clear and descriptive title
* A detailed description of the proposed enhancement
* Examples of how the enhancement would be used
* Any potential drawbacks or considerations

### Pull Requests

1. Fork the repo and create your branch from `main`
2. If you've added code that should be tested, add tests
3. If you've changed APIs, update the documentation
4. Ensure the test suite passes
5. Make sure your code follows the existing style
6. Issue that pull request!

### Development Process

1. Clone the repository
```bash
git clone https://github.com/muxi1998/GAI-android.git
```

2. Create a branch
```bash
git checkout -b feature/your-feature-name
```

3. Make your changes
   - Follow the coding style of the project
   - Write good commit messages
   - Keep your changes focused and atomic

4. Test your changes
   - Ensure all tests pass
   - Add new tests if needed
   - Test on different Android devices if possible

5. Push your changes
```bash
git push origin feature/your-feature-name
```

### Coding Style

- Follow Android/Java standard naming conventions
- Use meaningful variable and function names
- Write comments for complex logic
- Keep functions focused and concise
- Use consistent indentation (4 spaces)
- Add documentation for public APIs

### Documentation

- Update README.md if needed
- Document new features
- Keep API documentation up-to-date
- Include comments in your code
- Update the changelog

## Project Structure

Please maintain the existing project structure:

```
app/src/main/
├── java/com/mtkresearch/gai_android/
│   ├── service/                # AI engine services
│   ├── utils/                  # Utility classes
│   ├── AudioChatActivity.java  # Audio chat page  
│   ├── ChatActivity.java       # Chatbot page
│   └── MainActivity.java       # Home page
├── cpp/                        # Native code
├── assets/                     # Model files
└── res/                        # Android resources
```

## Questions?

Feel free to open an issue with your question or contact the maintainers directly.

## License

By contributing, you agree that your contributions will be licensed under the same Apache License 2.0 that covers the project. 