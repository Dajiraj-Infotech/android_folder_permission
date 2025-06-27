
import 'android_folder_permission_platform_interface.dart';

class AndroidFolderPermission {
  Future<String?> getPlatformVersion() {
    return AndroidFolderPermissionPlatform.instance.getPlatformVersion();
  }
}
