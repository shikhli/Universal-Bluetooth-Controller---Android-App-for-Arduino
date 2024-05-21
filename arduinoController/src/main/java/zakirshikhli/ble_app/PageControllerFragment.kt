package zakirshikhli.ble_app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import zakirshikhli.ble_app.BLEserialService.SerialBinder
import zakirshikhli.ble_app.PageMainActivity.Companion.btIsClassic
import java.io.IOException
import java.io.OutputStream
import java.util.ArrayDeque


@Suppress("DEPRECATION")
class PageControllerFragment : Fragment(), ServiceConnection, BLEserialListener {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }


    // COMMON
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var ledEnabledBool: Boolean = true
    private var dirReversedBool: Boolean = false
    private var highSpeedBool: Boolean = false
    private var tankModeBool: Boolean = false
    private lateinit var view: View
    private lateinit var indicatorCon: ImageView
    private lateinit var connStatus: AppCompatTextView
    private lateinit var mainActivity: View

    //    private lateinit var sharedPref: SharedPreferences
    private lateinit var joysticksNormal: View
    private lateinit var joysticksTank: View


    // CLASSIC
    private lateinit var outputStream: OutputStream


    // BLE
    private var deviceAddress: String? = null
    private var service: BLEserialService? = null
    private var connected = Connected.False
    private var initialStart = true

    private enum class Connected {
        False,
        Pending,
        True
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        retainInstance = true

        if (!btIsClassic) {
            // is BLE
            assert(arguments != null)
            deviceAddress = requireArguments().getString("device")
        }


        dirReversedBool = false
        highSpeedBool = false
        tankModeBool = false
    }

    private fun checkAndRequestPermissions() {
        val requiredPermissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                Log.e("TAG", "BLUETOOTH_CONNECT = PERMISSION_GRANTED")
            }

        } else {
            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.BLUETOOTH
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requiredPermissions.add(Manifest.permission.BLUETOOTH)
            } else {
                Log.e("TAG", "BLUETOOTH = PERMISSION_GRANTED")
            }

            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.BLUETOOTH_ADMIN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requiredPermissions.add(Manifest.permission.BLUETOOTH_ADMIN)
            } else {
                Log.e("TAG", "BLUETOOTH_ADMIN = PERMISSION_GRANTED")
            }
        }


        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            Log.e("TAG", "ACCESS_FINE_LOCATION = PERMISSION_GRANTED")
        }




        if (requiredPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                requiredPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        } else {
            Log.e("TAG", "ALL PERMISSIONS GRANTED")
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            pairedDevices = bluetoothAdapter.bondedDevices

            startLocationPermissionRequest()
//            connectToDevice()
        }
    }

    private lateinit var pairedDevices: Set<BluetoothDevice>
    private lateinit var device: BluetoothDevice
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            connectToDevice()
        } else {
            val errorMessage: String ="Permissions are required for Bluetooth functionality"
            Log.e("TAG", errorMessage)

            Toast.makeText(
                requireContext(),
                errorMessage,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Ex. Launching ACCESS_FINE_LOCATION permission.
    private fun startLocationPermissionRequest() {
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }



    @SuppressLint("SetTextI18n")
    private fun connectToDevice() {


        // Get the default Bluetooth adapter
        if (!bluetoothAdapter.isEnabled) {
            // BT DISABLED
            connStatus.text = getString(R.string.not_connected)
            indicatorCon.setImageResource(R.drawable.indicator_red)

            val errorMessage: String =
                resources.getString(R.string.please_enable_bluetooth)
            Log.e("TAG", errorMessage)

            Toast.makeText(
                activity,
                errorMessage,
                Toast.LENGTH_SHORT
            ).show()
            return
        }



        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            Log.e("TAG", "BLUETOOTH_CONNECT DENIED")

            Toast.makeText(activity, "BLUETOOTH_CONNECT DENIED", Toast.LENGTH_SHORT)
                .show()
            return
        } else {
            Log.e("TAG", "BLUETOOTH_CONNECT = PERMISSION_GRANTED")
        }


        if (pairedDevices.isNotEmpty()) {
            device = pairedDevices.first()
            Log.e("TAG", "device = " + device.name)
        } else {
            // CANNOT CONNECT TO PAIRED DEVICE
            connStatus.text = getString(R.string.not_connected)
            indicatorCon.setImageResource(R.drawable.indicator_red)
            Toast.makeText(
                activity,
                getString(R.string.cannot_connect_to_pd),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        var socket: BluetoothSocket? = null
        val handler = Handler(Looper.getMainLooper())
        val timeout = 5000L // Timeout in milliseconds
        try {
            socket = device.createRfcommSocketToServiceRecord(device.uuids[0].uuid)
            Log.e("TAG", "socket assigned")
            Log.e("TAG", "Socket Remote Device Name = " + socket.remoteDevice.name)

            socket.remoteDevice.name

            // Start a timer to handle the connection timeout
            handler.postDelayed({
                if (!socket.isConnected) {
                    try {
                        socket.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                    Log.e("TAG", "ConnectTimeout")
                }
            }, timeout)

            socket.connect()
            Log.e("TAG", "socket connected")

            outputStream = socket.outputStream
            Log.e("TAG", "outputStream assigned")

            connStatus.text = getString(R.string.conn_status) + device.address
            indicatorCon.setImageResource(R.drawable.indicator_green)

            Toast.makeText(
                activity, getString(R.string.connected_toast), Toast.LENGTH_SHORT
            ).show()

            // If connection successful, remove the timeout handler
            handler.removeCallbacksAndMessages(null)

        } catch (e: IOException) {
            e.printStackTrace()

            connStatus.text = getString(R.string.not_connected)
            indicatorCon.setImageResource(R.drawable.indicator_red)

            val errorMessage: String =
                resources.getString(R.string.failed_to_connect_to_the_device) + e.message
            Log.e("TAG", errorMessage)

            Toast.makeText(
                activity,
                errorMessage,
                Toast.LENGTH_SHORT
            ).show()

            // Attempt to close the socket if an error occurs
            try {
                socket?.close()
            } catch (closeException: IOException) {
                closeException.printStackTrace()
            }
        }
    }


    override fun onDestroy() {
        if (!btIsClassic) {
            if (connected != Connected.False) disconnect()
            requireActivity().stopService(Intent(activity, BLEserialService::class.java))
        }
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()

        if (btIsClassic) return

        if (service != null) {
            service!!.attach(this)
        } else requireActivity().startService(
            Intent(
                activity,
                BLEserialService::class.java
            )
        )
    }

    override fun onStop() {
        if (!btIsClassic && service != null && !requireActivity().isChangingConfigurations) service!!.detach()
        super.onStop()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        if (btIsClassic) return

        requireActivity().bindService(
            Intent(this.requireActivity(), BLEserialService::class.java),
            this,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onDetach() {
        if (!btIsClassic) {
            try {
                requireActivity().unbindService(this)
            } catch (ignored: Exception) {
            }
        }
        super.onDetach()
    }

    override fun onResume() {
        super.onResume()

        if (btIsClassic) return

        if (initialStart && service != null) {
            initialStart = false
            requireActivity().runOnUiThread { connect() }
        }
    }

    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        if (btIsClassic) return

        service = (binder as SerialBinder).service
        service!!.attach(this)
        if (initialStart && isResumed) {
            initialStart = false
            requireActivity().runOnUiThread { connect() }
        }
    }

    override fun onServiceDisconnected(name: ComponentName) {

        if (btIsClassic) return

        service = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        view = inflater.inflate(R.layout.page_controller_fragment, container, false)
        mainActivity = inflater.inflate(R.layout.page_main_activity, container, false)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        uiFunc()

        checkAndRequestPermissions() // Check and request permissions

        sendCharacter('4') // STOP CAR
        sendCharacter('$') // STOP CAR
    }


    @SuppressLint("ClickableViewAccessibility")
    private fun uiFunc() {
        val sliderTankLeft: AppCompatSeekBar = view.findViewById(R.id.sliderLeft)
        val sliderTankRight: AppCompatSeekBar = view.findViewById(R.id.sliderRight)
        val sliderNormalDir: AppCompatSeekBar = view.findViewById(R.id.slider_dir)
        val sliderNormalMotor: AppCompatSeekBar = view.findViewById(R.id.slider_motor)

        val restartButton: ImageButton = view.findViewById(R.id.restartButton)
        val ledSwitch: SwitchCompat = view.findViewById(R.id.led_switch)
        val reverseSwtich: SwitchCompat = view.findViewById(R.id.reverse_switch)
        val highSpeedSwitch: SwitchCompat = view.findViewById(R.id.high_speed_switch)
        val tankModeSwitch: SwitchCompat = view.findViewById(R.id.tankModeSwitch)
        joysticksNormal = view.findViewById(R.id.joysticksNormal)
        joysticksTank = view.findViewById(R.id.joysticksTank)

        var sliderLeftChar: Char
        var sliderRightChar: Char

        restartButton.setOnClickListener { restart() }

        indicatorCon = view.findViewById(R.id.indicator_con)
        indicatorCon.setImageResource(R.drawable.indicator_red)
        connStatus = view.findViewById(R.id.connStatus)


        ledSwitch.isChecked = true
        reverseSwtich.isChecked = dirReversedBool

        tankModeSwitch.isChecked = tankModeBool
        if (tankModeBool) {
            sendCharacter('T')
            joysticksTank.visibility = View.VISIBLE
            joysticksNormal.visibility = View.GONE
        } else {
            sendCharacter('t')
            joysticksNormal.visibility = View.VISIBLE
            joysticksTank.visibility = View.GONE
        }



        highSpeedSwitch.isChecked = highSpeedBool
        if (highSpeedBool) {
            sendCharacter(']')
        } else {
            sendCharacter('[')
        }

        ledSwitch.setOnClickListener {
            ledEnabledBool = if (!ledEnabledBool) {
                sendCharacter(',')
                true
            } else {
                sendCharacter('.')
                false
            }
            ledSwitch.isChecked = ledEnabledBool
        }

        reverseSwtich.setOnClickListener {
            dirReversedBool = !dirReversedBool
            reverseSwtich.isChecked = dirReversedBool
        }


        tankModeSwitch.setOnClickListener {
            tankModeBool = !tankModeBool
            tankModeSwitch.isChecked = tankModeBool

            // Change control mode
            if (tankModeBool) {
                sendCharacter('T')
                joysticksTank.visibility = View.VISIBLE
                joysticksNormal.visibility = View.GONE
            } else {
                sendCharacter('t')
                joysticksNormal.visibility = View.VISIBLE
                joysticksTank.visibility = View.GONE
            }
        }

        highSpeedSwitch.setOnClickListener {
            highSpeedBool = if (!highSpeedBool) {
                sendCharacter(']')
                true
            } else {
                sendCharacter('[')
                false
            }

            highSpeedSwitch.isChecked = highSpeedBool
        }

        // Disable Toolbar
        activity?.findViewById<View>(R.id.appBarLayout)!!.visibility = View.GONE



        sliderNormalDir.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                sliderNormalDir.progress = 5
                sendCharacter('$')
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {

            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!dirReversedBool) {
                    sliderLeftChar = when (progress) {
                        0 -> ')'
                        1 -> '!'
                        2 -> '@'
                        3 -> '#'
                        5 -> '$' // 5 is Zero, 4 & 6 are Dead Zones
                        7 -> '%'
                        8 -> '^'
                        9 -> '&'
                        10 -> '*'
                        else -> {
                            '$'
                        }
                    }
                } else {
                    sliderLeftChar = when (progress) {
                        0 -> '*'
                        1 -> '&'
                        2 -> '^'
                        3 -> '%'
                        5 -> '$' // 5 is Zero, 4 & 6 are Dead Zones
                        7 -> '#'
                        8 -> '@'
                        9 -> '!'
                        10 -> ')'
                        else -> {
                            '$'
                        }
                    }
                }

                sendCharacter(sliderLeftChar)
            }
        })

        sliderNormalMotor.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                sliderNormalMotor.progress = 5
                sendCharacter('4')
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {

            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!dirReversedBool) {
                    sliderRightChar = when (progress) {
                        0 -> '0'
                        1 -> '1'
                        2 -> '2'
                        3 -> '3'
                        5 -> '4' // Zero
                        7 -> '5'
                        8 -> '6'
                        9 -> '7'
                        10 -> '8'
                        else -> {
                            '4'
                        }
                    }
                } else {
                    sliderRightChar = when (progress) {
                        0 -> '8'
                        1 -> '7'
                        2 -> '6'
                        3 -> '5'
                        5 -> '4' // Zero
                        7 -> '3'
                        8 -> '2'
                        9 -> '1'
                        10 -> '0'
                        else -> {
                            '4'
                        }
                    }
                }
                sendCharacter(sliderRightChar)
            }
        })

        sliderTankLeft.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                sliderTankLeft.progress = 5
                sendCharacter('$')
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {

            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!dirReversedBool) {
                    sliderLeftChar = when (progress) {
                        0 -> ')'
                        1 -> '!'
                        2 -> '@'
                        3 -> '#'
                        5 -> '$' // 5 is Zero, 4 & 6 are Dead Zones
                        7 -> '%'
                        8 -> '^'
                        9 -> '&'
                        10 -> '*'
                        else -> {
                            '$'
                        }
                    }
                } else {
                    sliderLeftChar = when (progress) {
                        0 -> '*'
                        1 -> '&'
                        2 -> '^'
                        3 -> '%'
                        5 -> '$' // 5 is Zero, 4 & 6 are Dead Zones
                        7 -> '#'
                        8 -> '@'
                        9 -> '!'
                        10 -> ')'
                        else -> {
                            '$'
                        }
                    }
                }

                sendCharacter(sliderLeftChar)
            }
        })

        sliderTankRight.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                sliderTankRight.progress = 5
                sendCharacter('4')
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {

            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!dirReversedBool) {
                    sliderRightChar = when (progress) {
                        0 -> '0'
                        1 -> '1'
                        2 -> '2'
                        3 -> '3'
                        5 -> '4' // Zero
                        7 -> '5'
                        8 -> '6'
                        9 -> '7'
                        10 -> '8'
                        else -> {
                            '4'
                        }
                    }
                } else {
                    sliderRightChar = when (progress) {
                        0 -> '8'
                        1 -> '7'
                        2 -> '6'
                        3 -> '5'
                        5 -> '4' // Zero
                        7 -> '3'
                        8 -> '2'
                        9 -> '1'
                        10 -> '0'
                        else -> {
                            '4'
                        }
                    }
                }
                sendCharacter(sliderRightChar)
            }
        })


    }

    private fun restart() {
        val intent = activity?.intent
        activity?.finish()
        startActivity(intent)
    }


    ///////////////////
    // BLE

    private fun connect() {
        try {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
            connected = Connected.Pending
            val socket = BLEserialSocket(requireActivity().applicationContext, device)
            service!!.connect(socket)
        } catch (e: Exception) {
            onSerialConnectError(e)
        }
    }

    private fun disconnect() {
        connected = Connected.False
        service!!.disconnect()
    }

    private fun sendCharacter(character: Char) {
        if (btIsClassic) {
//            Log.e("TAG", character.toString())
            if (::outputStream.isInitialized) {
                try {
                    outputStream.write(character.code)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        } else {
            if (connected != Connected.True) {
                connStatus.text = getString(R.string.not_connected)
                indicatorCon.setImageResource(R.drawable.indicator_red)
                Toast.makeText(activity, getString(R.string.not_connected), Toast.LENGTH_SHORT)
                    .show()
                return
            }
            try {
                val data: ByteArray = character.toString().toByteArray()
                service!!.write(data)
            } catch (e: Exception) {
                onSerialIoError(e)
            }
        }
    }


    @SuppressLint("SetTextI18n")
    override fun onSerialConnect() {
        if (btIsClassic) return

        connStatus.text = getString(R.string.conn_status) + deviceAddress
        indicatorCon.setImageResource(R.drawable.indicator_green)
        Toast.makeText(activity, getString(R.string.connected_toast), Toast.LENGTH_SHORT).show()
        connected = Connected.True
    }

    override fun onSerialConnectError(e: Exception?) {
        if (btIsClassic) return
        connStatus.text = getString(R.string.not_connected)
        indicatorCon.setImageResource(R.drawable.indicator_red)

        val errorMessage: String =
            resources.getString(R.string.failed_to_connect_to_the_device) + e!!.message
        Log.e("TAG", errorMessage)

        Toast.makeText(
            activity,
            errorMessage,
            Toast.LENGTH_SHORT
        ).show()
        disconnect()
    }

    override fun onSerialRead(data: ByteArray) {
        if (btIsClassic) return

        val datas = ArrayDeque<ByteArray>()
        datas.add(data)
    }

    override fun onSerialRead(datas: ArrayDeque<ByteArray?>?) {}

    override fun onSerialIoError(e: Exception?) {
        if (btIsClassic) return
        connStatus.text = getString(R.string.not_connected)
        indicatorCon.setImageResource(R.drawable.indicator_red)
        Toast.makeText(activity, getString(R.string.conn_lost) + e!!.message, Toast.LENGTH_SHORT)
            .show()
        disconnect()
    }

    // BLE
    ///////////////////


}
