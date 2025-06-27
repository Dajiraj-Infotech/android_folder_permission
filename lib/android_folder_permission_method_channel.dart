import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'android_folder_permission_platform_interface.dart';

/// An implementation of [AndroidFolderPermissionPlatform] that uses method channels.
class MethodChannelAndroidFolderPermission extends AndroidFolderPermissionPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('android_folder_permission');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }
}
