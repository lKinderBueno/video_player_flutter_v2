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
import androidx.media3.exoplayer.trackselection.TrackSelector;
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
  public Context context;

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
    this.context = context;
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
    return CustomExoplayerFunctions.getEmbeddedSubtitles(exoPlayer);
  }

  void setEmbeddedSubtitles(Long trackIndex, Long groupIndex, Long rendererIndex) {
    CustomExoplayerFunctions.setEmbeddedSubtitles(exoPlayer, trackIndex, groupIndex, rendererIndex);
  }

  ArrayList<String> getAudioTracks() {
    return CustomExoplayerFunctions.getAudioTracks(exoPlayer);
  }

  void setAudioTrack(String trackName) {
    CustomExoplayerFunctions.setAudioTrack(exoPlayer, trackName);
  }

  void setAudioTrackByIndex(int  index) {
    CustomExoplayerFunctions.setAudioTrackByIndex(exoPlayer, index);
  }


  ArrayList<String> getVideoTracks() {
    return CustomExoplayerFunctions.getVideoTracks(exoPlayer);
  }

  void setVideoTrack(String trackName) {
    CustomExoplayerFunctions.setVideoTrack(exoPlayer, trackName);
  }

  void setVideoTrackByIndex(int  index) {
    CustomExoplayerFunctions.setVideoTrackByIndex(exoPlayer, index);
  }
}