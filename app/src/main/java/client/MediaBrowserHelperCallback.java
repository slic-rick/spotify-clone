package client;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;

//SENDS DATA FROM MEDIA BROWSER TO MAIN ACTIVITY
public interface MediaBrowserHelperCallback {

    void onMetadataChanged(final MediaMetadataCompat metadata);

    void onPlaybackStateChanged(PlaybackStateCompat state);

    void onMediaControllerConnected(MediaControllerCompat mediaController);   //connecting the seekbar and the media controller
}
