# Release Build Script

Automated release build script for BreezeApp that handles version management, APK, and AAB (Android App Bundle) generation.

## Features

- 🔢 **Auto Version Increment**: Automatically increments version code and version name
- 📦 **Semantic Versioning**: Supports major, minor, and patch version increments
- 🎁 **Multiple Build Types**: Builds APK, AAB, or both formats
- 📱 **Play Store Ready**: Generates AAB (Android App Bundle) for Google Play Store
- 🔐 **Safe Updates**: Creates backups and restores on failure
- 🎨 **Colored Output**: User-friendly colored console output
- ✅ **User Confirmation**: Confirms version changes before building
- 📁 **Versioned Outputs**: Saves release files with version in filename
- 🔄 **Git Integration**: Provides git commands for version commits and tags

## Usage

### Basic Usage

```bash
# From BreezeApp/ directory
cd BreezeApp
./release-build.sh [OPTIONS] [VERSION_TYPE]
```

### Options

- **-v, --version VERSION**: Set version name manually (e.g., 2.1.0)
- **-c, --code CODE**: Set version code manually (e.g., 15)
- **-b, --build TYPE**: Build type - `apk`, `aab`, or `both` [default: both]
- **-h, --help**: Show help message

### Build Types

- **apk**: Build APK only (for direct installation and testing)
- **aab**: Build AAB only (Android App Bundle for Google Play Store)
- **both**: Build both APK and AAB [default]

### Version Types (Auto-Increment)

- **patch** (default): Increments patch version (1.0.0 → 1.0.1)
- **minor**: Increments minor version (1.0.0 → 1.1.0)
- **major**: Increments major version (1.0.0 → 2.0.0)

### Examples

#### Auto-Increment Mode (Default - builds both APK and AAB)

```bash
# Increment patch version (default - builds both APK and AAB)
./release-build.sh
# or
./release-build.sh patch

# Increment minor version
./release-build.sh minor

# Increment major version
./release-build.sh major
```

#### Build Specific Format

```bash
# Build AAB only for Play Store submission
./release-build.sh -b aab

# Build APK only for testing/distribution
./release-build.sh -b apk

# Build both formats (default)
./release-build.sh --build both
```

#### Manual Version Mode

```bash
# Set version manually, auto-increment code
./release-build.sh -v 2.5.0

# Set both version and code manually
./release-build.sh -v 2.5.0 -c 25
./release-build.sh --version 3.0.0 --code 30
```

#### Combined Options

```bash
# Manual version + AAB only (for Play Store)
./release-build.sh -v 2.0.0 -b aab

# Minor increment + APK only (for testing)
./release-build.sh minor -b apk

# Major version + both formats
./release-build.sh -v 3.0.0 --build both

# Show help
./release-build.sh --help
```

## What It Does

1. **Reads Current Version**: Extracts current versionCode and versionName from `app/build.gradle.kts`
2. **Determines New Version**: Either auto-increments or uses manual values
3. **Validates Input**: Validates manual version format (X.Y.Z) and version code (integer)
4. **Shows Preview**: Displays current and new versions for confirmation
5. **Updates build.gradle.kts**: Modifies version values (with backup)
6. **Cleans Build**: Runs `./gradlew clean`
7. **Builds Release Files**: Based on build type:
   - APK: Runs `./gradlew assembleRelease`
   - AAB: Runs `./gradlew bundleRelease`
   - Both: Runs both commands
8. **Saves Versioned Files**: Copies outputs to `app/release/` with version in filename
9. **Provides Git Commands**: Shows commands for committing and tagging

## Output

### Default Build (both)

The script generates four files:

**APK Files:**
1. `app/build/outputs/apk/release/app-release.apk`
2. `app/release/BreezeApp-v[VERSION_NAME]-[VERSION_CODE].apk`

**AAB Files (for Play Store):**
3. `app/build/outputs/bundle/release/app-release.aab`
4. `app/release/BreezeApp-v[VERSION_NAME]-[VERSION_CODE].aab`

Example:
- `app/release/BreezeApp-v2.0.0-31.apk`
- `app/release/BreezeApp-v2.0.0-31.aab`

### Build Type Comparison

| Build Type | Use Case | Output Files | Play Store |
|------------|----------|--------------|------------|
| `apk` | Testing, direct installation, distribution outside Play Store | APK only | ❌ |
| `aab` | Google Play Store submission | AAB only | ✅ |
| `both` | Complete release (Play Store + testing) | APK + AAB | ✅ |

### Why AAB (Android App Bundle)?

**Android App Bundle (AAB)** is Google's recommended publishing format for the Play Store:

✅ **Smaller Downloads**: Play Store generates optimized APKs for each device configuration
✅ **Better Performance**: Users download only the code and resources they need
✅ **Required for Play Store**: New apps must use AAB format since August 2021
✅ **Dynamic Delivery**: Supports on-demand and instant app features
✅ **Easier Updates**: Reduces download size for app updates

**Note**: AAB files cannot be directly installed on devices. They must be uploaded to the Play Store, which then generates optimized APKs for distribution.

## Keystore Configuration (Required for Release Builds)

Before building release APK/AAB files, you must configure your production keystore for app signing.

### Quick Setup

Run the interactive setup script:

```bash
./setup-keystore.sh
```

This script will:
1. Verify your keystore file exists
2. Validate keystore password
3. List available key aliases
4. Verify key credentials
5. Create `keystore.properties` with your configuration

### Manual Setup

Alternatively, create `keystore.properties` manually:

```bash
cp keystore.properties.template keystore.properties
# Edit with your actual values
```

Example `keystore.properties`:
```properties
storeFile=/Users/muximacmini/Resource/android_key_mr
storePassword=your_keystore_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

### Security Notes

- ✅ `keystore.properties` is in `.gitignore` (never committed)
- ✅ Alternative: Use environment variables for CI/CD
- ❌ Never commit keystore credentials to Git
- ⚠️ Back up your keystore file securely!

For detailed information, see [KEYSTORE_SETUP.md](KEYSTORE_SETUP.md).

## Version Update Flow

### Auto-Increment Examples

#### Patch Increment (Default)
```
Current: 1.0.0 (code: 1)
   ↓
New: 1.0.1 (code: 2)
```

#### Minor Increment
```
Current: 1.0.5 (code: 6)
   ↓
New: 1.1.0 (code: 7)
```

#### Major Increment
```
Current: 1.2.3 (code: 10)
   ↓
New: 2.0.0 (code: 11)
```

### Manual Version Examples

#### Manual Version Only
```
Command: ./release-build.sh -v 2.5.0
Current: 1.2.3 (code: 10)
   ↓
New: 2.5.0 (code: 11)  ← code auto-incremented
```

#### Manual Version and Code
```
Command: ./release-build.sh -v 2.5.0 -c 25
Current: 1.2.3 (code: 10)
   ↓
New: 2.5.0 (code: 25)  ← both manually set
```

## Git Workflow

After successful build, the script suggests git commands:

```bash
# Commit version change
git add app/build.gradle.kts
git commit -m "chore: bump version to 1.0.1 (2)"

# Create version tag
git tag -a v1.0.1 -m "Release version 1.0.1"

# Push to remote
git push origin main
git push origin v1.0.1
```

## Safety Features

- ✅ **Backup Creation**: Creates `app/build.gradle.kts.backup` before modifications
- ✅ **Auto Restore**: Restores backup if build fails
- ✅ **User Confirmation**: Requires confirmation before version update
- ✅ **Error Handling**: Exits on error with meaningful messages

## Troubleshooting

### Script Won't Execute

```bash
# Make script executable
chmod +x release-build.sh
```

### Build Fails

- Check that you're in the `BreezeApp/` directory
- Ensure Gradle wrapper is executable: `chmod +x gradlew`
- Verify Android SDK is properly configured
- Check for compilation errors: `./check-compile.sh`

### Keystore/Signing Errors

**"Keystore file does not exist"**
```bash
# Verify your keystore file path
ls -la ~/Resource/android_key_mr
```

**"Incorrect keystore password"**
- Run `./setup-keystore.sh` to reconfigure
- Or verify password manually: `keytool -list -keystore ~/Resource/android_key_mr`

**"Cannot recover key"**
- Check key alias is correct in `keystore.properties`
- Verify key password matches the key (not keystore) password

**Missing `keystore.properties`**
```bash
# Run the setup script
./setup-keystore.sh
```

### Lint Errors During Release Build

**Why does this happen?**

Android Studio and command-line Gradle builds have different default lint configurations:
- **Android Studio**: Often skips or downgrades lint checks for faster builds
- **Command-line Gradle**: Runs stricter lint checks (`lintVitalRelease` task) that can fail the build

**Solution:**

The project's `app/build.gradle.kts` includes a lint configuration block that:
```kotlin
lint {
    abortOnError = false          // Allows build to complete with warnings
    checkReleaseBuilds = true     // Still creates lint reports for review
    disable += setOf("ResAuto")   // Ignores known false positives
}
```

This ensures command-line builds match Android Studio's behavior while still generating lint reports for code quality review.

### Version Not Updated

If build fails, the script automatically restores the original `build.gradle.kts` from backup.

## Integration with CI/CD

The script can be integrated into CI/CD pipelines:

```yaml
# Example: GitHub Actions
- name: Build Release APK
  run: |
    cd BreezeApp
    echo "y" | ./release-build.sh patch
```

## Related Commands

- **Check compilation**: `./check-compile.sh`
- **Run tests**: `./test-scripts/run-tests.sh`
- **Manual build**: `./gradlew assembleRelease`
- **Clean build**: `./gradlew clean build`

## Notes

- Version code always increments by 1, regardless of version type
- Version name follows semantic versioning (MAJOR.MINOR.PATCH)
- The script uses the signing config defined in `build.gradle.kts`
- APKs are signed with the debug keystore (as per current configuration)
