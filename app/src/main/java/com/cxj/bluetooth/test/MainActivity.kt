package com.cxj.bluetooth.test

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.lxj.xpopup.XPopup
import com.cxj.bluetoothlib.bean.BluetoothDeviceData
import com.cxj.bluetoothlib.bean.BluetoothOptions
import com.cxj.bluetoothlib.core.BluetoothLEDevice
import com.cxj.bluetoothlib.listener.BluetoothDeviceDataCallback
import com.cxj.bluetoothlib.listener.BluetoothDeviceStateListener
import com.cxj.bluetoothlib.util.ByteUtil
import com.cxj.bluetooth.test.device.util.ConvertUtils
import com.cxj.bluetooth.test.dialog.BluetoothScannerDialog
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.toast

@SuppressLint("LogNotTimber")
class MainActivity : AppCompatActivity(), View.OnClickListener,
     BluetoothDeviceStateListener ,BluetoothDeviceDataCallback{
    private val TAG = "MainActivity"
    private var device: BluetoothLEDevice? = null
    private var logStr = StringBuilder()
    private lateinit var bluetoothScannerDialog: BluetoothScannerDialog
    private var isChooseConnectDevice = true
    private var deviceMac = ""

    //设备服务服务、读、写特征uuid TODO FIXME 更换成设备实际UUID
    val SERVICE_UUID = "432141233-b432-4323-443e-1ae50e24da99"
    val WRITE_CHARACTERISTIC_UUID = "432141233-b432-4323-443e-1ae50e24da99"
    val NOTIFY_CHARACTERISTIC_UUID = "432141233-b432-4323-443e-1ae50e24da99"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initBluetoothDialog()

        btnChoose.setOnClickListener(this)
        btnConnect.setOnClickListener(this)
        btnSendCommand.setOnClickListener(this)
        btnClear.setOnClickListener(this)
    }


    private fun initBluetoothDialog() {
        bluetoothScannerDialog = BluetoothScannerDialog(this)
        bluetoothScannerDialog.resultList = object : BluetoothScannerDialog.ResultList {
            override fun onChooseItem(name: String, mac: String) {
                if (isChooseConnectDevice) {
                    if (null != device && device?.isConnected!!) {
                        disconnectDevice()
                    }
                    tvDeviceStatus.text = "$name - $mac - 未连接"
                    deviceMac = mac
                }
            }

        }
    }


    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnChoose -> {
                isChooseConnectDevice = true
                XPopup.Builder(applicationContext).asCustom(bluetoothScannerDialog).show()
            }
            R.id.btnConnect -> {
                if (null != device && device?.isConnected!!) {
                    disconnectDevice()
                } else {
                    if (TextUtils.isEmpty(deviceMac)) {
                        toast("请先选择要连接的设备")
                        return
                    }
                    connectDevice(deviceMac)
                }
            }


            R.id.btnSendCommand -> {
                val command = etCommand.text.toString()
                if (null == device || !device?.isConnected!!) {
                    toast("设备未连接")
                    return
                }
                if (TextUtils.isEmpty(command)) {
                    toast("请输入完整指令")
                    return
                }
                try {
                    device?.writeData(ConvertUtils.hexString2Bytes(command))
                } catch (e: Exception) {
                    toast("发送指令失败:${e.message}")
                }
            }
            R.id.btnClear -> {
                logStr = StringBuilder()
                tvLog.text = logStr.toString()
            }

        }
    }

    override fun onDataChanged(deviceData: BluetoothDeviceData?) {
        log("Receive data: ${ByteUtil.bytesToHex(deviceData?.data)}")
    }

    override fun onDataRead(deviceData: BluetoothDeviceData?, status: Int) {
        log("onDataRead data: ${ByteUtil.bytesToHex(deviceData?.data)}")
    }

    override fun onDataWrite(deviceData: BluetoothDeviceData?, status: Int) {
        log("onDataWrite data: ${ByteUtil.bytesToHex(deviceData?.data)}")
    }

    override fun onWriteError(
        deviceMac: String?,
        serviceUUID: String?,
        characteristicUUID: String?,
        errorMessage: String?
    ) {
        log("write error: ${errorMessage}")
    }

    override fun onReadError(
        deviceMac: String?,
        serviceUUID: String?,
        characteristicUUID: String?,
        errorMessage: String?
    ) {
        log("read error: ${errorMessage}")
    }

    private fun connectDevice(mac: String) {
        var bluetoothOptions = BluetoothOptions.Builder().setAutoConnect(true)
            .setDeviceMac(mac)
            .setServiceUUID(SERVICE_UUID)
            .setWriteCharacteristicUUID(WRITE_CHARACTERISTIC_UUID)
            .setNotifyCharacteristicUUID(NOTIFY_CHARACTERISTIC_UUID).build()
        if (null != device) {
            device?.close()
            device?.removeDeviceStateListener(this)
            device?.removeDeviceDataCallback(this)
        }
        device = BluetoothLEDevice.create(this, bluetoothOptions) as BluetoothLEDevice
        device?.addDeviceStateListener(this)
        device?.addDeviceDataCallback(this)
        log("Start to connect device :${mac}")
        btnConnect.text = "正在连接"
        tvDeviceStatus.text = "${device?.name} - ${device?.deviceMac} - 正在连接"
        device?.connect()
    }

    private fun disconnectDevice() {
        device?.close()
        device?.removeDeviceStateListener(this)
        device?.removeDeviceDataCallback(this)
        btnConnect.text = "连接设备"
        tvDeviceStatus.text = "${device?.name} - ${device?.deviceMac} - 未连接"
        log("Device disconnected")
    }

    private fun log(log: String) {
        if (logStr.length > 2000) {
            logStr = StringBuilder()
        }
        logStr.append(log).append("\n")
        runOnUiThread {
            tvLog.text = logStr.toString()
            tvLog.post {
                svLog.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }

    override fun onStateChange(deviceMac: String?, oldState: Int, newState: Int) {
        runOnUiThread {
            if (newState == BluetoothAdapter.STATE_CONNECTED) {
                log("Device connected.")
                tvDeviceStatus.text = "${device?.name} - ${device?.deviceMac} - 已连接"
                btnConnect.text = "断开设备"
            } else {
                log("Device disconnected.")
                tvDeviceStatus.text = "${device?.name} - ${device?.deviceMac} - 未连接"
                btnConnect.text = "连接设备"
            }
        }
    }

    override fun onReady(deviceMac: String?) {
    }

    override fun onDiscoverServicesError(deviceMac: String?, errorMessage: String?) {
    }

    override fun onConnectTimeout(deviceMac: String?) {
        log("Device connect timeout.")
    }
}