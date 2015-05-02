package org.xdty.imageviewer.model;

/**
 * Created by ty on 15-5-2.
 */
public class SambaInfo {

    public String server = "";
    public String folder = "";
    public String username = "";
    public String password = "";

    public String build() {
        String s = "";
        if (server != null && !server.isEmpty() && folder != null && !folder.isEmpty()) {
            s = "smb://" + server + "/" + folder + "/";
        }
        return s;
    }

}
