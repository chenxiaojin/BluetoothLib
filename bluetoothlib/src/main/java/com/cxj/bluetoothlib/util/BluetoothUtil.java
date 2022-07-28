package com.cxj.bluetoothlib.util;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * @author chenxiaojin
 * @date 2020/7/20
 * @description
 */
public class BluetoothUtil {

    private static final String TAG = "BluetoothUtil";
    private static BluetoothManager bluetoothManager;
    private static BluetoothAdapter bluetoothAdapter;


    /**
     * 是否支持低功耗蓝牙
     *
     * @param context
     * @return
     */
    public static boolean isSupportBLE(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2
                && context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE);
    }

    /**
     * 是否正在扫描设备
     *
     * @return
     */
    public static boolean isScanningDevice() {
        return getBluetoothAdapter().isDiscovering();
    }

    public static BluetoothAdapter getBluetoothAdapter() {
        if (bluetoothAdapter == null) {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }
        return bluetoothAdapter;
    }


    /**
     * 蓝牙是否开启
     *
     * @return
     */
    public static boolean isBluetoothOn() {
        return getBluetoothAdapter().isEnabled();
    }

    /**
     * 打开蓝牙
     *
     * @return
     */
    public static boolean openBluetooth() {
        BluetoothAdapter adapter = getBluetoothAdapter();
        if (adapter != null) {
            return adapter.enable();
        }
        return false;
    }

    /**
     * 关闭蓝牙
     *
     * @return
     */
    public static boolean closeBluetooth() {
        BluetoothAdapter adapter = getBluetoothAdapter();
        if (adapter != null) {
            return adapter.disable();
        }
        return false;
    }

    /**
     * 刷新设备连接缓存
     *
     * @param gatt
     * @return
     */
    public static boolean refreshGattCache(BluetoothGatt gatt) {
        Log.e(TAG, "Start to refresh device gatt cache...");
        boolean result = false;
        try {
            if (gatt != null) {
                Method refresh = BluetoothGatt.class.getMethod("refresh");
                if (refresh != null) {
                    refresh.setAccessible(true);
                    result = (boolean) refresh.invoke(gatt, new Object[0]);
                    Log.e(TAG, "Refresh device gatt cache finish.");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Refresh device gatt cache failed. error:" + e.getMessage(), e);
        }

        return result;
    }
}
