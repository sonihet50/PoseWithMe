package com.s23cp5054.posewithme.ui.poses;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.s23cp5054.posewithme.R;
import com.s23cp5054.posewithme.pose.model.PoseTemplate;
import com.s23cp5054.posewithme.ui.pose.PoseThumbnailView;

import java.util.ArrayList;
import java.util.List;

/** Grid cards showing a pose silhouette, name and framing/difficulty. */
public final class PoseCardAdapter extends RecyclerView.Adapter<PoseCardAdapter.Holder> {

    public interface OnPoseClick {
        void onPoseClick(PoseTemplate pose);
    }

    private final List<PoseTemplate> poses = new ArrayList<>();
    private final OnPoseClick listener;
    @LayoutRes private final int layout;

    public PoseCardAdapter(OnPoseClick listener) {
        this(listener, R.layout.item_pose_card);
    }

    /** {@code layout} must contain the same view ids as item_pose_card. */
    public PoseCardAdapter(OnPoseClick listener, @LayoutRes int layout) {
        this.listener = listener;
        this.layout = layout;
    }

    @SuppressWarnings("NotifyDataSetChanged")
    public void setPoses(List<PoseTemplate> newPoses) {
        poses.clear();
        poses.addAll(newPoses);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        PoseTemplate pose = poses.get(position);
        holder.thumbnail.setPose(pose.pose);
        holder.name.setText(pose.name);
        holder.meta.setText(PoseLabels.shortMeta(holder.itemView.getContext(), pose));
        holder.badge.setVisibility("user".equals(pose.source.type) ? View.VISIBLE : View.GONE);
        holder.itemView.setOnClickListener(v -> listener.onPoseClick(pose));
    }

    @Override
    public int getItemCount() {
        return poses.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final PoseThumbnailView thumbnail;
        final TextView name;
        final TextView meta;
        final View badge;

        Holder(View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.poseThumbnail);
            name = itemView.findViewById(R.id.poseName);
            meta = itemView.findViewById(R.id.poseMeta);
            badge = itemView.findViewById(R.id.sourceBadge);
        }
    }
}
