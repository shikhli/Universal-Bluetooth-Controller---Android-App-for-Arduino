package zakirshikhli.ble_app

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.LeScanCallback
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.ListFragment
import java.util.Collections


@Suppress("DEPRECATION")
class PageDevicesBLEFragment : ListFragment() {

    private enum class ScanState {
        NONE,
        LE_SCAN,
        DISCOVERY,
        DISCOVERY_FINISHED
    }

    private var scanState = ScanState.NONE
    private val leScanStopHandler = Handler()
    private val leScanCallback: LeScanCallback
    private val leScanStopCallback: Runnable
    private val discoveryBroadcastReceiver: BroadcastReceiver
    private val discoveryIntentFilter: IntentFilter
    private var menu: Menu? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private val listItems = ArrayList<BLEutil.Device>()
    private var listAdapter: ArrayAdapter<BLEutil.Device>? = null
    private var requestBluetoothPermissionLauncherForStartScan: ActivityResultLauncher<Array<String>?>
    private var requestLocationPermissionLauncherForStartScan: ActivityResultLauncher<String>


    //TODO implement options menu item for this bool
    private var skipUnnamed = true
    private var deviceName: String? = null

    private var listFilterOption: Boolean = false
    private lateinit var sharedPref: SharedPreferences


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        retainInstance = true

        if (requireActivity().packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) bluetoothAdapter =
            BluetoothAdapter.getDefaultAdapter()

        listAdapter =
            object : ArrayAdapter<BLEutil.Device>(requireActivity(), 0, listItems) {
                override fun getView(position: Int, viewP: View?, parent: ViewGroup): View {
                    var devicesFragment = viewP
                    val device = listItems[position]
                    if (devicesFragment == null) {
                        devicesFragment = requireActivity().layoutInflater.inflate(
                            R.layout.page_devices_fragment,
                            parent,
                            false
                        )
                    }
                    val text1 = devicesFragment!!.findViewById<TextView>(R.id.text1)
                    val text2 = devicesFragment.findViewById<TextView>(R.id.text2)
                    var deviceName = device.name
                    if (deviceName.isNullOrEmpty()) {
                        deviceName = getString(R.string.unnamed)
                    }
                    text1.text = deviceName


                    val adrs = device.device.address
                    val partOne = SpannableString(adrs.substring(0, adrs.length - 5))
                    partOne.setSpan(
                        ForegroundColorSpan(resources.getColor(R.color.textColor)),
                        0,
                        partOne.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    text2.text = partOne

                    val partTwo =
                        SpannableString(" " + adrs.substring(adrs.length - 5, adrs.length))
                    partTwo.setSpan(
                        ForegroundColorSpan(resources.getColor(R.color.accentColor)),
                        0,
                        partTwo.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    partTwo.setSpan(
                        StyleSpan(android.graphics.Typeface.BOLD), 0,
                        partTwo.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    text2.append(partTwo)
                    return devicesFragment
                }

            }
    }


    @Deprecated("Deprecated in Java")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setListAdapter(null)
        @SuppressLint("InflateParams") val header =
            requireActivity().layoutInflater.inflate(R.layout.device_list_linlyt, null, false)
        listView.addHeaderView(header, null, false)
        setEmptyText(getString(R.string.initializing))
        (listView.emptyView as TextView).textSize = 18f
        setListAdapter(listAdapter)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_devices_ble, menu)
        this.menu = menu
        if (bluetoothAdapter == null) {
            menu.findItem(R.id.bt_settings).setEnabled(false)
            menu.findItem(R.id.ble_scan).setEnabled(false)
        } else if (!bluetoothAdapter!!.isEnabled) {
            menu.findItem(R.id.ble_scan).setEnabled(false)
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().registerReceiver(discoveryBroadcastReceiver, discoveryIntentFilter)
        if (bluetoothAdapter == null) {
            setEmptyText(getString(R.string.bluetooth_le_not_supported))
        } else if (!bluetoothAdapter!!.isEnabled) {
            setEmptyText(getString(R.string.bluetooth_is_disabled))
            if (menu != null) {
                listItems.clear()
                listAdapter!!.notifyDataSetChanged()
                menu!!.findItem(R.id.ble_scan).setEnabled(false)
            }
        } else {
            setEmptyText(getString(R.string.use_scan_to_refresh_devices))
            if (menu != null) menu!!.findItem(R.id.ble_scan).setEnabled(true)
        }
    }

    override fun onPause() {
        super.onPause()
        stopScan()
        requireActivity().unregisterReceiver(discoveryBroadcastReceiver)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        menu = null
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        sharedPref = this.requireActivity().getPreferences(AppCompatActivity.MODE_PRIVATE)
        listFilterOption = sharedPref.getBoolean("filterNames", false)
        menu.findItem(R.id.filterNames).setChecked(listFilterOption)

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.ble_scan -> {
                startScan()
                true
            }

            R.id.filterNames -> {

                if (listFilterOption) {
                    listFilterOption = false
                    item.setChecked(false)
                } else {
                    listFilterOption = true
                    item.setChecked(true)
                }
                val editor = sharedPref.edit()
                editor.putBoolean("filterNames", listFilterOption)
                editor.apply()

                true
            }

            R.id.ble_scan_stop -> {
                stopScan()
                true
            }

            R.id.bt_settings -> {
                val intent = Intent()
                intent.setAction(Settings.ACTION_BLUETOOTH_SETTINGS)
                startActivity(intent)
                true
            }

            R.id.loc_settings -> {
                val intent = Intent()
                intent.setAction(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
                true
            }

            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }

    private fun startScan() {
        if (scanState != ScanState.NONE) return
        val nextScanState = ScanState.LE_SCAN
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!BLEutil.hasPermissions(
                    this,
                    requestBluetoothPermissionLauncherForStartScan
                )
            ) return
        } else {
            if (requireActivity().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                scanState = ScanState.NONE
                val builder = AlertDialog.Builder(activity)
                builder.setTitle(R.string.location_permission_title)
                builder.setMessage(R.string.location_permission_grant)
                builder.setPositiveButton(
                    android.R.string.ok
                ) { _: DialogInterface?, _: Int ->
                    requestLocationPermissionLauncherForStartScan.launch(
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                }
                builder.show()
                return
            }
            val locationManager =
                requireActivity().getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var locationEnabled = false
            try {
                locationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            } catch (ignored: Exception) {
            }
            try {
                locationEnabled =
                    locationEnabled or locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            } catch (ignored: Exception) {
            }
            if (!locationEnabled) scanState = ScanState.DISCOVERY
            // Starting with Android 6.0 a bluetooth scan requires ACCESS_COARSE_LOCATION permission, but that's not all!
            // LESCAN also needs enabled 'location services', whereas DISCOVERY works without.
            // Most users think of GPS as 'location service', but it includes more, as we see here.
            // Instead of asking the user to enable something they consider unrelated,
            // we fall back to the older API that scans for bluetooth classic _and_ LE
            // sometimes the older API returns less results or slower
        }
        scanState = nextScanState
        listItems.clear()
        listAdapter!!.notifyDataSetChanged()
        setEmptyText(getString(R.string.scanning))
        menu!!.findItem(R.id.ble_scan).setVisible(false)
        menu!!.findItem(R.id.ble_scan_stop).setVisible(true)
        if (scanState == ScanState.LE_SCAN) {
            leScanStopHandler.postDelayed(leScanStopCallback, LE_SCAN_PERIOD)
            Thread({ bluetoothAdapter!!.startLeScan(null, leScanCallback) }, "startLeScan")
                .start() // start async to prevent blocking UI, because startLeScan sometimes take some seconds
        } else {
            bluetoothAdapter!!.startDiscovery()
        }
    }

    init {
        leScanCallback =
            LeScanCallback { device: BluetoothDevice?, _: Int, _: ByteArray? ->
                if (device != null && activity != null) {
                    requireActivity().runOnUiThread { updateScan(device) }
                }
            }
        discoveryBroadcastReceiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context, intent: Intent) {
                if (BluetoothDevice.ACTION_FOUND == intent.action) {
                    val device =
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)!!
                    if (device.type != BluetoothDevice.DEVICE_TYPE_CLASSIC && activity != null) {
                        activity!!.runOnUiThread { updateScan(device) }
                    }
                }
                if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == intent.action) {
                    scanState = ScanState.DISCOVERY_FINISHED // don't cancel again
                    stopScan()
                }
            }
        }
        discoveryIntentFilter = IntentFilter()
        discoveryIntentFilter.addAction(BluetoothDevice.ACTION_FOUND)
        discoveryIntentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        leScanStopCallback =
            Runnable { stopScan() } // w/o explicit Runnable, a new lambda would be created on each postDelayed, which would not be found again by removeCallbacks
        requestBluetoothPermissionLauncherForStartScan =
            registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { granted: Map<String, Boolean> ->
                BLEutil.onPermissionsResult(
                    this,
                    granted
                ) { startScan() }
            }
        requestLocationPermissionLauncherForStartScan = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted: Boolean ->
            if (granted) {
                Handler(Looper.getMainLooper()).postDelayed(
                    { startScan() },
                    1
                ) // run after onResume to avoid wrong empty-text
            } else {
                val builder = AlertDialog.Builder(activity)
                builder.setTitle(getText(R.string.location_permission_title))
                builder.setMessage(getText(R.string.location_permission_denied))
                builder.setPositiveButton(android.R.string.ok, null)
                builder.show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateScan(device: BluetoothDevice) {
        if (scanState == ScanState.NONE) return
        val device2 = BLEutil.Device(device) // slow getName() only once
        deviceName = device2.name
        if (skipUnnamed && (deviceName == null || deviceName!!.isEmpty())) {
            return
        }
        if (listFilterOption) {
            if (!deviceName!!.contains("HM") && !deviceName!!.contains("HC")) {
                return
            }
        }
        val pos = Collections.binarySearch(listItems, device2)
        if (pos < 0) {
            listItems.add(-pos - 1, device2)
            listAdapter!!.notifyDataSetChanged()
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (scanState == ScanState.NONE) return
        setEmptyText(getString(R.string.no_bluetooth_devices_found))
        if (menu != null) {
            menu!!.findItem(R.id.ble_scan).setVisible(true)
            menu!!.findItem(R.id.ble_scan_stop).setVisible(false)
        }
        when (scanState) {
            ScanState.LE_SCAN -> {
                leScanStopHandler.removeCallbacks(leScanStopCallback)
                bluetoothAdapter!!.stopLeScan(leScanCallback)
            }

            ScanState.DISCOVERY -> bluetoothAdapter!!.cancelDiscovery()
            else -> {}
        }
        scanState = ScanState.NONE
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        stopScan()

        val device = listItems[position - 1]
        val args = Bundle()
        args.putString("device", device.device.address)
        val fragment: Fragment = PageControllerFragment()
        fragment.arguments = args
        assert(fragmentManager != null)
        requireFragmentManager().beginTransaction().replace(R.id.fragment, fragment, "ble")
            .addToBackStack(null).commit()
    }


    companion object {
        private const val LE_SCAN_PERIOD: Long = 10000 // similar to bluetoothAdapter.startDiscovery
    }
}
