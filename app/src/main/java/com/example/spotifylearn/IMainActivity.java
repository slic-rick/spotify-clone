package com.example.spotifylearn;

import android.support.v4.media.MediaMetadataCompat;

import Models.Artist;
import util.MyPreferenceManager;

//INTERFACE that sends info to the fragments
public interface IMainActivity {

    void hideProgressBar();

    void showProgressBar();

    void onCategorySelected(String category);               // for creating a new category Fragment

    void onArtistSelected(String category, Artist artist);  // for creating a new artist Fragment

    void setActionBarTitle(String title);                   //for setting the action bar title

    void playPause();

    //void onMediaSelected(MediaMetadataCompat mediaItem);
    void onMediaSelected(String playlistId, MediaMetadataCompat mediaItem, int position);

    MyApplication getMyApplicationInstance();
    MyPreferenceManager getMyPreferenceManager();

}