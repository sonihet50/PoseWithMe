package com.s23cp5054.posewithme.ui.poses;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.s23cp5054.posewithme.R;
import com.s23cp5054.posewithme.data.repository.FavoritesRepository;
import com.s23cp5054.posewithme.data.repository.PoseRepository;
import com.s23cp5054.posewithme.pose.model.PoseTemplate;
import com.s23cp5054.posewithme.ui.pose.PoseThumbnailView;

import java.util.concurrent.ExecutorService;

/** Bottom sheet with a pose's details, instructions, credit, favourite toggle and actions. */
public final class PoseDetailSheet {

    public interface Listener {
        void onTryPose(PoseTemplate pose);

        /** Favourite toggled or pose deleted; the caller may want to reload its list. */
        void onPosesChanged();
    }

    public static void show(Activity activity, PoseTemplate pose, ExecutorService ioExecutor,
                            Listener listener) {
        Handler main = new Handler(Looper.getMainLooper());
        FavoritesRepository favorites = new FavoritesRepository(activity);
        BottomSheetDialog sheet = new BottomSheetDialog(activity);
        View content = LayoutInflater.from(activity).inflate(R.layout.sheet_pose_detail, null);

        ((PoseThumbnailView) content.findViewById(R.id.detailThumbnail)).setPeople(pose.people);
        ((TextView) content.findViewById(R.id.detailName)).setText(pose.name);
        ((TextView) content.findViewById(R.id.detailMeta)).setText(
                PoseLabels.longMeta(activity, pose));
        TextView description = content.findViewById(R.id.detailDescription);
        description.setText(pose.description);
        description.setVisibility(pose.description.isEmpty() ? View.GONE : View.VISIBLE);

        StringBuilder steps = new StringBuilder();
        for (String step : pose.instructions) {
            if (steps.length() > 0) {
                steps.append('\n');
            }
            steps.append("•  ").append(step);
        }
        TextView instructions = content.findViewById(R.id.detailInstructions);
        instructions.setText(steps);
        instructions.setVisibility(steps.length() == 0 ? View.GONE : View.VISIBLE);

        TextView attribution = content.findViewById(R.id.detailAttribution);
        attribution.setText(attributionFor(activity, pose));
        attribution.setMovementMethod(LinkMovementMethod.getInstance());

        MaterialButton favoriteButton = content.findViewById(R.id.favoriteButton);
        boolean[] isFavorite = {false};
        ioExecutor.execute(() -> {
            boolean fav = favorites.isFavorite(pose.id);
            main.post(() -> {
                isFavorite[0] = fav;
                renderFavorite(activity, favoriteButton, fav);
            });
        });
        favoriteButton.setOnClickListener(v -> {
            isFavorite[0] = !isFavorite[0];
            boolean fav = isFavorite[0];
            renderFavorite(activity, favoriteButton, fav);
            ioExecutor.execute(() -> {
                favorites.setFavorite(pose.id, fav);
                main.post(listener::onPosesChanged);
            });
        });

        View deleteButton = content.findViewById(R.id.deletePoseButton);
        deleteButton.setVisibility("user".equals(pose.source.type) ? View.VISIBLE : View.GONE);
        deleteButton.setOnClickListener(v -> new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.pose_delete_title, pose.name))
                .setMessage(R.string.pose_delete_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.photo_delete, (d, w) -> {
                    sheet.dismiss();
                    ioExecutor.execute(() -> {
                        PoseRepository.get(activity).deleteUserPose(pose.id);
                        favorites.setFavorite(pose.id, false);
                        main.post(listener::onPosesChanged);
                    });
                })
                .show());

        content.findViewById(R.id.tryPoseButton).setOnClickListener(v -> {
            sheet.dismiss();
            listener.onTryPose(pose);
        });
        sheet.setContentView(content);
        sheet.show();
    }

    private static void renderFavorite(Activity activity, MaterialButton button, boolean fav) {
        button.setIconResource(fav ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        button.setContentDescription(activity.getString(
                fav ? R.string.favorite_remove : R.string.favorite_add));
    }

    private static CharSequence attributionFor(Activity activity, PoseTemplate pose) {
        PoseTemplate.Source s = pose.source;
        if ("designed".equals(s.type)) {
            return activity.getString(R.string.attribution_designed);
        }
        if ("web".equals(s.type)) {
            String titleText = Html.escapeHtml(s.title != null ? s.title : "photo");
            String title = s.url != null
                    ? "<a href=\"" + Html.escapeHtml(s.url) + "\">" + titleText + "</a>"
                    : titleText;
            String licenseText = Html.escapeHtml(s.license != null ? s.license : "");
            String license = s.licenseUrl != null
                    ? "<a href=\"" + Html.escapeHtml(s.licenseUrl) + "\">" + licenseText + "</a>"
                    : licenseText;
            String html = activity.getString(R.string.attribution_web, title,
                    Html.escapeHtml(s.author != null ? s.author : "unknown"), license);
            return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT);
        }
        return activity.getString("user".equals(s.type)
                ? R.string.attribution_user : R.string.attribution_recorded);
    }

    private PoseDetailSheet() {
    }
}
