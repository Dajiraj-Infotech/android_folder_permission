import 'package:flutter_test/flutter_test.dart';
import 'package:android_folder_permission/android_folder_permission.dart';
import 'package:android_folder_permission/android_folder_permission_platform_interface.dart';
import 'package:android_folder_permission/android_folder_permission_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockAndroidFolderPermissionPlatform
    with MockPlatformInterfaceMixin
    implements AndroidFolderPermissionPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final AndroidFolderPermissionPlatform initialPlatform = AndroidFolderPermissionPlatform.instance;

  test('$MethodChannelAndroidFolderPermission is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelAndroidFolderPermission>());
  });

  test('getPlatformVersion', () async {
    AndroidFolderPermission androidFolderPermissionPlugin = AndroidFolderPermission();
    MockAndroidFolderPermissionPlatform fakePlatform = MockAndroidFolderPermissionPlatform();
    AndroidFolderPermissionPlatform.instance = fakePlatform;

    expect(await androidFolderPermissionPlugin.getPlatformVersion(), '42');
  });
}
