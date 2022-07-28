package com.cxj.bluetooth.test.dialog

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.text.TextUtils
import android.widget.RadioButton
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.lxj.xpopup.core.CenterPopupView
import com.cxj.bluetoothlib.core.BluetoothScanner
import com.cxj.bluetoothlib.listener.BluetoothScanListener
import com.cxj.bluetooth.test.R
import kotlinx.android.synthetic.main.layout_bluetooth_scanner_dialog.view.*

/**
 *  author : chenxiaojin
 *  date : 2021/7/9 下午 04:07
 *  description :
 */
class BluetoothScannerDialog(context: Context) : CenterPopupView(context), BluetoothScanListener {

    private var itemList = ArrayList<Item>()
    private var itemAdapter: ItemAdapter? = null
    var resultList: ResultList? = null
    override fun getImplLayoutId(): Int {
        return R.layout.layout_bluetooth_scanner_dialog
    }

    override fun onCreate() {
        super.onCreate()

        itemAdapter = ItemAdapter(itemList)
        rvDevice.layoutManager = LinearLayoutManager(context)
        rvDevice.adapter = itemAdapter

        BluetoothScanner.getInstance(context).setBluetoothScanListener(this)
        btnCancel.setOnClickListener {
            BluetoothScanner.getInstance(context).stopScan()
            dismiss()
        }
        btnConfirm.setOnClickListener {
            var item = itemAdapter?.getChooseItem()
            if (null == item) {
                Toast.makeText(context, "请选择蓝牙设备", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            resultList?.onChooseItem(item.name, item.mac)
            itemAdapter?.resetChooseItem()
            BluetoothScanner.getInstance(context).stopScan()
            dismiss()
        }

        btnReSearch.setOnClickListener {
            if (BluetoothScanner.getInstance(context).isScanning) {
                BluetoothScanner.getInstance(context).stopScan()
            }
            startScanBluetooth()
        }
    }


    private fun startScanBluetooth() {
        tvTips.visibility = visibility
        tvTips.text = "正在搜索蓝牙设备..."
        itemAdapter?.resetChooseItem()
        itemList.clear()
        itemAdapter?.setList(itemList)
        if (BluetoothScanner.getInstance(context).isScanning) {
            BluetoothScanner.getInstance(context).stopScan()
        }
        BluetoothScanner.getInstance(context).startScan()
    }


    override fun dismiss() {
        super.dismiss()
        itemAdapter?.resetChooseItem()
        itemList.clear()
        itemAdapter?.setList(itemList)
    }

    override fun onShow() {
        super.onShow()
        startScanBluetooth()
    }

    override fun onStartScan() {
        tvTips.visibility
    }

    override fun onScanFailed(errorMessage: String?) {
        tvTips.text = "扫描蓝牙失败:$errorMessage"
    }

    override fun onDeviceFounded(device: BluetoothDevice, rssi: Int, scanRecord: ByteArray) {
        if (TextUtils.isEmpty(device.name)) {
            return
        }
        tvTips.visibility = GONE
        itemList.forEach {
            if (it.mac.contentEquals(device.address)) {
                return
            }
        }
        itemList.add(Item(device.name, device.address))
        itemAdapter?.setList(itemList)
    }

    override fun onStopScan() {
        if (itemList.isEmpty()) {
            tvTips.text = "没有搜索到蓝牙设备"
        }
    }

    data class Item(var name: String, var mac: String)

    private class ItemAdapter(itemList: List<Item>) :
        BaseQuickAdapter<Item, BaseViewHolder>(R.layout.rv_item) {
        private var itemList: List<Item> = java.util.ArrayList()
        private var chooseItem: Item? = null

        init {
            this.itemList = itemList
        }


        override fun convert(holder: BaseViewHolder, item: Item) {
            holder.setText(R.id.tvName, item.name)
            holder.setText(R.id.tvMac, item.mac)
            val rbCheck = holder.getView<RadioButton>(R.id.rbCheck)
            rbCheck.setOnCheckedChangeListener(null)
            rbCheck.isChecked = chooseItem == item
            rbCheck.setOnCheckedChangeListener { buttonView, isChecked ->
                chooseItem = if (isChecked) {
                    item
                } else {
                    null
                }
                notifyDataSetChanged()
            }
        }

        fun getChooseItem(): Item? {
            return chooseItem
        }

        fun resetChooseItem() {
            chooseItem = null
        }
    }

    interface ResultList {
        fun onChooseItem(name: String, mac: String)
    }
}