import 'package:android_folder_permission/android_folder_permission.dart';
import 'package:flutter/material.dart';
import 'dart:async';
import 'package:flutter/services.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  bool _hasFolderPermission = false;
  final _androidFolderPermissionPlugin = AndroidFolderPermission();

  static const _folderPath =
      // 'Android/media/com.whatsapp/WhatsApp/Media/.Statuses';
      'Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses';

  Future<void> checkFolderPermission() async {
    try {
      _hasFolderPermission = await _androidFolderPermissionPlugin
          .checkFolderPermission(path: _folderPath);
    } on PlatformException {
      _hasFolderPermission = false;
    }
    if (!mounted) return;
    setState(() => _hasFolderPermission = _hasFolderPermission);
  }

  Future<void> requestFolderPermission() async {
    try {
      final result = await _androidFolderPermissionPlugin
          .requestFolderPermission(path: _folderPath);
      debugPrint('result: $result');
    } on PlatformException catch (e) {
      debugPrint('error: ${e.message}');
    }
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(title: const Text('Plugin example app')),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16.0),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text('Running on: $_hasFolderPermission'),
                SizedBox(height: 16),
                ElevatedButton(
                  onPressed: checkFolderPermission,
                  child: const Text('Check Folder Permission'),
                ),
                SizedBox(height: 16),
                Text('Folder path: $_folderPath', textAlign: TextAlign.center),
                SizedBox(height: 16),
                ElevatedButton(
                  onPressed: requestFolderPermission,
                  child: const Text('Request Folder Permission'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
