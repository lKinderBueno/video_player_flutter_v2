package io.flutter.plugins.videoplayer;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;

public class CustomExoPlayerBuilder {
    ExoPlayer exoPlayer;
    public CustomExoPlayerBuilder(
            @NonNull Context context,
            @NonNull VideoAsset asset
    ){
        SharedPreferences sharedPref = context.getSharedPreferences("FlutterSharedPreferences",Context.MODE_PRIVATE);
        boolean enableExtensions = sharedPref.getBoolean("flutter.USE_LIB", false);

        DefaultRenderersFactory renderersFactory;
        if(enableExtensions){
            Log.d("INIT FFMPEG", "SI ESTENSIONI");
            renderersFactory = new DefaultRenderersFactory(context)
                    .setEnableDecoderFallback(true)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER).forceEnableMediaCodecAsynchronousQueueing();
        }else {
            Log.d("INIT FFMPEG", "NO ESTENSIONI");
            renderersFactory = new DefaultRenderersFactory(context)
                    .setEnableDecoderFallback(true)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF);
        }

        DefaultTrackSelector trackSelector = new DefaultTrackSelector(context);
        DefaultTrackSelector.Parameters.Builder tsParamsBuilder = trackSelector.buildUponParameters()
                .setAllowAudioMixedChannelCountAdaptiveness(true)
                .setAllowAudioMixedSampleRateAdaptiveness(true)
                .setAllowAudioMixedMimeTypeAdaptiveness(true)
                .setAllowVideoMixedMimeTypeAdaptiveness(true)
                .setAllowVideoNonSeamlessAdaptiveness(true)
                .setExceedAudioConstraintsIfNecessary(true)
                .setExceedVideoConstraintsIfNecessary(true)
                .setExceedRendererCapabilitiesIfNecessary(true);
        trackSelector.setParameters(tsParamsBuilder);

        //DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(context)
        //        .setLiveTargetOffsetMs(5000);

        LoadControl loadControl = new DefaultLoadControl.Builder()
                //.setBufferDurationsMs(10000, 120000, 200, 700) //minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs
                //.setBufferDurationsMs(32*1024, 64*1024, 1024, 1024) //minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs
                .setBufferDurationsMs(50000 , 50000 , DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS, DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS)
                .setTargetBufferBytes(C.LENGTH_UNSET)
                .setPrioritizeTimeOverSizeThresholds(false)
                .build();


        exoPlayer = new ExoPlayer.Builder(context)
                .setMediaSourceFactory(asset.getMediaSourceFactory(context))
                .setUseLazyPreparation(true)
                .setLoadControl(loadControl)
                .setTrackSelector(trackSelector)
                .setRenderersFactory(renderersFactory)
                .build();
    }

    public ExoPlayer getExoPlayer() {
        return exoPlayer;
    }
}
