package com.cxj.bluetoothlib.core;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanCallback;
//import android.bluetooth.le.ScanFilter;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import androidx.core.content.ContextCompat;


import com.cxj.bluetoothlib.beacon.BeaconItem;
import com.cxj.bluetoothlib.beacon.BeaconParser;
import com.cxj.bluetoothlib.listener.BluetoothScanListener;
import com.cxj.bluetoothlib.util.BluetoothUtil;
import com.cxj.bluetoothlib.util.ByteUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author chenxiaojin
 * @date 2020/7/22
 * @description 蓝牙搜索器
 */
public class BluetoothScanner {
    private static final String TAG = "BluetoothScanner";
    private static final long DEFAULT_MAX_SCAN_DURATION = 8 * 1000L;
    private BluetoothScanListener scanListener;
    private BluetoothAdapter bluetoothAdapter;
    private ScanCallback scanCallback;
    private BluetoothAdapter.LeScanCallback leScanCallback;
    private List<Filter> filters = new ArrayList<>();
    private Handler scanHandler = new Handler();
    private Context context;
    private static BluetoothScanner bluetoothScanner;
    // 是否正在搜索设备 由于bluetoothAdapter.isDiscovering()无法检测通过startLeScan启动的搜索状态
    // 只能通过APP自己来管理
    private boolean isScanning = false;
    String SERVICE_UUID = "0000fff0-0000-1000-8000-00805f9b34fb";//0000fff0-0000-1000-8000-00805f9b34fb

    public synchronized static BluetoothScanner getInstance(Context context) {
        if (null == bluetoothScanner) {
            bluetoothScanner = new BluetoothScanner(context.getApplicationContext());
        }
        return bluetoothScanner;
    }

    private BluetoothScanner(Context context) {
        this.context = context;
        if (BluetoothUtil.isSupportBLE(context)) {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//                scanCallback = new ScanCallback() {
//                    @Override
//                    public void onScanResult(int callbackType, ScanResult result) {
//                        SparseArray<byte[]> manufacturerSpecificData = result.getScanRecord().getManufacturerSpecificData();
//                        if (manufacturerSpecificData.size() > 0) {
//                            int i = manufacturerSpecificData.keyAt(0);
//                            //根据  厂商编号为0x0016 过滤 特定的设备
//                            if (i == 0x0016) {
//                                processDeviceFounded(result.getDevice(), result.getRssi(), result.getScanRecord().getBytes());
//                            }
//                        }
//                        Log.i(TAG, "onScanResult: name: " + result.getDevice().getName() +
//                                ", address: " + result.getDevice().getAddress() +
//                                ", rssi: " + result.getRssi() + ", scanRecord: " + result.getScanRecord());
//
//                    }
//                };
//            } else {
            leScanCallback = new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
                    Log.i(TAG, "onScanResult: name: " + device.getName() + ", address: "
                            + device.getAddress() + ", rssi: " + rssi + ", scanRecord: " + ByteUtil.bytesToHexSegment(scanRecord));
                    List<BeaconItem> beaconItems = BeaconParser.parseBeacon(scanRecord);
                    processDeviceFounded(device, rssi, scanRecord);
//                        for (BeaconItem beaconItem : beaconItems) {
//                            //Type 为FF 代表是厂商数据，在本应用中厂商数据的第一个字节标志是 0x16 是本公司
//                            //通过这个来过滤设备
//                            if (beaconItem.type == 0xFF) {
//                                if(beaconItem.bytes.length>0){
//                                    byte aByte = beaconItem.bytes[0];
//                                    int companyId = aByte;
//                                    if (companyId == 0x16) {
//
//                                    }
//                                }
////                                Log.e("beaconItem.type == 0xFF",ByteUtil.bytesToHexSegment(beaconItem.bytes));
//                            }
//                        }
//                        processDeviceFounded(device, rssi, scanRecord);
                }
            };
//            }
        } else {
            Log.e(TAG, "Current device is not support low energy bluetooth.");
        }
    }

    public void startScan() {
        startScan(DEFAULT_MAX_SCAN_DURATION);
    }

    /**
     * 扫描蓝牙设备
     * 注意如果当前正在搜索蓝牙,  会先停止扫描，再重新扫描
     * 注意不要多次扫描，一般30s不能扫描超过5次，否则有可能一直扫描不到数据
     *
     * @param scanDuration 扫描时长
     */
    public void startScan(long scanDuration) {
        Log.e(TAG, "Start to scan device...");
        if (null == bluetoothAdapter) {
            if (null != scanListener) {
                scanListener.onScanFailed("Not support low energy bluetooth.");
            }
            Log.e(TAG, "Not support low energy bluetooth.");
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            if (null != scanListener) {
                scanListener.onScanFailed("Bluetooth is close. Please open and retry");
            }
            Log.e(TAG, "Bluetooth is close. Please open and retry");
            return;
        }


        // 检查权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context,
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                if (null != scanListener) {
                    scanListener.onScanFailed(
                            "Can not scan device, miss ACCESS_COARSE_LOCATION " +
                                    "or ACCESS_FINE_LOCATION permission.");
                }
                Log.e(TAG, "Can not scan device, miss ACCESS_COARSE_LOCATION " +
                        "or ACCESS_FINE_LOCATION permission.");
                return;
            }
        }

        if (isScanning) {
            stopScan();
        }

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            if (bluetoothAdapter.getBluetoothLeScanner() != null) {
////                    // 搜索特定服务的蓝牙
////                    List<ScanFilter> filters = new ArrayList<>();
////                    ScanFilter filter = new ScanFilter.Builder()
////                            .setServiceUuid(ParcelUuid.fromString(SERVICE_UUID))
////                            .build();
////                    filters.add(filter);
////                    bluetoothAdapter.getBluetoothLeScanner().startScan(filters, new ScanSettings.Builder()
////                            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback);
//                // 搜索所有蓝牙设备
//                bluetoothAdapter.getBluetoothLeScanner().startScan(scanCallback);
//            } else {
//                if (null != scanListener) {
//                    scanListener.onScanFailed("BluetoothLeScanner is null. Can not scan device");
//                }
//                Log.e(TAG, "BluetoothLeScanner is null. Can not scan device");
//                return;
//            }
//        } else {
//        UUID[] serviceUuids = new UUID[]{UUID.fromString(SERVICE_UUID)};
//        bluetoothAdapter.startLeScan(serviceUuids,leScanCallback);
        bluetoothAdapter.startLeScan(leScanCallback);
//        }

        isScanning = true;
        if (null != scanListener) {
            scanListener.onStartScan();
        }

        scanHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopScan();
            }
        }, scanDuration);
    }

    public void stopScan() {
        scanHandler.removeCallbacksAndMessages(null);
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            if (bluetoothAdapter.getBluetoothLeScanner() != null) {
//                bluetoothAdapter.getBluetoothLeScanner().stopScan(scanCallback);
//            }
//        } else {
        bluetoothAdapter.stopLeScan(leScanCallback);
//        }
        isScanning = false;
        if (null != scanListener) {
            scanListener.onStopScan();
        }
    }

    private void processDeviceFounded(BluetoothDevice device, int rssi, byte[] scanRecord) {
        // 根据过滤器筛选设备
        if (!filters.isEmpty()) {
            for (Filter filter : filters) {
                if (!filter.onFilter(device, rssi, scanRecord)) {
                    Log.d(TAG, "Device is not match filter condition. Ignore it.");
                    return;
                }
            }
        }

        if (null != scanListener) {
            scanListener.onDeviceFounded(device, rssi, scanRecord);
        }
    }

    public boolean isScanning() {
        return isScanning;
    }

    public void setBluetoothScanListener(BluetoothScanListener scanListener) {
        this.scanListener = scanListener;
    }

    public void addFilter(Filter filter) {
        this.filters.add(filter);
    }

    public void removeFilter(Filter filter) {
        this.filters.remove(filter);
    }

    public void clearFilters() {
        this.filters.clear();
    }

    public interface Filter {
        /**
         * 过滤设备
         *
         * @param device     原始设备
         * @param rssi       信号
         * @param scanRecord 广播数据
         * @return true 设备满足要求, false 设备不满足要求, 不通过onDeviceFounded返回
         */
        boolean onFilter(BluetoothDevice device, int rssi, byte[] scanRecord);
    }

}
