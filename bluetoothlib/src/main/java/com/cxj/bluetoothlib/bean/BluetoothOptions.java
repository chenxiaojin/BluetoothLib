package com.cxj.bluetoothlib.bean;

import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;

import java.util.UUID;

/**
 * @author chenxiaojin
 * @date 2020/7/18
 * @description 蓝牙配置选项:包括连接超时、重试次数
 */
public class BluetoothOptions implements Parcelable {
    public static final int DEFAULT_MTU = 23;

    public void setAutoConnect(boolean autoConnect) {
        this.builder.setAutoConnect(autoConnect);
    }

    public void setDeviceMac(String deviceMac){
        this.builder.setDeviceMac(deviceMac);
    }

    // 配置构造器, 配置都在这里
    private Builder builder;

    public BluetoothOptions(Builder builder) {
        this.builder = builder;
    }


    protected BluetoothOptions(Parcel in) {
        builder = in.readParcelable(Builder.class.getClassLoader());
    }

    public static final Creator<BluetoothOptions> CREATOR = new Creator<BluetoothOptions>() {
        @Override
        public BluetoothOptions createFromParcel(Parcel in) {
            return new BluetoothOptions(in);
        }

        @Override
        public BluetoothOptions[] newArray(int size) {
            return new BluetoothOptions[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(builder, flags);
    }

    public String getDeviceMac() {
        return builder.deviceMac;
    }

    public boolean isAutoConnect() {
        return builder.autoConnect;
    }

    public int getRetryConnectCount() {
        return builder.retryConnectCount;
    }

    public long getConnectTimeout() {
        return builder.connectTimeout;
    }

    public long getRetryInterval() {
        return builder.retryInterval;
    }

    public boolean isRetryWhileScanning() {
        return builder.retryWhileScanning;
    }

    public UUID getServiceUUID() {
        return builder.serviceUUID.getUuid();
    }

    public UUID getNotifyCharacteristicUUID() {
        return builder.notifyCharacteristicUUID.getUuid();
    }

    public UUID getWriteCharacteristicUUID() {
        return builder.writeCharacteristicUUID.getUuid();
    }

    /**
     * 是否需要设置MTU, 不等于默认值则需要设置
     *
     * @return
     */
    public boolean isNeedToSetMTU() {
        return builder.mtu != DEFAULT_MTU;
    }

    /**
     * 获取用户手动指定的MTU
     *
     * @return
     */
    public int getMTU() {
        return builder.mtu;
    }

    /**
     * 获取实际设置成功的MTU, 不一定和mtu一样, 每个设备最终能设置成功的值都不一样
     *
     * @return
     */
    public int getRealMTU() {
        return builder.realMTU;
    }

    /**
     * 设置实际设置成功的MTU
     *
     * @param mtu
     */
    public void setRealMTU(int mtu) {
        builder.realMTU = mtu;
        builder.maxDataLen = mtu - 3;
    }

    /**
     * 获取单次发送最大字节长度
     *
     * @return
     */
    public int getMaxDataLen() {
        return builder.maxDataLen;
    }


    /**
     * BluetoothOptions构造器
     */
    public static class Builder implements Parcelable {
        // 设备mac地址
        private String deviceMac;
        // 自动重试, 为true时, retryConnectCount失效
        private boolean autoConnect;
        // 重连次数, 默认0次
        private int retryConnectCount = 0;
        // 连接超时，默认30秒
        private long connectTimeout = 30 * 1000L;
        // 重试间隔, 默认8秒
        private long retryInterval = 8 * 1000L;
        // 在蓝牙正在搜索时是否重连, true为重连(蓝牙搜索中重连的速度会比较慢), 默认不重连
        private boolean retryWhileScanning;
        // 默认23字节, 最大发送数据长度 = mtu - 3
        private int mtu = 23;
        // 这个是实际设置后的mtu, 每个手机的mtu不一定相同， 超过手机最大的mtu, 则会是默认成实际手机支持的最大MTU
        // 因此， mtu和realMtu不一定一样, 实际拆包的时候, 需要使用realMTU作为基准
        private int realMTU = 23;
        private int maxDataLen = realMTU - 3;

        // 写入操作时，服务的UUID
        private ParcelUuid serviceUUID;
        // 通知特征值UUID
        public ParcelUuid notifyCharacteristicUUID;
        // 写特征UUID
        private ParcelUuid writeCharacteristicUUID;

        public Builder() {

        }

        protected Builder(Parcel in) {
            deviceMac = in.readString();
            autoConnect = in.readByte() != 0;
            retryConnectCount = in.readInt();
            connectTimeout = in.readLong();
            retryInterval = in.readLong();
            retryWhileScanning = in.readByte() != 0;
            mtu = in.readInt();
            realMTU = in.readInt();
            maxDataLen = in.readInt();
            serviceUUID = in.readParcelable(ParcelUuid.class.getClassLoader());
            notifyCharacteristicUUID = in.readParcelable(ParcelUuid.class.getClassLoader());
            writeCharacteristicUUID = in.readParcelable(ParcelUuid.class.getClassLoader());
        }


        public Builder setDeviceMac(String deviceMac) {
            this.deviceMac = deviceMac;
            return this;
        }

        public Builder setRetryConnectCount(int retryConnectCount) {
            this.retryConnectCount = retryConnectCount;
            return this;
        }

        public Builder setRetryInterval(long retryInterval) {
            this.retryInterval = retryInterval;
            return this;
        }

        public Builder setAutoConnect(boolean isAutoConnect) {
            autoConnect = isAutoConnect;
            return this;
        }


        public Builder setConnectTimeout(long connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder setRetryWhileScanning(boolean retryWhileScanning) {
            this.retryWhileScanning = retryWhileScanning;
            return this;
        }

        public Builder setMTU(int mtu) {
            this.mtu = mtu;
            this.maxDataLen = this.mtu - 3;
            return this;
        }

        public Builder setServiceUUID(String serviceUUID) {
            this.serviceUUID = new ParcelUuid(UUID.fromString(serviceUUID));
            return this;
        }

        public Builder setNotifyCharacteristicUUID(String notifyCharacteristicUUID) {
            this.notifyCharacteristicUUID = new ParcelUuid(UUID.fromString(notifyCharacteristicUUID));
            return this;
        }

        public Builder setWriteCharacteristicUUID(String writeCharacteristicUUID) {
            this.writeCharacteristicUUID = new ParcelUuid(UUID.fromString(writeCharacteristicUUID));
            return this;
        }


        public BluetoothOptions build() {
            if (null == serviceUUID) {
                throw new IllegalArgumentException("Init bluetooth options failed. service uuid is null.");
            }

            if (null == notifyCharacteristicUUID) {
                throw new IllegalArgumentException("Init bluetooth options failed. notify characteristic uuid is null.");
            }

            if (null == writeCharacteristicUUID) {
                throw new IllegalArgumentException("Init bluetooth options failed. write characteristic uuid is null.");
            }
            return new BluetoothOptions(this);
        }

        // Parcelable实现
        public static final Creator<Builder> CREATOR = new Creator<Builder>() {
            @Override
            public Builder createFromParcel(Parcel in) {
                return new Builder(in);
            }

            @Override
            public Builder[] newArray(int size) {
                return new Builder[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(deviceMac);
            dest.writeByte((byte) (autoConnect ? 1 : 0));
            dest.writeInt(retryConnectCount);
            dest.writeLong(connectTimeout);
            dest.writeLong(retryInterval);
            dest.writeByte((byte) (retryWhileScanning ? 1 : 0));
            dest.writeInt(mtu);
            dest.writeInt(realMTU);
            dest.writeInt(maxDataLen);
            dest.writeParcelable(serviceUUID, flags);
            dest.writeParcelable(notifyCharacteristicUUID, flags);
            dest.writeParcelable(writeCharacteristicUUID, flags);
        }
    }
}
