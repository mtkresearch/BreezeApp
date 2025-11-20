# Keystore Setup for Release Builds

This document explains how to configure your keystore for signing release builds (APK and AAB).

## Overview

Release builds require proper signing with a production keystore for:
- **Play Store uploads**: AAB files must be signed with your production keystore
- **App updates**: Must use the same keystore to update existing installations
- **Security**: Verifies app authenticity

## Quick Setup

### Option 1: Using keystore.properties (Recommended for Local Development)

1. **Copy the template file**:
   ```bash
   cp keystore.properties.template keystore.properties
   ```

2. **Edit `keystore.properties`** with your actual values:
   ```properties
   storeFile=/Users/muximacmini/Resource/android_key_mr
   storePassword=YOUR_ACTUAL_PASSWORD
   keyAlias=YOUR_ACTUAL_ALIAS
   keyPassword=YOUR_ACTUAL_PASSWORD
   ```

3. **Test the configuration**:
   ```bash
   ./gradlew bundleRelease
   ```

### Option 2: Using Environment Variables (Recommended for CI/CD)

Set these environment variables before building:

```bash
export KEYSTORE_FILE="$HOME/Resource/android_key_mr"
export KEYSTORE_PASSWORD="your_keystore_password"
export KEY_ALIAS="your_key_alias"
export KEY_PASSWORD="your_key_password"

# Then build
./release-build.sh -b aab
```

## Finding Your Keystore Information

### Check Key Alias

To list all aliases in your keystore:

```bash
keytool -list -v -keystore ~/Resource/android_key_mr
# Enter keystore password when prompted
# Look for "Alias name:" in the output
```

### Verify Keystore

To verify your keystore is valid:

```bash
keytool -list -keystore ~/Resource/android_key_mr
```

## Build Configuration

The project is configured to:
- **Debug builds**: Use Android debug keystore (`~/.android/debug.keystore`)
- **Release builds**: Use production keystore from `keystore.properties` or environment variables

### Signing Config Priority

1. `keystore.properties` file (if exists)
2. Environment variables
3. Default path: `~/Resource/android_key_mr`

## Security Best Practices

### ✅ DO:
- Keep `keystore.properties` in `.gitignore` (already configured)
- Use environment variables in CI/CD pipelines
- Store keystore password in secure password manager
- Back up your keystore file securely
- Use different keystores for debug and release

### ❌ DON'T:
- Never commit `keystore.properties` to Git
- Never commit keystore files (`.jks`, `.keystore`) to Git
- Never share keystore passwords in plain text
- Never use debug keystore for Play Store releases

## Troubleshooting

### Error: "Keystore file does not exist"

**Solution**: Check the `storeFile` path in `keystore.properties`:
```bash
# Verify the file exists
ls -la ~/Resource/android_key_mr
```

### Error: "Incorrect keystore password"

**Solution**: Verify your password:
```bash
keytool -list -keystore ~/Resource/android_key_mr
# It will prompt for password - use this to verify
```

### Error: "Cannot recover key"

**Solution**: Check your key alias and key password:
```bash
keytool -list -v -keystore ~/Resource/android_key_mr
# Look for the correct alias name
```

### Build works in Android Studio but fails in command line

**Solution**: Android Studio may use different credentials. Ensure:
1. `keystore.properties` is properly configured
2. File paths use absolute paths
3. Environment variables are set in your terminal session

## Play Store Upload

### Uploading AAB to Play Store

1. **Build the signed AAB**:
   ```bash
   ./release-build.sh -b aab
   ```

2. **Verify the AAB is signed**:
   ```bash
   # Check the generated AAB
   ls -lh app/release/*.aab

   # Verify signing (optional)
   jarsigner -verify -verbose -certs app/release/BreezeApp-v*.aab
   ```

3. **Upload to Play Console**:
   - Go to [Google Play Console](https://play.google.com/console)
   - Navigate to your app → Release → Production
   - Create new release and upload the AAB file

### First-time Play Store Setup

If this is your first upload to Play Store:
1. You must create an app in Play Console first
2. Complete store listing and content rating
3. Upload your signed AAB
4. Google Play will generate optimized APKs for users

## CI/CD Integration

### GitHub Actions Example

```yaml
- name: Build Release AAB
  env:
    KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
    KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
    KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
  run: |
    cd BreezeApp
    echo "y" | ./release-build.sh -b aab
```

### GitLab CI Example

```yaml
build:release:
  script:
    - cd BreezeApp
    - echo "y" | ./release-build.sh -b aab
  variables:
    KEYSTORE_PASSWORD: $KEYSTORE_PASSWORD
    KEY_ALIAS: $KEY_ALIAS
    KEY_PASSWORD: $KEY_PASSWORD
```

## Additional Resources

- [Android App Signing Documentation](https://developer.android.com/studio/publish/app-signing)
- [Android App Bundle Guide](https://developer.android.com/guide/app-bundle)
- [Play Console Help](https://support.google.com/googleplay/android-developer)
