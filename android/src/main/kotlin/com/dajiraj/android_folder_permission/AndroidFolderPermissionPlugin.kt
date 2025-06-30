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

/** AndroidFolderPermissionPlugin */
class AndroidFolderPermissionPlugin : FlutterPlugin, MethodCallHandler, ActivityAware,
    PluginRegistry.ActivityResultListener {

    lateinit var context: Context
    private lateinit var channel: MethodChannel
    private var activity: Activity? = null
    private var pendingResult: MethodChannel.Result? = null
    val OPEN_DOCUMENT_TREE_CODE = 10

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        this.context = flutterPluginBinding.applicationContext
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "android_folder_permission")
        channel.setMethodCallHandler(this)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        this.activity = binding.activity
        binding.addActivityResultListener(this)
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        this.activity = binding.activity
        binding.addActivityResultListener(this)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "checkFolderPermission" -> {
                val hasPermission = checkFolderPermission(call)
                result.success(hasPermission)
            }

            "requestFolderPermission" -> {
                val hasPermission = checkFolderPermission(call)
                if (!hasPermission) {
                    openDocumentTree(call, result)
                } else {
                    result.error(
                        "PERMISSION_ALREADY_GRANTED", "Permission already granted", null
                    )
                }
            }

            else -> result.notImplemented()
        }
    }

    private fun checkFolderPermission(call: MethodCall): Boolean {
        try {
            val path = call.argument<String>("folderPath")
            if (path != null) {
                val uriPermissions = context.contentResolver.persistedUriPermissions
                return uriPermissions.any {
                    it.uri.path?.contains(path) == true && it.isReadPermission
                }
            }
            return false
        } catch (e: Exception) {
            return false
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun openDocumentTree(call: MethodCall, result: MethodChannel.Result) {
        try {
            val path = call.argument<String>("folderPath")

            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val intent = storageManager.primaryStorageVolume.createOpenDocumentTreeIntent()

            if (path != null) {
                val targetDirectory = Uri.encode(path)

                // Get the initial URI from the intent
                val initialUri = intent.parcelable<Uri>(DocumentsContract.EXTRA_INITIAL_URI)

                if (initialUri != null) {
                    var scheme = initialUri.toString()
                    scheme = scheme.replace("/root/", "/document/")
                    scheme += "%3A$targetDirectory"

                    val uri = Uri.parse(scheme)
                    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
                }
            }

            if (pendingResult != null) {
                result.error(
                    "PENDING_REQUEST", "Another permission request is already pending", null
                )
                return
            }

            pendingResult = result

            if (activity != null) {
                activity?.startActivityForResult(intent, OPEN_DOCUMENT_TREE_CODE)
            } else {
                result.error("NO_ACTIVITY", "Cannot access activity", null)
            }

        } catch (e: Exception) {
            result.error("PERMISSION_ERROR", e.message, null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == OPEN_DOCUMENT_TREE_CODE) {
            try {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val uri = data.data
                    if (uri != null) {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                        pendingResult?.success(uri.toString())
                    } else {
                        pendingResult?.error(
                            "NO_URI", "No URI returned from folder selection", null
                        )
                    }
                } else {
                    pendingResult?.error(
                        "PERMISSION_DENIED", "User denied folder permission", null
                    )
                }
            } catch (e: Exception) {
                Log.d("OnActivity failure", "ON_ACTIVITY_RESULT FAILED WITH EXCEPTION\n$e")
                pendingResult?.error("PERMISSION_ERROR", e.message, null)
            } finally {
                pendingResult = null
            }
            return true // Indicate that we handled this result
        }
        return false // We didn't handle this result
    }

    override fun onDetachedFromActivity() {
        this.activity = null
    }

    override fun onDetachedFromActivityForConfigChanges() {
        this.activity = null
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    private inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? = when {
        SDK_INT >= 33 -> getParcelableExtra(key, T::class.java)
        else -> @Suppress("DEPRECATION") getParcelableExtra(key) as? T
    }
}
