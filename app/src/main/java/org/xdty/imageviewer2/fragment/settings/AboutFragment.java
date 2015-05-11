package org.xdty.imageviewer2.fragment.settings;

import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import org.xdty.imageviewer2.R;

/**
 * Created by ty on 15-5-2.
 */
public class AboutFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings_about_fragment);

        try {
            Preference preference = findPreference("version");
            PackageManager packageManager = getActivity().getPackageManager();
            String version = packageManager.getPackageInfo(getActivity().getPackageName(), 0).versionName;
            preference.setSummary(version);
        }
        catch (NameNotFoundException e) {
            e.printStackTrace();
        }
    }
}
