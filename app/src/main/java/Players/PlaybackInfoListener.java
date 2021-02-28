package Players;

import android.support.v4.media.session.PlaybackStateCompat;

//SENDS STATE FROM EXO PLAYER TO THE SERVICE
public interface PlaybackInfoListener {


    void onPlaybackStateChange(PlaybackStateCompat state);

    void onSeekTo(long progress, long max);

    void onPlaybackComplete();

    void updateUI(String newMediaId);

}
