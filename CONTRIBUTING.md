# Contributing to breeze-app

First off, thank you for considering contributing to breeze-app! It's people like you that make this project better.

## Branch Descriptions

To ensure smooth collaboration and maintain a stable production version, we follow a Git Flow–style branching model. Here is a detailed overview of each branch:

- **develop**  
  - **Purpose:** Acts as the integration branch for all new development work.  
  - **Usage:** All feature and bug-fix branches should be created from and eventually merged back into `develop`. This branch is continuously updated and tested.

- **feature/<description>**  
  - **Purpose:** Dedicated to developing new features or making significant changes.  
  - **Usage:** Create a feature branch from `develop` using a descriptive name (e.g., `feature/login-improvement`). Once the feature is complete and tested, open a pull request to merge it back into `develop`.

- **release/vX.X.X**  
  - **Purpose:** Prepares the codebase for a production release.  
  - **Usage:** When `develop` reaches a stable state and features are frozen, create a release branch for final testing, documentation updates, and minor bug fixes. After stabilization, merge this branch into `main` and tag it with the release version.

- **main**  
  - **Purpose:** Contains the current production-ready code.  
  - **Usage:** Only thoroughly tested and stable code from release (or hotfix) branches is merged into `main`. This branch is tagged for official releases (e.g., on the Play Store).

- **hotfix/<description>**  
  - **Purpose:** Addresses urgent issues in the production environment.  
  - **Usage:** Create a hotfix branch from `main` when a critical bug is discovered. After applying the fix, merge the hotfix branch back into both `main` and `develop` to ensure consistency across branches.

## Code of Conduct

By participating in this project, you are expected to uphold our Code of Conduct:

- Use welcoming and inclusive language
- Be respectful of differing viewpoints and experiences
- Gracefully accept constructive criticism
- Focus on what is best for the community
- Show empathy towards other community members

## How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check the issue list as you might find that you don't need to create one. When creating a bug report, please include as many details as possible:

- Use a clear and descriptive title
- Describe the exact steps that reproduce the problem
- Provide specific examples to demonstrate the steps
- Describe the behavior you observed after following the steps
- Explain which behavior you expected and why
- Include screenshots if possible
- Include your environment details:
  - Android version
  - Device model
  - RAM size
  - Model versions being used

### Suggesting Enhancements

If you have a suggestion for the project, we'd love to hear it. Enhancement suggestions are tracked as GitHub issues. When creating an enhancement suggestion, please include:

- A clear and descriptive title
- A detailed description of the proposed enhancement
- Examples of how the enhancement would be used
- Any potential drawbacks or considerations

## Branching Strategy

We follow a Git Flow–style branching model to ensure smooth collaboration and maintain a stable production version on the Play Store. The key branches are:

- **develop**  
  This is the integration branch where all new features and bug fixes are merged. New work should branch off from here.

- **feature/<description>**  
  Create a feature branch from `develop` for any new features or significant changes. Use descriptive names (e.g., `feature/login-improvement`).

- **release/vX.X.X**  
  Once `develop` reaches a stable state and features are frozen, a release branch is created. This branch is used for final testing, bug fixes, and documentation updates before merging into production.

- **main**  
  This branch always reflects the current production-ready version. Only stable, tested code from release (or hotfix) branches should be merged here, and it is tagged for releases on the Play Store.

- **hotfix/<description>**  
  For urgent production bugs, create a hotfix branch from `main`. After the fix, merge it back into both `main` and `develop` to ensure consistency.

## Pull Requests

When you're ready to contribute your code:

1. **Fork the Repository and Create Your Branch**
   - Fork the repo.
   - **Important:** Create your branch from the `develop` branch using a naming convention such as `feature/your-feature-name` or `hotfix/description`.

2. **Develop and Test Your Changes**
   - If you've added code that should be tested, add appropriate tests.
   - If you’ve updated or changed APIs, update the documentation accordingly.
   - Ensure that the test suite passes locally.

3. **Submit Your Pull Request**
   - Once your changes are complete, push your branch and open a pull request against the `develop` branch.
   - Describe your changes clearly and reference any related issues.
   - Your pull request will undergo code review and automated testing before merging.

## Development Process

1. **Clone the Repository**
   ```bash
   git clone https://github.com/mtkresearch/Breeze2-android-demo.git
   cd Breeze2-android-demo
   ```

2. Create a Branch from Develop
   ```bash
   git checkout develop
   git checkout -b feature/your-feature-name
   ```
3. Make Your Changes
   - Follow the project's coding style.
   - Write clear commit messages.
   - Keep your commits focused and atomic.

4. Test Your Changes
   - Run all tests to ensure nothing is broken.
   - Add new tests if necessary.
   - Test on different Android devices if possible.

5. Push Your Changes
   ```bash
   git push origin feature/your-feature-name
   ```

## Documentation
- Update the README.md if necessary.
- Document new features clearly.
- Keep API documentation up-to-date.
- Include inline comments in your code.
- Update the changelog as needed.

## Questions?
Feel free to open an issue with your question or contact the maintainers directly.

