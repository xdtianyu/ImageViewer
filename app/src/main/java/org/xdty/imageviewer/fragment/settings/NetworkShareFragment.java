package org.xdty.imageviewer.fragment.settings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xdty.imageviewer.R;
import org.xdty.imageviewer.model.Config;

/**
 * Created by ty on 15-5-2.
 */
public class NetworkShareFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }


    @Override
    public void onResume() {
        super.onResume();
        PreferenceScreen screen = getPreferenceScreen();
        if (screen!=null) {
            screen.removeAll();
        }

        addPreferencesFromResource(R.xml.settings_network_fragment);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String servers = sharedPreferences.getString(Config.SERVERS, "");
        if (servers != null && !servers.isEmpty()) {

            try {
                PreferenceScreen preferenceScreen = getPreferenceScreen();
                PreferenceCategory preferenceCategory = new PreferenceCategory(getActivity());
                preferenceCategory.setTitle(R.string.added_shared_folder);
                preferenceScreen.addPreference(preferenceCategory);
                JSONObject jsonServers = new JSONObject(servers);
                JSONArray jsonArray = jsonServers.getJSONArray("server");
                for (int i = 0; i < jsonArray.length(); i++) {
                    Preference preference = new Preference(getActivity());
                    preference.setSummary(((JSONObject) jsonArray.get(i)).getString("address"));
                    preference.setTitle(((JSONObject) jsonArray.get(i)).getString("name"));
                    Intent intent = new Intent();
                    intent.setAction("org.xdty.imageviewer.action.ACTION_ADD_SHARED_FOLDER");
                    intent.putExtra("id", ((JSONObject) jsonArray.get(i)).getInt("id"));
                    preference.setIntent(intent);
                    preferenceScreen.addPreference(preference);
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

}
