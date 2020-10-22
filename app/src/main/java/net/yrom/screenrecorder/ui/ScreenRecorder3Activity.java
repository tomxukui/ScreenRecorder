package net.yrom.screenrecorder.ui;

import android.content.Intent;
import android.media.MediaCodecInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.Button;

import com.xukui.library.screenrecorder.AudioEncodeConfig;
import com.xukui.library.screenrecorder.ScreenRecorderKit;
import com.xukui.library.screenrecorder.Utils;
import com.xukui.library.screenrecorder.VideoEncodeConfig;
import com.yanzhenjie.permission.runtime.Permission;

import net.yrom.screenrecorder.R;
import net.yrom.screenrecorder.util.ToastUtil;
import net.yrom.screenrecorder.util.permission.PermissionUtil;

import java.io.File;

import static com.xukui.library.screenrecorder.ScreenRecorder.AUDIO_AAC;
import static com.xukui.library.screenrecorder.ScreenRecorder.VIDEO_AVC;

public class ScreenRecorder3Activity extends AppCompatActivity {

    private static final int REQUEST_MEDIA_PROJECTION = 1;

    private Button btn_start;
    private Button btn_stop;

    private MediaCodecInfo[] mAvcCodecInfos; // avc codecs
    private MediaCodecInfo[] mAacCodecInfos; // aac codecs

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_recorder3);
        initView();
        setView();

        Utils.findEncodersByTypeAsync(VIDEO_AVC, infos -> {
            mAvcCodecInfos = infos;
        });

        Utils.findEncodersByTypeAsync(AUDIO_AAC, infos -> {
            mAacCodecInfos = infos;
        });
    }

    private void initView() {
        btn_start = findViewById(R.id.btn_start);
        btn_stop = findViewById(R.id.btn_stop);
    }

    private void setView() {
        btn_start.setOnClickListener(v -> {
            PermissionUtil.requestPermission(ScreenRecorder3Activity.this, data -> {
                VideoEncodeConfig videoEncodeConfig = createVideoConfig();
                AudioEncodeConfig audioEncodeConfig = createAudioConfig();
                File savingDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera");

                ScreenRecorderKit.startRecord(videoEncodeConfig, audioEncodeConfig, savingDir, mRecorderCallback);
            }, new String[]{Permission.WRITE_EXTERNAL_STORAGE, Permission.RECORD_AUDIO});
        });

        btn_stop.setOnClickListener(v -> ScreenRecorderKit.stopRecord());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {

            case REQUEST_MEDIA_PROJECTION: {
                ScreenRecorderKit.handleActivityResult(resultCode, data);
            }
            break;

        }
    }

    private final ScreenRecorderKit.Callback mRecorderCallback = new ScreenRecorderKit.Callback() {

        @Override
        public void sendCaptureIntent(Intent captureIntent) {
            startActivityForResult(captureIntent, REQUEST_MEDIA_PROJECTION);
        }

        @Override
        public void onStart() {
            ToastUtil.showShort("开始录制");
        }

        @Override
        public void onRecording(long time) {
        }

        @Override
        public void onSuccess(File file) {
            ToastUtil.showShort("录制完成, 文件存储在:" + file.getAbsolutePath());

            openFile(file);
        }

        private void openFile(File file) {
            StrictMode.VmPolicy vmPolicy = StrictMode.getVmPolicy();

            try {
                StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());

                Intent view = new Intent(Intent.ACTION_VIEW);
                view.addCategory(Intent.CATEGORY_DEFAULT);
                view.setDataAndType(Uri.fromFile(file), VIDEO_AVC);
                view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(view);

            } catch (Exception e) {
            } finally {
                StrictMode.setVmPolicy(vmPolicy);
            }
        }

        @Override
        public void onFailure(String msg) {
            ToastUtil.showShort(msg);
        }

    };

    private AudioEncodeConfig createAudioConfig() {
//        if (!mAudioToggle.isChecked()) return null;
//        String codec = getSelectedAudioCodec();
//        if (codec == null) {
//            return null;
//        }
//        int bitrate = getSelectedAudioBitrate();
//        int samplerate = getSelectedAudioSampleRate();
//        int channelCount = getSelectedAudioChannelCount();
//        int profile = getSelectedAudioProfile();
//
//        return new AudioEncodeConfig(codec, AUDIO_AAC, bitrate, samplerate, channelCount, profile);

        return null;
    }

    private VideoEncodeConfig createVideoConfig() {
        final String codec = mAvcCodecInfos[0].getName();

        if (codec == null) {
            return null;
        }

        int width = 1080;
        int height = 1920;
        int framerate = 30;
        int iframe = 1;
        int bitrate = 5000000;

        MediaCodecInfo.CodecProfileLevel profileLevel = Utils.toProfileLevel("Default");
        return new VideoEncodeConfig(width, height, bitrate, framerate, iframe, codec, VIDEO_AVC, profileLevel);
    }

}