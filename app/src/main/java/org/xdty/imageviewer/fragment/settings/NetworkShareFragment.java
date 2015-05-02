package org.xdty.imageviewer.fragment.settings;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.PreferenceFragment;

import org.xdty.imageviewer.R;
import org.xdty.imageviewer.model.Config;
import org.xdty.imageviewer.utils.Utils;

/**
 * Created by ty on 15-5-2.
 */
public class NetworkShareFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener {



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings_network_fragment);
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        EditTextPreference server = (EditTextPreference) findPreference(Config.SAMBA_SERVER);
        EditTextPreference folder = (EditTextPreference) findPreference(Config.SAMBA_FOLDER);
        EditTextPreference username = (EditTextPreference) findPreference(Config.SAMBA_USERNAME);
        EditTextPreference password = (EditTextPreference) findPreference(Config.SAMBA_PASSWORD);
        server.setSummary(sharedPreferences.getString(Config.SAMBA_SERVER, ""));
        username.setSummary(sharedPreferences.getString(Config.SAMBA_USERNAME, ""));
        folder.setSummary(sharedPreferences.getString(Config.SAMBA_FOLDER, ""));
        String s = sharedPreferences.getString(Config.SAMBA_PASSWORD, "");
        password.setSummary(s == null || s.isEmpty() ? "" : Utils.fillString(s.length(), '*'));
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        EditTextPreference editTextPreference = (EditTextPreference) findPreference(key);
        String s = sharedPreferences.getString(key, "");
        if (key.equals(Config.SAMBA_PASSWORD)) {
            editTextPreference.setSummary(s == null || s.isEmpty() ? "" : Utils.fillString(s.length(), '*'));
        } else {
            editTextPreference.setSummary(s);
        }

    }
}
