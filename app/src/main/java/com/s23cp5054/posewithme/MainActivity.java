package com.s23cp5054.posewithme;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.snackbar.Snackbar;
import com.s23cp5054.posewithme.ui.camera.CameraActivity;

import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.main);
        int basePaddingTop = root.getPaddingTop();
        int basePaddingBottom = root.getPaddingBottom();
        int basePaddingLeft = root.getPaddingLeft();
        int basePaddingRight = root.getPaddingRight();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(basePaddingLeft + bars.left, basePaddingTop + bars.top,
                    basePaddingRight + bars.right, basePaddingBottom + bars.bottom);
            return insets;
        });

        ((TextView) findViewById(R.id.greetingText)).setText(greetingForNow());

        findViewById(R.id.heroCard).setOnClickListener(
                v -> startActivity(new Intent(this, CameraActivity.class)));
        findViewById(R.id.recommendedCard).setOnClickListener(
                v -> showComingSoon(R.string.home_recommended_title));
        findViewById(R.id.settingsButton).setOnClickListener(
                v -> showComingSoon(R.string.home_settings));

        setUpTile(R.id.tileBrowse, R.drawable.ic_poses, R.color.brand_primary,
                R.string.home_browse_title, R.string.home_browse_subtitle);
        setUpTile(R.id.tileNearby, R.drawable.ic_explore, R.color.brand_teal,
                R.string.home_nearby_title, R.string.home_nearby_subtitle);
        setUpTile(R.id.tilePhotos, R.drawable.ic_gallery, R.color.brand_sky,
                R.string.home_photos_title, R.string.home_photos_subtitle);
        setUpTile(R.id.tileFavorites, R.drawable.ic_favorite, R.color.brand_pink,
                R.string.home_favorites_title, R.string.home_favorites_subtitle);
    }

    private void setUpTile(int tileId, @DrawableRes int icon, @ColorRes int tint,
                           @StringRes int title, @StringRes int subtitle) {
        View tile = findViewById(tileId);
        ((ImageView) tile.findViewById(R.id.tileIcon)).setImageResource(icon);
        tile.findViewById(R.id.tileIconBg).setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(this, tint)));
        ((TextView) tile.findViewById(R.id.tileTitle)).setText(title);
        ((TextView) tile.findViewById(R.id.tileSubtitle)).setText(subtitle);
        tile.setOnClickListener(v -> showComingSoon(title));
    }

    private void showComingSoon(@StringRes int feature) {
        Snackbar.make(findViewById(R.id.main),
                getString(R.string.coming_soon, getString(feature)), Snackbar.LENGTH_SHORT).show();
    }

    private String greetingForNow() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) {
            return getString(R.string.greeting_morning);
        } else if (hour < 17) {
            return getString(R.string.greeting_afternoon);
        }
        return getString(R.string.greeting_evening);
    }
}
