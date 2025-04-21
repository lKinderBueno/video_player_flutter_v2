// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer.platformview;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import io.flutter.plugins.videoplayer.CustomExoplayerFunctions;
import io.flutter.plugins.videoplayer.ExoPlayerEventListener;
import io.flutter.plugins.videoplayer.VideoPlayerCallbacks;
import java.util.Objects;

public final class PlatformViewExoPlayerEventListener extends ExoPlayerEventListener {
  @VisibleForTesting
  public PlatformViewExoPlayerEventListener(
          @NonNull Context context,
      @NonNull ExoPlayer exoPlayer, @NonNull VideoPlayerCallbacks events) {
    this(context, exoPlayer, events, false);
  }

  public PlatformViewExoPlayerEventListener(
          @NonNull Context context,
      @NonNull ExoPlayer exoPlayer, @NonNull VideoPlayerCallbacks events, boolean initialized) {
    super(context, exoPlayer, events, initialized);
  }

  @OptIn(markerClass = UnstableApi.class)
  @Override
  protected void sendInitialized() {
    // We can't rely on VideoSize here, because at this point it is not available - the platform
    // view was not created yet. We use the video format instead.
    Format videoFormat = exoPlayer.getVideoFormat();
    RotationDegrees rotationCorrection =
        RotationDegrees.fromDegrees(Objects.requireNonNull(videoFormat).rotationDegrees);
    int width = videoFormat.width;
    int height = videoFormat.height;

    // Switch the width/height if video was taken in portrait mode and a rotation
    // correction was detected.
    if (rotationCorrection == RotationDegrees.ROTATE_90
        || rotationCorrection == RotationDegrees.ROTATE_270) {
      width = videoFormat.height;
      height = videoFormat.width;

      rotationCorrection = RotationDegrees.fromDegrees(0);
    }

    if(!CustomExoplayerFunctions.canPlayVideo(this.context, exoPlayer)){
      events.onError("VideoError", "Player Switch", null);
      exoPlayer.stop();
    }else events.onInitialized(width, height, exoPlayer.getDuration(), rotationCorrection.getDegrees());
  }
}
