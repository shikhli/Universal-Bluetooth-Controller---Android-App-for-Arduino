package zakirshikhli.ble_app.classic

import android.bluetooth.BluetoothAdapter
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.ScrollingMovementMethod
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import zakirshikhli.ble_app.R
import java.util.ArrayDeque

@Suppress("DEPRECATION")
class TerminalFragment : Fragment(), SerialListener {
    private enum class Connected {
        False, Pending, True
    }

    private var deviceAddress: String? = null
    private var receiveText: TextView? = null
    private var sendText: TextView? = null


    private var connected = Connected.False
    private var initialStart = true
    private var pendingNewline = false
    private val newline: String = TextUtil.newline_crlf

    /*
     * Lifecycle
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        retainInstance = true
        deviceAddress = requireArguments().getString("device")
    }


    override fun onResume() {
        super.onResume()
        if (initialStart) {
            initialStart = false
            requireActivity().runOnUiThread { this.connect() }
        }
    }

    /*
     * UI
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view: View = inflater.inflate(R.layout.fragment_terminal_classic, container, false)
        receiveText =
            view.findViewById(R.id.receive_text) // TextView performance decreases with number of spans
        receiveText!!.setTextColor(resources.getColor(R.color.colorRecieveText)) // set as default color to reduce number of spans
        receiveText!!.movementMethod = ScrollingMovementMethod.getInstance()

        sendText = view.findViewById(R.id.send_text)
        sendText!!.setHint("")

        val sendBtn = view.findViewById<View>(R.id.send_btn)
        sendBtn.setOnClickListener { v: View? -> send(sendText!!.getText().toString()) }
        return view
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_terminal, menu)
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.clear) {
            receiveText!!.text = ""
            return true
        } else {
            return super.onOptionsItemSelected(item)
        }
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
            val socket = SerialSocket(requireActivity().applicationContext, device)
        } catch (e: Exception) {
            onSerialConnectError(e)
        }
    }


    private fun send(str: String) {
        if (connected != Connected.True) {
            Toast.makeText(activity, "not connected", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val data = (str + newline).toByteArray()
            val spn = SpannableStringBuilder(str + '\n')
            spn.setSpan(
                ForegroundColorSpan(resources.getColor(R.color.colorSendText)),
                0,
                spn.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            receiveText!!.append(spn)
        } catch (e: Exception) {
            onSerialIoError(e)
        }
    }

    private fun receive(datas: ArrayDeque<ByteArray>) {
        val spn = SpannableStringBuilder()
        for (data in datas) {
            var msg = String(data)
            if (msg.isNotEmpty()) {
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
    }

    override fun onSerialConnectError(e: Exception?) {

    }

    override fun onSerialRead(data: ByteArray?) {

    }

    override fun onSerialRead(datas: ArrayDeque<ByteArray>?) {

    }

    override fun onSerialIoError(e: Exception?) {

    }

    private fun onSerialConnectError(e: Exception) {
        status("connection failed: " + e.message)
    }

    fun onSerialRead(data: ByteArray) {
        val datas = ArrayDeque<ByteArray>()
        datas.add(data)
        receive(datas)
    }

    fun onSerialRead(datas: ArrayDeque<ByteArray>) {
        receive(datas)
    }

    private fun onSerialIoError(e: Exception) {
        status("connection lost: " + e.message)
    }
}
