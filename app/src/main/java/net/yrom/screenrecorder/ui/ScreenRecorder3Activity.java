package net.yrom.screenrecorder.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.Button;

import com.xukui.library.screenrecorder.AudioEncodeConfig;
import com.xukui.library.screenrecorder.ScreenRecorderKit;
import com.xukui.library.screenrecorder.VideoEncodeConfig;
import com.yanzhenjie.permission.runtime.Permission;

import net.yrom.screenrecorder.Notifications;
import net.yrom.screenrecorder.R;
import net.yrom.screenrecorder.util.ToastUtil;
import net.yrom.screenrecorder.util.permission.PermissionUtil;

import java.io.File;

import static com.xukui.library.screenrecorder.ScreenRecorder.VIDEO_AVC;

public class ScreenRecorder3Activity extends AppCompatActivity {

    private static final int REQUEST_MEDIA_PROJECTION = 1;

    private Button btn_start;
    private Button btn_stop;

    private Notifications mNotifications;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_recorder3);
        initData();
        initView();
        setView();
    }

    private void initData() {
        mNotifications = new Notifications(getApplicationContext());
    }

    private void initView() {
        btn_start = findViewById(R.id.btn_start);
        btn_stop = findViewById(R.id.btn_stop);
    }

    private void setView() {
        btn_start.setOnClickListener(v -> {
            PermissionUtil.requestPermission(ScreenRecorder3Activity.this, data -> {
                File savingDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera");
                VideoEncodeConfig videoEncodeConfig = ScreenRecorderKit.createDefaultVideoEncodeConfig();
                AudioEncodeConfig audioEncodeConfig = ScreenRecorderKit.createDefaultAudioEncodeConfig();

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
            mNotifications.recording(0);
        }

        @Override
        public void onRecording(long time) {
            mNotifications.recording(time);
        }

        @Override
        public void onSuccess(File file) {
            ToastUtil.showShort("录制完成, 文件存储在:" + file.getAbsolutePath());
            mNotifications.clear();
            openFile(file);
        }

        @Override
        public void onFailure(String msg) {
            ToastUtil.showShort(msg);
            mNotifications.clear();
        }

    };

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

}