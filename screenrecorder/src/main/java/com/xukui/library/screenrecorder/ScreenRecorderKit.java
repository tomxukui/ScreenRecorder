package com.xukui.library.screenrecorder;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodecInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.util.Range;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import static com.xukui.library.screenrecorder.ScreenRecorder.VIDEO_AVC;

public class ScreenRecorderKit {

    public static Application mApplication;

    private static MediaProjectionManager mMediaProjectionManager;
    private static ScreenRecorder mScreenRecorder;
    private static MediaProjection mMediaProjection;
    private static VirtualDisplay mVirtualDisplay;

    private static VideoEncodeConfig mVideoEncodeConfig;
    @Nullable
    private static AudioEncodeConfig mAudioEncodeConfig;
    private static File mSavingDir;
    private static Callback mCallback;

    private static Handler mHandler;

    private ScreenRecorderKit() {
    }

    public static void init(Application application) {
        mApplication = application;
    }

    public static MediaProjectionManager getMediaProjectionManager() {
        if (mMediaProjectionManager == null) {
            mMediaProjectionManager = (MediaProjectionManager) mApplication.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        }

        return mMediaProjectionManager;
    }

    /**
     * 开始录制
     */
    public static void startRecord(VideoEncodeConfig videoEncodeConfig, @Nullable AudioEncodeConfig audioEncodeConfig, File savingDir, Callback callback) {
        if (mScreenRecorder != null) {
            callback.onFailure("已在屏幕录制中, 不能同时录制多个!");
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            callback.onFailure("安卓系统版本过低, 无法录制屏幕");
            return;
        }

        if (!savingDir.isDirectory()) {
            callback.onFailure("文件存储地址需要是文件夹");
            return;
        }

        if ((!savingDir.exists()) && (!savingDir.mkdirs())) {
            callback.onFailure("文件存储目录创建失败");
            return;
        }

        mVideoEncodeConfig = videoEncodeConfig;
        mAudioEncodeConfig = audioEncodeConfig;
        mSavingDir = savingDir;
        mCallback = callback;

        if (mMediaProjection == null) {
            requestMediaProjection();

        } else {
            startCapturing(mMediaProjection);
        }
    }

    /**
     * 开始录制
     */
    public static void startRecord(boolean audio, final File savingDir, final Callback callback) {
        Utils.findEncodersByTypeAsync(VIDEO_AVC, new Utils.Callback() {

            @Override
            public void onResult(MediaCodecInfo[] infos) {
                if (infos.length == 0) {
                    callback.onFailure("该设备不支持屏幕录制");
                    return;
                }

                MediaCodecInfo videoCodec = infos[0];

                if (videoCodec.getName() == null) {
                    callback.onFailure("该设备不支持屏幕录制");
                    return;
                }

                AudioEncodeConfig audioEncodeConfig = null;

                Integer width = 1080;
                Integer height = 1920;
                Double framerate = new Double(25);
                int iframe = 1;
                int bitrate = 5000 * 1000;

                MediaCodecInfo.VideoCapabilities videoCapabilities = videoCodec.getCapabilitiesForType(VIDEO_AVC).getVideoCapabilities();

                //匹配宽分辨率
                Range<Integer> widthRange = videoCapabilities.getSupportedWidths();
                width = widthRange.clamp(width);

                if (width == null) {
                    callback.onFailure("该设备不支持屏幕录制");
                    return;
                }

                //匹配高分辨率
                Range<Integer> heightRange = videoCapabilities.getSupportedHeightsFor(width);
                height = heightRange.clamp(height);

                if (height == null) {
                    callback.onFailure("该设备不支持屏幕录制");
                    return;
                }

                if (!videoCapabilities.isSizeSupported(width, height)) {
                    height = heightRange.getUpper();

                    if (!videoCapabilities.isSizeSupported(width, height)) {
                        callback.onFailure("该设备不支持屏幕录制");
                        return;
                    }
                }

                //匹配码率
                Range<Double> frameRateRange = videoCapabilities.getSupportedFrameRatesFor(width, height);
                framerate = frameRateRange.clamp(framerate);

                if (framerate == null) {
                    callback.onFailure("该设备不支持屏幕录制");
                    return;
                }

                MediaCodecInfo.CodecProfileLevel profileLevel = Utils.toProfileLevel("Default");
                VideoEncodeConfig videoEncodeConfig = new VideoEncodeConfig(width, height, bitrate, framerate.intValue(), iframe, videoCodec.getName(), VIDEO_AVC, profileLevel);

                startRecord(videoEncodeConfig, audioEncodeConfig, savingDir, callback);
            }

        });
    }

    public static void stopRecord() {
        stopCapturing();
    }

    public static void handleActivityResult(int resultCode, @Nullable Intent data) {
        MediaProjection mediaProjection = getMediaProjectionManager().getMediaProjection(resultCode, data);

        if (mediaProjection == null) {
            mCallback.onFailure("该设备不支持屏幕录制");
            return;
        }

        mMediaProjection = mediaProjection;
        mMediaProjection.registerCallback(new MediaProjection.Callback() {

            @Override
            public void onStop() {
                stopCapturing();
            }

        }, getHandler());

        startCapturing(mediaProjection);
    }

    private static Handler getHandler() {
        if (mHandler == null) {
            mHandler = new Handler(Looper.getMainLooper());
        }

        return mHandler;
    }

    private static void requestMediaProjection() {
        Intent captureIntent = getMediaProjectionManager().createScreenCaptureIntent();
        mCallback.sendCaptureIntent(captureIntent);
    }

    private static void startCapturing(MediaProjection mediaProjection) {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
        File file = new File(mSavingDir, "屏幕录像_" + format.format(new Date()) + "_" + mVideoEncodeConfig.width + "x" + mVideoEncodeConfig.height + ".mp4");

        mScreenRecorder = createScreenRecorder(mediaProjection, mVideoEncodeConfig, mAudioEncodeConfig, file);
        mScreenRecorder.start();
    }

    private static void stopCapturing() {
        if (mScreenRecorder != null) {
            mScreenRecorder.quit();
            mScreenRecorder = null;
        }
    }

    private static ScreenRecorder createScreenRecorder(MediaProjection mediaProjection, VideoEncodeConfig video, AudioEncodeConfig audio, final File output) {
        VirtualDisplay virtualDisplay = getOrCreateVirtualDisplay(mediaProjection, video);

        ScreenRecorder screenRecorder = new ScreenRecorder(video, audio, virtualDisplay, output.getAbsolutePath());
        screenRecorder.setCallback(new ScreenRecorder.Callback() {

            long startTime = 0;

            @Override
            public void onStop(final Throwable error) {
                getHandler().post(new Runnable() {

                    @Override
                    public void run() {
                        stopCapturing();

                        if (error != null) {
                            output.deleteOnExit();
                            mCallback.onFailure("录制失败!");

                        } else {
                            Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                                    .addCategory(Intent.CATEGORY_DEFAULT)
                                    .setData(Uri.fromFile(output));
                            mApplication.sendBroadcast(intent);

                            mCallback.onSuccess(output);
                        }
                    }

                });
            }

            @Override
            public void onStart() {
                getHandler().post(new Runnable() {

                    @Override
                    public void run() {
                        mCallback.onStart();
                    }

                });
            }

            @Override
            public void onRecording(final long presentationTimeUs) {
                if (startTime <= 0) {
                    startTime = presentationTimeUs;
                }

                final long time = (presentationTimeUs - startTime) / 1000;

                getHandler().post(new Runnable() {

                    @Override
                    public void run() {
                        mCallback.onRecording(time);
                    }

                });
            }

        });

        return screenRecorder;
    }

    private static VirtualDisplay getOrCreateVirtualDisplay(MediaProjection mediaProjection, VideoEncodeConfig videoEncodeConfig) {
        if (mVirtualDisplay == null) {
            mVirtualDisplay = mediaProjection.createVirtualDisplay("ScreenRecorder-display0",
                    videoEncodeConfig.width, videoEncodeConfig.height, 1,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    null, null, null);

        } else {
            Point size = new Point();
            mVirtualDisplay.getDisplay().getSize(size);

            if (size.x != videoEncodeConfig.width || size.y != videoEncodeConfig.height) {
                mVirtualDisplay.resize(videoEncodeConfig.width, videoEncodeConfig.height, 1);
            }
        }

        return mVirtualDisplay;
    }

    public interface Callback {

        void sendCaptureIntent(Intent captureIntent);

        void onStart();

        void onRecording(long time);

        void onSuccess(File file);

        void onFailure(String msg);

    }

}