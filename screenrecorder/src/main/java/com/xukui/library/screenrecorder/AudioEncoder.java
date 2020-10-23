package com.xukui.library.screenrecorder;

import android.media.MediaFormat;

public class AudioEncoder extends BaseEncoder {

    private final AudioEncodeConfig mConfig;

    public AudioEncoder(AudioEncodeConfig config) {
        super(config.codecName);
        this.mConfig = config;
    }

    @Override
    protected MediaFormat createMediaFormat() {
        return mConfig.toFormat();
    }

}