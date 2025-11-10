# Quick Start: Building Release APK/AAB

**One-time setup + build in under 2 minutes!**

## Step 1: Configure Keystore (One-time, ~1 minute)

```bash
cd BreezeApp
./setup-keystore.sh
```

**What you'll need:**
- Keystore password
- Key alias (shown in setup script)
- Key password

## Step 2: Build Release (~1 minute)

### For Play Store Upload (AAB)
```bash
./release-build.sh -b aab
```

### For Testing (APK)
```bash
./release-build.sh -b apk
```

### For Both
```bash
./release-build.sh
```

## Output Files

Find your builds in `app/release/`:

```
app/release/
├── BreezeApp-v2.0.0-31.aab  ← Upload to Play Store
└── BreezeApp-v2.0.0-31.apk  ← Install on device
```

## Common Commands

```bash
# Patch version: 2.0.0 → 2.0.1
./release-build.sh

# Minor version: 2.0.1 → 2.1.0
./release-build.sh minor

# Major version: 2.1.0 → 3.0.0
./release-build.sh major

# Set specific version
./release-build.sh -v 3.5.0

# AAB only for Play Store
./release-build.sh -b aab

# Show all options
./release-build.sh --help
```

## What Gets Updated

When you run the build script:
1. ✅ Version code auto-increments (31 → 32)
2. ✅ Version name updates (2.0.0 → 2.0.1)
3. ✅ AAB/APK built and signed
4. ✅ Files saved to `app/release/`
5. ✅ Git commit commands suggested

## Next Steps

### Upload to Play Store

1. Go to [Google Play Console](https://play.google.com/console)
2. Navigate to your app → Production
3. Create new release
4. Upload the `.aab` file from `app/release/`
5. Complete the release notes and submit

### Commit Version Change

```bash
git add app/build.gradle.kts
git commit -m "chore: bump version to 2.0.1 (32)"
git tag -a v2.0.1 -m "Release version 2.0.1"
git push origin main --tags
```

## Troubleshooting

### First Time Building?

1. **Run keystore setup**: `./setup-keystore.sh`
2. **Verify keystore exists**: `ls ~/Resource/android_key_mr`
3. **Try a test build**: `./release-build.sh -b apk`

### Build Failed?

```bash
# Check compilation
./check-compile.sh

# Clean and retry
./gradlew clean
./release-build.sh
```

### Need Help?

- Keystore issues: [KEYSTORE_SETUP.md](KEYSTORE_SETUP.md)
- Build options: [RELEASE_BUILD.md](RELEASE_BUILD.md)
- Signing details: [SIGNING_README.md](SIGNING_README.md)

## Summary

| What | Command | Output |
|------|---------|--------|
| **Setup** (once) | `./setup-keystore.sh` | `keystore.properties` created |
| **Play Store** | `./release-build.sh -b aab` | `*.aab` in `app/release/` |
| **Testing** | `./release-build.sh -b apk` | `*.apk` in `app/release/` |
| **Both** | `./release-build.sh` | Both APK + AAB |

That's it! 🎉
