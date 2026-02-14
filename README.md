# Passport for Android

#### Publish under Monmouth University developer accoount

## Android Keystore

```
cd Android/app

keytool -genkey -v -keystore release.keystore -alias passport-key -keyalg RSA -keysize 2048 -validity 10000

// answer questions about Monmouth University as an organization representing this keystore

//Generating 2,048 bit RSA key pair and self-signed certificate (SHA384withRSA) with a validity of 10,000 days for: CN=Nicholas Carducci, OU=Sole Proprietor, O=Nicholas Carducci, L=Leonardo, ST=New Jersey, C=US [Storing release.keystore] (under app/)

base64 -i release.keystore | pbcopy
```

The keystore as base64 is now copied in your computer's clipboard. Go you your cloud repository (e.g. GitHub) and find the action secrets under settings and paste that base64 format keystore into:

`KEYSTORE_BASE64`

`KEYSTORE_PASSWORD` and `KEY_PASSWORD` are the same, whatever you made in keytool command.

`KEY_ALIAS` is passport-key.

##### Great! Here's your complete deployment roadmap for Google Play Console internal testing:

🔐 Step 1: Generate Your Keystore (One-Time Setup)

```
cd /Users/nicholascarducci/Desktop/passport/Android/app
keytool -genkey -v -keystore release.keystore -alias passport-key -keyalg RSA -keysize 2048 -validity 10000
```

When prompted, provide:

- Keystore password: (create a strong password, save it!)
- Re-enter password
- First and last name: Your name or "Monmouth University"
- Organizational unit: "IT" or "Student Engagement"
- Organization: "Monmouth University"
- City/Locality: "West Long Branch"
- State: "New Jersey"
- Country code: "US"
- Key password: (can be same as keystore password)

**CRITICAL**: Write down these values - you'll need them forever:

- Keystore password
- Key alias: passport-key
- Key password

🔑 Step 2: Add GitHub Secrets

_Encode your keystore to base64 and copy to clipboard_

`base64 -i release.keystore | pbcopy`

> Go to GitHub → Your repo → Settings → Secrets and variables → Actions → New repository secret

Add these _4 secrets_:

1. Name: KEYSTORE_BASE64
2. Value: Paste from clipboard (Cmd+V)
3. Name: KEYSTORE_PASSWORD
4. Value: Your keystore password
5. Name: KEY_ALIAS
6. Value: passport-key
7. Name: KEY_PASSWORD
8. Value: Your key password

🚀 Step 3: Build Your AAB

```
cd /Users/nicholascarducci/Desktop/passport
git add .
git commit -m "Add Play Store assets and AAB build"
git push
```

_Wait for GitHub Actions to_ complete:

1. Go to GitHub → Actions tab
2. Watch the workflow run
3. When complete, download passport-release-aab artifact
4. Extract app-release.aab from the zip

📱 Step 4: Google Play Console Setup

##### A. Create Your App Listing

1. Go to Google Play Console
2. Sign in with your developer account
3. Click "Create app"
4. Fill in:
   - App name: Monmouth University Passport
   - Default language: English (United States)
   - App or game: App
   - Free or paid: Free
   - Accept declarations

##### B. Complete Required Sections

Dashboard → _Set up your app_:

1. App access → All functionality available without restrictions
2. Ads → No, does not contain ads
3. Content rating:
   - Start questionnaire
   - Category: Reference, News, or Educational
   - Answer questions (no violence, mature content, etc.)
   - Submit
4. Target audience:
   - Age: 18-24 (college students)
   - Appeal to children: No
5. Data safety:
   - Does your app collect data? Yes
   - Data collected:
     - ✅ Email address
     - ✅ User IDs (student ID)
   - Data usage: Account management, App functionality
   - Data handling: Encrypted in transit, User can request deletion
   - Privacy policy URL: https://your-deployed-webapp-url.com/privacy
6. App category: Education
7. Store listing:
   - Short description: (from the file I created)Track your Scholarship Week attendance. Support student scholarship. Earn prizes.
   -
   - Full description: (copy from play-store-description.md)
   - App icon: Upload your 512x512 icon
   - Feature graphic: Create 1024x500 image
   - Screenshots: Upload 2-8 screenshots (minimum 2 required)
     - Login screen
     - Event list
     - Leaderboard
     - QR scanner

📦 Step 5: Upload Your AAB

1. Production → Testing → Internal testing
2. Click "Create new release"
3. Upload the app-release.aab file you downloaded from GitHub Actions
4. Release name: 1 (1.0) (auto-filled)
5. Release notes:Initial internal testing release:
6. • QR code event check-in
7. • Live leaderboard
8. • Event schedule
9. • Microsoft authentication
10.
11. Click "Save" then "Review release"
12. If no errors, click "Start rollout to Internal testing"

👥 Step 6: Add Internal Testers

1. Internal testing → Testers tab
2. Click "Create email list"
3. List name: "MU Team"
4. Add email addresses of testers (comma-separated)
5. Save

✅ Step 7: Share Test Link

Once the release is live (takes ~1 hour):

1. Go to Internal testing → Testers tab
2. Copy the "Copy link" URL
3. Share with your testers
4. Testers click link → Join program → Install app

🐛 Common Issues & Solutions

##### "App not signed"

- Check GitHub secrets are set correctly
- Re-run GitHub Actions workflow
  "Upload failed: Version code already exists"
- Increment versionCode in Android/app/build.gradle.kts
- Line 15: Change versionCode = 1 to versionCode = 2
- Rebuild AAB
  "Privacy policy URL not accessible"
- Deploy your webapp first
- Or use a temporary Google Doc with your privacy policy
- Then update with real URL later

📋 Quick Checklist

##### Before submitting:

-  Keystore generated and GitHub secrets added
-  AAB built successfully from GitHub Actions
-  App listing completed (name, description, icon)
-  Screenshots uploaded (minimum 2)
-  Privacy policy URL working
-  Content rating completed
-  Data safety form completed
-  Internal testing release created
-  Testers added to email list

After internal testing passes, you can promote the same release to Production without re-uploading!

1. push to repo
2. let actions workflow build
3. find Upload AAB step
4. At the end you'll find

> Artifact download URL: https://github.com/NickCarducci/Passport-Android/actions/runs/22024743959/artifacts/5512970477