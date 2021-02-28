package Adapters;

import android.content.Context;
import android.support.v4.media.MediaMetadataCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.spotifylearn.R;

import java.util.ArrayList;

public class PlaylistRecyclerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String TAG = "PlaylistRecyclerAdapter";

    private ArrayList<MediaMetadataCompat> mMediaList = new ArrayList<>();
    private Context mContext;
    private IMediaSelector mIMediaSelector;
    private int mSelectedIndex;

    public PlaylistRecyclerAdapter(Context context, ArrayList<MediaMetadataCompat> mediaList, IMediaSelector mediaSelector) {
        Log.d(TAG, "PlaylistRecyclerAdapter: called.");
        this.mMediaList = mediaList;
        this.mContext = context;
        this.mIMediaSelector = mediaSelector;
        mSelectedIndex = -1;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.layout_playlist_list, null);
        ViewHolder vh = new ViewHolder(view, mIMediaSelector);
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int i) {
        ((ViewHolder) viewHolder).title.setText(mMediaList.get(i).getDescription().getTitle());
        ((ViewHolder) viewHolder).artist.setText(mMediaList.get(i).getDescription().getSubtitle());

        //changing color of playing song
        if (i == mSelectedIndex) {
            ((ViewHolder) viewHolder).title.setTextColor(ContextCompat.getColor(mContext, R.color.green));
        } else {
            ((ViewHolder) viewHolder).title.setTextColor(ContextCompat.getColor(mContext, R.color.white));
        }
    }

    @Override
    public int getItemCount() {
        return mMediaList.size();
    }

    public void setSelectedIndex(int index) {
        mSelectedIndex = index;
        notifyDataSetChanged();
    }

    public int getSelectedIndex() {
        return mSelectedIndex;
    }

    public int getIndexOfItem(MediaMetadataCompat mediaItem) {
        for (int i = 0; i < mMediaList.size(); i++) {
            if (mMediaList.get(i).getDescription().getMediaId().equals(mediaItem.getDescription().getMediaId())) {
                return i;
            }
        }
        return -1;
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private TextView title, artist;
        private IMediaSelector iMediaSelector;

        public ViewHolder(@NonNull View itemView, IMediaSelector categorySelector) {
            super(itemView);
            title = itemView.findViewById(R.id.media_title);
            artist = itemView.findViewById(R.id.media_artist);
            iMediaSelector = categorySelector;

            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            iMediaSelector.onMediaSelected(getAdapterPosition());                                       // getting the position of the seleted media
        }
    }

    public interface IMediaSelector {

        void onMediaSelected(int position);

    }

}
