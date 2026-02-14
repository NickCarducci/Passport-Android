# Account Deletion & Data Portability - Mobile Integration

## Required by Apple & Google

Both Apple App Store and Google Play Store **require** that apps provide account deletion and data management features that are accessible from within the app itself.

## Web App Solution (Already Implemented)

The web app now has a dedicated `/account` page at:
```
https://your-webapp-url.com/account
```

This page provides:
- View account information
- Export data (CSV download)
- Remove display name from leaderboard
- Instructions for account deletion

## Android App Integration (Required)

You need to add a way for users to access this page from the Android app.

### Option 1: Add Menu Item in MainActivity (Recommended)

Add a settings menu option that opens the account page in a WebView or browser.

**1. Create Menu Resource** (`res/menu/main_menu.xml`):
```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:id="@+id/menu_account_settings"
        android:title="Account Settings"
        android:icon="@android:drawable/ic_menu_manage" />
</menu>
```

**2. Update MainActivity.kt**:
```kotlin
import android.content.Intent
import android.net.Uri
import android.view.Menu
import android.view.MenuItem

class MainActivity : AppCompatActivity() {

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_account_settings -> {
                openAccountSettings()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openAccountSettings() {
        val url = "https://your-webapp-url.com/account"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }
}
```

### Option 2: WebView Activity (Alternative)

Create a dedicated settings activity with embedded WebView:

**WebViewActivity.kt**:
```kotlin
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class WebViewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient()

        val url = intent.getStringExtra("url") ?: "https://your-webapp-url.com/account"
        webView.loadUrl(url)

        setContentView(webView)
    }
}
```

Then launch it from MainActivity:
```kotlin
val intent = Intent(this, WebViewActivity::class.java)
intent.putExtra("url", "https://your-webapp-url.com/account")
startActivity(intent)
```

## iOS App Integration (Required)

For iOS (Swift), add to your main view or settings:

```swift
import SafariServices

func openAccountSettings() {
    if let url = URL(string: "https://your-webapp-url.com/account") {
        let safariVC = SFSafariViewController(url: url)
        present(safariVC, animated: true)
    }
}
```

Or use in-app WebView:
```swift
import WebKit

let webView = WKWebView()
if let url = URL(string: "https://your-webapp-url.com/account") {
    webView.load(URLRequest(url: url))
}
```

## App Store Review Requirements

### Google Play Console

When submitting, you'll be asked:
- **Account deletion method**: Web page
- **Account deletion URL**: `https://your-webapp-url.com/account`

In the "Data safety" section:
- Users can request data deletion: ✅ Yes
- Account deletion available: ✅ Yes

### Apple App Store Connect

In the **App Privacy** section:
- Account deletion instructions: Accessible via in-app menu → Account Settings
- Data download: Available via CSV export on account page

In the app's **Settings** or **Profile** section, provide clear navigation to account management.

## Google Play Policy Compliance

Per [Google Play Data Deletion Policy](https://support.google.com/googleplay/android-developer/answer/13316080):
- Users must be able to request account deletion from within the app
- The deletion request mechanism must be easy to find
- You have 30 days to complete deletion requests (already documented in privacy policy)

## Apple App Store Compliance

Per [Apple Human Interface Guidelines - Account Deletion](https://developer.apple.com/design/human-interface-guidelines/account-management):
- Apps that allow account creation must allow account deletion
- Deletion must be easy to find (not buried in settings)
- Clear consequences of deletion must be stated (done in web app)

## Testing Checklist

Before submission:
- [ ] Android: Menu item opens `/account` page
- [ ] iOS: Settings option opens `/account` page
- [ ] Web app loads correctly in mobile browsers
- [ ] CSV export downloads successfully on mobile
- [ ] Account deletion instructions are clear
- [ ] Privacy policy is accessible from account page
- [ ] All links work on both platforms

## Alternative: Native Implementation

If you want fully native account deletion (no web dependency):

**Android (Kotlin)**:
```kotlin
private fun deleteAccount() {
    AlertDialog.Builder(this)
        .setTitle("Delete Account")
        .setMessage("This will permanently delete your account and all data. Continue?")
        .setPositiveButton("Delete") { _, _ ->
            // Call your backend API
            val auth = Firebase.auth
            auth.currentUser?.getIdToken(true)?.addOnSuccessListener { result ->
                val token = result.token
                // HTTP DELETE to your backend: /api/users/{uid}
                // Then sign out and close app
            }
        }
        .setNegativeButton("Cancel", null)
        .show()
}
```

**Backend API** (if you implement native deletion):
```javascript
// index.js or cloud function
app.delete('/api/users/:uid', async (req, res) => {
    const uid = req.params.uid;
    const idToken = req.headers.authorization.split('Bearer ')[1];

    // Verify token
    const decodedToken = await admin.auth().verifyIdToken(idToken);
    if (decodedToken.uid !== uid) {
        return res.status(403).send('Unauthorized');
    }

    // Delete Firestore data
    await admin.firestore().collection('users').doc(uid).delete();
    await admin.firestore().collection('leaders').doc(decodedToken.email.split('@')[0]).delete();

    // Delete auth account
    await admin.auth().deleteUser(uid);

    res.send({ message: 'Account deleted successfully' });
});
```

## Recommended Approach

**For internal testing**: Use the web-based approach (Option 1) - fastest to implement

**For production**: Consider native implementation for better user experience

## Update Privacy Policy Link

Make sure to update the privacy policy contact email:
- Replace `[IT contact email]` with actual email address in both the web app privacy policy and account deletion page
