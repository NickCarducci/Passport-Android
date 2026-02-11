# Keystore Setup for University Event Passport

## Generate Keystore

Run this command to generate a new keystore (replace values as needed):

```bash
keytool -genkey -v -keystore release.keystore -alias passport -keyalg RSA -keysize 2048 -validity 10000
```

You'll be prompted for:
- **Keystore password**: Choose a strong password
- **Key password**: Can be same as keystore password
- Your name, organization, etc.

**IMPORTANT**: Save these values securely:
- Keystore password
- Key alias: `passport`
- Key password

## Encode Keystore for GitHub Secrets

```bash
base64 -i release.keystore -o release.keystore.base64
```

Or on Linux:
```bash
base64 -w 0 release.keystore > release.keystore.base64
```

Copy the contents of `release.keystore.base64` - you'll need this for GitHub.

## Add to GitHub Secrets

1. Go to your Android repo on GitHub
2. Navigate to **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret** and add these 4 secrets:

   - `KEYSTORE_BASE64`: Paste the base64-encoded keystore content
   - `KEYSTORE_PASSWORD`: Your keystore password
   - `KEY_ALIAS`: `passport`
   - `KEY_PASSWORD`: Your key password

## Store Locally (for local builds)

Create a file `~/.gradle/gradle.properties` or `Android/gradle.properties` (gitignored):

```properties
KEYSTORE_FILE=/absolute/path/to/release.keystore
KEYSTORE_PASSWORD=your_keystore_password
KEY_ALIAS=passport
KEY_PASSWORD=your_key_password
```

Or use environment variables:

```bash
export KEYSTORE_FILE=~/release.keystore
export KEYSTORE_PASSWORD=your_password
export KEY_ALIAS=passport
export KEY_PASSWORD=your_password
```

## Store in Notes App

Save this information securely:

```
App: University Event Passport (Passport)
Bundle ID: com.sayists.passport

Keystore Info:
- File: release.keystore
- Keystore Password: [YOUR_PASSWORD]
- Key Alias: passport
- Key Password: [YOUR_PASSWORD]
- Validity: 10000 days

Base64 Encoded Keystore:
[PASTE BASE64 CONTENT HERE]
```

## Build Locally

```bash
./gradlew assembleRelease
```

APK will be at: `app/build/outputs/apk/release/app-release.apk`

## Security Notes

- **NEVER** commit `release.keystore` to git
- Add `*.keystore` to `.gitignore`
- Store keystore backup in secure location
- If keystore is lost, you cannot update the app in Play Store
