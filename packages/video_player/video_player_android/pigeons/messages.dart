// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'package:pigeon/pigeon.dart';

@ConfigurePigeon(PigeonOptions(
  dartOut: 'lib/src/messages.g.dart',
  dartTestOut: 'test/test_api.g.dart',
  javaOut: 'android/src/main/java/io/flutter/plugins/videoplayer/Messages.java',
  javaOptions: JavaOptions(
    package: 'io.flutter.plugins.videoplayer',
  ),
  copyrightHeader: 'pigeons/copyright.txt',
))

class TrackMessage {
  TrackMessage(this.playerId, this.trackName, this.index);
  int? playerId;
  String? trackName;
  int? index;
}

/// Pigeon equivalent of VideoViewType.
enum PlatformVideoViewType {
  textureView,
  platformView,
}

/// Information passed to the platform view creation.
class PlatformVideoViewCreationParams {
  const PlatformVideoViewCreationParams({
    required this.playerId,
  });

  final int playerId;
}

class CreateMessage {
  CreateMessage({required this.httpHeaders});
  String? asset;
  String? uri;
  String? packageName;
  String? formatHint;
  Map<String, String> httpHeaders;
  PlatformVideoViewType? viewType;

}

class GetEmbeddedSubtitlesMessage{
  GetEmbeddedSubtitlesMessage(this.language, this.label, this.trackIndex, this.groupIndex, this.renderIndex);

  final String? language;
  final String? label;
  final int trackIndex;
  final int groupIndex;
  final int renderIndex;
}

class SetEmbeddedSubtitlesMessage {
  SetEmbeddedSubtitlesMessage(
    this.playerId,
    this.language,
    this.label,
    this.trackIndex,
    this.groupIndex,
    this.renderIndex,
  );

  final int playerId;
  final String? language;
  final String? label;
  final int? trackIndex;
  final int? groupIndex;
  final int? renderIndex;
}

@HostApi(dartHostTestHandler: 'TestHostVideoPlayerApi')
abstract class AndroidVideoPlayerApi {
  void initialize();
  int create(CreateMessage msg);
  void dispose(int playerId);
  void setLooping(int playerId, bool looping);
  void setVolume(int playerId, double volume);
  void setPlaybackSpeed(int playerId, double speed);
  void play(int playerId);
  int position(int playerId);
  void seekTo(int playerId, int position);
  void pause(int playerId);
  void setMixWithOthers(bool mixWithOthers);
  void setAudioTrack(TrackMessage msg);
  void setAudioTrackByIndex(TrackMessage msg);
  List<String> getAudioTracks(int playerId);
  void setVideoTrack(TrackMessage msg);
  void setVideoTrackByIndex(TrackMessage msg);
  List<String> getVideoTracks(int playerId);
    List<GetEmbeddedSubtitlesMessage?> getEmbeddedSubtitles(int playerId);
  void setEmbeddedSubtitles(SetEmbeddedSubtitlesMessage msg);
}
