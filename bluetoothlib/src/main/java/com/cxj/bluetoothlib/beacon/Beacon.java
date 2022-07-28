package com.cxj.bluetoothlib.beacon;



import android.os.Handler;
import android.os.Message;

import androidx.annotation.NonNull;

import com.cxj.bluetoothlib.util.ByteUtil;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by dingjikerbo on 2016/9/5.
 */
public class Beacon {

    public byte[] mBytes;

    public List<BeaconItem> mItems;

    private Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            return true;
        }
    });

    public Beacon(byte[] scanRecord) {
        mItems = new LinkedList<BeaconItem>();
        if (!ByteUtil.isEmpty(scanRecord)) {
            mBytes = ByteUtil.trimLast(scanRecord);
            mItems.addAll(BeaconParser.parseBeacon(mBytes));
        }
    }

    @Override
    public String toString() {
        // TODO Auto-generated method stub
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("preParse: %s\npostParse:\n", ByteUtil.bytesToHex(mBytes)));

        for (int i = 0; i < mItems.size(); i++) {
            sb.append(mItems.get(i).toString());
            if (i != mItems.size() - 1) {
                sb.append("\n");
            }
        }

        return sb.toString();
    }

}
