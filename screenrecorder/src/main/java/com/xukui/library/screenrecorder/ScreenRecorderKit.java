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

import static com.xukui.library.screenrecorder.ScreenRecorder.AUDIO_AAC;
import static com.xukui.library.screenrecorder.ScreenRecorder.VIDEO_AVC;

public class ScreenRecorderKit {

    public static Application mApplication;

    private static MediaProjectionManager mMediaProjectionManager;
    private static ScreenRecorder mScreenRecorder;
    private static MediaProjection mMediaProjection;
    private static VirtualDisplay mVirtualDisplay;

    private static MediaCodecInfo[] mVideoCodecInfos;
    private static MediaCodecInfo[] mAudioCodecInfos;

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
     * 正在录制中
     */
    public static boolean isRecording() {
        return mScreenRecorder != null;
    }

    /**
     * 准备录制
     */
    public static void prepareRecord(VideoEncodeConfig videoEncodeConfig, @Nullable AudioEncodeConfig audioEncodeConfig, File savingDir, Callback callback) {
        if (mScreenRecorder != null) {
            callback.onFailure(false, "已在屏幕录制中, 不能同时录制多个!");
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            callback.onFailure(false, "安卓系统版本过低, 无法录制屏幕");
            return;
        }

        if (!savingDir.isDirectory()) {
            callback.onFailure(false, "文件存储地址需要是文件夹");
            return;
        }

        if ((!savingDir.exists()) && (!savingDir.mkdirs())) {
            callback.onFailure(false, "文件存储目录创建失败");
            return;
        }

        if (videoEncodeConfig == null) {
            callback.onFailure(false, "该设备不支持屏幕录制");
            return;
        }

        mVideoEncodeConfig = videoEncodeConfig;
        mAudioEncodeConfig = audioEncodeConfig;
        mSavingDir = savingDir;
        mCallback = callback;

        if (mMediaProjection == null) {
            requestMediaProjection();

        } else {
            mCallback.onPrepared();
        }
    }

    /**
     * 开始录制
     */
    public static void startRecord(String prefix) {
        if (prefix == null || mMediaProjection == null) {
            return;
        }

        startCapturing(prefix, mMediaProjection);
    }

    @Nullable
    public static VideoEncodeConfig createDefaultVideoEncodeConfig() {
        try {
            if (mVideoCodecInfos == null) {
                mVideoCodecInfos = Utils.findEncodersByType(VIDEO_AVC);
            }

            if (mVideoCodecInfos == null || mVideoCodecInfos.length == 0) {
                return null;
            }

            MediaCodecInfo codecInfo = mVideoCodecInfos[0];

            if (codecInfo.getName() == null) {
                return null;
            }

            Integer width = 1080;
            Integer height = 1920;
            Double framerate = new Double(30);
            int iframe = 1;

            MediaCodecInfo.VideoCapabilities capabilities = codecInfo.getCapabilitiesForType(VIDEO_AVC).getVideoCapabilities();

            //匹配宽分辨率
            Range<Integer> widthRange = capabilities.getSupportedWidths();
            width = widthRange.clamp(width);

            if (width == null) {
                return null;
            }

            //匹配高分辨率
            Range<Integer> heightRange = capabilities.getSupportedHeightsFor(width);
            height = heightRange.clamp(height);

            if (height == null) {
                return null;
            }

            if (!capabilities.isSizeSupported(width, height)) {
                height = heightRange.getUpper();

                if (!capabilities.isSizeSupported(width, height)) {
                    return null;
                }
            }

            //匹配码率
            Range<Double> frameRateRange = capabilities.getSupportedFrameRatesFor(width, height);
            framerate = frameRateRange.clamp(framerate);

            if (framerate == null) {
                return null;
            }

            MediaCodecInfo.CodecProfileLevel profileLevel = Utils.toProfileLevel("Default");
            int bitrate = Math.min(width * height * 3, 5000 * 1000);

            return new VideoEncodeConfig(width, height, bitrate, framerate.intValue(), iframe, codecInfo.getName(), VIDEO_AVC, profileLevel);

        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public static AudioEncodeConfig createDefaultAudioEncodeConfig() {
        try {
            if (mAudioCodecInfos == null) {
                mAudioCodecInfos = Utils.findEncodersByType(AUDIO_AAC);
            }

            if (mAudioCodecInfos == null || mAudioCodecInfos.length == 0) {
                return null;
            }

            MediaCodecInfo codecInfo = mAudioCodecInfos[0];

            if (codecInfo.getName() == null) {
                return null;
            }

            Integer bitrate = 80000;
            int samplerate = 44100;
            int channelCount = 1;
            int profile = MediaCodecInfo.CodecProfileLevel.AACObjectMain;

            MediaCodecInfo.AudioCapabilities capabilities = codecInfo.getCapabilitiesForType(AUDIO_AAC).getAudioCapabilities();

            //匹配比特率
            Range<Integer> bitrateRange = capabilities.getBitrateRange();
            bitrate = bitrateRange.clamp(bitrate);

            if (bitrate == null) {
                return null;
            }

            //匹配采样器
            int[] sampleRates = capabilities.getSupportedSampleRates();
            for (int value : sampleRates) {
                if (samplerate == value) {
                    break;

                } else {
                    samplerate = value;
                }
            }

            //匹配配置文件
            String[] aacProfiles = Utils.aacProfiles();
            if (aacProfiles != null && aacProfiles.length > 0) {
                MediaCodecInfo.CodecProfileLevel level = Utils.toProfileLevel(aacProfiles[0]);

                if (level != null) {
                    profile = level.profile;
                }
            }

            return new AudioEncodeConfig(codecInfo.getName(), AUDIO_AAC, bitrate, samplerate, channelCount, profile);

        } catch (Exception e) {
            return null;
        }
    }

    public static void stopRecord() {
        stopCapturing();
    }

    public static void handleActivityResult(int resultCode, @Nullable Intent data) {
        MediaProjection mediaProjection = getMediaProjectionManager().getMediaProjection(resultCode, data);

        if (mediaProjection == null) {
            mCallback.onCancel();
            return;
        }

        mMediaProjection = mediaProjection;
        mMediaProjection.registerCallback(new MediaProjection.Callback() {

            @Override
            public void onStop() {
                stopCapturing();
            }

        }, getHandler());

        mCallback.onPrepared();
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

    private static void startCapturing(String prefix, MediaProjection mediaProjection) {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
        File file = new File(mSavingDir, prefix + "_" + format.format(new Date()) + ".mp4");

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
                            mCallback.onFailure(true, "录制失败!");

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

        void onPrepared();

        void onStart();

        void onRecording(long time);

        void onSuccess(File file);

        void onFailure(boolean capturing, String msg);

        void onCancel();

    }

}