# Git Submodules Guide

This project uses Git Submodules to manage sub-repositories, including the following submodules:

- `BreezeApp-engine`: BreezeApp engine core components
- `BreezeApp-client`: BreezeApp client components

## Initial Setup

### First-time clone with submodules
```bash
git clone --recursive https://github.com/mtkresearch/BreezeApp.git
```

### If you've already cloned the project, initialize submodules
```bash
git submodule init
git submodule update
```

## Daily Usage

### Update all submodules to latest version
```bash
git submodule update --remote
```

### Update specific submodule
```bash
git submodule update --remote BreezeApp-engine
git submodule update --remote BreezeApp-client
```

### Enter submodule directory for development
```bash
cd BreezeApp-engine
# Make changes and commit
git add .
git commit -m "Update BreezeApp-engine"
git push origin main
cd ..
# Update submodule reference in main project
git add BreezeApp-engine
git commit -m "Update BreezeApp-engine submodule"
```

### Switch submodule to specific branch or tag
```bash
cd BreezeApp-engine
git checkout <branch-or-tag>
cd ..
git add BreezeApp-engine
git commit -m "Switch BreezeApp-engine to <branch-or-tag>"
```

## Team Collaboration

### New team member joining the project
1. Clone main project: `git clone https://github.com/mtkresearch/BreezeApp.git`
2. Initialize submodules: `git submodule init && git submodule update`

### Push commits with submodule updates
```bash
# Ensure submodule updates are committed
git submodule update --remote
git add .
git commit -m "Update submodules"
git push
```

## Troubleshooting

### Submodule status issues
```bash
# Reset submodules to correct state
git submodule deinit -f BreezeApp-engine
git submodule deinit -f BreezeApp-client
git submodule update --init --recursive
```

### Clean up submodules
```bash
# Completely remove submodule (use with caution)
git submodule deinit -f BreezeApp-engine
git rm BreezeApp-engine
rm -rf .git/modules/BreezeApp-engine
git commit -m "Remove BreezeApp-engine submodule"
```

## Best Practices

1. **Regular Updates**: Run `git submodule update --remote` regularly to keep submodules up to date
2. **Explicit Commits**: After making changes in submodules, remember to commit submodule updates in the main project
3. **Version Control**: Use tags or specific commits to ensure submodule version consistency
4. **Documentation Sync**: Update related documentation when submodule APIs change 