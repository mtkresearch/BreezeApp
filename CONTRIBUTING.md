# Contributing to BreezeApp

First off, thank you for considering contributing to BreezeApp! Your contributions help make this project better.


## Branching Strategy

To ensure smooth collaboration and maintain a stable production version, we follow a Git Flow–style branching model. Here is an overview of each branch:

### 1. **Main Branches**

#### **main**
- **Purpose:** Contains the current production-ready code.
- **Usage:** Only thoroughly tested and stable code from `release` or `hotfix` branches is merged into `main`. This branch is tagged for official releases.

#### **develop**
- **Purpose:** Acts as the integration branch for all new development work.
- **Usage:** All feature and bug-fix branches should be created from and eventually merged back into `develop`. This branch is continuously updated and tested.

---

### 2. **Development and Feature Branches**

#### **feature/<description>**
- **Purpose:** Dedicated to developing new features or making significant changes.
- **Usage:** Create a feature branch from `develop` using a descriptive name (e.g., `feature/login-improvement`). Once the feature is complete and tested, open a pull request to merge it back into `develop`.

#### **fix/<description>** *(For minor, non-urgent bug fixes and enhancements)*
- **Purpose:** Handles small bug fixes and minor improvements that do not require an urgent production hotfix.
- **Usage:** Create a branch from `develop` with a descriptive name, e.g., `fix/ui-alignment`. Once resolved, merge it back into `develop`.

#### **chore/<description>** *(For non-functional updates such as documentation or CI/CD changes)*
- **Purpose:** Used for updates that do not involve code changes, such as documentation fixes, dependency updates, or CI/CD workflow improvements.
- **Usage:** Create a branch from `develop` with a descriptive name, e.g., `chore/update-readme`. Once complete, merge it back into `develop`.

---

### 3. **Release and Hotfix Branches**

#### **release/vX.X.X**
- **Purpose:** Prepares the codebase for a production release.
- **Usage:** When `develop` reaches a stable state and features are frozen, create a release branch for final testing, documentation updates, and minor bug fixes. After stabilization, merge this branch into `main` and tag it with the release version.

#### **hotfix/<description>**
- **Purpose:** Addresses urgent issues in the production environment.
- **Usage:** Create a hotfix branch from `main` when a critical bug is discovered. After applying the fix, merge the hotfix branch back into both `main` and `develop` to ensure consistency across branches.

---

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

---

## How to Contribute

### Reporting Bugs
- Check existing issues before submitting a new one.
- Provide a clear title and detailed description.
- Include exact steps to reproduce the issue.
- Attach screenshots if applicable.
- Provide environment details (Android version, device model, etc.).

### Suggesting Enhancements
- Describe the feature request clearly.
- Explain its benefit to the project.
- Include potential use cases and considerations.

---

## Documentation Guidelines
- Update the README.md as needed.
- Maintain clear API documentation.
- Add inline comments where necessary.
- Keep the changelog up-to-date.

---

## Code of Conduct
By contributing to this project, you agree to:
- Use welcoming and inclusive language.
- Be respectful of different viewpoints.
- Accept constructive feedback gracefully.
- Focus on improving the community.
- Show empathy towards others.

---

## Need Help?
Feel free to open an issue with your question or contact the maintainers directly.

Thank you for your contributions! 🚀
