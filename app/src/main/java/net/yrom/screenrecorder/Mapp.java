package net.yrom.screenrecorder;

import android.app.Application;

import com.xukui.library.screenrecorder.ScreenRecorderKit;

public class Mapp extends Application {

    private static Mapp mInstance;

    @Override
    public void onCreate() {
        super.onCreate();
        mInstance = this;

        ScreenRecorderKit.init(this);
    }

    public static Mapp getInstance() {
        return mInstance;
    }

}
