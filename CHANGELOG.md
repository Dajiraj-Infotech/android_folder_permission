## 0.0.2

* Minor Fixes

## 0.0.1

* Initial release of the Android Folder Permission Flutter plugin
* **Core Features:**
  * Check folder permissions using Android's Storage Access Framework (SAF)
  * Request folder permissions through native Android document picker
  * Support for persistent URI permissions across app sessions
  * Handle permission state management and error handling
* **Platform Support:**
  * Android API level 26+ (Android 8.0 Oreo and above)
  * Uses Android's Storage Access Framework for secure folder access
* **Key Methods:**
  * `checkFolderPermission()` - Verify if folder permission is already granted
  * `requestFolderPermission()` - Open document tree picker to request folder access
* **Error Handling:**
  * Comprehensive error codes for different permission scenarios
  * Graceful handling of permission denials and pending requests
  * Activity lifecycle management for permission requests
* **Technical Implementation:**
  * Kotlin-based Android implementation
  * Flutter platform interface for cross-platform compatibility
  * Method channel communication between Flutter and native Android code
  * Support for both read and write permissions on granted folders
