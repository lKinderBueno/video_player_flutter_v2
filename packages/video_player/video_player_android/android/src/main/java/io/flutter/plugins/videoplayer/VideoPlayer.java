// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer;

import static androidx.media3.common.Player.REPEAT_MODE_ALL;
import static androidx.media3.common.Player.REPEAT_MODE_OFF;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.MappingTrackSelector.MappedTrackInfo;
import androidx.media3.exoplayer.trackselection.MappingTrackSelector;
import androidx.media3.ui.DefaultTrackNameProvider;
import androidx.media3.ui.TrackNameProvider;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.DefaultLoadControl;

import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.ui.DefaultTrackNameProvider;

import androidx.media3.common.Format;
import androidx.media3.common.TrackGroup;
import androidx.media3.exoplayer.source.TrackGroupArray;

import java.util.ArrayList;
import java.util.List;
import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 * A class responsible for managing video playback using {@link ExoPlayer}.
 *
 * <p>It provides methods to control playback, adjust volume, and handle seeking.
 */
public abstract class VideoPlayer {
  @NonNull private final ExoPlayerProvider exoPlayerProvider;
  @NonNull private final MediaItem mediaItem;
  @NonNull private final VideoPlayerOptions options;
  @NonNull protected final VideoPlayerCallbacks videoPlayerEvents;
  @NonNull protected ExoPlayer exoPlayer;

  private Long textTrackIndex;
  private boolean enableExtensions = false;
  private DefaultTrackSelector trackSelector;
  private Context context;

  /** A closure-compatible signature since {@link java.util.function.Supplier} is API level 24. */
  public interface ExoPlayerProvider {
    /**
     * Returns a new {@link ExoPlayer}.
     *
     * @return new instance.
     */
    @NonNull
    ExoPlayer get();
  }

  public VideoPlayer(
          @NonNull Context context,
          @NonNull VideoPlayerCallbacks events,
          @NonNull MediaItem mediaItem,
          @NonNull VideoPlayerOptions options,
          @NonNull ExoPlayerProvider exoPlayerProvider) {
    this.videoPlayerEvents = events;
    this.mediaItem = mediaItem;
    this.options = options;
    this.exoPlayerProvider = exoPlayerProvider;
    this.exoPlayer = createVideoPlayer();
  }

  @NonNull
  protected ExoPlayer createVideoPlayer() {
    ExoPlayer exoPlayer = exoPlayerProvider.get();
    exoPlayer.setMediaItem(mediaItem);
    exoPlayer.prepare();

    exoPlayer.addListener(createExoPlayerEventListener(exoPlayer));
    setAudioAttributes(exoPlayer, options.mixWithOthers);

    return exoPlayer;
  }

  @NonNull
  protected abstract ExoPlayerEventListener createExoPlayerEventListener(
          @NonNull ExoPlayer exoPlayer);

  void sendBufferingUpdate() {
    videoPlayerEvents.onBufferingUpdate(exoPlayer.getBufferedPosition());
  }

  private static void setAudioAttributes(ExoPlayer exoPlayer, boolean isMixMode) {
    exoPlayer.setAudioAttributes(
            new AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            !isMixMode);
  }

  void play() {
    exoPlayer.play();
  }

  void pause() {
    exoPlayer.pause();
  }

  void setLooping(boolean value) {
    exoPlayer.setRepeatMode(value ? REPEAT_MODE_ALL : REPEAT_MODE_OFF);
  }

  void setVolume(double value) {
    float bracketedValue = (float) Math.max(0.0, Math.min(1.0, value));
    exoPlayer.setVolume(bracketedValue);
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

  @NonNull
  public ExoPlayer getExoPlayer() {
    return exoPlayer;
  }

  public void dispose() {
    exoPlayer.release();
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
      //Map<String, Object> event = new HashMap<>();
      //event.put("event", "subtitle");
      //event.put("value", "");
      //videoPlayerEvents.success(event);
    }
    trackSelector.setParameters(parametersBuilder);
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
}