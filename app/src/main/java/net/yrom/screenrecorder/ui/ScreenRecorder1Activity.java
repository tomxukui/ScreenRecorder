package net.yrom.screenrecorder.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodecInfo;
import android.media.projection.MediaProjection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StrictMode;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.xukui.library.screenrecorder.AudioEncodeConfig;
import com.xukui.library.screenrecorder.ScreenRecorder;
import com.xukui.library.screenrecorder.ScreenRecorderKit;
import com.xukui.library.screenrecorder.Utils;
import com.xukui.library.screenrecorder.VideoEncodeConfig;
import com.yanzhenjie.permission.Action;
import com.yanzhenjie.permission.AndPermission;
import com.yanzhenjie.permission.runtime.Permission;

import net.yrom.screenrecorder.Mapp;
import net.yrom.screenrecorder.R;
import net.yrom.screenrecorder.util.ToastUtil;
import net.yrom.screenrecorder.util.permission.PermissionUtil;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static android.os.Build.VERSION_CODES.M;
import static com.xukui.library.screenrecorder.ScreenRecorder.AUDIO_AAC;
import static com.xukui.library.screenrecorder.ScreenRecorder.VIDEO_AVC;

public class ScreenRecorder1Activity extends AppCompatActivity {

    private static final int REQUEST_MEDIA_PROJECTION = 1;

    private Button btn_start;
    private Button btn_stop;

    private ScreenRecorder mScreenRecorder;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;

    private MediaCodecInfo[] mAvcCodecInfos; // avc codecs
    private MediaCodecInfo[] mAacCodecInfos; // aac codecs

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_recorder1);
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
        btn_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mScreenRecorder != null) {
                    stopRecordingAndOpenFile();

                } else if (AndPermission.hasPermissions(Mapp.getInstance(), new String[]{Permission.WRITE_EXTERNAL_STORAGE, Permission.RECORD_AUDIO})) {
                    if (mMediaProjection == null) {
                        requestMediaProjection();

                    } else {
                        startCapturing(mMediaProjection);
                    }

                } else if (Build.VERSION.SDK_INT >= M) {
                    PermissionUtil.requestPermission(ScreenRecorder1Activity.this, new Action<List<String>>() {

                        @Override
                        public void onAction(List<String> data) {
                            requestMediaProjection();
                        }

                    }, new String[]{Permission.WRITE_EXTERNAL_STORAGE, Permission.RECORD_AUDIO});

                } else {
                    ToastUtil.showShort(getString(R.string.no_permission_to_write_sd_ard));
                }
            }
        });

        btn_stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mScreenRecorder != null) {
                    stopRecordingAndOpenFile();
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {

            case REQUEST_MEDIA_PROJECTION: {
                MediaProjection mediaProjection = ScreenRecorderKit.getMediaProjectionManager().getMediaProjection(resultCode, data);
                if (mediaProjection == null) {
                    Log.e("@@", "media projection is null");
                    return;
                }

                mMediaProjection = mediaProjection;
                mMediaProjection.registerCallback(mProjectionCallback, new Handler());
                startCapturing(mediaProjection);
            }
            break;

        }
    }

    private void stopRecordingAndOpenFile() {
        File file = new File(mScreenRecorder.getSavedPath());
        stopRecorder();
        ToastUtil.showShort(getString(R.string.recorder_stopped_saved_file) + " " + file);
        StrictMode.VmPolicy vmPolicy = StrictMode.getVmPolicy();
        try {
            // disable detecting FileUriExposure on public file
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());
            viewResult(file);
        } finally {
            StrictMode.setVmPolicy(vmPolicy);
        }
    }

    private void viewResult(File file) {
        Intent view = new Intent(Intent.ACTION_VIEW);
        view.addCategory(Intent.CATEGORY_DEFAULT);
        view.setDataAndType(Uri.fromFile(file), VIDEO_AVC);
        view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(view);
        } catch (ActivityNotFoundException e) {
        }
    }

    private void startCapturing(MediaProjection mediaProjection) {
        VideoEncodeConfig video = createVideoConfig();
        AudioEncodeConfig audio = createAudioConfig(); // audio can be null

        if (video == null) {
            ToastUtil.showShort(getString(R.string.create_screenRecorder_failure));
            return;
        }

        File dir = getSavingDir();
        if (!dir.exists() && !dir.mkdirs()) {
            cancelRecorder();
            return;
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
        final File file = new File(dir, "Screenshots-" + format.format(new Date())
                + "-" + video.width + "x" + video.height + ".mp4");
        Log.d("@@", "Create recorder with :" + video + " \n " + audio + "\n " + file);
        mScreenRecorder = newRecorder(mediaProjection, video, audio, file);
        if (AndPermission.hasPermissions(Mapp.getInstance(), new String[]{Permission.WRITE_EXTERNAL_STORAGE, Permission.RECORD_AUDIO})) {
            startRecorder();

        } else {
            cancelRecorder();
        }
    }

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

    private static File getSavingDir() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Screenshots");
    }

    private void requestMediaProjection() {
        Intent captureIntent = ScreenRecorderKit.getMediaProjectionManager().createScreenCaptureIntent();
        startActivityForResult(captureIntent, REQUEST_MEDIA_PROJECTION);
    }

    private void startRecorder() {
        if (mScreenRecorder == null) {
            return;
        }

        mScreenRecorder.start();
//        mButton.setText(getString(R.string.stop_recorder));
//        registerReceiver(mStopActionReceiver, new IntentFilter(ACTION_STOP));
//        moveTaskToBack(true);
    }

    private void stopRecorder() {
//        mNotifications.clear();

        if (mScreenRecorder != null) {
            mScreenRecorder.quit();
            mScreenRecorder = null;
        }

//        mButton.setText(getString(R.string.restart_recorder));
//        try {
//            unregisterReceiver(mStopActionReceiver);
//        } catch (Exception e) {
//            //ignored
//        }
    }

    private void cancelRecorder() {
        if (mScreenRecorder == null) {
            return;
        }

        ToastUtil.showShort(getString(R.string.permission_denied_screen_recorder_cancel));
        stopRecorder();
    }

    private ScreenRecorder newRecorder(MediaProjection mediaProjection, VideoEncodeConfig video,
                                       AudioEncodeConfig audio, File output) {
        final VirtualDisplay display = getOrCreateVirtualDisplay(mediaProjection, video);
        ScreenRecorder r = new ScreenRecorder(video, audio, display, output.getAbsolutePath());
        r.setCallback(new ScreenRecorder.Callback() {
            long startTime = 0;

            @Override
            public void onStop(Throwable error) {
                runOnUiThread(() -> stopRecorder());

                if (error != null) {
                    ToastUtil.showShort("Recorder error ! See logcat for more details");
                    error.printStackTrace();
                    output.delete();

                } else {
                    Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                            .addCategory(Intent.CATEGORY_DEFAULT)
                            .setData(Uri.fromFile(output));
                    sendBroadcast(intent);
                }
            }

            @Override
            public void onStart() {
//                mNotifications.recording(0);
            }

            @Override
            public void onRecording(long presentationTimeUs) {
                if (startTime <= 0) {
                    startTime = presentationTimeUs;
                }
                long time = (presentationTimeUs - startTime) / 1000;
//                mNotifications.recording(time);
            }
        });

        return r;
    }

    private VirtualDisplay getOrCreateVirtualDisplay(MediaProjection mediaProjection, VideoEncodeConfig config) {
        if (mVirtualDisplay == null) {
            mVirtualDisplay = mediaProjection.createVirtualDisplay("ScreenRecorder-display0",
                    config.width, config.height, 1 /*dpi*/,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    null /*surface*/, null, null);
        } else {
            // resize if size not matched
            Point size = new Point();
            mVirtualDisplay.getDisplay().getSize(size);
            if (size.x != config.width || size.y != config.height) {
                mVirtualDisplay.resize(config.width, config.height, 1);
            }
        }
        return mVirtualDisplay;
    }

    private MediaProjection.Callback mProjectionCallback = new MediaProjection.Callback() {

        @Override
        public void onStop() {
            if (mScreenRecorder != null) {
                stopRecorder();
            }
        }

    };

}