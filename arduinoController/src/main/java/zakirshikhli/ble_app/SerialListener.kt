package zakirshikhli.ble_app

import java.util.ArrayDeque

public interface SerialListener {
    fun onSerialConnect()
    fun onSerialConnectError(e: Exception?)
    fun onSerialRead(data: ByteArray?) // socket -> service
    fun onSerialRead(datas: ArrayDeque<ByteArray?>?) // service -> UI thread
    fun onSerialIoError(e: Exception?)
}
