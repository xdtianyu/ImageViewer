package org.xdty.imageviewer.activity;

import android.os.Bundle;
import android.preference.PreferenceActivity;

import org.xdty.imageviewer.R;
import org.xdty.imageviewer.fragment.SettingsAboutFragment;
import org.xdty.imageviewer.fragment.SettingsGeneralFragment;

import java.util.List;

/**
 * Created by ty on 15-5-2.
 */
public class SettingsActivity extends PreferenceActivity {
    @Override
    protected void onStart() {
        super.onStart();
        overridePendingTransition(0, 0);
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        return SettingsGeneralFragment.class.getName().equals(fragmentName)||
                SettingsAboutFragment.class.getName().equals(fragmentName);
    }

    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.setting_headers, target);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
}