package net.yrom.screenrecorder;

import android.app.Application;

public class Mapp extends Application {

    private static Mapp mInstance;

    @Override
    public void onCreate() {
        super.onCreate();
        mInstance = this;
    }

    public static Mapp getInstance() {
        return mInstance;
    }

}
