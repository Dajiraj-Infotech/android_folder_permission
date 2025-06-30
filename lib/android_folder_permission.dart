
import 'android_folder_permission_platform_interface.dart';

class AndroidFolderPermission {
  Future<bool> checkFolderPermission({required String path}) {
    return AndroidFolderPermissionPlatform.instance.checkFolderPermission(folderPath: path);
  }

  Future<String> requestFolderPermission({required String path}) {
    return AndroidFolderPermissionPlatform.instance.requestFolderPermission(
      folderPath: path,
    );
  }
}
