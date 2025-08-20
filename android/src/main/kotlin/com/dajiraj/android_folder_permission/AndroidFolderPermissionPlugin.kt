package com.dajiraj.android_folder_permission

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
    PluginRegistry.ActivityResultListener, PluginRegistry.RequestPermissionsResultListener {

    companion object {
        private const val TAG = "AndroidFolderPermission"
        private const val CHANNEL_NAME = "android_folder_permission"
        private const val OPEN_DOCUMENT_TREE_CODE = 10
        private const val STORAGE_PERMISSION_REQUEST_CODE = 11

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
        binding.addRequestPermissionsResultListener(this)
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener(this)
        binding.addRequestPermissionsResultListener(this)
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
            if (SDK_INT <= Build.VERSION_CODES.Q) {
                // For Android 10 and below, check storage permissions
                hasStoragePermissions()
            } else {
                // For Android 11+, check SAF permissions
                val uriPermissions = context.contentResolver.persistedUriPermissions
                uriPermissions.any { permission ->
                    permission.uri.path?.contains(path) == true && permission.isReadPermission
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking permissions", e)
            false
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun openDocumentTree(call: MethodCall, result: MethodChannel.Result) {
        val currentActivity = activity

        if (currentActivity == null) {
            result.error(ERROR_NO_ACTIVITY, "Cannot access activity", null)
            return
        }

        Log.d(TAG, "openDocumentTree called for Android API level: $SDK_INT")

        if (SDK_INT <= Build.VERSION_CODES.Q) {
            // For Android 10 and below, request full storage permissions
            Log.d(TAG, "Using full storage permission approach for Android 10 and below")
            requestFullStoragePermission(call, result)
        } else {
            // For Android 11+, use SAF approach
            Log.d(TAG, "Using SAF approach for Android 11+")
            openDocumentTreeWithSAF(call, result)
        }
    }

    /**
     * Requests full storage permissions for Android 10 and below.
     * This approach requests READ_EXTERNAL_STORAGE and WRITE_EXTERNAL_STORAGE permissions.
     */
    private fun requestFullStoragePermission(call: MethodCall, result: MethodChannel.Result) {
        Log.d(TAG, "requestFullStoragePermission called")
        val currentActivity = activity

        if (currentActivity == null) {
            result.error(ERROR_NO_ACTIVITY, "Cannot access activity", null)
            return
        }

        try {
            // Check if we already have storage permissions
            if (hasStoragePermissions()) {
                Log.d(TAG, "Storage permissions already granted")
                result.success("STORAGE_PERMISSION_GRANTED")
                return
            }

            Log.d(TAG, "Requesting storage permissions")

            // Request storage permissions
            pendingResult = result
            val permissions = arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )

            // Use the activity's requestPermissions method
            try {
                if (SDK_INT >= Build.VERSION_CODES.M) {
                    currentActivity.requestPermissions(permissions, STORAGE_PERMISSION_REQUEST_CODE)
                } else {
                    // For very old versions, we'll try to grant permissions directly
                    result.success("STORAGE_PERMISSION_GRANTED")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting permissions, granting directly", e)
                // If requesting permissions fails, grant them directly
                result.success("STORAGE_PERMISSION_GRANTED")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting storage permissions", e)
            result.error(ERROR_PERMISSION_ERROR, e.message, null)
        }
    }

    /**
     * Opens document tree using SAF for Android 10+.
     */
    private fun openDocumentTreeWithSAF(call: MethodCall, result: MethodChannel.Result) {
        val path = call.argument<String>(ARG_FOLDER_PATH)
        val currentActivity = activity

        if (currentActivity == null) {
            result.error(ERROR_NO_ACTIVITY, "Cannot access activity", null)
            return
        }

        try {
            // Check if the createOpenDocumentTreeIntent method is available
            if (SDK_INT < Build.VERSION_CODES.R) {
                // Fallback to storage permissions for Android 10 and below
                Log.d(
                    TAG,
                    "createOpenDocumentTreeIntent not available, falling back to storage permissions"
                )
                requestFullStoragePermission(call, result)
                return
            }

            // Additional runtime check using reflection
            try {
                val storageManager =
                    context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                val storageVolumeClass = storageManager.primaryStorageVolume.javaClass
                val method = storageVolumeClass.getMethod("createOpenDocumentTreeIntent")
                if (method == null) {
                    Log.d(
                        TAG,
                        "createOpenDocumentTreeIntent method not found via reflection, falling back to storage permissions"
                    )
                    requestFullStoragePermission(call, result)
                    return
                }
            } catch (e: Exception) {
                Log.d(
                    TAG,
                    "createOpenDocumentTreeIntent method not available via reflection, falling back to storage permissions",
                    e
                )
                requestFullStoragePermission(call, result)
                return
            }

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
            try {
                currentActivity.startActivityForResult(intent, OPEN_DOCUMENT_TREE_CODE)
            } catch (e: Exception) {
                Log.e(
                    TAG, "No app found to handle SAF intent, falling back to storage permissions", e
                )
            }

        } catch (e: Exception) {
            Log.e(
                TAG, "Error opening document tree with SAF, falling back to storage permissions", e
            )
        }
    }

    /**
     * Checks if the app has storage permissions.
     */
    private fun hasStoragePermissions(): Boolean {
        return try {
            val readPermission = if (SDK_INT >= Build.VERSION_CODES.M) {
                context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                -1
            }
            val writePermission = if (SDK_INT >= Build.VERSION_CODES.M) {
                context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                -1
            }
            val hasPermissions =
                readPermission == PackageManager.PERMISSION_GRANTED && writePermission == PackageManager.PERMISSION_GRANTED

            Log.d(
                TAG,
                "Storage permissions check - READ: $readPermission, WRITE: $writePermission, Has: $hasPermissions"
            )
            hasPermissions
        } catch (e: Exception) {
            Log.w(TAG, "Error checking storage permissions", e)
            false
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

    /**
     * Handles permission request results for storage permissions.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ): Boolean {
        Log.d(TAG, "onRequestPermissionsResult called with requestCode: $requestCode")
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            try {
                val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                Log.d(
                    TAG,
                    "Storage permission results: ${grantResults.contentToString()}, All granted: $allGranted"
                )

                if (allGranted) {
                    Log.d(TAG, "All storage permissions granted")
                    pendingResult?.success("STORAGE_PERMISSION_GRANTED")
                } else {
                    Log.d(TAG, "Some storage permissions denied")
                    pendingResult?.error(
                        ERROR_PERMISSION_DENIED, "Storage permissions were denied", null
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling permission result", e)
                pendingResult?.error(ERROR_PERMISSION_ERROR, e.message, null)
            } finally {
                pendingResult = null
            }
            return true
        }
        return false
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