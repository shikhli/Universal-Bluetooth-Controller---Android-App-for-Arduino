package zakirshikhli.ble_app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Toast
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import zakirshikhli.ble_app.ActivityMain.Companion.btIsClassic

import zakirshikhli.ble_app.ble.BLEserialService
import zakirshikhli.ble_app.ble.BLEserialSocket

import zakirshikhli.ble_app.classic.CLserialService
import zakirshikhli.ble_app.classic.CLserialSocket

import java.util.ArrayDeque


@Suppress("DEPRECATION")
class FragmentController : Fragment(), ServiceConnection,
     zakirshikhli.ble_app.classic.SerialListener, zakirshikhli.ble_app.ble.SerialListener {

    private enum class Connected {
        False, Pending, True
    }

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var ledEnabledBool: Boolean = true
    private var dirReversedBool: Boolean = false
    private var highSpeedBool: Boolean = false
    private var tankModeBool: Boolean = false
    private lateinit var view: View
    private lateinit var indicatorCon: ImageView
    private lateinit var connStatus: AppCompatTextView
    private lateinit var mainActivity: View

    private lateinit var joysticksNormal: View
    private lateinit var joysticksTank: View

    private lateinit var device: BluetoothDevice
    private var deviceAddress: String? = null

    private var serviceBLE: BLEserialService? = null
    private var serviceClassic: CLserialService? = null

    private var connected = Connected.False
    private var initialStart = true


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        retainInstance = true

        assert(arguments != null)
        deviceAddress = requireArguments().getString("device")

        dirReversedBool = false
        highSpeedBool = false
        tankModeBool = false
    }


    override fun onDestroy() {
        if (connected != Connected.False) disconnect()

        if (btIsClassic) {
            requireActivity().stopService(
                Intent(
                    activity, CLserialService::class.java
                )
            )
        } else {
            requireActivity().stopService(
                Intent(
                    activity, BLEserialService::class.java
                )
            )
        }

        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()

        if (btIsClassic) {
            if (serviceClassic != null) {
                serviceClassic!!.attach(this)
            } else requireActivity().startService(
                Intent(
                    activity, CLserialService::class.java
                )
            )
        } else {
            if (serviceBLE != null) {
                serviceBLE!!.attach(this.serviceBLE!!)
                if (!serviceBLE!!.areNotificationsEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
                }
            } else {
                requireActivity().startService(
                    Intent(
                        activity,
                        BLEserialService::class.java
                    )
                )
            }
        }
    }

    override fun onStop() {
        if (btIsClassic) {
            if (serviceClassic != null &&
                !requireActivity().isChangingConfigurations) {
                serviceClassic!!.detach()
            }
        } else {
            if (serviceBLE != null &&
                !requireActivity().isChangingConfigurations
            ) {
                serviceBLE!!.detach()
            }
        }
        super.onStop()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        if (btIsClassic) {
            requireActivity().bindService(
                Intent(
                    activity, CLserialService::class.java
                ), this, Context.BIND_AUTO_CREATE
            )

        } else {
            requireActivity().bindService(
                Intent(activity, BLEserialService::class.java),
                this,
                Context.BIND_AUTO_CREATE
            )

        }


    }

    override fun onDetach() {
        try {
            requireActivity().unbindService(this)
        } catch (ignored: Exception) {
        }
        super.onDetach()
    }

    override fun onResume() {
        super.onResume()
        Log.e("TAG", "(onResume) initialStart = $initialStart")

        if (btIsClassic) {
            if (initialStart && serviceClassic != null) {
                initialStart = false
                requireActivity().runOnUiThread {
                    Log.e("TAG", "(onResume) connect()")
                    connect()
                }
            }
        } else {
            if (initialStart && serviceBLE != null) {
                initialStart = false
                requireActivity().runOnUiThread {
                    Log.e("TAG", "(onResume) connect()")
                    connect()
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        if (btIsClassic) {
            serviceClassic = (binder as CLserialService.SerialBinder).service
            serviceClassic!!.attach(this)
        } else {
            serviceBLE = (binder as BLEserialService.SerialBinder).service
            serviceBLE!!.attach(this)
        }

        Log.e("TAG", "(onServiceConnected) initialStart = $initialStart")

        if (initialStart && isResumed) {
            initialStart = false
            Log.e("TAG", "(onServiceConnected) connect()")
            requireActivity().runOnUiThread { connect() }
        }
    }

    override fun onServiceDisconnected(name: ComponentName) {
        if (btIsClassic) {
            serviceClassic = null
        } else {
            serviceBLE = null
        }

        connStatus.text = getString(R.string.not_connected)
        indicatorCon.setImageResource(R.drawable.indicator_red)

        val errorMessage: String = resources.getString(R.string.failed_to_connect_to_the_device)
        Log.e("TAG", errorMessage)

        Toast.makeText(
            activity, errorMessage, Toast.LENGTH_SHORT
        ).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        view = inflater.inflate(R.layout.fragment_controller, container, false)
        mainActivity = inflater.inflate(R.layout.activity_main, container, false)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        uiFunc()
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
        joysticksNormal.visibility = View.VISIBLE
        joysticksTank.visibility = View.GONE

        highSpeedSwitch.isChecked = highSpeedBool

        ledSwitch.setOnClickListener {
            ledEnabledBool = if (!ledEnabledBool) {
                send(',')
                true
            } else {
                send('.')
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
                send('T')
                joysticksTank.visibility = View.VISIBLE
                joysticksNormal.visibility = View.GONE
            } else {
                send('t')
                joysticksNormal.visibility = View.VISIBLE
                joysticksTank.visibility = View.GONE
            }
        }

        highSpeedSwitch.setOnClickListener {
            highSpeedBool = if (!highSpeedBool) {
                send(']')
                true
            } else {
                send('[')
                false
            }

            highSpeedSwitch.isChecked = highSpeedBool
        }

        // Disable Toolbar
        activity?.findViewById<View>(R.id.appBarLayout)!!.visibility = View.GONE



        sliderNormalDir.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                sliderNormalDir.progress = 5
                send('$')
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

                send(sliderLeftChar)
            }
        })

        sliderNormalMotor.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                sliderNormalMotor.progress = 5
                send('4')
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
                send(sliderRightChar)
            }
        })

        sliderTankLeft.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                sliderTankLeft.progress = 5
                send('$')
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

                send(sliderLeftChar)
            }
        })

        sliderTankRight.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                sliderTankRight.progress = 5
                send('4')
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
                send(sliderRightChar)
            }
        })


    }

    private fun restart() {
        val intent = activity?.intent
        activity?.finish()
        startActivity(intent)
    }


    ///////////////////
    // Serial

    private fun connect() = try {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        device = bluetoothAdapter.getRemoteDevice(deviceAddress)

        connected = Connected.Pending

        if (btIsClassic) {
            val socketClassic = CLserialSocket(
                requireActivity().applicationContext, device
            )
            serviceClassic!!.connect(socketClassic)
        } else {
            val socketBLE = BLEserialSocket(
                requireActivity().applicationContext, device
            )
            serviceBLE!!.connect(socketBLE)
        }



    } catch (e: Exception) {
        val errorMessage: String =
            resources.getString(R.string.failed_to_connect_to_the_device) + e.message
        Log.e("TAG", errorMessage)
        onSerialConnectError(e)
    }

    private fun disconnect() {
        connected = Connected.False

        connStatus.text = getString(R.string.not_connected)
        indicatorCon.setImageResource(R.drawable.indicator_red)
        Toast.makeText(activity, getString(R.string.not_connected), Toast.LENGTH_SHORT).show()


        if (btIsClassic) {
            serviceClassic!!.disconnect()
        } else {
            serviceBLE!!.disconnect()
        }
    }


    private fun send(character: Char) {
        Log.e("TAG", "connected = $connected")
        if (connected != Connected.True) return
        try {
            val data: ByteArray = character.toString().toByteArray()

            if (btIsClassic) {
                serviceClassic!!.write(data)
            } else {
                serviceBLE!!.write(data)
            }

        } catch (e: Exception) {
            onSerialIoError(e)
        }
    }


    ////////////////////////////////
    // SerialListener

    @SuppressLint("SetTextI18n")
    override fun onSerialConnect() {
        connected = Connected.True

        connStatus.text = getString(R.string.conn_status) + deviceAddress
        indicatorCon.setImageResource(R.drawable.indicator_green)

        Toast.makeText(activity, getString(R.string.connected_toast), Toast.LENGTH_SHORT).show()

        send('4') // STOP CAR
        send('$') // STOP CAR
        send('t') // No TankMode
        send('[') // No HighspeedMode
    }

    override fun onSerialConnectError(e: Exception?) {
        connStatus.text = getString(R.string.not_connected)
        indicatorCon.setImageResource(R.drawable.indicator_red)

        val errorMessage: String =
            resources.getString(R.string.failed_to_connect_to_the_device) + e!!.message
        Log.e("TAG", errorMessage)

        Toast.makeText(
            activity, errorMessage, Toast.LENGTH_SHORT
        ).show()

        disconnect()
    }

    override fun onSerialRead(data: ByteArray?) {
        val datas = ArrayDeque<ByteArray>()
        datas.add(data)
    }


    override fun onSerialRead(datas: ArrayDeque<ByteArray?>?) {}

    override fun onSerialIoError(e: Exception?) {
        connStatus.text = getString(R.string.not_connected)
        indicatorCon.setImageResource(R.drawable.indicator_red)
        Toast.makeText(activity, getString(R.string.conn_lost) + e!!.message, Toast.LENGTH_SHORT)
            .show()
        disconnect()
    }
    // SerialListener
    ////////////////////////////////


}
