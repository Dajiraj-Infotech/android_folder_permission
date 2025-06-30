import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'android_folder_permission_platform_interface.dart';

/// An implementation of [AndroidFolderPermissionPlatform] that uses method channels.
class MethodChannelAndroidFolderPermission
    extends AndroidFolderPermissionPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('android_folder_permission');

  @override
  Future<bool> checkFolderPermission({required String folderPath}) async {
    final permission = await methodChannel.invokeMethod<bool>(
      'checkFolderPermission',
      {'folderPath': folderPath},
    );
    return permission ?? false;
  }

  @override
  Future<String> requestFolderPermission({required String folderPath}) async {
    final uri = await methodChannel.invokeMethod<String>(
      'requestFolderPermission',
      {'folderPath': folderPath},
    );
    return uri ?? '';
  }
}
