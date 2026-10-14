package com.s23cp5054.posewithme.ui.gallery;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.s23cp5054.posewithme.R;
import com.s23cp5054.posewithme.data.model.Photo;
import com.s23cp5054.posewithme.data.repository.PhotoRepository;
import com.s23cp5054.posewithme.utils.ImageLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Grid of photos taken with the app, newest first. */
public class GalleryActivity extends AppCompatActivity {

    private static final int COLUMNS = 3;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final PhotoAdapter adapter = new PhotoAdapter();
    private TextView countText;
    private View emptyText;
    private int thumbSizePx;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_gallery);
        View header = findViewById(R.id.galleryHeader);
        RecyclerView grid = findViewById(R.id.galleryGrid);
        int headerTop = header.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.galleryRoot), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            header.setPadding(header.getPaddingLeft(), headerTop + bars.top,
                    header.getPaddingRight(), header.getPaddingBottom());
            grid.setPadding(grid.getPaddingLeft(), 0, grid.getPaddingRight(), bars.bottom);
            return insets;
        });

        countText = findViewById(R.id.galleryCount);
        emptyText = findViewById(R.id.galleryEmpty);
        findViewById(R.id.galleryBack).setOnClickListener(v -> finish());
        thumbSizePx = getResources().getDisplayMetrics().widthPixels / COLUMNS;
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        grid.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ioExecutor.execute(() -> {
            List<Photo> list = new PhotoRepository(this).getAll();
            mainHandler.post(() -> {
                adapter.setPhotos(list);
                countText.setText(getResources().getQuantityString(R.plurals.gallery_count,
                        list.size(), list.size()));
                emptyText.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdown();
        mainHandler.removeCallbacksAndMessages(null);
    }

    private final class PhotoAdapter extends RecyclerView.Adapter<PhotoViewHolder> {
        private final List<Photo> photos = new ArrayList<>();

        @SuppressWarnings("NotifyDataSetChanged")
        void setPhotos(List<Photo> newPhotos) {
            photos.clear();
            photos.addAll(newPhotos);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new PhotoViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_photo, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
            Photo photo = photos.get(position);
            ImageLoader.get(GalleryActivity.this).load(photo.uri, holder.thumb, thumbSizePx);
            if (photo.score != null) {
                holder.badge.setVisibility(View.VISIBLE);
                holder.badge.setText(getString(R.string.percent_short,
                        Math.round(photo.score * 100f)));
                holder.badge.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(
                        GalleryActivity.this, photo.score >= 0.8f ? R.color.match_good
                                : photo.score >= 0.5f ? R.color.match_adjust
                                : R.color.match_off)));
            } else {
                holder.badge.setVisibility(View.GONE);
            }
            holder.thumb.setOnClickListener(v -> startActivity(
                    new Intent(GalleryActivity.this, PhotoActivity.class)
                            .putExtra(PhotoActivity.EXTRA_PHOTO_ID, photo.id)));
        }

        @Override
        public int getItemCount() {
            return photos.size();
        }
    }

    private static final class PhotoViewHolder extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final TextView badge;

        PhotoViewHolder(View itemView) {
            super(itemView);
            thumb = itemView.findViewById(R.id.photoThumb);
            badge = itemView.findViewById(R.id.photoBadge);
        }
    }
}
