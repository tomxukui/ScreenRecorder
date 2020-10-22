package net.yrom.screenrecorder.util;

import android.text.TextUtils;
import android.widget.Toast;

import net.yrom.screenrecorder.Mapp;

public class ToastUtil {

    public static void showShort(String message) {
        if (TextUtils.isEmpty(message)) {
            return;
        }

        Toast.makeText(Mapp.getInstance(), message, Toast.LENGTH_SHORT).show();
    }

}
