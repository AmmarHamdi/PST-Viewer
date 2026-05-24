# PST Viewer — Android App

Browse and read Microsoft Outlook PST archive files on Android.

## Features
- Open any `.pst` file via the Storage Access Framework (no root needed)
- Full folder tree with message counts
- Email list with sender, subject, date preview and unread bolding
- Search/filter emails within a folder
- Full email body rendered in WebView (HTML + plain text fallback)
- Attachment list with tap-to-open via installed apps
- Works on Android 8+ (API 26)

## Build Instructions

### Prerequisites
- Android Studio Hedgehog (2023.1) or newer
- JDK 17
- Android SDK with API 34

### Steps
1. Clone the repository: `git clone https://github.com/AmmarHamdi/PST-Viewer.git`
2. Open the `PSTViewer` folder in Android Studio
3. Let Gradle sync (it downloads `com.pff:java-libpst:0.9.3` automatically)
4. Connect an Android device or start an emulator
5. Click **Run ▶**

### Command-line build
```bash
cd PSTViewer
./gradlew assembleDebug
# APK output: app/build/outputs/apk/debug/app-debug.apk
```

## Project Structure
```
PSTViewer/
├── app/src/main/
│   ├── java/com/pstviewer/
│   │   ├── MainActivity.java          # Landing screen & file picker
│   │   ├── FolderActivity.java        # Folder tree browser
│   │   ├── FolderAdapter.java         # RecyclerView adapter for folders
│   │   ├── EmailListActivity.java     # Emails inside a folder
│   │   ├── EmailAdapter.java          # RecyclerView adapter for emails
│   │   ├── EmailDetailActivity.java   # Full message viewer
│   │   └── PSTRepository.java        # Shared PST file state & helpers
│   └── res/
│       ├── layout/                    # XML layouts for all screens
│       ├── drawable/                  # Chip & badge backgrounds
│       ├── values/                    # Colors, strings, themes
│       └── xml/file_paths.xml         # FileProvider config for attachments
```

## Key Dependency
`com.pff:java-libpst:0.9.3` — pure-Java PST parser, no native code needed.

## Notes
- Large PST files (>1 GB) may take several seconds to copy into cache on first open
- The app copies the PST to internal cache so no special permissions are needed on Android 13+
- JavaScript is disabled in the email WebView for security
