# Shared Money — Android

A native Android WebView wrapper for the Shared Money expense tracker and passbook.

## Requirements
- JDK 17
- Android SDK platform 35 and build-tools 35.0.0
- Gradle wrapper (included)

## Build APK locally

On macOS/Linux:
```bash
chmod +x ./gradlew
./gradlew --no-daemon :app:assembleDebug
```

On Windows:
```bat
gradlew.bat :app:assembleDebug
```

The debug APK is created at:
`app/build/outputs/apk/debug/app-debug.apk`

## Build with GitHub Actions
1. Upload/push this project to a GitHub repository.
2. Open **Actions** and select **Build Shared Money Android APK**.
3. Run **Build debug APK** using **Run workflow**, or push to `main`/`master`.
4. Download the `SharedMoney-debug-apk` artifact from the completed workflow run.

App ID: `com.sharedmoney.app`  
Minimum Android: 6.0 (API 23)  
Target Android SDK: 35  
Java: 17

## Data note
App records are stored locally in the WebView. Export a backup regularly before clearing app storage or uninstalling the app.

## Data backup and restore

- **Export Excel** saves a CSV file (`shared-money-backup-YYYY-MM-DD.csv`) that opens in Microsoft Excel / Google Sheets. Edit budget, expenses, incomes and debits there, then import the same file back — changes will show in the app.
- **Import Excel** reads that CSV (or a JSON backup). The app asks for confirmation before replacing current records.
- **Export / Import JSON** is still available as a full technical backup.
- On Android, system Save As / Open document pickers are used. In a regular browser, normal download + file picker work.
