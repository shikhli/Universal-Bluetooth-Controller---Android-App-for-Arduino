package zakirshikhli.ble_app

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment

object BLEutil {

    /*
     * more efficient caching of name than BluetoothDevice which always does RPC
     */
    class Device(val device: BluetoothDevice) : Comparable<Device> {
        @SuppressLint("MissingPermission")
        var name: String? = device.name

        override fun equals(other: Any?): Boolean {
            if (other is Device) {
                return device == other.device
            }
            return false
        }

        /**
         * sort by name, then address. sort named devices first
         */
        override fun compareTo(other: Device): Int {
            val thisValid = !this.name.isNullOrEmpty()
            val otherValid = !other.name.isNullOrEmpty()
            if (thisValid && otherValid) {
                val ret = this.name!!.compareTo(other.name!!)
                if (ret != 0) return ret
                return this.device.address.compareTo(other.device.address)
            }
            if (thisValid) return -1
            return if (otherValid) 1 else this.device.address.compareTo(other.device.address)
        }
    }

    /**
     * Android 12 permission handling
     */
    private fun showRationaleDialog(fragment: Fragment, listener: DialogInterface.OnClickListener) {
        val builder = AlertDialog.Builder(fragment.requireActivity())
        builder.setTitle(fragment.getString(R.string.bluetooth_permission_title))
        builder.setMessage(fragment.getString(R.string.bluetooth_permission_grant))
        builder.setNegativeButton("Cancel", null)
        builder.setPositiveButton("Continue", listener)
        builder.show()
    }

    /**
     * CONNECT + SCAN are granted together in same permission group, so actually no need to check/request both, but one never knows
     */
    fun hasPermissions(
        fragment: Fragment,
        requestPermissionLauncher: ActivityResultLauncher<Array<String>?>
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
            return true
        val missingPermissions =
            fragment.requireActivity().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    fragment.requireActivity().checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
        val showRationale =
            fragment.shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT) ||
                    fragment.shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_SCAN)
        val permissions =
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        return if (missingPermissions) {
            if (showRationale) {
                showRationaleDialog(fragment) { _, _ ->
                    requestPermissionLauncher.launch(permissions)
                }
            } else {
                requestPermissionLauncher.launch(permissions)
            }
            false
        } else {
            true
        }
    }

    fun onPermissionsResult(
        fragment: Fragment,
        grants: Map<String, Boolean>,
        cb: () -> Unit // Change the type of cb
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
            return
        val showRationale =
            fragment.shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT) ||
                    fragment.shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_SCAN)
        val granted = grants.values.reduce { a, b -> a && b }
        if (granted) {
            cb() // Correct way to call the callback function
        } else if (showRationale) {
            showRationaleDialog(fragment) { _, _ -> cb() } // Correct way to call the callback function
        } else {
            //showSettingsDialog(fragment)
        }
    }
}
