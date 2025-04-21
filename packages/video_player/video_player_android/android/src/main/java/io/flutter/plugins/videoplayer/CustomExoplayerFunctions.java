package io.flutter.plugins.videoplayer;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlayer;

public class CustomExoplayerFunctions {
    static public boolean isExtensionEnabled(@NonNull Context context){
        if(context == null) {
            Log.d("############ CONTEXT NULL ", "############ CONTEXT NULL ");
            return false;
        }
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
}
