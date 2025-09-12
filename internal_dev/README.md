# Internal Development Documentation

This folder contains internal development documents that are tracked locally but not pushed to the remote repository.

## Purpose
- **Local Development**: Documents are tracked by git for local version control
- **Not Remote**: Contents are not pushed to remote repositories (configured via .gitignore)
- **Internal Use**: Development specs, technical architectures, and refactoring suggestions

## Current Documents
- `BreezeApp_iOS_Product_Specification.md` - iOS product specifications and requirements
- `BreezeApp_iOS_Technical_Architecture.md` - iOS technical architecture documentation  
- `kotlin_arch.md` - Kotlin architecture guidelines and patterns
- `overview.md` - BreezeApp architecture overview and design principles
- `recommended_structure.md` - Recommended project structure for BreezeApp Kotlin development
- `refactoring_suggestions.md` - Code refactoring suggestions and improvements

## Usage
- Add any internal development documents here
- They will be tracked locally for version control
- They won't be pushed to remote repositories
- Safe for confidential or work-in-progress documentation

## Git Configuration

### Setup (Run once after cloning)
```bash
# Navigate to project root
cd /path/to/BreezeApp

# Make internal documents assume-unchanged (won't be pushed to remote)
git update-index --assume-unchanged internal_dev/BreezeApp_iOS_Product_Specification.md
git update-index --assume-unchanged internal_dev/BreezeApp_iOS_Technical_Architecture.md
git update-index --assume-unchanged internal_dev/kotlin_arch.md
git update-index --assume-unchanged internal_dev/overview.md
git update-index --assume-unchanged internal_dev/recommended_structure.md
git update-index --assume-unchanged internal_dev/refactoring_suggestions.md
```

### Adding New Internal Documents
```bash
# After adding a new document to internal_dev/
git add internal_dev/your_new_document.md
git update-index --assume-unchanged internal_dev/your_new_document.md
```

### Reset (if you want to push changes)
```bash
# To re-enable tracking for remote push (use carefully)
git update-index --no-assume-unchanged internal_dev/filename.md
```

### Check Status
```bash
# List files marked as assume-unchanged
git ls-files -v | grep '^h'
```