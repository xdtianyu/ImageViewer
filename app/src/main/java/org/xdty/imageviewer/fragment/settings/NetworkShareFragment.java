package org.xdty.imageviewer.fragment.settings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xdty.imageviewer.R;
import org.xdty.imageviewer.model.Config;

/**
 * Created by ty on 15-5-2.
 */
public class NetworkShareFragment extends PreferenceFragment {

    public final static String TAG = "NetworkShareFragment";

    private float touchX;
    private float touchY;

    private int popUpWidth = 0;
    private int popUpHeight = 0;

    private PopupWindow popupWindow;

    @Override
    public void onStart() {
        super.onStart();
        // setup delete button
        final View parentView = getView();
        final ListView listView;
        if (parentView != null) {
            listView = (ListView) parentView.findViewById(android.R.id.list);

            LinearLayout layout = (LinearLayout) getActivity().
                    getLayoutInflater().inflate(R.layout.setting_popup, (ViewGroup) parentView, false);
            layout.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);

            popUpWidth = layout.getMeasuredWidth();
            popUpHeight = layout.getMeasuredHeight();

            popupWindow = new PopupWindow(getActivity());
            popupWindow.setContentView(layout);
            popupWindow.setBackgroundDrawable(
                    new ColorDrawable(getActivity().getResources().getColor(android.R.color.transparent)));
            popupWindow.setOutsideTouchable(true);

            final Button removeButton = (Button) layout.findViewById(R.id.remove);

            removeButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    popupWindow.dismiss();
                    removeServer((int) removeButton.getTag());
                    PreferenceScreen screen = getPreferenceScreen();
                    if (screen != null) {
                        screen.removeAll();
                    }
                    addPreferencesFromResource(R.xml.settings_network_fragment);
                    generateServerList();
                }
            });


            listView.setOnItemLongClickListener(new OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                    Object object = listView.getAdapter().getItem(position);
                    if (object instanceof Preference) {
                        String title = ((Preference) object).getTitle().toString();

                        if (popupWindow.isShowing()) {
                            popupWindow.dismiss();
                        }

                        int serverID = ((Preference) object).getIntent().getExtras().getInt("id");
                        removeButton.setTag(serverID);

                        popupWindow.showAtLocation(parentView, Gravity.TOP | Gravity.LEFT, (int) touchX, (int) touchY);
                        //popupWindow.update((int) touchX, (int) touchY, popUpWidth, popUpHeight);
                        popupWindow.update((int) view.getX() + view.getWidth(),
                                (int) view.getY() + view.getHeight() + 10, popUpWidth, popUpHeight);
                    }
                    return true;
                }
            });
            listView.setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    touchX = event.getX();
                    touchY = event.getY();
                    return false;
                }
            });
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }


    @Override
    public void onResume() {
        super.onResume();
        PreferenceScreen screen = getPreferenceScreen();
        if (screen != null) {
            screen.removeAll();
        }
        addPreferencesFromResource(R.xml.settings_network_fragment);
        generateServerList();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    private void removeServer(int id) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String servers = sharedPreferences.getString(Config.SERVERS, "");
        if (servers != null && !servers.isEmpty()) {
            try {
                JSONObject jsonServers = new JSONObject(servers);
                JSONArray jsonArray = jsonServers.getJSONArray("server");
                for (int i = 0; i < jsonArray.length(); i++) {
                    if (((JSONObject) (jsonArray.get(i))).getInt("id") == id) {
                        jsonArray.remove(i);
                        Editor editor = sharedPreferences.edit();
                        editor.putString(Config.SERVERS, jsonServers.toString());
                        editor.apply();
                        break;
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private void generateServerList() {
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

}
