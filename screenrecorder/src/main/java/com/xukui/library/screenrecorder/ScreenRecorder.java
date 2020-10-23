package com.xukui.library.screenrecorder;

import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

import static android.media.MediaFormat.MIMETYPE_AUDIO_AAC;
import static android.media.MediaFormat.MIMETYPE_VIDEO_AVC;

public class ScreenRecorder {

    private static final String TAG = "ScreenRecorder";

    private static final int INVALID_INDEX = -1;

    public static final String VIDEO_AVC = MIMETYPE_VIDEO_AVC; // H.264 Advanced Video Coding
    public static final String AUDIO_AAC = MIMETYPE_AUDIO_AAC; // H.264 Advanced Audio Coding

    private String mDstPath;
    private VideoEncoder mVideoEncoder;
    private MicRecorder mAudioEncoder;

    private MediaFormat mVideoOutputFormat = null;
    private MediaFormat mAudioOutputFormat = null;
    private int mVideoTrackIndex = INVALID_INDEX;
    private int mAudioTrackIndex = INVALID_INDEX;
    private MediaMuxer mMuxer;
    private boolean mMuxerStarted = false;

    private AtomicBoolean mForceQuit = new AtomicBoolean(false);
    private AtomicBoolean mIsRunning = new AtomicBoolean(false);
    private VirtualDisplay mVirtualDisplay;

    private HandlerThread mWorker;
    private CallbackHandler mHandler;

    private Callback mCallback;
    private LinkedList<Integer> mPendingVideoEncoderBufferIndices = new LinkedList<>();
    private LinkedList<Integer> mPendingAudioEncoderBufferIndices = new LinkedList<>();
    private LinkedList<MediaCodec.BufferInfo> mPendingAudioEncoderBufferInfos = new LinkedList<>();
    private LinkedList<MediaCodec.BufferInfo> mPendingVideoEncoderBufferInfos = new LinkedList<>();

    /**
     * @param display for {@link VirtualDisplay#setSurface(Surface)}
     * @param dstPath saving path
     */
    public ScreenRecorder(VideoEncodeConfig video,
                          AudioEncodeConfig audio,
                          VirtualDisplay display,
                          String dstPath) {
        mVirtualDisplay = display;
        mDstPath = dstPath;
        mVideoEncoder = new VideoEncoder(video);
        mAudioEncoder = audio == null ? null : new MicRecorder(audio);
    }

    /**
     * stop task
     */
    public final void quit() {
        mForceQuit.set(true);

        if (!mIsRunning.get()) {
            release();

        } else {
            signalStop(false);
        }
    }

    public void start() {
        if (mWorker != null) {
            throw new IllegalStateException();
        }
        mWorker = new HandlerThread(TAG);
        mWorker.start();
        mHandler = new CallbackHandler(mWorker.getLooper());
        mHandler.sendEmptyMessage(MSG_START);
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    public String getSavedPath() {
        return mDstPath;
    }

    public interface Callback {
        void onStop(Throwable error);

        void onStart();

        void onRecording(long presentationTimeUs);
    }

    private static final int MSG_START = 0;
    private static final int MSG_STOP = 1;
    private static final int MSG_ERROR = 2;
    private static final int STOP_WITH_EOS = 1;

    private class CallbackHandler extends Handler {

        CallbackHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {

                case MSG_START:
                    try {
                        record();

                        if (mCallback != null) {
                            mCallback.onStart();
                        }
                        break;

                    } catch (Exception e) {
                        msg.obj = e;
                    }

                case MSG_STOP:
                case MSG_ERROR:
                    stopEncoders();

                    if (msg.arg1 != STOP_WITH_EOS) {
                        signalEndOfStream();
                    }

                    if (mCallback != null) {
                        mCallback.onStop((Throwable) msg.obj);
                    }

                    release();
                    break;
            }
        }
    }

    private void signalEndOfStream() {
        MediaCodec.BufferInfo eos = new MediaCodec.BufferInfo();
        ByteBuffer buffer = ByteBuffer.allocate(0);
        eos.set(0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        if (mVideoTrackIndex != INVALID_INDEX) {
            writeSampleData(mVideoTrackIndex, eos, buffer);
        }
        if (mAudioTrackIndex != INVALID_INDEX) {
            writeSampleData(mAudioTrackIndex, eos, buffer);
        }
        mVideoTrackIndex = INVALID_INDEX;
        mAudioTrackIndex = INVALID_INDEX;
    }

    private void record() {
        if (mIsRunning.get() || mForceQuit.get()) {
            throw new IllegalStateException();
        }
        if (mVirtualDisplay == null) {
            throw new IllegalStateException("maybe release");
        }
        mIsRunning.set(true);

        try {
            // create muxer
            mMuxer = new MediaMuxer(mDstPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            // create encoder and input surface
            prepareVideoEncoder();
            prepareAudioEncoder();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // "turn on" VirtualDisplay after VideoEncoder prepared
        mVirtualDisplay.setSurface(mVideoEncoder.getInputSurface());
    }

    private void muxVideo(int index, MediaCodec.BufferInfo buffer) {
        if (!mIsRunning.get()) {
            return;
        }
        if (!mMuxerStarted || mVideoTrackIndex == INVALID_INDEX) {
            mPendingVideoEncoderBufferIndices.add(index);
            mPendingVideoEncoderBufferInfos.add(buffer);
            return;
        }
        ByteBuffer encodedData = mVideoEncoder.getOutputBuffer(index);
        writeSampleData(mVideoTrackIndex, buffer, encodedData);
        mVideoEncoder.releaseOutputBuffer(index);
        if ((buffer.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mVideoTrackIndex = INVALID_INDEX;
            signalStop(true);
        }
    }


    private void muxAudio(int index, MediaCodec.BufferInfo buffer) {
        if (!mIsRunning.get()) {
            return;
        }
        if (!mMuxerStarted || mAudioTrackIndex == INVALID_INDEX) {
            mPendingAudioEncoderBufferIndices.add(index);
            mPendingAudioEncoderBufferInfos.add(buffer);
            return;

        }
        ByteBuffer encodedData = mAudioEncoder.getOutputBuffer(index);
        writeSampleData(mAudioTrackIndex, buffer, encodedData);
        mAudioEncoder.releaseOutputBuffer(index);
        if ((buffer.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mAudioTrackIndex = INVALID_INDEX;
            signalStop(true);
        }
    }

    private void writeSampleData(int track, MediaCodec.BufferInfo buffer, ByteBuffer encodedData) {
        if ((buffer.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            buffer.size = 0;
        }
        boolean eos = (buffer.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
        if (buffer.size == 0 && !eos) {
            encodedData = null;

        } else {
            if (buffer.presentationTimeUs != 0) { // maybe 0 if eos
                if (track == mVideoTrackIndex) {
                    resetVideoPts(buffer);
                } else if (track == mAudioTrackIndex) {
                    resetAudioPts(buffer);
                }
            }
            if (!eos && mCallback != null) {
                mCallback.onRecording(buffer.presentationTimeUs);
            }
        }
        if (encodedData != null) {
            encodedData.position(buffer.offset);
            encodedData.limit(buffer.offset + buffer.size);
            mMuxer.writeSampleData(track, encodedData, buffer);
        }
    }

    private long mVideoPtsOffset, mAudioPtsOffset;

    private void resetAudioPts(MediaCodec.BufferInfo buffer) {
        if (mAudioPtsOffset == 0) {
            mAudioPtsOffset = buffer.presentationTimeUs;
            buffer.presentationTimeUs = 0;

        } else {
            buffer.presentationTimeUs -= mAudioPtsOffset;
        }
    }

    private void resetVideoPts(MediaCodec.BufferInfo buffer) {
        if (mVideoPtsOffset == 0) {
            mVideoPtsOffset = buffer.presentationTimeUs;
            buffer.presentationTimeUs = 0;

        } else {
            buffer.presentationTimeUs -= mVideoPtsOffset;
        }
    }

    private void resetVideoOutputFormat(MediaFormat newFormat) {
        // should happen before receiving buffers, and should only happen once
        if (mVideoTrackIndex >= 0 || mMuxerStarted) {
            throw new IllegalStateException("output format already changed!");
        }
        mVideoOutputFormat = newFormat;
    }

    private void resetAudioOutputFormat(MediaFormat newFormat) {
        // should happen before receiving buffers, and should only happen once
        if (mAudioTrackIndex >= 0 || mMuxerStarted) {
            throw new IllegalStateException("output format already changed!");
        }
        mAudioOutputFormat = newFormat;
    }

    private void startMuxerIfReady() {
        if (mMuxerStarted || mVideoOutputFormat == null
                || (mAudioEncoder != null && mAudioOutputFormat == null)) {
            return;
        }

        mVideoTrackIndex = mMuxer.addTrack(mVideoOutputFormat);
        mAudioTrackIndex = mAudioEncoder == null ? INVALID_INDEX : mMuxer.addTrack(mAudioOutputFormat);
        mMuxer.start();
        mMuxerStarted = true;
        if (mPendingVideoEncoderBufferIndices.isEmpty() && mPendingAudioEncoderBufferIndices.isEmpty()) {
            return;
        }
        MediaCodec.BufferInfo info;
        while ((info = mPendingVideoEncoderBufferInfos.poll()) != null) {
            int index = mPendingVideoEncoderBufferIndices.poll();
            muxVideo(index, info);
        }
        if (mAudioEncoder != null) {
            while ((info = mPendingAudioEncoderBufferInfos.poll()) != null) {
                int index = mPendingAudioEncoderBufferIndices.poll();
                muxAudio(index, info);
            }
        }
    }

    // @WorkerThread
    private void prepareVideoEncoder() throws IOException {
        VideoEncoder.Callback callback = new VideoEncoder.Callback() {
            boolean ranIntoError = false;

            @Override
            public void onOutputBufferAvailable(BaseEncoder codec, int index, MediaCodec.BufferInfo info) {
                try {
                    muxVideo(index, info);

                } catch (Exception e) {
                    Message.obtain(mHandler, MSG_ERROR, e).sendToTarget();
                }
            }

            @Override
            public void onError(Encoder codec, Exception e) {
                ranIntoError = true;
                Message.obtain(mHandler, MSG_ERROR, e).sendToTarget();
            }

            @Override
            public void onOutputFormatChanged(BaseEncoder codec, MediaFormat format) {
                resetVideoOutputFormat(format);
                startMuxerIfReady();
            }
        };
        mVideoEncoder.setCallback(callback);
        mVideoEncoder.prepare();
    }

    private void prepareAudioEncoder() throws IOException {
        final MicRecorder micRecorder = mAudioEncoder;
        if (micRecorder == null) {
            return;
        }
        AudioEncoder.Callback callback = new AudioEncoder.Callback() {
            boolean ranIntoError = false;

            @Override
            public void onOutputBufferAvailable(BaseEncoder codec, int index, MediaCodec.BufferInfo info) {
                try {
                    muxAudio(index, info);

                } catch (Exception e) {
                    Message.obtain(mHandler, MSG_ERROR, e).sendToTarget();
                }
            }

            @Override
            public void onOutputFormatChanged(BaseEncoder codec, MediaFormat format) {
                resetAudioOutputFormat(format);
                startMuxerIfReady();
            }

            @Override
            public void onError(Encoder codec, Exception e) {
                ranIntoError = true;
                Message.obtain(mHandler, MSG_ERROR, e).sendToTarget();
            }


        };
        micRecorder.setCallback(callback);
        micRecorder.prepare();
    }

    private void signalStop(boolean stopWithEOS) {
        Message msg = Message.obtain(mHandler, MSG_STOP, stopWithEOS ? STOP_WITH_EOS : 0, 0);
        mHandler.sendMessageAtFrontOfQueue(msg);
    }

    private void stopEncoders() {
        mIsRunning.set(false);
        mPendingAudioEncoderBufferInfos.clear();
        mPendingAudioEncoderBufferIndices.clear();
        mPendingVideoEncoderBufferInfos.clear();
        mPendingVideoEncoderBufferIndices.clear();

        try {
            if (mVideoEncoder != null) {
                mVideoEncoder.stop();
            }

            if (mAudioEncoder != null) {
                mAudioEncoder.stop();
            }

        } catch (IllegalStateException e) {
        }
    }

    private void release() {
        if (mVirtualDisplay != null) {
            mVirtualDisplay.setSurface(null);
            mVirtualDisplay = null;
        }

        mVideoOutputFormat = mAudioOutputFormat = null;
        mVideoTrackIndex = mAudioTrackIndex = INVALID_INDEX;
        mMuxerStarted = false;

        if (mWorker != null) {
            mWorker.quitSafely();
            mWorker = null;
        }
        if (mVideoEncoder != null) {
            mVideoEncoder.release();
            mVideoEncoder = null;
        }
        if (mAudioEncoder != null) {
            mAudioEncoder.release();
            mAudioEncoder = null;
        }

        if (mMuxer != null) {
            try {
                mMuxer.stop();
                mMuxer.release();

            } catch (Exception e) {
            }

            mMuxer = null;
        }

        mHandler = null;
    }

    @Override
    protected void finalize() throws Throwable {
        if (mVirtualDisplay != null) {
            release();
        }
    }

}