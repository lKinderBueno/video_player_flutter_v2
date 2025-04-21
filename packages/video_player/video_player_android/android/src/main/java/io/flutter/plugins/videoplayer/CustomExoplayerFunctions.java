package io.flutter.plugins.videoplayer;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.MappingTrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelector;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;

public class CustomExoplayerFunctions {
    static public boolean isExtensionEnabled(@NonNull Context context){
        //Log.d("############ CONTEXT NON NULL ", "############ CONTEXT NON NULL ");
        SharedPreferences sharedPref = context.getSharedPreferences("FlutterSharedPreferences",Context.MODE_PRIVATE);
        return sharedPref.getBoolean("flutter.USE_LIB", false);
    }

    static public boolean canPlayVideo(@NonNull Context context, @NonNull ExoPlayer exoPlayer){
        if(isExtensionEnabled(context)) return true;
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
        return  audio && video;
    }

    static public List<String> getTrackDescriptionsByType(ExoPlayer exoPlayer, int trackType) {
        List<String> trackDescriptions = new ArrayList<>();

        Tracks currentTracks = exoPlayer.getCurrentTracks();

        for (Tracks.Group group : currentTracks.getGroups()) {
            if (group.getType() == trackType) {
                for (int i = 0; i < group.length; i++) {
                    Format format = group.getTrackFormat(i);

                    StringBuilder description = new StringBuilder();

                    // Etichetta personalizzata
                    description.append("Track ").append(i);

                    if (format.label != null) {
                        description.append(" - ").append(format.label);
                    }

                    if (format.language != null) {
                        description.append(" [").append(format.language).append("]");
                    }

                    if (format.sampleMimeType != null) {
                        description.append(" (").append(format.sampleMimeType).append(")");
                    }

                    trackDescriptions.add(description.toString());
                }
            }
        }

        return trackDescriptions;
    }

    static public ArrayList<String> getVideoTracks(ExoPlayer exoPlayer){
        return new ArrayList<String>(getTrackDescriptionsByType(exoPlayer, C.TRACK_TYPE_VIDEO));
    }

    static public ArrayList<String> getAudioTracks(ExoPlayer exoPlayer){
        return new ArrayList<String>(getTrackDescriptionsByType(exoPlayer, C.TRACK_TYPE_AUDIO));
    }


    static void setTrack(ExoPlayer exoPlayer, int trackType, String trackName, int trackIndex) {
        Tracks currentTracks = exoPlayer.getCurrentTracks();
        int index = -1;

        for (Tracks.Group group : currentTracks.getGroups()) {
            if (group.getType() == trackType) {
                for (int i = 0; i < group.length; i++) {
                    index +=1;
                    Format format = group.getTrackFormat(i);


                    StringBuilder description = new StringBuilder();

                    // Etichetta personalizzata
                    description.append("Track ").append(i);

                    if (format.label != null) {
                        description.append(" - ").append(format.label);
                    }

                    if (format.language != null) {
                        description.append(" [").append(format.language).append("]");
                    }

                    if (format.sampleMimeType != null) {
                        description.append(" (").append(format.sampleMimeType).append(")");
                    }

                    if(trackName == description.toString() || index == trackIndex) {
                        exoPlayer.setTrackSelectionParameters(
                                exoPlayer.getTrackSelectionParameters()
                                        .buildUpon()
                                        .setOverrideForType(
                                                new TrackSelectionOverride(
                                                        group.getMediaTrackGroup(),
                                                        i))
                                        .build());
                        return;
                    }

                }
            }
        }
    }

    static void setAudioTrack(ExoPlayer exoPlayer, String trackName){
        setTrack(exoPlayer, C.TRACK_TYPE_AUDIO, trackName, -1);
    }

    static void setAudioTrackByIndex(ExoPlayer exoPlayer, int index){
        setTrack(exoPlayer, C.TRACK_TYPE_AUDIO, null, index);
    }

    static void setVideoTrack(ExoPlayer exoPlayer, String trackName){
        setTrack(exoPlayer, C.TRACK_TYPE_VIDEO, trackName, -1);
    }

    static void setVideoTrackByIndex(ExoPlayer exoPlayer, int index){
        setTrack(exoPlayer, C.TRACK_TYPE_VIDEO, null, index);
    }


    static List<Messages.GetEmbeddedSubtitlesMessage> getEmbeddedSubtitles(ExoPlayer exoPlayer){
        List<Messages.GetEmbeddedSubtitlesMessage> subtitleItems = new ArrayList<>();
        int rendererIndex = 2;
        Tracks currentTracks = exoPlayer.getCurrentTracks();
        ImmutableList<Tracks.Group> trackGroups = currentTracks.getGroups();
        for (Tracks.Group group : trackGroups) {
            for (int i = 0; i < group.length; i++) {
                Format format = group.getTrackFormat(i);

                if (MimeTypes.isText(format.sampleMimeType)) {
                    int g = trackGroups.indexOf(group);

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

    static void setEmbeddedSubtitles(ExoPlayer exoPlayer, Long trackIndex, Long groupIndex, Long rendererIndex){
        Tracks currentTracks = exoPlayer.getCurrentTracks();
        ImmutableList<Tracks.Group> trackGroups = currentTracks.getGroups();

        for (Tracks.Group group : trackGroups) {
            for (int i = 0; i < group.length; i++) {
                Format format = group.getTrackFormat(i);

                if (MimeTypes.isText(format.sampleMimeType)) {
                    int g = trackGroups.indexOf(group);

                    if(trackIndex == i && groupIndex == g) {
                        exoPlayer.setTrackSelectionParameters(
                                exoPlayer.getTrackSelectionParameters()
                                        .buildUpon()
                                        .setOverrideForType(
                                                new TrackSelectionOverride(
                                                        group.getMediaTrackGroup(),
                                                        i))
                                        .build()
                        );
                        return;
                    }
                }
            }
        }
    }
}
