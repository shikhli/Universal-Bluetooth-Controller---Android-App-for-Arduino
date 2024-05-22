@file:Suppress("DEPRECATION")

package zakirshikhli.ble_app.classic

import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.ScrollingMovementMethod
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import zakirshikhli.ble_app.R
import zakirshikhli.ble_app.SerialListener
import java.util.ArrayDeque


/** @noinspection deprecation
 */
class TerminalFragment : Fragment(), ServiceConnection, SerialListener {
    private enum class Connected {
        False, Pending, True
    }

    private var deviceAddress: String? = null
    private var service: ClassicSerialService? = null

    private var receiveText: TextView? = null
    private var sendText: TextView? = null

    private var connected = Connected.False
    private var initialStart = true
    private var pendingNewline = false

    /*
     * Lifecycle
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        retainInstance = true
        if (arguments != null) {
            deviceAddress = requireArguments().getString("device")
        }
    }

    override fun onDestroy() {
        if (connected != Connected.False) disconnect()
        requireActivity().stopService(Intent(activity, ClassicSerialService::class.java))
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()

        if (service != null) {
            service!!.attach(this)
        } else requireActivity().startService(
            Intent(
                activity,
                ClassicSerialService::class.java
            )
        )
    }

    override fun onStop() {
        if (service != null && !requireActivity().isChangingConfigurations) {
            service!!.detach()
        }
        super.onStop()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        requireActivity().bindService(
            Intent(
                activity,
                ClassicSerialService::class.java
            ), this,
            Context.BIND_AUTO_CREATE
        )
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
        if (initialStart && service != null) {
            initialStart = false
            requireActivity().runOnUiThread { this.connect() }
        }
    }

    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        service = (binder as ClassicSerialService.SerialBinder).service
        service!!.attach(this)
        if (initialStart && isResumed) {
            initialStart = false
            requireActivity().runOnUiThread { this.connect() }
        }
    }

    override fun onServiceDisconnected(name: ComponentName) {
        service = null
    }


    /*
     * UI
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_classic_terminal, container, false)
        receiveText =
            view.findViewById(R.id.receive_text) // TextView performance decreases with number of spans
        receiveText!!.setTextColor(resources.getColor(R.color.colorRecieveText)) // set as default color to reduce number of spans
        receiveText!!.movementMethod = ScrollingMovementMethod.getInstance()

        sendText = view.findViewById(R.id.send_text)
        val sendBtn = view.findViewById<View>(R.id.send_btn)
        sendBtn.setOnClickListener { v: View? -> send(sendText!!.getText().toString()) }
        return view
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_terminal, menu)
    }


    /*
     * Serial + UI
     */
    private fun connect() {
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
            status("connecting...")
            connected = Connected.Pending
            val socket = ClassicSerialSocket(requireActivity().applicationContext, device)
            service!!.connect(socket)
        } catch (e: Exception) {
            onSerialConnectError(e)
        }
    }

    private fun disconnect() {
        connected = Connected.False
        service!!.disconnect()
    }

    private fun send(str: String) {
        if (connected != Connected.True) {
            Toast.makeText(activity, "not connected", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val data: ByteArray

            val msg = str
            val newline = TextUtil.newline_crlf
            data = (str + newline).toByteArray()

            val spn = SpannableStringBuilder(msg + '\n')
            spn.setSpan(
                ForegroundColorSpan(resources.getColor(R.color.colorSendText)),
                0,
                spn.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            receiveText!!.append(spn)
            service!!.write(data)
        } catch (e: Exception) {
            onSerialIoError(e)
        }
    }

    private fun receive(datas: ArrayDeque<ByteArray?>?) {
        val spn = SpannableStringBuilder()
        for (data in datas!!) {
            var msg = String(data!!)
            if (!msg.isEmpty()) {
                // don't show CR as ^M if directly before LF
                msg = msg.replace(TextUtil.newline_crlf, TextUtil.newline_lf)
                // special handling if CR and LF come in separate fragments
                if (pendingNewline && msg[0] == '\n') {
                    if (spn.length >= 2) {
                        spn.delete(spn.length - 2, spn.length)
                    } else {
                        val edt = receiveText!!.editableText
                        if (edt != null && edt.length >= 2) edt.delete(edt.length - 2, edt.length)
                    }
                }
                pendingNewline = msg[msg.length - 1] == '\r'
            }
            spn.append(TextUtil.toCaretString(msg, true))
        }
        receiveText!!.append(spn)
    }

    private fun status(str: String) {
        val spn = SpannableStringBuilder(str + '\n')
        spn.setSpan(
            ForegroundColorSpan(resources.getColor(R.color.colorStatusText)),
            0,
            spn.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        receiveText!!.append(spn)
    }

    /*
     * SerialListener
     */
    override fun onSerialConnect() {
        status("connected")
        connected = Connected.True
        Toast.makeText(activity, "Connected", Toast.LENGTH_SHORT).show()
    }

    override fun onSerialConnectError(e: Exception?) {
        assert(e != null)
        status("connection failed: " + e!!.message)
        disconnect()
    }

    override fun onSerialRead(data: ByteArray?) {
        val datas = ArrayDeque<ByteArray?>()
        datas.add(data)
        receive(datas)
    }

    override fun onSerialRead(datas: ArrayDeque<ByteArray?>?) {
        receive(datas)
    }

    override fun onSerialIoError(e: Exception?) {
        assert(e != null)
        status("connection lost: " + e!!.message)
        disconnect()
    }
}
