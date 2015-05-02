package org.xdty.imageviewer.fragment;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import org.xdty.imageviewer.R;

/**
 * Created by ty on 15-5-2.
 */
public class SettingsAboutFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings_about_fragment);
    }
}
