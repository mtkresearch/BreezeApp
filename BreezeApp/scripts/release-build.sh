#!/bin/bash

# BreezeApp Release Build Script
# Automatically increments version code and version name, then builds release APK and AAB

set -e  # Exit on error

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
BUILD_GRADLE="app/build.gradle.kts"
BACKUP_FILE="app/build.gradle.kts.backup"
MANUAL_VERSION=""
MANUAL_CODE=""
BUILD_TYPE="both"  # Default: both APK and AAB
VERSION_TYPE="patch"  # Default to patch if not specified

# Function to print colored output
print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

# Function to extract current version
get_current_version() {
    local version_code=$(grep "versionCode = " "$BUILD_GRADLE" | sed 's/.*versionCode = \([0-9]*\).*/\1/')
    local version_name=$(grep "versionName = " "$BUILD_GRADLE" | sed 's/.*versionName = "\([^"]*\)".*/\1/')
    echo "$version_code|$version_name"
}

# Function to increment version
increment_version() {
    local version=$1
    local type=$2

    IFS='.' read -r major minor patch <<< "$version"

    case $type in
        major)
            major=$((major + 1))
            minor=0
            patch=0
            ;;
        minor)
            minor=$((minor + 1))
            patch=0
            ;;
        patch)
            patch=$((patch + 1))
            ;;
        *)
            print_error "Invalid version type: $type. Use major, minor, or patch."
            exit 1
            ;;
    esac

    echo "$major.$minor.$patch"
}

# Function to update version in build.gradle.kts
update_version() {
    local new_version_code=$1
    local new_version_name=$2

    # Create backup
    cp "$BUILD_GRADLE" "$BACKUP_FILE"

    # Update versionCode
    sed -i '' "s/versionCode = [0-9]*/versionCode = $new_version_code/" "$BUILD_GRADLE"

    # Update versionName
    sed -i '' "s/versionName = \"[^\"]*\"/versionName = \"$new_version_name\"/" "$BUILD_GRADLE"
}

# Function to restore backup
restore_backup() {
    if [ -f "$BACKUP_FILE" ]; then
        mv "$BACKUP_FILE" "$BUILD_GRADLE"
        print_warning "Restored backup due to build failure"
    fi
}

# Function to validate version format
validate_version() {
    local version=$1
    if [[ ! $version =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        print_error "Invalid version format: $version. Must be in format X.Y.Z (e.g., 1.2.3)"
        exit 1
    fi
}

# Function to validate version code
validate_version_code() {
    local code=$1
    if [[ ! $code =~ ^[0-9]+$ ]]; then
        print_error "Invalid version code: $code. Must be a positive integer"
        exit 1
    fi
}

# Main script
main() {
    print_info "BreezeApp Release Build Script"
    echo "================================"

    # Check if build.gradle.kts exists
    if [ ! -f "$BUILD_GRADLE" ]; then
        print_error "build.gradle.kts not found at $BUILD_GRADLE"
        exit 1
    fi

    # Get current version
    version_info=$(get_current_version)
    IFS='|' read -r current_version_code current_version_name <<< "$version_info"

    print_info "Current version: $current_version_name (code: $current_version_code)"

    # Determine if using manual or automatic versioning
    if [ -n "$MANUAL_VERSION" ]; then
        # Manual version mode
        validate_version "$MANUAL_VERSION"
        new_version_name="$MANUAL_VERSION"

        if [ -n "$MANUAL_CODE" ]; then
            validate_version_code "$MANUAL_CODE"
            new_version_code="$MANUAL_CODE"
        else
            new_version_code=$((current_version_code + 1))
        fi

        print_info "Manual version mode"
        print_info "New version: $new_version_name (code: $new_version_code)"
    else
        # Automatic increment mode
        new_version_code=$((current_version_code + 1))
        new_version_name=$(increment_version "$current_version_name" "$VERSION_TYPE")

        print_info "Auto-increment mode: $VERSION_TYPE"
        print_info "New version: $new_version_name (code: $new_version_code)"
    fi

    # Show build type
    if [[ "$BUILD_TYPE" == "both" ]]; then
        print_info "Build type: APK + AAB (for testing and Play Store)"
    elif [[ "$BUILD_TYPE" == "aab" ]]; then
        print_info "Build type: AAB only (for Play Store)"
    else
        print_info "Build type: APK only (for testing)"
    fi

    echo ""

    # Confirm with user
    read -p "Proceed with version update? (y/n): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        print_warning "Build cancelled by user"
        exit 0
    fi

    # Update version in build.gradle.kts
    print_info "Updating version in $BUILD_GRADLE..."
    update_version "$new_version_code" "$new_version_name"
    print_success "Version updated successfully"

    # Clean build
    print_info "Cleaning previous builds..."
    ./gradlew clean

    # Create release directory
    VERSION_RELEASE_DIR="app/release"
    mkdir -p "$VERSION_RELEASE_DIR"

    # Build based on BUILD_TYPE
    BUILD_SUCCESS=true

    if [[ "$BUILD_TYPE" == "apk" || "$BUILD_TYPE" == "both" ]]; then
        # Build release APK
        print_info "Building release APK..."
        if ./gradlew assembleRelease; then
            print_success "Release APK built successfully!"

            # Find the APK
            APK_PATH=$(find app/build/outputs/apk/release -name "*.apk" -type f | head -n 1)

            if [ -n "$APK_PATH" ]; then
                APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
                print_success "APK location: $APK_PATH"
                print_success "APK size: $APK_SIZE"

                # Create a versioned copy
                VERSION_APK_NAME="BreezeApp-v${new_version_name}-${new_version_code}.apk"
                cp "$APK_PATH" "$VERSION_RELEASE_DIR/$VERSION_APK_NAME"
                print_success "Versioned APK saved: $VERSION_RELEASE_DIR/$VERSION_APK_NAME"
            fi
        else
            print_error "APK build failed!"
            BUILD_SUCCESS=false
        fi
    fi

    if [[ "$BUILD_TYPE" == "aab" || "$BUILD_TYPE" == "both" ]]; then
        # Build release AAB (Android App Bundle)
        print_info "Building release AAB (Android App Bundle)..."
        if ./gradlew bundleRelease; then
            print_success "Release AAB built successfully!"

            # Find the AAB
            AAB_PATH=$(find app/build/outputs/bundle/release -name "*.aab" -type f | head -n 1)

            if [ -n "$AAB_PATH" ]; then
                AAB_SIZE=$(du -h "$AAB_PATH" | cut -f1)
                print_success "AAB location: $AAB_PATH"
                print_success "AAB size: $AAB_SIZE"

                # Create a versioned copy
                VERSION_AAB_NAME="BreezeApp-v${new_version_name}-${new_version_code}.aab"
                cp "$AAB_PATH" "$VERSION_RELEASE_DIR/$VERSION_AAB_NAME"
                print_success "Versioned AAB saved: $VERSION_RELEASE_DIR/$VERSION_AAB_NAME"
            fi
        else
            print_error "AAB build failed!"
            BUILD_SUCCESS=false
        fi
    fi

    if [ "$BUILD_SUCCESS" = true ]; then
        # Remove backup
        rm -f "$BACKUP_FILE"

        # Git status check
        if git rev-parse --git-dir > /dev/null 2>&1; then
            print_info "Git repository detected"
            print_warning "Don't forget to commit the version change:"
            echo -e "${YELLOW}  git add $BUILD_GRADLE${NC}"
            echo -e "${YELLOW}  git commit -m \"chore: bump version to $new_version_name ($new_version_code)\"${NC}"
            echo -e "${YELLOW}  git tag -a v$new_version_name -m \"Release version $new_version_name\"${NC}"
        fi

        echo ""
        print_success "Release build completed successfully! 🎉"
        print_info "Version: $new_version_name (code: $new_version_code)"

        if [[ "$BUILD_TYPE" == "both" ]]; then
            print_info "Built: APK + AAB (Android App Bundle for Play Store)"
        elif [[ "$BUILD_TYPE" == "aab" ]]; then
            print_info "Built: AAB only (Android App Bundle for Play Store)"
        else
            print_info "Built: APK only"
        fi
    else
        print_error "Build failed!"
        restore_backup
        exit 1
    fi
}

# Trap errors and restore backup
trap 'restore_backup' ERR

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            echo "Usage: ./release-build.sh [OPTIONS] [VERSION_TYPE]"
            echo ""
            echo "OPTIONS:"
            echo "  -v, --version VERSION    Set version name manually (e.g., 2.1.0)"
            echo "  -c, --code CODE          Set version code manually (e.g., 15)"
            echo "  -b, --build TYPE         Build type: apk, aab, both [default: both]"
            echo "  -h, --help               Show this help message"
            echo ""
            echo "VERSION_TYPE (auto-increment):"
            echo "  major - Increment major version (X.0.0)"
            echo "  minor - Increment minor version (0.X.0)"
            echo "  patch - Increment patch version (0.0.X) [default]"
            echo ""
            echo "BUILD TYPES:"
            echo "  apk  - Build APK only (for direct installation/testing)"
            echo "  aab  - Build AAB only (Android App Bundle for Play Store)"
            echo "  both - Build both APK and AAB [default]"
            echo ""
            echo "Examples:"
            echo ""
            echo "  Auto-increment (default - builds both APK and AAB):"
            echo "    ./release-build.sh              # Patch: 1.0.0 -> 1.0.1"
            echo "    ./release-build.sh minor        # Minor: 1.0.0 -> 1.1.0"
            echo "    ./release-build.sh major        # Major: 1.0.0 -> 2.0.0"
            echo ""
            echo "  Build specific format:"
            echo "    ./release-build.sh -b aab       # Build AAB only for Play Store"
            echo "    ./release-build.sh -b apk       # Build APK only for testing"
            echo "    ./release-build.sh --build both # Build both (default)"
            echo ""
            echo "  Manual version (with auto-increment code):"
            echo "    ./release-build.sh -v 2.5.0     # Set to 2.5.0, code auto-increments"
            echo ""
            echo "  Manual version and code:"
            echo "    ./release-build.sh -v 2.5.0 -c 25   # Set to 2.5.0 (code: 25)"
            echo "    ./release-build.sh --version 3.0.0 --code 30"
            echo ""
            echo "  Combined options:"
            echo "    ./release-build.sh -v 2.0.0 -b aab   # Manual version, AAB only"
            echo "    ./release-build.sh minor -b apk      # Minor increment, APK only"
            exit 0
            ;;
        -v|--version)
            MANUAL_VERSION="$2"
            shift 2
            ;;
        -c|--code)
            MANUAL_CODE="$2"
            shift 2
            ;;
        -b|--build)
            case $2 in
                apk|aab|both)
                    BUILD_TYPE="$2"
                    ;;
                *)
                    print_error "Invalid build type: $2. Use apk, aab, or both"
                    exit 1
                    ;;
            esac
            shift 2
            ;;
        major|minor|patch)
            VERSION_TYPE="$1"
            shift
            ;;
        *)
            print_error "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Run main function
main
