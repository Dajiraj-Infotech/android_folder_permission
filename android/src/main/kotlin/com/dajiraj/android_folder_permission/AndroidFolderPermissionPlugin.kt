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
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.PluginRegistry

/** AndroidFolderPermissionPlugin */
class AndroidFolderPermissionPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {

    lateinit var context: Context
    private lateinit var channel: MethodChannel
    private var activity: Activity? = null
    private var activityResultLauncher: ActivityResultLauncher<Intent>? = null
    private var useModernApi = false
    private var pendingResultPair: Pair<MethodCall, MethodChannel.Result>? = null
    val OPEN_DOCUMENT_TREE_CODE = 10

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        this.context = flutterPluginBinding.applicationContext
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "android_folder_permission")
        channel.setMethodCallHandler(this)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        this.activity = binding.activity
        activityResultListener(binding)
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        this.activity = binding.activity
        activityResultListener(binding)
    }

    fun activityResultListener(binding: ActivityPluginBinding) {
        // Try to use modern ActivityResultLauncher
        val componentActivity = binding.activity as? ComponentActivity
        if (componentActivity != null) {
            try {
                activityResultLauncher = componentActivity.registerForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        val intent = result.data
                        if (intent != null && intent.data != null) {
                            val uri = intent.data!!
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )

                            // Find and return the result to the pending request
                            pendingResultPair?.second?.success(uri.toString())
                            pendingResultPair = null
                        } else {
                            // Handle case where no URI was returned
                            pendingResultPair?.second?.error(
                                "NO_URI", "No URI returned from folder selection", null
                            )
                            pendingResultPair = null
                        }
                    } else {
                        // Handle user cancellation or error
                        pendingResultPair?.second?.error(
                            "PERMISSION_DENIED", "User denied folder permission", null
                        )
                        pendingResultPair = null
                    }
                }
                useModernApi = true
            } catch (e: Exception) {
                // Fallback to traditional approach
                useModernApi = false
                binding.addActivityResultListener(object : PluginRegistry.ActivityResultListener {
                    override fun onActivityResult(
                        requestCode: Int, resultCode: Int, data: Intent?
                    ): Boolean {
                        return this@AndroidFolderPermissionPlugin.onActivityResult(
                            requestCode, resultCode, data
                        )
                    }
                })
            }
        } else {
            // Fallback to traditional approach
            useModernApi = false
            binding.addActivityResultListener(object : PluginRegistry.ActivityResultListener {
                override fun onActivityResult(
                    requestCode: Int, resultCode: Int, data: Intent?
                ): Boolean {
                    return this@AndroidFolderPermissionPlugin.onActivityResult(
                        requestCode, resultCode, data
                    )
                }
            })
        }
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

            if (pendingResultPair != null) {
                result.error(
                    "PENDING_REQUEST", "Another permission request is already pending", null
                )
                return
            }

            pendingResultPair = Pair(call, result)

            // Try modern API first, fallback to traditional approach
            if (useModernApi && activityResultLauncher != null) {
                activityResultLauncher?.launch(intent)
            } else if (activity != null) {
                // Fallback to traditional startActivityForResult
                activity?.startActivityForResult(intent, OPEN_DOCUMENT_TREE_CODE)
            } else {
                result.error("NO_ACTIVITY", "Cannot access activity", null)
            }

        } catch (e: Exception) {
            result.error("PERMISSION_ERROR", e.message, null)
        }
    }

    private fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == OPEN_DOCUMENT_TREE_CODE) {
            if (pendingResultPair != null) {
                try {
                    if (resultCode == Activity.RESULT_OK && data != null) {
                        val uri = data.data
                        if (uri != null) {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                            pendingResultPair?.second?.success(uri.toString())
                        } else {
                            pendingResultPair?.second?.error(
                                "NO_URI", "No URI returned from folder selection", null
                            )
                        }
                    } else {
                        pendingResultPair?.second?.error(
                            "PERMISSION_DENIED", "User denied folder permission", null
                        )
                    }
                } finally {
                    pendingResultPair = null
                }
            }
            return true // Indicate that we handled this result
        }
        return false // We didn't handle this result
    }

    override fun onDetachedFromActivity() {
        this.activity = null
        this.activityResultLauncher = null
    }

    override fun onDetachedFromActivityForConfigChanges() {
        this.activity = null
        this.activityResultLauncher = null
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    private inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? = when {
        SDK_INT >= 33 -> getParcelableExtra(key, T::class.java)
        else -> @Suppress("DEPRECATION") getParcelableExtra(key) as? T
    }
}
