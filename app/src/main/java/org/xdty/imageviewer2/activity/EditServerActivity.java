package org.xdty.imageviewer2.activity;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xdty.imageviewer2.R;
import org.xdty.imageviewer2.model.Config;

/**
 * Created by ty on 15-5-7.
 */
public class EditServerActivity extends Activity {

    public final static String TAG = "EditServerActivity";
    private EditText name;
    private EditText address;
    private EditText username;
    private EditText password;
    private EditText folder;

    private int id = -1;

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.mean_add_share, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case android.R.id.home:
                this.finish();
                return true;
            case R.id.action_save:
                addNewServer();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void addNewServer() {
        String nameText = name.getText().toString();
        String addressText = address.getText().toString();
        String usernameText = username.getText().toString();
        String passwordText = password.getText().toString();
        String folderText = folder.getText().toString();

        if (addressText.isEmpty()) {
            Toast.makeText(this, R.string.server_address_cannot_be_empty, Toast.LENGTH_SHORT).show();
        } else {
            try {
                JSONObject jsonServer = new JSONObject();
                jsonServer.put("name", nameText);
                jsonServer.put("address", addressText);
                jsonServer.put("username", usernameText);
                jsonServer.put("password", passwordText);
                jsonServer.put("folder", folderText);

                Log.d(TAG, jsonServer.toString());

                JSONObject jsonServers;
                JSONArray jsonArray;

                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                String services = sharedPreferences.getString(Config.SERVERS, "");

                if (services == null || services.isEmpty()) {
                    jsonServers = new JSONObject();
                    jsonArray = new JSONArray();

                    jsonServer.put("id", 0);
                    jsonArray.put(jsonServer);
                    jsonServers.put("server", jsonArray);
                } else {
                    int maxID = 0;
                    jsonServers = new JSONObject(services);
                    jsonArray = jsonServers.getJSONArray("server");

                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject jsonObject = (JSONObject) jsonArray.get(i);
                        int currentID = jsonObject.getInt("id");

                        if (id != -1 && id == currentID) {
                            jsonObject.put("name", nameText);
                            jsonObject.put("address", addressText);
                            jsonObject.put("username", usernameText);
                            jsonObject.put("password", passwordText);
                            jsonObject.put("folder", folderText);
                            break;
                        } else {
                            maxID = maxID > currentID ? maxID : currentID;
                        }
                    }

                    if (id == -1) {
                        jsonServer.put("id", maxID + 1);
                        jsonArray.put(jsonArray.length(), jsonServer);
                    }
                }
                Log.d(TAG, jsonServers.toString());
                Editor editor = sharedPreferences.edit();
                editor.putString(Config.SERVERS, jsonServers.toString());
                editor.apply();
                finish();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        setContentView(R.layout.activity_add_shared);

        name = (EditText) findViewById(R.id.windows_share_name);
        address = (EditText) findViewById(R.id.windows_share_server);
        username = (EditText) findViewById(R.id.windows_share_username);
        password = (EditText) findViewById(R.id.windows_share_password);
        folder = (EditText) findViewById(R.id.windows_share_folder);

        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            id = (int) bundle.get("id");
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            String services = sharedPreferences.getString(Config.SERVERS, "");

            if (services != null && !services.isEmpty()) {
                try {
                    JSONObject jsonServers = new JSONObject(services);
                    JSONArray jsonArray = jsonServers.getJSONArray("server");
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject jsonServer = (JSONObject) jsonArray.get(i);
                        if (jsonServer.getInt("id") == id) {
                            name.setText(jsonServer.getString("name"));
                            address.setText(jsonServer.getString("address"));
                            username.setText(jsonServer.getString("username"));
                            password.setText(jsonServer.getString("password"));
                            folder.setText(jsonServer.getString("folder"));
                            break;
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
        }

    }

}
