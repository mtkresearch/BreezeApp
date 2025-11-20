#!/bin/bash

# Interactive Keystore Setup Script
# Helps configure keystore.properties for release builds

set -e

# Color codes
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}╔═══════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║      BreezeApp Keystore Configuration Setup       ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════════════════╝${NC}"
echo ""

KEYSTORE_FILE="keystore.properties"
TEMPLATE_FILE="keystore.properties.template"

# Check if keystore.properties already exists
if [ -f "$KEYSTORE_FILE" ]; then
    echo -e "${YELLOW}⚠️  keystore.properties already exists!${NC}"
    echo ""
    read -p "Do you want to overwrite it? (y/n): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${BLUE}Setup cancelled. Existing configuration preserved.${NC}"
        exit 0
    fi
fi

# Default values
DEFAULT_KEYSTORE="$HOME/Resource/android_key_mr"

echo -e "${BLUE}ℹ️  This script will help you configure your release keystore.${NC}"
echo ""

# Keystore file path
echo -e "${GREEN}Step 1: Keystore File Location${NC}"
echo "Default: $DEFAULT_KEYSTORE"
read -p "Enter keystore file path (press Enter for default): " KEYSTORE_PATH
KEYSTORE_PATH=${KEYSTORE_PATH:-$DEFAULT_KEYSTORE}

# Verify keystore exists
if [ ! -f "$KEYSTORE_PATH" ]; then
    echo -e "${RED}❌ Error: Keystore file not found at: $KEYSTORE_PATH${NC}"
    echo ""
    echo "Available keystore files in ~/Resource/:"
    ls -1 ~/Resource/ | grep -i "android.*key" || echo "  (none found)"
    exit 1
fi

echo -e "${GREEN}✅ Keystore file found${NC}"
echo ""

# Keystore password
echo -e "${GREEN}Step 2: Keystore Password${NC}"
read -s -p "Enter keystore password: " STORE_PASSWORD
echo ""
echo ""

# Verify keystore password and get aliases
echo -e "${BLUE}ℹ️  Verifying keystore and listing aliases...${NC}"
if ! ALIASES=$(keytool -list -keystore "$KEYSTORE_PATH" -storepass "$STORE_PASSWORD" 2>&1); then
    echo -e "${RED}❌ Error: Invalid keystore password or corrupted keystore${NC}"
    exit 1
fi

# Extract alias names
echo ""
echo -e "${GREEN}Available aliases in keystore:${NC}"
echo "$ALIASES" | grep "別名名稱\|Alias name:" | awk -F': ' '{print "  - " $2}'
echo ""

# Key alias
echo -e "${GREEN}Step 3: Key Alias${NC}"
read -p "Enter key alias: " KEY_ALIAS

if [ -z "$KEY_ALIAS" ]; then
    echo -e "${RED}❌ Error: Key alias cannot be empty${NC}"
    exit 1
fi

# Key password
echo ""
echo -e "${GREEN}Step 4: Key Password${NC}"
echo "(Often the same as keystore password)"
read -s -p "Enter key password: " KEY_PASSWORD
echo ""
echo ""

# Verify key password
echo -e "${BLUE}ℹ️  Verifying key alias and password...${NC}"
if ! keytool -list -keystore "$KEYSTORE_PATH" -storepass "$STORE_PASSWORD" -alias "$KEY_ALIAS" -keypass "$KEY_PASSWORD" > /dev/null 2>&1; then
    echo -e "${RED}❌ Error: Invalid key alias or key password${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Key credentials verified${NC}"
echo ""

# Create keystore.properties
echo -e "${BLUE}ℹ️  Creating keystore.properties...${NC}"

cat > "$KEYSTORE_FILE" << EOF
# Keystore properties for release builds
# Generated: $(date)
# IMPORTANT: This file is in .gitignore - never commit it!

# Path to your keystore file
storeFile=$KEYSTORE_PATH

# Keystore password
storePassword=$STORE_PASSWORD

# Key alias
keyAlias=$KEY_ALIAS

# Key password
keyPassword=$KEY_PASSWORD
EOF

echo -e "${GREEN}✅ keystore.properties created successfully!${NC}"
echo ""

# Test build
echo -e "${BLUE}╔═══════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   Configuration Complete!                         ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${GREEN}Your keystore is now configured for release builds.${NC}"
echo ""
echo "To test your configuration:"
echo -e "${YELLOW}  ./gradlew bundleRelease${NC}"
echo ""
echo "To build a release with version increment:"
echo -e "${YELLOW}  ./release-build.sh -b aab${NC}"
echo ""
echo -e "${RED}⚠️  IMPORTANT:${NC}"
echo "- keystore.properties is in .gitignore (not tracked by Git)"
echo "- Keep your keystore file and passwords secure"
echo "- Back up your keystore file - losing it means you can't update your app!"
echo ""
