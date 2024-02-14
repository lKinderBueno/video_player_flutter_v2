// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer;

import static com.google.android.exoplayer2.Player.REPEAT_MODE_ALL;
import static com.google.android.exoplayer2.Player.REPEAT_MODE_OFF;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.view.Surface;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player.Listener;
import com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.extractor.ts.AdtsExtractor;
import com.google.android.exoplayer2.extractor.ts.TsExtractor;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.extractor.mp4.FragmentedMp4Extractor;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.hls.HlsExtractorFactory;
import com.google.android.exoplayer2.source.hls.DefaultHlsExtractorFactory;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.text.CueGroup;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.Parameters;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.DefaultTrackNameProvider;
import com.google.android.exoplayer2.ui.TrackNameProvider;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource.HttpDataSourceException;
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;

import io.flutter.plugin.common.EventChannel;
import io.flutter.view.TextureRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class VideoPlayer {
  private static final String FORMAT_SS = "ss";
  private static final String FORMAT_DASH = "dash";
  private static final String FORMAT_HLS = "hls";
  private static final String FORMAT_OTHER = "other";

  private ExoPlayer exoPlayer;

  private Long textTrackIndex;

  private Surface surface;

  private final TextureRegistry.SurfaceTextureEntry textureEntry;

  private QueuingEventSink eventSink;

  private final EventChannel eventChannel;

  private static final String USER_AGENT = "User-Agent";

  @VisibleForTesting boolean isInitialized = false;

  private final VideoPlayerOptions options;

  private DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true);

  private boolean enableExtensions = false;
  private DefaultTrackSelector trackSelector;
  private Context context;

  VideoPlayer(
          Context context,
          EventChannel eventChannel,
          TextureRegistry.SurfaceTextureEntry textureEntry,
          String dataSource,
          String formatHint,
          @NonNull Map<String, String> httpHeaders,
          VideoPlayerOptions options) {
    this.eventChannel = eventChannel;
    this.textureEntry = textureEntry;
    this.options = options;
    this.context = context;

    SharedPreferences sharedPref = context.getSharedPreferences("FlutterSharedPreferences",Context.MODE_PRIVATE);
    enableExtensions = sharedPref.getBoolean("flutter.USE_LIB", false);

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

    this.trackSelector = new DefaultTrackSelector(context);
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

    DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(context)
            .setLiveTargetOffsetMs(5000);

    LoadControl loadControl = new DefaultLoadControl.Builder()
            //.setBufferDurationsMs(10000, 120000, 200, 700) //minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs
            //.setBufferDurationsMs(32*1024, 64*1024, 1024, 1024) //minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs
            .setBufferDurationsMs(50000 , 50000 , DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS, DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS)
            .setTargetBufferBytes(C.LENGTH_UNSET)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build();

    ExoPlayer exoPlayer = new ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setUseLazyPreparation(true)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .setRenderersFactory(renderersFactory)
            .build();
    exoPlayer.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT);

    Uri uri = Uri.parse(dataSource);

    buildHttpDataSourceFactory(httpHeaders);
    DataSource.Factory dataSourceFactory =
            new DefaultDataSource.Factory(context, httpDataSourceFactory);

    MediaSource mediaSource = buildMediaSource(uri, dataSourceFactory, formatHint);

    exoPlayer.setMediaSource(mediaSource);
    exoPlayer.prepare();

    setUpVideoPlayer(exoPlayer, new QueuingEventSink());
  }

  // Constructor used to directly test members of this class.
  @VisibleForTesting
  VideoPlayer(
          ExoPlayer exoPlayer,
          EventChannel eventChannel,
          TextureRegistry.SurfaceTextureEntry textureEntry,
          VideoPlayerOptions options,
          QueuingEventSink eventSink,
          DefaultHttpDataSource.Factory httpDataSourceFactory) {
    this.eventChannel = eventChannel;
    this.textureEntry = textureEntry;
    this.options = options;
    this.httpDataSourceFactory = httpDataSourceFactory;

    setUpVideoPlayer(exoPlayer, eventSink);
  }

  @VisibleForTesting
  public void buildHttpDataSourceFactory(@NonNull Map<String, String> httpHeaders) {
    final boolean httpHeadersNotEmpty = !httpHeaders.isEmpty();
    final String userAgent =
            httpHeadersNotEmpty && httpHeaders.containsKey(USER_AGENT)
                    ? httpHeaders.get(USER_AGENT)
                    : "ExoPlayer";

    httpDataSourceFactory.setUserAgent(userAgent).setAllowCrossProtocolRedirects(true);

    if (httpHeadersNotEmpty) {
      httpDataSourceFactory.setDefaultRequestProperties(httpHeaders);
    }
  }

  private MediaSource buildMediaSource(
          Uri uri, DataSource.Factory mediaDataSourceFactory, String formatHint) {
    int type;
    if (formatHint == null) {
      type = Util.inferContentType(uri);
    } else {
      switch (formatHint) {
        case FORMAT_SS:
          type = C.CONTENT_TYPE_SS;
          break;
        case FORMAT_DASH:
          type = C.CONTENT_TYPE_DASH;
          break;
        case FORMAT_HLS:
          type = C.CONTENT_TYPE_HLS;
          break;
        case FORMAT_OTHER:
          type = C.CONTENT_TYPE_OTHER;
          break;
        default:
          type = -1;
          break;
      }
    }
    switch (type) {
      case C.CONTENT_TYPE_SS:
        return new SsMediaSource.Factory(
                new DefaultSsChunkSource.Factory(mediaDataSourceFactory), mediaDataSourceFactory)
                .createMediaSource(MediaItem.fromUri(uri));
      case C.CONTENT_TYPE_DASH:
        return new DashMediaSource.Factory(
                new DefaultDashChunkSource.Factory(mediaDataSourceFactory), mediaDataSourceFactory)
                .createMediaSource(MediaItem.fromUri(uri));
      case C.CONTENT_TYPE_HLS:
        HlsExtractorFactory hlsFactory = new DefaultHlsExtractorFactory();
        //.setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES);
        //| HlsExtractorFactory.FLAG_DETECT_ACCESS_UNITS)
        return new HlsMediaSource.Factory(mediaDataSourceFactory)
                .setAllowChunklessPreparation(true)
                .setExtractorFactory(hlsFactory)
                .createMediaSource(MediaItem.fromUri(uri));
      case C.CONTENT_TYPE_OTHER:
        DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory()
                //.setConstantBitrateSeekingEnabled(true)
                //.setConstantBitrateSeekingAlwaysEnabled(true)
                .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES
                        //  | DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS)
                        | DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
                        | DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS
                )
                .setTsExtractorTimestampSearchBytes(1500 * TsExtractor.TS_PACKET_SIZE)
                //.setTsExtractorTimestampSearchBytes(TsExtractor.TS_PACKET_SIZE)
                .setFragmentedMp4ExtractorFlags(FragmentedMp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS |
                        FragmentedMp4Extractor.FLAG_WORKAROUND_IGNORE_TFDT_BOX |
                        FragmentedMp4Extractor.FLAG_WORKAROUND_EVERY_VIDEO_FRAME_IS_SYNC_FRAME |
                        FragmentedMp4Extractor.FLAG_ENABLE_EMSG_TRACK)
                .setTsExtractorMode(TsExtractor.MODE_MULTI_PMT);
        //.setAdtsExtractorFlags(AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING);

        return new ProgressiveMediaSource.Factory(mediaDataSourceFactory, extractorsFactory)
                .setContinueLoadingCheckIntervalBytes(ProgressiveMediaSource.DEFAULT_LOADING_CHECK_INTERVAL_BYTES / 2)
                .createMediaSource(MediaItem.fromUri(uri));
      default:
      {
        throw new IllegalStateException("Unsupported type: " + type);
      }
    }
  }

  private void setUpVideoPlayer(ExoPlayer exoPlayer, QueuingEventSink eventSink) {
    this.exoPlayer = exoPlayer;
    this.eventSink = eventSink;

    eventChannel.setStreamHandler(
            new EventChannel.StreamHandler() {
              @Override
              public void onListen(Object o, EventChannel.EventSink sink) {
                eventSink.setDelegate(sink);
              }

              @Override
              public void onCancel(Object o) {
                eventSink.setDelegate(null);
              }
            });

    surface = new Surface(textureEntry.surfaceTexture());
    exoPlayer.setVideoSurface(surface);
    setAudioAttributes(exoPlayer, options.mixWithOthers);

    exoPlayer.addListener(
            new Listener() {
              private boolean isBuffering = false;

              public void setBuffering(boolean buffering) {
                if (isBuffering != buffering) {
                  isBuffering = buffering;
                  Map<String, Object> event = new HashMap<>();
                  event.put("event", isBuffering ? "bufferingStart" : "bufferingEnd");
                  eventSink.success(event);
                }
              }

              @Override
              public void onPlaybackStateChanged(final int playbackState) {
                if (playbackState == Player.STATE_BUFFERING) {
                  setBuffering(true);
                  sendBufferingUpdate();
                } else if (playbackState == Player.STATE_READY) {
                  if (!isInitialized) {
                    isInitialized = true;
                    sendInitialized();
                  }
                } else if (playbackState == Player.STATE_ENDED) {
                  Map<String, Object> event = new HashMap<>();
                  event.put("event", "completed");
                  eventSink.success(event);
                }

                if (playbackState != Player.STATE_BUFFERING) {
                  setBuffering(false);
                }
              }

              @Override
              public void onPlayerError(@NonNull final PlaybackException error) {
                setBuffering(false);
                if (eventSink != null) {
                  Throwable cause = error.getCause();
                  if (cause instanceof HttpDataSource.HttpDataSourceException) {
                    // An HTTP error occurred.
                    HttpDataSource.HttpDataSourceException httpError = (HttpDataSource.HttpDataSourceException) cause;
                    // It's possible to find out more about the error both by casting and by
                    // querying the cause.
                    if (httpError instanceof HttpDataSource.InvalidResponseCodeException) {
                      HttpDataSource.InvalidResponseCodeException _e = (HttpDataSource.InvalidResponseCodeException) httpError;
                      eventSink.error("VideoError", "Network error: " + _e.responseCode, null);
                      return;
                    }else if (httpError instanceof HttpDataSource.HttpDataSourceException) {
                      eventSink.error("VideoError", "Network error: Source not reachable", null);
                      return;
                    } else {
                      // Try calling httpError.getCause() to retrieve the underlying cause,
                      // although note that it may be null.
                    }
                  }
                  //eventSink.error("VideoError", "Video player had error - " + error, null);
                  eventSink.error("VideoError", "Can't play stream.", null);
                  //eventSink.error("VideoError", "Player Switch", null);
                }
              }

              @Override
              public void onIsPlayingChanged(boolean isPlaying) {
                if (eventSink != null) {
                  Map<String, Object> event = new HashMap<>();
                  event.put("event", "isPlayingStateUpdate");
                  event.put("isPlaying", isPlaying);
                  eventSink.success(event);
                }
              }

              @Override
              public void onCues(CueGroup cueGroup) {
                Listener.super.onCues(cueGroup);
                //Log.d("SUPER SUB - CUES", "onCues");
                if(textTrackIndex != null) {

                  Map<String, Object> event = new HashMap<>();
                  event.put("event", "subtitle");
                  if (!cueGroup.cues.isEmpty()) {
                    if (cueGroup.cues.get(0).text != null) {
                      //Log.d("SUPER SUB - CUES", cueGroup.cues.get(0).text.toString());
                      event.put("value", cueGroup.cues.get(0).text.toString());
                    }
                  } else {
                    //Log.d("SUPER SUB - CUES", "VUOTO :C");
                    event.put("value", "");
                  }
                  eventSink.success(event);
                }
              }
            });
  }

  void sendBufferingUpdate() {
    Map<String, Object> event = new HashMap<>();
    event.put("event", "bufferingUpdate");
    List<? extends Number> range = Arrays.asList(0, exoPlayer.getBufferedPosition());
    // iOS supports a list of buffered ranges, so here is a list with a single range.
    event.put("values", Collections.singletonList(range));
    eventSink.success(event);
  }

  private static void setAudioAttributes(ExoPlayer exoPlayer, boolean isMixMode) {
    exoPlayer.setAudioAttributes(
            new AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            !isMixMode);
  }

  void play() {
    exoPlayer.setPlayWhenReady(true);
  }

  void pause() {
    exoPlayer.setPlayWhenReady(false);
  }

  void setLooping(boolean value) {
    exoPlayer.setRepeatMode(value ? REPEAT_MODE_ALL : REPEAT_MODE_OFF);
  }

  void setVolume(double value) {
    float bracketedValue = (float) Math.max(0.0, Math.min(1.0, value));
    exoPlayer.setVolume(bracketedValue);
  }

  ArrayList<String> getAudioTracks() {
    ArrayList<String> tracks = new ArrayList<>();
    MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
    if(mappedTrackInfo == null){
      return tracks;
    }

    for(int i =0;i<mappedTrackInfo.getRendererCount();i++)
    {
      if(mappedTrackInfo.getRendererType(i)!= C.TRACK_TYPE_AUDIO)
        continue;

      TrackGroupArray trackGroupArray = mappedTrackInfo.getTrackGroups(i);
      for(int j =0;j<trackGroupArray.length;j++) {

        TrackGroup group = trackGroupArray.get(j);
        TrackNameProvider provider = new DefaultTrackNameProvider(context.getResources());
        for (int k = 0; k < group.length; k++) {
          if ((mappedTrackInfo.getTrackSupport(i, j, k) &0b111) == C.FORMAT_HANDLED) {
            tracks.add(provider.getTrackName(group.getFormat(k)));
          }

        }
      }

    }
    return tracks;
  }

  void setAudioTrack(String trackName) {
    MappingTrackSelector.MappedTrackInfo mappedTrackInfo =  trackSelector.getCurrentMappedTrackInfo();

    StringBuilder str = new StringBuilder();

    for (int i = 0; i < mappedTrackInfo.getRendererCount(); i++) {
      if (mappedTrackInfo.getRendererType(i) != C.TRACK_TYPE_AUDIO)
        continue;

      TrackGroupArray trackGroupArray = mappedTrackInfo.getTrackGroups(i);
      for (int j = 0; j < trackGroupArray.length; j++) {

        TrackGroup group = trackGroupArray.get(j);
        TrackNameProvider provider = new DefaultTrackNameProvider(context.getResources());
        for (int k = 0; k < group.length; k++) {

          if (provider.getTrackName(group.getFormat(k)).equals(trackName)) {
            exoPlayer.setTrackSelectionParameters(
                    exoPlayer.getTrackSelectionParameters()
                            .buildUpon()
                            .setOverrideForType(
                                    new TrackSelectionOverride(
                                            group,
                                            k))
                            .build());
            return ;

          }

        }
      }

    }
  }

  void setAudioTrackByIndex(int  index) {
    MappingTrackSelector.MappedTrackInfo mappedTrackInfo =
            trackSelector.getCurrentMappedTrackInfo();

    int trackIndex = 0;

    for (int i = 0; i < mappedTrackInfo.getRendererCount(); i++) {
      if (mappedTrackInfo.getRendererType(i) != C.TRACK_TYPE_AUDIO)
        continue;

      TrackGroupArray trackGroupArray = mappedTrackInfo.getTrackGroups(i);
      for (int j = 0; j < trackGroupArray.length; j++) {

        TrackGroup group = trackGroupArray.get(j);
        TrackNameProvider provider = new DefaultTrackNameProvider(context.getResources());
        for (int k = 0; k < group.length; k++) {

          if (trackIndex == index) {
            exoPlayer.setTrackSelectionParameters(
                    exoPlayer.getTrackSelectionParameters()
                            .buildUpon()
                            .setOverrideForType(
                                    new TrackSelectionOverride(
                                            group,
                                            k))
                            .build());
            return ;
          }
          trackIndex++;
        }
      }

    }
  }




  ArrayList<String> getVideoTracks() {
    ArrayList<String> tracks = new ArrayList<>();
    MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
    if(mappedTrackInfo == null){
      return tracks;
    }

    for(int i =0;i<mappedTrackInfo.getRendererCount();i++)
    {
      if(mappedTrackInfo.getRendererType(i)!= C.TRACK_TYPE_VIDEO)
        continue;

      TrackGroupArray trackGroupArray = mappedTrackInfo.getTrackGroups(i);
      for(int j =0;j<trackGroupArray.length;j++) {

        TrackGroup group = trackGroupArray.get(j);
        TrackNameProvider provider = new DefaultTrackNameProvider(context.getResources());
        for (int k = 0; k < group.length; k++) {
          if ((mappedTrackInfo.getTrackSupport(i, j, k) &0b111) == C.FORMAT_HANDLED) {
            tracks.add(provider.getTrackName(group.getFormat(k)));
          }

        }
      }

    }
    return tracks;
  }

  void setVideoTrack(String trackName) {
    MappingTrackSelector.MappedTrackInfo mappedTrackInfo =  trackSelector.getCurrentMappedTrackInfo();

    StringBuilder str = new StringBuilder();

    for (int i = 0; i < mappedTrackInfo.getRendererCount(); i++) {
      if (mappedTrackInfo.getRendererType(i) != C.TRACK_TYPE_VIDEO)
        continue;

      TrackGroupArray trackGroupArray = mappedTrackInfo.getTrackGroups(i);
      for (int j = 0; j < trackGroupArray.length; j++) {

        TrackGroup group = trackGroupArray.get(j);
        TrackNameProvider provider = new DefaultTrackNameProvider(context.getResources());
        for (int k = 0; k < group.length; k++) {

          if (provider.getTrackName(group.getFormat(k)).equals(trackName)) {
            exoPlayer.setTrackSelectionParameters(
                    exoPlayer.getTrackSelectionParameters()
                            .buildUpon()
                            .setOverrideForType(
                                    new TrackSelectionOverride(
                                            group,
                                            k))
                            .build());
            return ;

          }

        }
      }

    }
  }

  void setVideoTrackByIndex(int  index) {
    MappingTrackSelector.MappedTrackInfo mappedTrackInfo =
            trackSelector.getCurrentMappedTrackInfo();

    int trackIndex = 0;

    for (int i = 0; i < mappedTrackInfo.getRendererCount(); i++) {
      if (mappedTrackInfo.getRendererType(i) != C.TRACK_TYPE_VIDEO)
        continue;

      TrackGroupArray trackGroupArray = mappedTrackInfo.getTrackGroups(i);
      for (int j = 0; j < trackGroupArray.length; j++) {

        TrackGroup group = trackGroupArray.get(j);
        TrackNameProvider provider = new DefaultTrackNameProvider(context.getResources());
        for (int k = 0; k < group.length; k++) {

          if (trackIndex == index) {
            exoPlayer.setTrackSelectionParameters(
                    exoPlayer.getTrackSelectionParameters()
                            .buildUpon()
                            .setOverrideForType(
                                    new TrackSelectionOverride(
                                            group,
                                            k))
                            .build());
            return ;
          }
          trackIndex++;
        }
      }

    }
  }




  void setPlaybackSpeed(double value) {
    // We do not need to consider pitch and skipSilence for now as we do not handle them and
    // therefore never diverge from the default values.
    final PlaybackParameters playbackParameters = new PlaybackParameters(((float) value));

    exoPlayer.setPlaybackParameters(playbackParameters);
  }

  void seekTo(int location) {
    exoPlayer.seekTo(location);
  }

  long getPosition() {
    return exoPlayer.getCurrentPosition();
  }

  @SuppressWarnings("SuspiciousNameCombination")
  @VisibleForTesting
  void sendInitialized() {
    if (isInitialized) {
      if(!enableExtensions){
        boolean audio = true;
        boolean video = true;

        Tracks tracks = exoPlayer.getCurrentTracks();
        for (Tracks.Group trackGroup : tracks.getGroups()) {
          // Group level information.
          //boolean trackInGroupIsSelected = trackGroup.isSelected();
          //boolean trackInGroupIsSupported = trackGroup.isSupported();
          for (int j = 0; j < trackGroup.length; j++) {
            TrackGroup _tracks = trackGroup.getMediaTrackGroup();
            for (int i = 0; i < _tracks.length; i++) {
              if (MimeTypes.isAudio(_tracks.getFormat(i).sampleMimeType)) {
                audio = trackGroup.isTrackSupported(i);
                if(audio) {
                  break;
                }
              }
            }
            for (int i = 0; i < _tracks.length; i++) {
              if (MimeTypes.isVideo(_tracks.getFormat(i).sampleMimeType)) {
                video = trackGroup.isTrackSupported(i);
                if(video) {
                  break;
                }
              }
            }
          }
        }
        if(!audio || !video){
          eventSink.error("VideoError", "Player Switch", null);
          exoPlayer.stop();
          //exoPlayer.removeListener(listener);

          isInitialized = false;

          //initPlayer(true);

          //surface = new Surface(textureEntry.surfaceTexture());
          //exoPlayer.setVideoSurface(surface);
          return;
        }
      }else{
        //if(FfmpegLibrary.isAvailable())
        //  Log.d("Check FFMPEG", "${FfmpegLibrary.isAvailable()}");

      }
      Map<String, Object> event = new HashMap<>();
      event.put("event", "initialized");
      event.put("duration", exoPlayer.getDuration());

      if (exoPlayer.getVideoFormat() != null) {
        Format videoFormat = exoPlayer.getVideoFormat();
        int width = videoFormat.width;
        int height = videoFormat.height;
        int rotationDegrees = videoFormat.rotationDegrees;
        // Switch the width/height if video was taken in portrait mode
        if (rotationDegrees == 90 || rotationDegrees == 270) {
          width = exoPlayer.getVideoFormat().height;
          height = exoPlayer.getVideoFormat().width;
        }
        event.put("width", width);
        event.put("height", height);

        double ratio = width/height;
        if(ratio >=0 && ratio < 1.33){//(ratio != 16/9 || ratio != 4/3 || ratio != 21/9){
          event.put("width", height * 16 / 9);
          //eventSink.error("VideoError", "RATIO - " + ratio, null);
        }

        // Rotating the video with ExoPlayer does not seem to be possible with a Surface,
        // so inform the Flutter code that the widget needs to be rotated to prevent
        // upside-down playback for videos with rotationDegrees of 180 (other orientations work
        // correctly without correction).
        if (rotationDegrees == 180) {
          event.put("rotationCorrection", rotationDegrees);
        }
      }else{
        event.put("width", 1280);
        event.put("height", 720);
        // eventSink.error("VideoError", "NO VIDEO SIZE", null);
      }

      eventSink.success(event);
    }
  }


  List<Messages.GetEmbeddedSubtitlesMessage> getEmbeddedSubtitles() {
    List<Messages.GetEmbeddedSubtitlesMessage> subtitleItems = new ArrayList<>();
    int rendererIndex = 2;

    MappingTrackSelector.MappedTrackInfo trackInfo = trackSelector.getCurrentMappedTrackInfo();
    if (trackInfo == null) {
      // TrackSelector not initialized
      return subtitleItems;
    }

    Tracks tracks = exoPlayer.getCurrentTracks();
    ImmutableList<Tracks.Group> trackGroups = tracks.getGroups();
    for (Tracks.Group trackGroup : tracks.getGroups()) {
      int g = trackGroups.indexOf(trackGroup);
      TrackGroup _tracks = trackGroup.getMediaTrackGroup();
      for (int i = 0; i < _tracks.length; i++) {
        Format format = _tracks.getFormat(i);
        String mimeType = format.sampleMimeType;
        if (MimeTypes.isText(mimeType)) {
          subtitleItems.add(
                  new Messages.GetEmbeddedSubtitlesMessage.Builder()
                          .setLanguage(format.language)
                          .setLabel(format.label)
                          .setTrackIndex((long) i)
                          .setGroupIndex((long) g)
                          .setRenderIndex((long) rendererIndex)
                          .build()
          );
        }
      }
    }


    return subtitleItems;
  }

  void setEmbeddedSubtitles(Long trackIndex, Long groupIndex, Long rendererIndex) {
    this.textTrackIndex = trackIndex;
    boolean isDisabled;
    if(trackSelector == null) return;
    DefaultTrackSelector.Parameters parameters = trackSelector.getParameters();
    isDisabled = parameters.getRendererDisabled(Math.toIntExact(2));
    DefaultTrackSelector.Parameters.Builder parametersBuilder = trackSelector.buildUponParameters().setRendererDisabled(C.TRACK_TYPE_VIDEO, false);
    parametersBuilder.setRendererDisabled(2, isDisabled);
    parametersBuilder.clearOverrides();

    if(trackIndex != null && groupIndex != null && rendererIndex != null) {

      MappingTrackSelector.MappedTrackInfo trackInfo =  trackSelector == null ? null : trackSelector.getCurrentMappedTrackInfo();
      if (trackSelector == null || trackInfo == null) {
        //Log.d("SUPER SUB", "NO SUB");
        // TrackSelector not initialized
        return;
      }

      parametersBuilder.setRendererDisabled(Math.toIntExact(2), isDisabled);

      Tracks tracks = exoPlayer.getCurrentTracks();
      ImmutableList<Tracks.Group> trackGroups = tracks.getGroups();
      Tracks.Group trackGroup = trackGroups.get(Math.toIntExact(groupIndex));
      TrackGroup _tracks = trackGroup.getMediaTrackGroup();
      Format format = _tracks.getFormat(Math.toIntExact(trackIndex));
      String mimeType = format.sampleMimeType;
      if (MimeTypes.isText(mimeType)) {
        TrackSelectionOverride override = new TrackSelectionOverride(_tracks, Math.toIntExact(trackIndex));
        parametersBuilder.addOverride(override);
      }
      //Log.d("SUPER SUB", "TUTTO OKAY");
    }else{
      //Log.d("SUPER SUB", "DISABILITATO");
      Map<String, Object> event = new HashMap<>();
      event.put("event", "subtitle");
      event.put("value", "");
      eventSink.success(event);
    }
    trackSelector.setParameters(parametersBuilder);
  }

  void dispose() {
    if (isInitialized) {
      exoPlayer.stop();
    }
    textureEntry.release();
    eventChannel.setStreamHandler(null);
    if (surface != null) {
      surface.release();
    }
    if (exoPlayer != null) {
      exoPlayer.release();
    }
  }
}
