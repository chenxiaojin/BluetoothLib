package com.cxj.bluetooth.test;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;


import java.util.List;

import pub.devrel.easypermissions.EasyPermissions;


public class SplashActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks {
    // 需要申请的权限列表
    private static final String[] REQUIRED_PERMISSION_LIST = new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
    };

    private static final int SDCARD_REQUEST_CODE = 1;
    // 是否等待用户自行设置权限
    private boolean isWaitingGrantPermission = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_splash);
        // 请求权限
        checkPermissions(true);
    }

    private void checkPermissions(boolean isRequest) {
        if (!EasyPermissions.hasPermissions(this, REQUIRED_PERMISSION_LIST)) {
            // 是否正在进行用户自行设置权限的步骤， 如果是的话，不再请求权限，否则会造成死循环
            if (isRequest) {
                EasyPermissions.requestPermissions(this,
                        "为确保程序正常允许，需要允许特定权限", SDCARD_REQUEST_CODE, REQUIRED_PERMISSION_LIST);
            } else {
                showSystemSettingGrantDialog();
            }
        } else {
            gotoMainActivity(1000);
            return;
        }
    }


    /**
     * 必须重写此方法, 才能正常使用权限
     *
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    /**
     * 授权成功的权限
     *
     * @param requestCode
     * @param perms
     */
    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> perms) {
        // 权限已被授予
        Log.e("TAG", "requestCode:" + requestCode);
        StringBuilder str = new StringBuilder();
        for (String permission : perms) {
            str.append(permission).append(",");
        }

        boolean isAllGranted = true;
        for (String perm : REQUIRED_PERMISSION_LIST) {
            if (!perms.contains(perm)) {
                isAllGranted = false;
            }
        }

        // 全部授权成功, 跳转主页
        if (isAllGranted) {
            gotoMainActivity(1000);
        }

        Log.e("TAG", "permission grant:" + str.toString());
    }

    /**
     * 授权失败的权限
     *
     * @param requestCode
     * @param perms
     */
    @Override
    public void onPermissionsDenied(int requestCode, @NonNull List<String> perms) {
        StringBuilder str = new StringBuilder();
        for (String permission : perms) {
            str.append(permission).append(",");
        }
        Log.e("TAG", "permission Denied:" + str.toString());
        // 权限被拒绝
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            // 权限被拒绝且用户勾选禁止后不再询问选项
            Log.e("TAG", "permission denied forever.");
            showSystemSettingGrantDialog();
            return;
        }

        if (requestCode == SDCARD_REQUEST_CODE) {
            new AlertDialog.Builder(this)
                    .setMessage("为了确保程序功能正常运行，程序需要使用读取设备信息、SD卡权限，请重新授权。")
                    .setPositiveButton("去授权", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            checkPermissions(true);
                        }
                    })
                    .setNegativeButton("退出程序", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            //引导用户手动授权，权限请求失败
                            System.exit(0);
                        }
                    }).setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    //引导用户手动授权，权限请求失败
                }
            }).setCancelable(false).show();
        }
    }


    /**
     * 用户勾选了不再提示，并且使用了点击了禁止按钮
     * 这种情况需要弹出提示， 由用户自行打开系统设置授权
     */
    private void showSystemSettingGrantDialog() {
        new AlertDialog.Builder(this)
                .setMessage("为了确保程序功能正常运行，程序需要使用读取设备信息、SD卡权限，请在系统设置中授权程序使用权限。")
                .setPositiveButton("打开系统设置", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        isWaitingGrantPermission = true;
                        //引导用户至设置页手动授权
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", getApplicationContext().getPackageName(), null);
                        intent.setData(uri);
                        startActivity(intent);
                    }
                })
                .setNegativeButton("退出程序", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //引导用户手动授权，权限请求失败
                        System.exit(0);
                    }
                }).setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                //引导用户手动授权，权限请求失败
            }
        }).setCancelable(false).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isWaitingGrantPermission) {
            checkPermissions(false);
        }
    }

    private void gotoMainActivity(long delay) {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        }, delay);
    }

}
