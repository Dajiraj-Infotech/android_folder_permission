package com.dajiraj.android_folder_permission

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Parcelable
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.util.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterPluginBinding
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.PluginRegistry

/**
 * AndroidFolderPermissionPlugin
 *
 * A Flutter plugin for handling Android folder permissions using SAF (Storage Access Framework).
 * Provides methods to check and request folder permissions for accessing external storage.
 */
class AndroidFolderPermissionPlugin : FlutterPlugin, MethodCallHandler, ActivityAware,
    PluginRegistry.ActivityResultListener {

    companion object {
        private const val TAG = "AndroidFolderPermission"
        private const val CHANNEL_NAME = "android_folder_permission"
        private const val OPEN_DOCUMENT_TREE_CODE = 10

        // Method names
        private const val METHOD_CHECK_PERMISSION = "checkFolderPermission"
        private const val METHOD_REQUEST_PERMISSION = "requestFolderPermission"

        // Error codes
        private const val ERROR_PERMISSION_ALREADY_GRANTED = "PERMISSION_ALREADY_GRANTED"
        private const val ERROR_PENDING_REQUEST = "PENDING_REQUEST"
        private const val ERROR_NO_ACTIVITY = "NO_ACTIVITY"
        private const val ERROR_PERMISSION_ERROR = "PERMISSION_ERROR"
        private const val ERROR_NO_URI = "NO_URI"
        private const val ERROR_PERMISSION_DENIED = "PERMISSION_DENIED"

        // Argument names
        private const val ARG_FOLDER_PATH = "folderPath"
    }

    private lateinit var context: Context
    private lateinit var channel: MethodChannel
    private var activity: Activity? = null
    private var pendingResult: MethodChannel.Result? = null

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        context = flutterPluginBinding.applicationContext
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, CHANNEL_NAME)
        channel.setMethodCallHandler(this)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener(this)
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener(this)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            METHOD_CHECK_PERMISSION -> handleCheckPermission(call, result)
            METHOD_REQUEST_PERMISSION -> handleRequestPermission(call, result)
            else -> result.notImplemented()
        }
    }

    private fun handleCheckPermission(call: MethodCall, result: MethodChannel.Result) {
        try {
            val hasPermission = checkFolderPermission(call)
            result.success(hasPermission)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking folder permission", e)
            result.error(ERROR_PERMISSION_ERROR, e.message, null)
        }
    }

    private fun handleRequestPermission(call: MethodCall, result: MethodChannel.Result) {
        try {
            val hasPermission = checkFolderPermission(call)
            if (hasPermission) {
                result.error(ERROR_PERMISSION_ALREADY_GRANTED, "Permission already granted", null)
                return
            }

            if (pendingResult != null) {
                result.error(
                    ERROR_PENDING_REQUEST, "Another permission request is already pending", null
                )
                return
            }

            openDocumentTree(call, result)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting folder permission", e)
            result.error(ERROR_PERMISSION_ERROR, e.message, null)
        }
    }

    private fun checkFolderPermission(call: MethodCall): Boolean {
        val path = call.argument<String>(ARG_FOLDER_PATH) ?: return false

        return try {
            val uriPermissions = context.contentResolver.persistedUriPermissions
            uriPermissions.any { permission ->
                permission.uri.path?.contains(path) == true && permission.isReadPermission
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking persisted URI permissions", e)
            false
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun openDocumentTree(call: MethodCall, result: MethodChannel.Result) {
        val path = call.argument<String>(ARG_FOLDER_PATH)
        val currentActivity = activity

        if (currentActivity == null) {
            result.error(ERROR_NO_ACTIVITY, "Cannot access activity", null)
            return
        }

        try {
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val intent = storageManager.primaryStorageVolume.createOpenDocumentTreeIntent()

            // Configure initial URI if path is provided
            val targetDirectory = Uri.encode(path)

            // Get the initial URI from the intent
            val initialUri = intent.parcelable<Uri>(DocumentsContract.EXTRA_INITIAL_URI)

            if (initialUri != null) {
                val scheme = initialUri.toString().replace("/root/", "/document/")
                    .plus("%3A$targetDirectory")

                val uri = Uri.parse(scheme)
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
            }

            pendingResult = result
            currentActivity.startActivityForResult(intent, OPEN_DOCUMENT_TREE_CODE)

        } catch (e: Exception) {
            Log.e(TAG, "Error opening document tree", e)
            result.error(ERROR_PERMISSION_ERROR, e.message, null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != OPEN_DOCUMENT_TREE_CODE) return false

        try {
            when {
                resultCode == Activity.RESULT_OK && data?.data != null -> {
                    handleSuccessfulPermissionGrant(data.data!!, pendingResult)
                }

                resultCode == Activity.RESULT_CANCELED -> {
                    pendingResult?.error(
                        ERROR_PERMISSION_DENIED, "User denied folder permission", null
                    )
                }

                else -> {
                    pendingResult?.error(
                        ERROR_NO_URI, "No URI returned from folder selection", null
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling activity result", e)
            pendingResult?.error(ERROR_PERMISSION_ERROR, e.message, null)
        } finally {
            pendingResult = null
        }

        return true
    }

    private fun handleSuccessfulPermissionGrant(uri: Uri, result: MethodChannel.Result?) {
        try {
            val flags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            result?.success(uri.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error taking persistable URI permission", e)
            result?.error(
                ERROR_PERMISSION_ERROR, "Failed to persist URI permission: ${e.message}", null
            )
        }
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    private inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? = when {
        SDK_INT >= 33 -> getParcelableExtra(key, T::class.java)
        else -> @Suppress("DEPRECATION") getParcelableExtra(key) as? T
    }
}
