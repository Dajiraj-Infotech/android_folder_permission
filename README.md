# Android Folder Permission

A Flutter plugin for handling Android folder permissions using SAF (Storage Access Framework). This plugin provides a simple way to check and request folder permissions for accessing external storage on Android devices.

## Features

- ✅ **Check Folder Permissions**: Verify if your app has permission to access a specific folder
- ✅ **Request Folder Permissions**: Request permission to access folders using Android's Storage Access Framework
- ✅ **Persistent Permissions**: Permissions are persisted across app restarts
- ✅ **Android 10+ Support**: Uses modern SAF approach for Android 10 and above
- ✅ **Error Handling**: Comprehensive error handling with detailed error messages
- ✅ **Activity Lifecycle Management**: Properly handles activity lifecycle changes

## Installation

Add this to your package's `pubspec.yaml` file:

```yaml
dependencies:
  android_folder_permission: ^0.0.1
```

## Usage

### Basic Setup

Import the plugin in your Dart file:

```dart
import 'package:android_folder_permission/android_folder_permission.dart';
```

### Initialize the Plugin

```dart
final _androidFolderPermissionPlugin = AndroidFolderPermission();
```

### Check Folder Permission

Check if your app already has permission to access a specific folder:

```dart
Future<void> checkFolderPermission() async {
  try {
    bool hasPermission = await _androidFolderPermissionPlugin
        .checkFolderPermission(path: 'Android/media/com.whatsapp/WhatsApp/Media/.Statuses');
    
    print('Has permission: $hasPermission');
  } on PlatformException catch (e) {
    print('Error checking permission: ${e.message}');
  }
}
```

### Request Folder Permission

Request permission to access a folder. This will open the Android folder picker:

```dart
Future<void> requestFolderPermission() async {
  try {
    String result = await _androidFolderPermissionPlugin
        .requestFolderPermission(path: 'Android/media/com.whatsapp/WhatsApp/Media/.Statuses');
    
    print('Permission granted. URI: $result');
  } on PlatformException catch (e) {
    print('Error requesting permission: ${e.message}');
  }
}
```

## Complete Example

Here's a complete example showing how to use the plugin:

```dart
import 'package:android_folder_permission/android_folder_permission.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class FolderPermissionExample extends StatefulWidget {
  @override
  _FolderPermissionExampleState createState() => _FolderPermissionExampleState();
}

class _FolderPermissionExampleState extends State<FolderPermissionExample> {
  bool _hasFolderPermission = false;
  final _androidFolderPermissionPlugin = AndroidFolderPermission();
  
  // Example folder path (WhatsApp statuses folder)
  static const _folderPath = 'Android/media/com.whatsapp/WhatsApp/Media/.Statuses';

  @override
  void initState() {
    super.initState();
    checkFolderPermission();
  }

  Future<void> checkFolderPermission() async {
    try {
      bool hasPermission = await _androidFolderPermissionPlugin
          .checkFolderPermission(path: _folderPath);
      
      if (mounted) {
        setState(() {
          _hasFolderPermission = hasPermission;
        });
      }
    } on PlatformException {
      if (mounted) {
        setState(() {
          _hasFolderPermission = false;
        });
      }
    }
  }

  Future<void> requestFolderPermission() async {
    try {
      String result = await _androidFolderPermissionPlugin
          .requestFolderPermission(path: _folderPath);
      
      // Re-check permission after request
      await checkFolderPermission();
      print('Permission result: $result');
    } on PlatformException catch (e) {
      print('Error: ${e.message}');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('Android Folder Permission')),
      body: Center(
        child: Padding(
          padding: EdgeInsets.all(16.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                'Permission Status: ${_hasFolderPermission ? "Granted" : "Not Granted"}',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
              ),
              SizedBox(height: 20),
              Text('Folder: $_folderPath', textAlign: TextAlign.center),
              SizedBox(height: 30),
              ElevatedButton(
                onPressed: checkFolderPermission,
                child: Text('Check Permission'),
              ),
              SizedBox(height: 16),
              ElevatedButton(
                onPressed: requestFolderPermission,
                child: Text('Request Permission'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
```

## Error Handling

The plugin provides comprehensive error handling. Here are the possible error codes:

- `PERMISSION_ALREADY_GRANTED`: Permission is already granted for the folder
- `PENDING_REQUEST`: Another permission request is already in progress
- `NO_ACTIVITY`: Cannot access the current activity
- `PERMISSION_ERROR`: General permission-related error
- `NO_URI`: No URI returned from folder selection
- `PERMISSION_DENIED`: User denied the permission request

## Common Use Cases

### WhatsApp Status Downloader

```dart
// Check if app can access WhatsApp statuses
bool canAccessStatuses = await _androidFolderPermissionPlugin
    .checkFolderPermission(path: 'Android/media/com.whatsapp/WhatsApp/Media/.Statuses');

if (!canAccessStatuses) {
  // Request permission
  await _androidFolderPermissionPlugin
      .requestFolderPermission(path: 'Android/media/com.whatsapp/WhatsApp/Media/.Statuses');
}
```

### File Manager App

```dart
// Request permission for Downloads folder
String downloadsUri = await _androidFolderPermissionPlugin
    .requestFolderPermission(path: 'Download');
```

## Requirements

- **Flutter**: >=3.3.0
- **Dart**: ^3.8.1
- **Android**: API level 21+ (Android 5.0+)
- **Target Android**: API level 26+ (Android 8.0+) for full SAF support

## Permissions

This plugin uses Android's Storage Access Framework (SAF), which means:

- No additional permissions need to be declared in `AndroidManifest.xml`
- Users grant permission through the system folder picker
- Permissions are scoped to specific folders
- Permissions persist across app restarts

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

If you encounter any issues or have questions, please open an issue on the GitHub repository.

