package org.xdty.imageviewer2.fragment.settings;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import org.xdty.imageviewer2.R;

/**
 * Created by ty on 15-5-2.
 */
public class GeneralFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings_general_fragment);
    }
}
