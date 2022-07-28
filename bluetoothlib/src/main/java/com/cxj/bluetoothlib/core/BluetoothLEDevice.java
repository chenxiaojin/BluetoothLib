package com.cxj.bluetoothlib.core;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;


import com.cxj.bluetoothlib.bean.BluetoothDeviceData;
import com.cxj.bluetoothlib.bean.BluetoothMessage;
import com.cxj.bluetoothlib.bean.BluetoothOptions;
import com.cxj.bluetoothlib.listener.BluetoothDeviceDataCallback;
import com.cxj.bluetoothlib.listener.BluetoothDeviceStateListener;
import com.cxj.bluetoothlib.util.BluetoothUtil;
import com.cxj.bluetoothlib.util.ByteUtil;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author chenxiaojin
 * @date 2020/7/18
 * @description 低功耗蓝牙设备, 包含设备所有功能
 * 注意不需要使用后需要调用destroy方法, 否则会有内存泄露问题
 */
public class BluetoothLEDevice {
    private static final String TAG = "BluetoothLEDevice";
    private static final int CMD_CONNECT = 1;
    private static final int CMD_SET_MTU = 2;
    private static final String DEFAULT_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb";

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothOptions bluetoothOptions;
    private BluetoothDevice bluetoothDevice;
    private BluetoothGatt bluetoothGatt;
    // 通知特性
    private BluetoothGattCharacteristic notifyCharacteristic;
    // 写特性
    private BluetoothGattCharacteristic writeCharacteristic;

    protected Context context;
    private List<BluetoothDeviceStateListener> deviceStateListeners = new ArrayList<>();
    private List<BluetoothDeviceDataCallback> deviceDataCallbacks = new ArrayList<>();
    // 已经重试连接的次数
    private int retryConnectCount = 0;
    // 重连任务是否正在执行
    private boolean isRetryConnect = false;
    // 设备连接超时检测
    private Handler connectTimeoutHandler = new Handler(Looper.getMainLooper());
    // 是否准备就绪, 准备就绪后才能发送消息
    private boolean isReady;
    private int connectState = BluetoothProfile.STATE_DISCONNECTED;

    // 待发送数据
    private Queue<BluetoothMessage> dataQueue = new ConcurrentLinkedQueue<>();
    private ExecutorService dataSenderService;
    private boolean isCallClose = false;

    // 发送数据
    private Runnable sendDataRunnable = new Runnable() {
        @Override
        public void run() {
            // 设备已经准备好, 才能发数据
            while (isConnected()) {
                BluetoothMessage msg = dataQueue.peek();
                // 有待发送的消息且当前处于可发消息的状态, 才下发数据并从队列删除数据
                if (null != msg && isReady) {
                    // 设备忙时不下发信息
                    if (!isDeviceBusy()) {
                        sendData(msg);
                    }
                    // 消息发完后暂停10ms, 立刻发下一条设备会处于忙的状态
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        //  Log.e(TAG, "Thread failed. error:" + e.getMessage(), e);
                    }
                }
            }
        }
    };


    /**
     * 蓝牙开启状态变更广播
     */
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_ON:
                        startReconnectDeviceTask();
                        break;
                    case BluetoothAdapter.STATE_OFF:
                        connectTimeoutHandler.removeCallbacksAndMessages(null);
                        close(false);
                        break;
                }
            }
        }
    };


    /**
     * 下发消息到设备
     *
     * @param message 消息
     */
    private void sendData(BluetoothMessage message) {
        BluetoothGattCharacteristic characteristic = message.getCharacteristic();
        characteristic.setValue(message.getData());
        Log.d(TAG, "[BluetoothDevice] Send data:" + ByteUtil.bytesToHex(message.getData()));
        if (message.getMessageType() == BluetoothMessage.MessageType.WRITE) {
            boolean isSuccess = bluetoothGatt.writeCharacteristic(characteristic);
            if (isSuccess) {
                // 删除队列的数据
                dataQueue.poll();
            } else {
                notifyDataWriteError(bluetoothOptions.getDeviceMac(),
                        characteristic.getService().getUuid().toString(),
                        characteristic.getUuid().toString(), "Write characteristic result is false.");
            }
            Log.d(TAG, "device mac is " + getDeviceMac() + " Write data result:" + isSuccess + ", data:" + ByteUtil.bytesToHex(characteristic.getValue()));
        } else {
            boolean isSuccess = bluetoothGatt.readCharacteristic(characteristic);
            if (isSuccess) {
                dataQueue.poll();
            } else {
                notifyDataReadError(bluetoothOptions.getDeviceMac(),
                        characteristic.getService().getUuid().toString(),
                        characteristic.getUuid().toString(), "Read characteristic result is false.");
            }
            Log.d(TAG, "device mac is " + getDeviceMac() + " Read data result:" + isSuccess);
        }
    }

    /**
     * 主线程handler, 处理发消息，连接等操作
     */
    private Handler mainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            int what = msg.what;
            switch (what) {
                case CMD_CONNECT:
                    connectState = BluetoothProfile.STATE_CONNECTING;
                    // 通过disconnect断开的, 可以再通过bluetoothGatt重连
                    // 通过close断开, 只能重新重连获取bluetoothGatt
                    Log.e(TAG, "Start to connect device:" + bluetoothOptions.getDeviceMac());
                    if (null != bluetoothGatt) {
                        Log.e(TAG, "bluetoothGatt.connect()");
                        BluetoothUtil.refreshGattCache(bluetoothGatt);
                        bluetoothGatt.connect();
                    } else {
                        Log.e(TAG, "bluetoothDevice.connectGatt()");
                        bluetoothGatt = bluetoothDevice.connectGatt(context.getApplicationContext(),
                                false, bluetoothGattCallback);
                    }

                    connectTimeoutHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (connectState != BluetoothProfile.STATE_CONNECTED) {
                                Log.e(TAG, String.format(Locale.ENGLISH,"Connect device[%s] time out.", bluetoothOptions.getDeviceMac()));
                                notifyDeviceConnectTimeout(bluetoothOptions.getDeviceMac());
                                close(bluetoothOptions.isAutoConnect());
                                // 超时后开启重连
                                if (bluetoothOptions.isAutoConnect()) {
                                    startReconnectDeviceTask();
                                } else if (retryConnectCount < bluetoothOptions.getRetryConnectCount()) {
                                    Message msg = new Message();
                                    msg.what = CMD_CONNECT;
                                    retryConnectHandler.removeCallbacksAndMessages(null);
                                    retryConnectHandler.sendMessageDelayed(msg, 500);
                                }
                            }
                        }
                    }, bluetoothOptions.getConnectTimeout());
                    break;
                case CMD_SET_MTU:
                    boolean result = bluetoothGatt.requestMtu(msg.arg1);
                    Log.d(TAG, "Set mtu result:" + result);
                    break;
            }
        }
    };

    /**
     * 通过发射，解决发送命令时，出现的发送失败，返回结果是false的情况
     *
     * @return
     */
    private boolean isDeviceBusy() {
        boolean state = false;
        try {
            state = (boolean) readField(bluetoothGatt, "mDeviceBusy");
            Log.i(TAG, "Device is busy:" + state);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        return state;
    }

    public Object readField(Object object, String name) throws IllegalAccessException, NoSuchFieldException {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    /**
     * 重连任务
     */
    private Handler retryConnectHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            Log.e(TAG, "[RetryTask] Start to reconnect device:" + bluetoothOptions.getDeviceMac());
            if (connectState == BluetoothProfile.STATE_CONNECTED) {
                Log.e(TAG, "[RetryTask] Device is connected. no need to reconnect. mac:"
                        + bluetoothOptions.getDeviceMac());
                removeCallbacksAndMessages(null);
                return;
            } else if (connectState == BluetoothProfile.STATE_CONNECTING) {
                Log.e(TAG, "[RetryTask] Device is connecting, ignore to reconnect." + bluetoothOptions.getRetryInterval());
                retryConnectHandler.removeCallbacksAndMessages(null);
                retryConnectHandler.sendMessageDelayed(new Message(), bluetoothOptions.getRetryInterval());
                return;
            }


            // 如果不允许在搜索蓝牙设备的时候重连, 则跳过本次重连
            if (BluetoothUtil.isScanningDevice() && bluetoothOptions.isRetryWhileScanning()) {
                retryConnectHandler.sendMessageDelayed(new Message(), bluetoothOptions.getRetryInterval());
                return;
            }

            // 非持续重连的情况, 判断重连次数是否已经用完
            if (!bluetoothOptions.isAutoConnect()) {
                retryConnectCount++;
                Log.e(TAG, "[RetryTask] Current mode is not auto connect. max retry count:"
                        + bluetoothOptions.getRetryConnectCount() + ", current count:" + retryConnectCount);
                if (retryConnectCount < bluetoothOptions.getRetryConnectCount()) {
                    close(true);
                    Message connectMsg = new Message();
                    connectMsg.what = CMD_CONNECT;
                    mainHandler.sendMessageDelayed(connectMsg, 500);
                }
                if (retryConnectCount == bluetoothOptions.getRetryConnectCount()) {
                    mainHandler.removeCallbacksAndMessages(null);
                    retryConnectHandler.removeCallbacksAndMessages(null);
                    Log.e(TAG, "[RetryTask] The maximum number of reconnect has been reached, do not reconnect next time.");
                    return;
                }
            } else {
                Log.e(TAG, "[RetryTask] Current mode is auto connect.");
                close();
                Message connectMsg = new Message();
                connectMsg.what = CMD_CONNECT;
                mainHandler.sendMessageDelayed(connectMsg, 500);
                retryConnectHandler.removeCallbacksAndMessages(null);
            }
            retryConnectHandler.sendMessageDelayed(new Message(),
                    bluetoothOptions.getRetryInterval());
        }
    };


    /**
     * 设备状态、数据回调
     */
    private BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.e(TAG, "Device connection changed. oldStatus:" + status + ", newState:" + newState);
            int lastState = connectState;
            connectState = newState;
            String mac = gatt.getDevice().getAddress();
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.i(TAG, "Device connected, name:"
                            + gatt.getDevice().getName() + ",mac:" + gatt.getDevice().getAddress());

                    if (bluetoothOptions.isNeedToSetMTU()) {
                        setMTU();
                    } else {
                        Log.i(TAG, "Start to discover services...");
                        // 必须调用发现服务，才能获取到服务, 直接通过gatt.getService获取不到
                        gatt.discoverServices();
                    }
                    stopReconnectDeviceTask();
                    connectTimeoutHandler.removeCallbacksAndMessages(null);
                    startDataService();
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.e(TAG, "Device disconnect:" + mac);
                    notifyCharacteristic = null;
                    writeCharacteristic = null;
                    isReady = false;
                    // 从连接变为断开才需要启动重试机制, 否则会一直重试
                    // 断开后, status = 8, 不能用这个作为判断
                    if (lastState == BluetoothProfile.STATE_CONNECTED) {
                        startReconnectDeviceTask();
                    }
                    stopDataService();
                    if (isCallClose) {
                        isCallClose = false;
                        close();
                        bluetoothGatt = null;
                    }
                    break;
            }
            notifyConnectStateChange(mac, status, newState);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            Log.e(TAG, "onServicesDiscovered");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(bluetoothOptions.getServiceUUID());
                if (service == null) {
                    String errorMessage = "Can not find service:" + bluetoothOptions.getServiceUUID();
                    Log.e(TAG, errorMessage);
                    notifyDiscoverServicesError(bluetoothOptions.getDeviceMac(), errorMessage);
                    return;
                }

                notifyCharacteristic = service
                        .getCharacteristic(bluetoothOptions.getNotifyCharacteristicUUID());
                if (null == notifyCharacteristic) {
                    String errorMessage = "Can not find notify characteristic:"
                            + bluetoothOptions.getNotifyCharacteristicUUID();
                    Log.e(TAG, errorMessage);
                    notifyDiscoverServicesError(bluetoothOptions.getDeviceMac(), errorMessage);
                    return;
                }

                // 开启通知
                setCharacteristicNotification(notifyCharacteristic, true);
                writeCharacteristic = service.getCharacteristic(
                        bluetoothOptions.getWriteCharacteristicUUID());
                if (null == notifyCharacteristic) {
                    String errorMessage = "Can not find write characteristic:"
                            + bluetoothOptions.getWriteCharacteristicUUID();
                    Log.e(TAG, errorMessage);
                    notifyDiscoverServicesError(bluetoothOptions.getDeviceMac(), errorMessage);
                    return;
                }
                if (null != writeCharacteristic) {
                    isReady = true;
                    notifyDeviceReady(gatt.getDevice().getAddress());
                }
            } else {
                // 重新刷新缓存
                BluetoothUtil.refreshGattCache(gatt);
                notifyDiscoverServicesError(bluetoothOptions.getDeviceMac(),
                        "state error:" + status);
            }

        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic, int status) {
            Log.d(TAG, "onCharacteristicRead");
            super.onCharacteristicRead(gatt, characteristic, status);
            // 读数据回调通知
            notifyDeviceDataRead(new BluetoothDeviceData(gatt.getDevice().getAddress(),
                    characteristic.getUuid(), characteristic.getValue()), status);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic, int status) {
            Log.d(TAG, "onCharacteristicWrite");
            super.onCharacteristicWrite(gatt, characteristic, status);
            // 写数据回调通知
            notifyDeviceDataWrite(new BluetoothDeviceData(gatt.getDevice().getAddress(),
                    characteristic.getUuid(), characteristic.getValue()), status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, "onCharacteristicChanged:" + ByteUtil.bytesToHex(characteristic.getValue()));
            super.onCharacteristicChanged(gatt, characteristic);
            // 数据变更回调通知
            notifyDeviceDataChanged(new BluetoothDeviceData(gatt.getDevice().getAddress(),
                    characteristic.getUuid(), characteristic.getValue()));
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.d(TAG, "onDescriptorRead");
            super.onDescriptorRead(gatt, descriptor, status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.e(TAG, "onDescriptorWrite  " + "device mac is " + getDeviceMac());
            super.onDescriptorWrite(gatt, descriptor, status);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            Log.i(TAG, "MTU changed,  mtu=" + mtu + ", status:" + status);
            gatt.discoverServices();
        }
    };

    /**
     * 设置MTU
     */
    private void setMTU() {
        Log.i(TAG, "Start set MTU to " + bluetoothOptions.getMTU());
        Message msg = Message.obtain();
        msg.what = CMD_SET_MTU;
        msg.arg1 = bluetoothOptions.getMTU();
        // 需要延迟发送 , 连接后立马设置会设置失败
        mainHandler.sendMessageDelayed(msg, 100);
    }

    /**
     * 开始数据服务
     */
    private void startDataService() {
        Log.e(TAG, "Start data service ...");
        if (null == dataSenderService) {
            dataSenderService = Executors.newSingleThreadExecutor();
            dataSenderService.execute(sendDataRunnable);
        }
    }

    /**
     * 停止数据服务
     */
    private void stopDataService() {
        if (null != dataSenderService) {
            dataSenderService.shutdownNow();
            dataSenderService = null;
        }
    }

    public static BluetoothLEDevice create(Context context, BluetoothOptions bluetoothOptions) {
        if (null == bluetoothOptions) {
            throw new NullPointerException("Create bluetooth device failed, BluetoothOptions is null.");
        }
        if (!BluetoothUtil.isSupportBLE(context)) {
            throw new RuntimeException("Not support low energy bluetooth device.");
        }
        return new BluetoothLEDevice(context, bluetoothOptions);
    }

    protected BluetoothLEDevice(Context context, BluetoothOptions bluetoothOptions) {
        this.context = context.getApplicationContext();
        this.bluetoothOptions = bluetoothOptions;
        try {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        } catch (Throwable e) {
            e.printStackTrace();
        }

        if (null == bluetoothAdapter) {
            throw new RuntimeException("Not support bluetooth.");
        }
        bluetoothDevice = bluetoothAdapter.getRemoteDevice(bluetoothOptions.getDeviceMac());
        IntentFilter intent = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        this.context.registerReceiver(receiver, intent);
    }

    /***
     * 连接设备
     * 注意:建议在主线程中调用，三星部分手机只能在主线程调用
     */
    public boolean connect() {
        if (null == bluetoothDevice) {
            Log.e(TAG, "Connect device failed. Can not find bluetooth device:"
                    + bluetoothOptions.getDeviceMac());
            return false;
        }

        // 如果当前正在搜索且不允许设备在搜索时连接, 则不连接, 启动重连机制
        if (BluetoothScanner.getInstance(context).isScanning()
                && bluetoothOptions.isRetryWhileScanning()) {
            Log.e(TAG, "Current is scanning device, auto connect device when stop scanning.");
            if (!isRetryConnect) {
                startReconnectDeviceTask();
            }
            return true;
        }
        Message msg = new Message();
        msg.what = CMD_CONNECT;
        mainHandler.sendMessageDelayed(msg, 100);
        return true;
    }

    /**
     * 开始重连设备任务
     */
    private void startReconnectDeviceTask() {
        if (bluetoothOptions.isAutoConnect() || bluetoothOptions.getRetryConnectCount() > 0) {
            retryConnectCount = 0;
            Log.e(TAG, "Start reconnect device task.");
            stopReconnectDeviceTask();
            isRetryConnect = true;
            Message msg = new Message();
            msg.what = CMD_CONNECT;
            retryConnectHandler.removeCallbacksAndMessages(null);
            retryConnectHandler.sendMessageDelayed(msg, 500);
        } else {
            Log.e(TAG, "Device is not set retry connect config , no need to retry.");
        }
    }

    /**
     * 取消重连
     */
    private void stopReconnectDeviceTask() {
        Log.e(TAG, "Stop reconnect device task.");
        isRetryConnect = false;
        retryConnectHandler.removeCallbacksAndMessages(null);
//        retryConnectCount = 0;
    }

    /**
     * 写数据
     * 注意:
     * 1、BLE特征一次写入的最大字节是20个. 超过20的会丢弃
     * 2、需要在UI线程中写数据，否则接受不到回调
     *
     * @param data 下发数据
     */
    public void writeData(byte[] data) {
        if (!isConnected()) {
            Log.e(TAG, "Device is not connected.");
            notifyDataWriteError(bluetoothOptions.getDeviceMac(),
                    null != bluetoothOptions.getServiceUUID() ?
                            bluetoothOptions.getServiceUUID().toString() : null,
                    null != bluetoothOptions.getWriteCharacteristicUUID() ?
                            bluetoothOptions.getWriteCharacteristicUUID().toString() : null,
                    "Write data failed. Device is not connected");
            return;
        }

        if (null == writeCharacteristic) {
            Log.e(TAG, "device mac is " + getDeviceMac() + " Write Characteristic is null");
            notifyDataWriteError(bluetoothOptions.getDeviceMac(),
                    null != bluetoothOptions.getServiceUUID() ?
                            bluetoothOptions.getServiceUUID().toString() : null,
                    null != bluetoothOptions.getWriteCharacteristicUUID() ?
                            bluetoothOptions.getWriteCharacteristicUUID().toString() : null,
                    "Write data failed. Write Characteristic is null");
            return;
        }

        processData(writeCharacteristic, data, BluetoothMessage.MessageType.WRITE);
    }


    /**
     * 处理待发送的数据
     *
     * @param characteristic 特征
     * @param data           数据
     * @param messageType    写或读
     */
    private void processData(BluetoothGattCharacteristic characteristic, byte[] data, BluetoothMessage.MessageType messageType) {
        int dataLen = data.length;
        // 数据超过MTU, 则需要拆包进行发送
        int maxDataLen = bluetoothOptions.getMaxDataLen();
        if (dataLen > maxDataLen) {
            int dataCount = dataLen % maxDataLen == 0 ? dataLen / maxDataLen : dataLen / maxDataLen + 1;
            Log.e(TAG, "dataCount:" + dataCount);
            byte[] sendData = null;
            for (int i = 0; i < dataCount; i++) {
                if (i != dataCount - 1) {
                    sendData = Arrays.copyOfRange(data, i * maxDataLen, (i + 1) * maxDataLen);
                } else {
                    sendData = Arrays.copyOfRange(data, i * maxDataLen, dataLen);
                }
                dataQueue.add(new BluetoothMessage(characteristic, sendData, messageType));
            }
        } else {
            dataQueue.add(new BluetoothMessage(characteristic, data, messageType));
        }
    }


    /**
     * 通过指定特性写数据
     * 注意:
     * 1、BLE特征一次写入的最大字节是20个. 超过20的会丢弃
     * 2、需要在UI线程中写数据，否则接受不到回调
     * 3、为了保证数据能正常发送，延迟了100ms去发送，如果有问题，需自行修改
     *
     * @param characteristicUUID 特征id
     * @param data               下发数据
     */
    public void writeData(String characteristicUUID, byte[] data) {
        writeData(bluetoothOptions.getServiceUUID().toString(), characteristicUUID, data);
    }


    /**
     * 通过指定服务、特性写数据
     * 注意:
     * 1、BLE特征一次写入的最大字节是20个. 超过20的会丢弃
     * 2、需要在UI线程中写数据，否则接受不到回调
     * 3、为了保证数据能正常发送，延迟了100ms去发送，如果有问题，需自行修改
     *
     * @param serviceUUID        服务id
     * @param characteristicUUID 特征id
     * @param data               下发数据
     */
    public void writeData(String serviceUUID, String characteristicUUID, byte[] data) {
        if (!isConnected()) {
            Log.e(TAG, "Device is not connected.");
            notifyDataWriteError(bluetoothOptions.getDeviceMac(),
                    serviceUUID, characteristicUUID,
                    "Write data failed. Device is not connected");
            return;
        }
        BluetoothGattService service = bluetoothGatt.getService(UUID.fromString(serviceUUID));
        if (null == service) {
            Log.e(TAG, "Can not find service:" + serviceUUID);
            notifyDataWriteError(bluetoothOptions.getDeviceMac(),
                    serviceUUID, characteristicUUID,
                    "Write data failed. Can not find service:" + serviceUUID);
            return;
        }
        final BluetoothGattCharacteristic characteristic =
                service.getCharacteristic(UUID.fromString(characteristicUUID));
        if (null == characteristic) {
            Log.e(TAG, "Can not find characteristic:" + characteristicUUID);
            notifyDataWriteError(bluetoothOptions.getDeviceMac(),
                    serviceUUID, characteristicUUID,
                    "Write data failed. Can not find characteristic:" + characteristicUUID);
            return;
        }
        processData(characteristic, data, BluetoothMessage.MessageType.WRITE);
    }

    private void notifyDataWriteError(String deviceMac, String serviceUUID,
                                      String characteristicUUID, String errorMessage) {
        for (BluetoothDeviceDataCallback dataCallback : deviceDataCallbacks) {
            dataCallback.onWriteError(deviceMac, serviceUUID, characteristicUUID, errorMessage);
        }
    }

    /**
     * 读特征数据
     * 注意:
     * 1、BLE特征一次写入的最大字节是20个. 超过20的会丢弃
     * 2、需要在UI线程中写数据，否则接受不到回调
     * 3、为了保证数据能正常发送，延迟了100ms去发送，如果有问题，需自行修改
     *
     * @param data 下发数据
     */
    public void readData(byte[] data) {
        if (!isConnected()) {
            Log.e(TAG, "Device is not connected.");
            return;
        }

        if (null == writeCharacteristic) {
            Log.e(TAG, "Write Characteristic is null");
            return;
        }
        dataQueue.add(new BluetoothMessage(notifyCharacteristic, data, BluetoothMessage.MessageType.READ));
    }

    /**
     * 通过指定特性写数据
     * 注意:
     * 1、BLE特征一次写入的最大字节是20个. 超过20的会丢弃
     * 2、需要在UI线程中写数据，否则接受不到回调
     *
     * @param characteristicUUID 特征id
     * @param data               下发数据
     */
    public void readData(String characteristicUUID, byte[] data) {
        readData(bluetoothOptions.getServiceUUID().toString(), characteristicUUID, data);
    }

    /**
     * 通过指定服务、特性写数据
     * 注意:
     * 1、BLE特征一次写入的最大字节是20个. 超过20的会丢弃
     * 2、需要在UI线程中写数据，否则接受不到回调
     * 3、为了保证数据能正常发送，延迟了100ms去发送，如果有问题，需自行修改
     *
     * @param serviceUUID        服务id
     * @param characteristicUUID 特征id
     * @param data               下发数据
     */
    public void readData(String serviceUUID, String characteristicUUID, byte[] data) {
        if (!isConnected()) {
            Log.e(TAG, "Device is not connected.");
            return;
        }
        BluetoothGattService service = bluetoothGatt.getService(UUID.fromString(serviceUUID));
        if (null == service) {
            Log.e(TAG, "Can not find service:" + serviceUUID);
            notifyDataReadError(bluetoothOptions.getDeviceMac(),
                    serviceUUID, characteristicUUID,
                    "Write data failed. Can not find service:" + serviceUUID);
            return;
        }
        final BluetoothGattCharacteristic characteristic =
                service.getCharacteristic(UUID.fromString(characteristicUUID));
        if (null == characteristic) {
            Log.e(TAG, "Can not find characteristic:" + characteristicUUID);
            notifyDataReadError(bluetoothOptions.getDeviceMac(),
                    serviceUUID, characteristicUUID,
                    "Write data failed. Can not find characteristic:" + characteristicUUID);
            return;
        }
        setCharacteristicNotification(characteristic, true);
        dataQueue.add(new BluetoothMessage(characteristic, data, BluetoothMessage.MessageType.READ));
    }

    private void notifyDataReadError(String deviceMac, String serviceUUID,
                                     String characteristicUUID, String errorMessage) {
        for (BluetoothDeviceDataCallback dataCallback : deviceDataCallbacks) {
            dataCallback.onReadError(deviceMac, serviceUUID, characteristicUUID, errorMessage);
        }
    }

    private void notifyConnectStateChange(String deviceMac, int oldState, int newState) {
        for (BluetoothDeviceStateListener stateListener : deviceStateListeners) {
            stateListener.onStateChange(deviceMac, oldState, newState);
        }
    }

    private void notifyDeviceReady(String deviceMac) {
        for (BluetoothDeviceStateListener stateListener : deviceStateListeners) {
            stateListener.onReady(deviceMac);
        }
    }

    private void notifyDiscoverServicesError(String deviceMac, String errorMessage) {
        for (BluetoothDeviceStateListener stateListener : deviceStateListeners) {
            stateListener.onDiscoverServicesError(deviceMac, errorMessage);
        }
    }

    private void notifyDeviceConnectTimeout(String deviceMac) {
        synchronized (deviceStateListeners) {
            for (BluetoothDeviceStateListener stateListener : deviceStateListeners) {
                stateListener.onConnectTimeout(deviceMac);
            }
        }
    }

    private void notifyDeviceDataRead(BluetoothDeviceData deviceData, int state) {
        for (BluetoothDeviceDataCallback dataCallback : deviceDataCallbacks) {
            dataCallback.onDataRead(deviceData, state);
        }
    }

    private void notifyDeviceDataWrite(BluetoothDeviceData deviceData, int state) {
        for (BluetoothDeviceDataCallback dataCallback : deviceDataCallbacks) {
            dataCallback.onDataWrite(deviceData, state);
        }
    }

    private void notifyDeviceDataChanged(BluetoothDeviceData deviceData) {
        for (BluetoothDeviceDataCallback dataCallback : deviceDataCallbacks) {
            try {
                dataCallback.onDataChanged(deviceData);
            } catch (Exception e) {
                Log.e(TAG, "notifyDeviceDataChanged error:" + e.getMessage(), e);
            }
        }
    }

    /**
     * 设置开启特性的通知
     * 如果不开启特性通知, 无法接受蓝牙的响应数据
     *
     * @param serviceUUID              服务id
     * @param notifyCharacteristicUUID 特征id
     */
    public void setCharacteristicNotification(boolean isEnable, String serviceUUID, String notifyCharacteristicUUID) {
        if (null == bluetoothGatt || TextUtils.isEmpty(serviceUUID)
                || TextUtils.isEmpty(notifyCharacteristicUUID)) {
            Log.e(TAG, "Set characteristic notification failed.BluetoothGatt or serviceUUID or notifyCharacteristicUUID is null.");
            return;
        }
        BluetoothGattService bluetoothGattService = bluetoothGatt.getService(UUID.fromString(serviceUUID));
        if (null == bluetoothGattService) {
            Log.e(TAG, "Set characteristic notification failed. Can not find service:" + serviceUUID);
            return;
        }
        BluetoothGattCharacteristic notifyCharacteristic =
                bluetoothGattService.getCharacteristic(UUID.fromString(notifyCharacteristicUUID));
        if (null == notifyCharacteristic) {
            Log.e(TAG, "Set characteristic notification failed. Can not find characteristic:" + serviceUUID);
            return;
        }
        setCharacteristicNotification(notifyCharacteristic, isEnable);
    }

//    /**
//     * 设置开启特性的通知
//     * 保留: 该方法迭代了所有的descriptors,暂未确认有何影响
//     * @param notifyCharacteristic
//     */
//    public void setCharacteristicNotification(boolean isEnable, BluetoothGattCharacteristic notifyCharacteristic) {
//        if (null == bluetoothGatt || null == notifyCharacteristic) {
//            Log.e(TAG, "BluetoothGatt or BluetoothGattCharacteristic is null.");
//            return;
//        }
//
//        // 开启通知特性通知功能
//        if (bluetoothGatt.setCharacteristicNotification(notifyCharacteristic, true)) {
//            List<BluetoothGattDescriptor> descriptors = notifyCharacteristic.getDescriptors();
//            if (null == descriptors) {
//                Log.i(TAG, "There is no descriptor.");
//                return;
//            }
//            for (final BluetoothGattDescriptor dp : notifyCharacteristic.getDescriptors()) {
//                if ((notifyCharacteristic.getProperties()
//                        & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
//                    dp.setValue(isEnable ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
//                    Log.e(TAG, "notify descriptor:");
//                    bluetoothGatt.writeDescriptor(dp);
//                } else if ((notifyCharacteristic.getProperties()
//                        & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
//                    dp.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
//                    Log.e(TAG, "indicate descriptor:");
//                    bluetoothGatt.writeDescriptor(dp);
//                }
//            }
//        } else {
//            Log.e(TAG, "Set Characteristic Notification failed.");
//        }
//    }


    /**
     * 设置特性通知是否启用
     *
     * @param characteristic 需要设置的特性
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean isEnable) {
        if (null == bluetoothGatt || null == characteristic) {
            Log.e(TAG, "BluetoothGatt or BluetoothGattCharacteristic is null.");
            return;
        }

        // 开启通知特性通知功能
        if (bluetoothGatt.setCharacteristicNotification(characteristic, isEnable)) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(DEFAULT_DESCRIPTOR_UUID));
            if (null == descriptor) {
                Log.e(TAG, "Set characteristic descriptor failed. descriptor is null");
                return;
            }
            descriptor.setValue(isEnable ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            boolean result = bluetoothGatt.writeDescriptor(descriptor);
            Log.e(TAG, "Write descriptor result:" + result);
        } else {
            Log.e(TAG, "Set Characteristic Notification failed.");
        }
    }

    /**
     * 设置特性通知是否启用indication
     *
     * @param serviceUUID              服务id
     * @param notifyCharacteristicUUID 特征id
     */
    public void setCharacteristicIndication(String serviceUUID, String notifyCharacteristicUUID, boolean isEnable) {
        if (null == bluetoothGatt || TextUtils.isEmpty(serviceUUID)
                || TextUtils.isEmpty(notifyCharacteristicUUID)) {
            Log.e(TAG, "Set characteristic indication failed.BluetoothGatt or serviceUUID or notifyCharacteristicUUID is null.");
            return;
        }
        BluetoothGattService bluetoothGattService = bluetoothGatt.getService(UUID.fromString(serviceUUID));
        if (null == bluetoothGattService) {
            Log.e(TAG, "Set characteristic indication failed. Can not find service:" + serviceUUID);
            return;
        }
        BluetoothGattCharacteristic notifyCharacteristic =
                bluetoothGattService.getCharacteristic(UUID.fromString(notifyCharacteristicUUID));
        if (null == notifyCharacteristic) {
            Log.e(TAG, "Set characteristic indication failed. Can not find characteristic:" + serviceUUID);
            return;
        }
        setCharacteristicNotification(notifyCharacteristic, isEnable);
    }

    /**
     * 设置特性通知是否启用indication
     *
     * @param characteristic 需要设置的特性
     */
    public void setCharacteristicIndication(BluetoothGattCharacteristic characteristic, boolean isEnable) {
        if (null == bluetoothGatt || null == characteristic) {
            Log.e(TAG, "Set characteristic indication.BluetoothGatt or BluetoothGattCharacteristic is null.");
            return;
        }

        // 开启通知特性通知功能
        if (bluetoothGatt.setCharacteristicNotification(characteristic, isEnable)) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(DEFAULT_DESCRIPTOR_UUID));
            if (null == descriptor) {
                Log.e(TAG, "Set characteristic descriptor failed. descriptor is null");
                return;
            }
            descriptor.setValue(isEnable ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            boolean result = bluetoothGatt.writeDescriptor(descriptor);
            Log.e(TAG, "Write descriptor result:" + result);
        } else {
            Log.e(TAG, "Set Characteristic Notification failed.");
        }
    }


    public boolean isConnected() {
        return connectState == BluetoothProfile.STATE_CONNECTED;
    }

    public int getState() {
        return connectState;
    }

    public String getDeviceMac() {
        return bluetoothOptions.getDeviceMac();
    }

    public String getName() {
        if (null == bluetoothDevice) {
            return "";
        } else {
            return bluetoothDevice.getName();
        }
    }

    public boolean isReady() {
        return isReady;
    }

    public BluetoothDevice getOriginalDevice() {
        return bluetoothDevice;
    }

    public void addDeviceStateListener(BluetoothDeviceStateListener deviceStateListener) {
        if (!deviceStateListeners.contains(deviceStateListener)) {
            deviceStateListeners.add(deviceStateListener);
        }
    }

    public void removeDeviceStateListener(BluetoothDeviceStateListener deviceStateListener) {
        Iterator<BluetoothDeviceStateListener> iterator = deviceStateListeners.iterator();
        while (iterator.hasNext()) {
            if (deviceStateListener == iterator.next()) {
                iterator.remove();
                break;
            }
        }
    }

    public void addDeviceDataCallback(BluetoothDeviceDataCallback dataCallback) {
        if (!deviceDataCallbacks.contains(dataCallback)) {
            deviceDataCallbacks.add(dataCallback);
        }
    }

    public void removeDeviceDataCallback(BluetoothDeviceDataCallback dataCallback) {
        Iterator<BluetoothDeviceDataCallback> iterator = deviceDataCallbacks.iterator();
        while (iterator.hasNext()) {
            if (dataCallback == iterator.next()) {
                iterator.remove();
                break;
            }
        }
    }

    /**
     * 断开连接, 如需重新连接, 可以通过bluetoothGatt.connect()重新连接
     */
    public void disconnect() {
        disconnect(false);
    }

    /**
     * 断开连接, 如需重新连接, 可以通过bluetoothGatt.connect()重新连接
     * 供外部调用, 手动关闭连接后, 不触发重连机制
     */
    private void disconnect(boolean isRetry) {
        Log.e(TAG, "isConnected:" + isConnected() + ", bluetoothGatt :" + (null != bluetoothGatt));
        if (isConnected()) {
            Log.e(TAG, "Device disconnect.");
            bluetoothGatt.disconnect();
        }
        // 兼容手动调用disconnect方法, 手动close不重连
        if (!isRetry) {
            stopReconnectDeviceTask();
        }
        connectState = BluetoothProfile.STATE_DISCONNECTED;
        isReady = false;
    }

    /**
     * 关闭蓝牙连接, 释放Gatt资源, 如需重新连接, 需要通过BluetoothDevice.connectGatt连接
     * 供外部调用, 手动关闭连接后, 不触发重连机制
     */
    public void close() {
        close(false);
    }

    /**
     * 关闭蓝牙连接, 释放Gatt资源, 如需重新连接, 需要通过BluetoothDevice.connectGatt连接
     */
    private void close(boolean isRetry) {
        if (null != bluetoothGatt) {
            Log.e(TAG, String.format(Locale.ENGLISH,"Device [%s] close.", bluetoothOptions.getDeviceMac()));

            bluetoothGatt.disconnect();
            BluetoothUtil.refreshGattCache(bluetoothGatt);
            bluetoothGatt.close();
            BluetoothUtil.refreshGattCache(bluetoothGatt);
            if (!isRetry) {
                isCallClose = true;
            }
//            new Thread(new Runnable() {
//                @Override
//                public void run() {
//                    try {
//                        Thread.sleep(1000);
//                        BluetoothUtil.refreshGattCache(bluetoothGatt);
//                        bluetoothGatt = null;
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }).start();

        }

        // 兼容手动调用close方法, 手动close不重连
        if (!isRetry) {
            stopReconnectDeviceTask();
            connectTimeoutHandler.removeCallbacksAndMessages(null);
        }
        stopDataService();
        dataQueue.clear();
        connectState = BluetoothProfile.STATE_DISCONNECTED;
        isReady = false;
    }

    public BluetoothOptions getBluetoothOptions() {
        return bluetoothOptions;
    }

    /**
     * 销毁实例
     */
    public void destroy() {
        close();
        context.unregisterReceiver(receiver);
    }
}
