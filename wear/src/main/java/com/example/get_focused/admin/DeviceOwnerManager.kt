package com.example.get_focused.admin

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log

class DeviceOwnerManager(private val context: Context) {

    private val dpm: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val adminComponent: ComponentName =
        ComponentName(context, FocusDeviceAdminReceiver::class.java)

    companion object {
        private const val TAG = "DeviceOwnerManager"
    }

    /**
     * Check if the app is set as device owner
     */
    fun isDeviceOwner(): Boolean {
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    /**
     * Check if lock task mode is active
     * Uses ActivityManager for system-wide check
     */
    fun isLockTaskModeActive(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // API 23
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            activityManager.lockTaskModeState != android.app.ActivityManager.LOCK_TASK_MODE_NONE
        } else {
            // Lock task mode not available before API 23
            false
        }
    }

    /**
     * Set which packages are allowed in lock task mode
     */
    fun setLockTaskPackages(packages: Array<String>) {
        if (!isDeviceOwner()) {
            Log.e(TAG, "Not device owner - cannot set lock task packages")
            return
        }

        try {
            dpm.setLockTaskPackages(adminComponent, packages)
            Log.d(TAG, "Lock task packages set: ${packages.joinToString()}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set lock task packages", e)
        }
    }

    /**
     * Start lock task mode for the given activity
     */
    fun startLockTask(activity: Activity) {
        if (!isDeviceOwner()) {
            Log.e(TAG, "Not device owner - cannot start lock task")
            return
        }

        try {
            activity.startLockTask()
            Log.d(TAG, "Lock task mode started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start lock task", e)
        }
    }

    /**
     * Stop lock task mode
     */
    fun stopLockTask(activity: Activity) {
        try {
            activity.stopLockTask()
            Log.d(TAG, "Lock task mode stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop lock task", e)
        }
    }

    /**
     * Set user restrictions (e.g., disable settings, factory reset)
     */
    fun setUserRestrictions(restrictions: Map<String, Boolean>) {
        if (!isDeviceOwner()) {
            Log.e(TAG, "Not device owner - cannot set restrictions")
            return
        }

        restrictions.forEach { (key, value) ->
            try {
                if (value) {
                    dpm.addUserRestriction(adminComponent, key)
                } else {
                    dpm.clearUserRestriction(adminComponent, key)
                }
                Log.d(TAG, "User restriction $key set to $value")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set restriction $key", e)
            }
        }
    }

    /**
     * Disable the status bar (requires device owner and API 23+)
     */
    fun setStatusBarDisabled(disabled: Boolean) {
        if (!isDeviceOwner()) {
            Log.e(TAG, "Not device owner - cannot disable status bar")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                dpm.setStatusBarDisabled(adminComponent, disabled)
                Log.d(TAG, "Status bar disabled: $disabled")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set status bar disabled", e)
            }
        } else {
            Log.w(TAG, "setStatusBarDisabled requires API 23+")
        }
    }

    /**
     * Set up parental control restrictions
     */
    fun setupParentalControls() {
        if (!isDeviceOwner()) {
            Log.e(TAG, "Not device owner - cannot setup parental controls")
            return
        }

        // Set lock task packages to only allow this app
        setLockTaskPackages(arrayOf(context.packageName))

        // Set common parental control restrictions
        setUserRestrictions(mapOf(
            android.os.UserManager.DISALLOW_FACTORY_RESET to true,
            android.os.UserManager.DISALLOW_ADD_USER to true,
            android.os.UserManager.DISALLOW_REMOVE_USER to true,
            android.os.UserManager.DISALLOW_MODIFY_ACCOUNTS to true
        ))

        Log.d(TAG, "Parental controls setup complete")
    }

    /**
     * Remove device owner (for uninstall/reset)
     * WARNING: This is permanent and requires factory reset to undo
     */
    fun removeDeviceOwner() {
        if (!isDeviceOwner()) {
            Log.w(TAG, "Not device owner - nothing to remove")
            return
        }

        try {
            dpm.clearDeviceOwnerApp(context.packageName)
            Log.d(TAG, "Device owner removed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove device owner", e)
        }
    }
}