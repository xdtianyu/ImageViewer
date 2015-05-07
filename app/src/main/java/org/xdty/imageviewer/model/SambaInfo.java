package org.xdty.imageviewer.model;

import org.json.JSONException;
import org.json.JSONObject;

import jcifs.smb.NtlmPasswordAuthentication;

/**
 * Created by ty on 15-5-2.
 */
public class SambaInfo {

    public String server = "";
    public String folder = "";
    public String username = "";
    public String password = "";
    public int id = -1;

    private NtlmPasswordAuthentication smbAuth;

    public SambaInfo() {

    }

    public SambaInfo(JSONObject jsonObject) throws JSONException {
        server = jsonObject.getString("address");
        username = jsonObject.getString("username");
        password = jsonObject.getString("password");
        folder = jsonObject.getString("folder");
        id = jsonObject.getInt("id");
    }

    public String build() {
        String s = "";
        if (server != null && !server.isEmpty() && folder != null && !folder.isEmpty()) {
            s = "smb://" + server + "/" + folder + "/";
        }

        return s;
    }

    public NtlmPasswordAuthentication auth() {

        if (smbAuth==null) {
            smbAuth = new NtlmPasswordAuthentication("", username, password);
        }
        return smbAuth;
    }

}
