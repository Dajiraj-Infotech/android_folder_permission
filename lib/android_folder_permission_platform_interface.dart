import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'android_folder_permission_method_channel.dart';

abstract class AndroidFolderPermissionPlatform extends PlatformInterface {
  /// Constructs a AndroidFolderPermissionPlatform.
  AndroidFolderPermissionPlatform() : super(token: _token);

  static final Object _token = Object();

  static AndroidFolderPermissionPlatform _instance = MethodChannelAndroidFolderPermission();

  /// The default instance of [AndroidFolderPermissionPlatform] to use.
  ///
  /// Defaults to [MethodChannelAndroidFolderPermission].
  static AndroidFolderPermissionPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [AndroidFolderPermissionPlatform] when
  /// they register themselves.
  static set instance(AndroidFolderPermissionPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
