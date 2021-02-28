package Adapters;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.example.spotifylearn.R;

import java.util.ArrayList;

public class HomeRecyclerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final String TAG = "HomeRecyclerAdapter";
    private ArrayList<String> mCategories = new ArrayList<>();
    private Context mContext;
    private IHomeSelector mIHomeSelector;

    public HomeRecyclerAdapter(ArrayList<String> mCategories, Context mContext, IHomeSelector mIHomeSelector) {
        this.mCategories = mCategories;
        this.mContext = mContext;
        this.mIHomeSelector = mIHomeSelector;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.layout_category_list, null);
        Log.d(TAG, "onCreateViewHolder: METHOD CALLED");
        return new ViewHolder(view, mIHomeSelector);
        
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int i) {

        Log.d(TAG, "onBindViewHolder: METHOD CALLED");

        ((ViewHolder)viewHolder).category.setText(mCategories.get(i));
        RequestOptions requestOptions = new RequestOptions()
                .error(R.drawable.ic_launcher_background);

        Drawable iconResource = null;
        switch(mCategories.get(i)){
            case "Music":{
                iconResource = ContextCompat.getDrawable(mContext, R.drawable.ic_audiotrack_white_24dp);
                Log.d(TAG, "onBindViewHolder: MUSIC CONDITION CALLED");
                break;
            }

            case "Podcasts":{
                iconResource = ContextCompat.getDrawable(mContext, R.drawable.ic_mic_white_24dp);
                Log.d(TAG, "onBindViewHolder: PODCASTS CONDITION CALLED");
                break;
            }
        }


        Glide.with(mContext)
                .setDefaultRequestOptions(requestOptions)
                .load(iconResource)
                .into(((ViewHolder)viewHolder).category_icon);

    }

    @Override
    public int getItemCount() {
        return mCategories.size();
    }



    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener{

        private TextView category;
        private ImageView category_icon;
        private IHomeSelector iHomeSelector;


        public ViewHolder(@NonNull View itemView, IHomeSelector iHomeSelector) {
            super(itemView);
            category = itemView.findViewById(R.id.category_title);
            category_icon = itemView.findViewById(R.id.category_icon);
            this.iHomeSelector = iHomeSelector;
            Log.d(TAG, "ViewHolder: METHOD CALLED");
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            iHomeSelector.onCategorySelected(getAdapterPosition());
            Log.d(TAG, "onClick: METHOD CALLED");
        }
    }

    public interface IHomeSelector{
        void onCategorySelected(int postion);

    }
}
