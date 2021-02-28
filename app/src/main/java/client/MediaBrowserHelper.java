package client;

import android.content.ComponentName;
import android.content.Context;
import android.os.RemoteException;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;

import java.util.List;


//CONNECTS THE UI TO THE SERVICE
public class MediaBrowserHelper {

    private static final String TAG = "MediaBrowserHelper";

    private final Context mContext;
    private final Class<? extends MediaBrowserServiceCompat> mMediaBrowserServiceClass;             // service class

    private MediaBrowserCompat mMediaBrowser;
    private MediaControllerCompat mMediaController;

    //browser call backs
    private MediaBrowserConnectionCallback mMediaBrowserConnectionCallback;                         // connecting to the service
    private final MediaBrowserSubscriptionCallback mMediaBrowserSubscriptionCallback;              //
    private MediaControllerCallback mMediaControllerCallback;
    private MediaBrowserHelperCallback mMediaBrowserCallback;                                        // this is the interface var
    private boolean mWasConfigurationChange;

// class responsible for subscribing to the service
    public MediaBrowserHelper(Context context, Class<? extends MediaBrowserServiceCompat> serviceClass) {


        mContext = context;
        mMediaBrowserServiceClass = serviceClass;

        mMediaBrowserConnectionCallback = new MediaBrowserConnectionCallback();
        mMediaBrowserSubscriptionCallback = new MediaBrowserSubscriptionCallback();
        mMediaControllerCallback = new MediaControllerCallback();


    }


    public void setMediaBrowserHelperCallback(MediaBrowserHelperCallback callback){
        //initialising the MediaBrowserHelperCallback interface, method will be called in main class
        mMediaBrowserCallback = callback;

    }

    // Receives callbacks from the MediaController and updates the UI state,
    // i.e.: Which is the current item, whether it's playing or paused, etc.

    private class MediaControllerCallback extends MediaControllerCompat.Callback {

        @Override
        public void onMetadataChanged(final MediaMetadataCompat metadata) {
            Log.d(TAG, "onMetadataChanged: CALLED");
            if(mMediaBrowserCallback != null){
                mMediaBrowserCallback.onMetadataChanged(metadata);
            }
        }

        @Override
        public void onPlaybackStateChanged(@Nullable final PlaybackStateCompat state) {
            Log.d(TAG, "onPlaybackStateChanged: CALLED");
            if(mMediaBrowserCallback != null){
                mMediaBrowserCallback.onPlaybackStateChanged(state);
            }
        }

        // This might happen if the MusicService is killed while the Activity is in the
        // foreground and onStart() has been called (but not onStop()).
        @Override
        public void onSessionDestroyed() {
            onPlaybackStateChanged(null);

        }
    }


    public void subscribeToNewPlaylist(String newPlatlistId){

    //      String currentPlaylistId
    //        if(!currentPlaylistId.equals("")){
    //            mMediaBrowser.unsubscribe(currentPlaylistId);
    //        }
        mMediaBrowser.subscribe(newPlatlistId, mMediaBrowserSubscriptionCallback);
    }


    public void onStart(boolean wasConfigurationChange) {
        mWasConfigurationChange = wasConfigurationChange;

        if (mMediaBrowser == null) {
            mMediaBrowser =
                    new MediaBrowserCompat(
                            mContext,
                            new ComponentName(mContext, mMediaBrowserServiceClass),
                            mMediaBrowserConnectionCallback,
                            null);
            mMediaBrowser.connect();                                                                // connecting the browser to the service
        }
        Log.d(TAG, "onStart: CALLED: Creating MediaBrowser, and connecting");
    }

    public void onStop() {

        if (mMediaController != null) {
            mMediaController.unregisterCallback(mMediaControllerCallback);                          // unregistering media Controller callback
            mMediaController = null;
        }

        if (mMediaBrowser != null && mMediaBrowser.isConnected()) {
            mMediaBrowser.disconnect();                                                             // disconnect the media browser
            mMediaBrowser = null;
        }

        Log.d(TAG, "onStop: CALLED: Releasing MediaController, Disconnecting from MediaBrowser");
    }

    // Receives callbacks from the MediaBrowser when it has successfully connected to the
    // MediaBrowserService (MusicService).


    private class MediaBrowserConnectionCallback extends MediaBrowserCompat.ConnectionCallback {

        //callback from the service class???

        @Override
        public void onConnected() {
            Log.d(TAG, "onConnected: CALLED");

            try {

                // Get a MediaController for the MediaSession.
                mMediaController =
                 new MediaControllerCompat(mContext, mMediaBrowser.getSessionToken());

                mMediaController.registerCallback(mMediaControllerCallback);                        // call back for the mController

                mMediaBrowserCallback.onMediaControllerConnected(mMediaController);                 // if it is connected, then we link the media controller ond the seekBar


            } catch (RemoteException e) {
                Log.d(TAG, String.format("onConnected: Problem: %s", e.toString()));
                throw new RuntimeException(e);
            }

            mMediaBrowser.subscribe(mMediaBrowser.getRoot(), mMediaBrowserSubscriptionCallback);    // more like subscribing to the playlist
            Log.d(TAG, "onConnected: CALLED: subscribing to: " + mMediaBrowser.getRoot());    //

            //mMediaBrowserCallback.onMediaControllerConnected(mMediaController);
        }


    }

    // Receives callbacks from the MediaBrowser when the MediaBrowserService has loaded new media
    // that is ready for playback.

    public class MediaBrowserSubscriptionCallback extends MediaBrowserCompat.SubscriptionCallback {

        @Override
        public void onChildrenLoaded(@NonNull String parentId,@NonNull List<MediaBrowserCompat.MediaItem> children) {

            Log.d(TAG, "onChildrenLoaded: CALLED: " + parentId + ", " + children.toString());

            if(!mWasConfigurationChange){                                                           // to avoid data duplication
                for (final MediaBrowserCompat.MediaItem mediaItem : children) {
                    Log.d(TAG, "onChildrenLoaded: CALLED: queue item: " + mediaItem.getMediaId());
                    mMediaController.addQueueItem(mediaItem.getDescription());                      // adding Media Items to playlist
                }
            }

        }
    }

    public MediaControllerCompat.TransportControls getTransportControls() {

        // method used to connect the client to the service
        if (mMediaController == null) {
            Log.d(TAG, "getTransportControls: MediaController is null!");
            throw new IllegalStateException("MediaController is null!");
        }

        return mMediaController.getTransportControls();
    }


}





