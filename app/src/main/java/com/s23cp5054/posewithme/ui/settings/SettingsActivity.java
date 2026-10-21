package com.s23cp5054.posewithme.ui.settings;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.s23cp5054.posewithme.R;
import com.s23cp5054.posewithme.data.settings.AppSettings;
import com.s23cp5054.posewithme.ui.poses.PoseLabels;

import java.util.HashSet;
import java.util.Set;

/** Preferences screen; every change is written to SharedPreferences immediately. */
public class SettingsActivity extends AppCompatActivity {

    private static final String[] CATEGORY_KEYS = {
            "casual_standing", "crossed_arms", "walking", "sitting", "portrait", "beach",
            "monument", "scenic", "cafe", "couple", "friends", "group",
    };

    private AppSettings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);
        View header = findViewById(R.id.settingsHeader);
        View scroll = findViewById(R.id.settingsScroll);
        int headerTop = header.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settingsRoot), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            header.setPadding(header.getPaddingLeft(), headerTop + bars.top,
                    header.getPaddingRight(), header.getPaddingBottom());
            scroll.setPadding(0, 0, 0, bars.bottom);
            return insets;
        });
        findViewById(R.id.settingsBack).setOnClickListener(v -> finish());
        settings = new AppSettings(this);

        bindSwitch(R.id.switchAutoCapture, settings.isAutoCapture(), settings::setAutoCapture);
        bindSwitch(R.id.switchMirror, settings.isMirrorSelfies(), settings::setMirrorSelfies);
        bindSwitch(R.id.switchHints, settings.isShowHints(), settings::setShowHints);
        bindSwitch(R.id.switchGhost, settings.isShowGhost(), settings::setShowGhost);
        bindSwitch(R.id.switchSkeleton, settings.isShowSkeleton(), settings::setShowSkeleton);

        TextView thresholdLabel = findViewById(R.id.thresholdLabel);
        Slider slider = findViewById(R.id.thresholdSlider);
        float percent = Math.round(settings.getAutoCaptureThreshold() * 20f) * 5f;
        slider.setValue(Math.max(slider.getValueFrom(), Math.min(slider.getValueTo(), percent)));
        slider.setLabelFormatter(value -> Math.round(value) + "%");
        showThreshold(thresholdLabel, slider.getValue());
        slider.addOnChangeListener((s, value, fromUser) -> {
            showThreshold(thresholdLabel, value);
            if (fromUser) {
                settings.setAutoCaptureThreshold(value / 100f);
            }
        });

        ChipGroup countdown = findViewById(R.id.countdownChips);
        int currentSeconds = settings.getCountdownSeconds();
        for (int seconds : AppSettings.COUNTDOWN_CHOICES) {
            Chip chip = new Chip(this);
            chip.setCheckable(true);
            chip.setText(seconds == 0 ? getString(R.string.settings_countdown_off)
                    : getString(R.string.settings_countdown_seconds, seconds));
            chip.setChecked(seconds == currentSeconds);
            chip.setOnCheckedChangeListener((button, checked) -> {
                if (checked) {
                    settings.setCountdownSeconds(seconds);
                }
            });
            countdown.addView(chip);
        }

        ChipGroup categories = findViewById(R.id.categoryChips);
        Set<String> preferred = new HashSet<>(settings.getPreferredCategories());
        for (String key : CATEGORY_KEYS) {
            Chip chip = new Chip(this);
            chip.setCheckable(true);
            chip.setText(PoseLabels.category(this, key));
            chip.setChecked(preferred.contains(key));
            chip.setOnCheckedChangeListener((button, checked) -> {
                if (checked) {
                    preferred.add(key);
                } else {
                    preferred.remove(key);
                }
                settings.setPreferredCategories(preferred);
            });
            categories.addView(chip);
        }

        ((TextView) findViewById(R.id.aboutText)).setText(
                getString(R.string.settings_about_body, versionName()));
    }

    private interface BoolSetter {
        void set(boolean value);
    }

    private void bindSwitch(int id, boolean value, BoolSetter setter) {
        MaterialSwitch view = findViewById(id);
        view.setChecked(value);
        view.setOnCheckedChangeListener((button, checked) -> setter.set(checked));
    }

    private void showThreshold(TextView label, float value) {
        label.setText(getString(R.string.settings_threshold, Math.round(value)));
    }

    private String versionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }
}
