// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.Tracks;
import androidx.media3.common.text.CueGroup;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.datasource.HttpDataSource.HttpDataSourceException;
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException;

import java.util.HashMap;
import java.util.Map;

public abstract class ExoPlayerEventListener implements Player.Listener {
  private boolean isBuffering = false;
  private boolean isInitialized;
  protected final ExoPlayer exoPlayer;
  protected final VideoPlayerCallbacks events;
  @NonNull protected final Context context;

  protected enum RotationDegrees {
    ROTATE_0(0),
    ROTATE_90(90),
    ROTATE_180(180),
    ROTATE_270(270);

    private final int degrees;

    RotationDegrees(int degrees) {
      this.degrees = degrees;
    }

    public static RotationDegrees fromDegrees(int degrees) {
      for (RotationDegrees rotationDegrees : RotationDegrees.values()) {
        if (rotationDegrees.degrees == degrees) {
          return rotationDegrees;
        }
      }
      throw new IllegalArgumentException("Invalid rotation degrees specified: " + degrees);
    }

    public int getDegrees() {
      return this.degrees;
    }
  }

  public ExoPlayerEventListener(
        @NonNull Context context,
      @NonNull ExoPlayer exoPlayer, @NonNull VideoPlayerCallbacks events, boolean initialized) {
    this.context = context;
    this.exoPlayer = exoPlayer;
    this.events = events;
    this.isInitialized = initialized;
  }

  private void setBuffering(boolean buffering) {
    if (isBuffering == buffering) {
      return;
    }
    isBuffering = buffering;
    if (buffering) {
      events.onBufferingStart();
    } else {
      events.onBufferingEnd();
    }
  }

  protected abstract void sendInitialized();

  @Override
  public void onPlaybackStateChanged(final int playbackState) {
    switch (playbackState) {
      case Player.STATE_BUFFERING:
        setBuffering(true);
        events.onBufferingUpdate(exoPlayer.getBufferedPosition());
        break;
      case Player.STATE_READY:
        if (isInitialized) {
          return;
        }
        isInitialized = true;
        sendInitialized();
        break;
      case Player.STATE_ENDED:
        events.onCompleted();
        break;
      case Player.STATE_IDLE:
        break;
    }
    if (playbackState != Player.STATE_BUFFERING) {
      setBuffering(false);
    }
  }

  /*@Override
  public void onTracksChanged(Tracks tracks) {
    for (Tracks.Group group : tracks.getGroups()) {
      for (int i = 0; i < group.length; i++) {
        TrackGroup trackGroup = group.getMediaTrackGroup();

        if (group.isTrackSupported(i)) {
          Format format = trackGroup.getFormat(i);

          // Stampa tipo e info della traccia
          Log.d("TrackInfo", "Track type: " + format.sampleMimeType);

          if (MimeTypes.isText(format.sampleMimeType)) {
            Log.d("Subtitle", "Subtitle track: " + format.language + " (" + format.label + ")");
          }
        }
      }
    }
  }*/

  @Override
  public void onCues(CueGroup cueGroup) {
    Player.Listener.super.onCues(cueGroup);
    //Log.d("SUPER SUB - CUES", "onCues");
    //if(textTrackIndex != null) {

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
      events.success(event);
    //}
  }

  @Override
  public void onPlayerError(@NonNull final PlaybackException error) {
    setBuffering(false);
    if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
      // See
      // https://exoplayer.dev/live-streaming.html#behindlivewindowexception-and-error_code_behind_live_window
      exoPlayer.seekToDefaultPosition();
      exoPlayer.prepare();
    } else {
      if (events != null) {
        Throwable cause = error.getCause();
        if (cause instanceof HttpDataSourceException) {
          // An HTTP error occurred.
          HttpDataSourceException httpError = (HttpDataSourceException) cause;
          // It's possible to find out more about the error both by casting and by
          // querying the cause.
          if (httpError instanceof InvalidResponseCodeException) {
            InvalidResponseCodeException _e = (InvalidResponseCodeException) httpError;
            events.onError("VideoError", "Network error: " + _e.responseCode, null);
            return;
          } else if (httpError instanceof HttpDataSourceException) {
            events.onError("VideoError", "Network error: Source not reachable", null);
            return;
          } else {
            // Try calling httpError.getCause() to retrieve the underlying cause,
            // although note that it may be null.
          }
        }
        //eventSink.error("VideoError", "Video player had error - " + error, null);
        events.onError("VideoError", "Can't play stream.", null);
        //eventSink.error("VideoError", "Player Switch", null);
      }
    }
  }

  @Override
  public void onIsPlayingChanged(boolean isPlaying) {
    events.onIsPlayingStateUpdate(isPlaying);
  }
}
