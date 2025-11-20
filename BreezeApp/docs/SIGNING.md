# App Signing Configuration

Quick reference for keystore setup and signing configuration.

## First-Time Setup

**Run this once to configure your keystore:**

```bash
./setup-keystore.sh
```

Follow the prompts to configure your production keystore at `~/Resource/android_key_mr`.

## File Structure

```
BreezeApp/
├── keystore.properties          # Your credentials (NOT in Git)
├── keystore.properties.template # Template file (in Git)
├── setup-keystore.sh           # Interactive setup script
├── KEYSTORE_SETUP.md           # Detailed documentation
└── app/build.gradle.kts        # Build configuration with signing
```

## Configuration

### keystore.properties (Local Development)

```properties
storeFile=/Users/muximacmini/Resource/android_key_mr
storePassword=YOUR_PASSWORD
keyAlias=YOUR_ALIAS
keyPassword=YOUR_KEY_PASSWORD
```

### Environment Variables (CI/CD)

```bash
export KEYSTORE_FILE="$HOME/Resource/android_key_mr"
export KEYSTORE_PASSWORD="your_password"
export KEY_ALIAS="your_alias"
export KEY_PASSWORD="your_key_password"
```

## Signing Configurations

| Build Type | Keystore | Usage |
|------------|----------|-------|
| **Debug** | `~/.android/debug.keystore` | Development, testing |
| **Release** | `~/Resource/android_key_mr` | Production, Play Store |

## Quick Commands

```bash
# Setup keystore (first time)
./setup-keystore.sh

# Build signed AAB for Play Store
./release-build.sh -b aab

# Build signed APK for testing
./release-build.sh -b apk

# Build both
./release-build.sh

# Verify keystore
keytool -list -keystore ~/Resource/android_key_mr

# Check AAB signature
jarsigner -verify -verbose app/release/*.aab
```

## Security Checklist

- [x] `keystore.properties` is in `.gitignore`
- [x] Keystore file is backed up securely
- [x] Passwords stored in password manager
- [x] CI/CD uses environment variables (not committed files)
- [x] Different keystores for debug and release

## Troubleshooting

| Error | Solution |
|-------|----------|
| Keystore not found | Check path: `ls ~/Resource/android_key_mr` |
| Wrong password | Run `./setup-keystore.sh` again |
| Wrong alias | Run `keytool -list -keystore ~/Resource/android_key_mr` |
| Missing config | Create `keystore.properties` or set env vars |

## More Information

- [KEYSTORE_SETUP.md](KEYSTORE_SETUP.md) - Detailed setup guide
- [RELEASE_BUILD.md](RELEASE_BUILD.md) - Build script documentation
- [Android App Signing](https://developer.android.com/studio/publish/app-signing) - Official docs
