package com.crschnick.pdx_unlimiter.app.util;

import com.crschnick.pdx_unlimiter.app.game.GameCampaignEntry;
import com.crschnick.pdx_unlimiter.app.gui.DialogHelper;
import com.crschnick.pdx_unlimiter.app.gui.GuiErrorReporter;
import com.crschnick.pdx_unlimiter.app.installation.Settings;
import com.crschnick.pdx_unlimiter.app.installation.TaskExecutor;
import com.crschnick.pdx_unlimiter.app.savegame.SavegameCache;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.concurrent.Task;

import javax.net.ssl.HttpsURLConnection;
import java.awt.*;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;

public class RakalyHelper {

    public static void uploadSavegame(SavegameCache cache, GameCampaignEntry<?, ?> entry) {
        if (Settings.getInstance().getRakalyApiKey().isEmpty() || Settings.getInstance().getRakalyUserId().isEmpty()) {
            GuiErrorReporter.showErrorMessage("Missing rakaly.com User ID or API key. " +
                    "To use this functionality, set both in the settings menu.", null, false);
            return;
        }

        TaskExecutor.getInstance().submitTask(() -> {
            try {
                byte[] body = Files.readAllBytes(cache.getPath(entry).resolve("savegame.eu4"));
                String saveId = executePost(cache.getFileName(entry), new URL("https://rakaly.com/api/saves"), body);
                Desktop.getDesktop().browse(new URL("https://rakaly.com/eu4/saves/" + saveId).toURI());
            } catch (Exception e) {
                GuiErrorReporter.showErrorMessage(e.getMessage(), null, false);
            }
        });
    }

    private static String executePost(String sgName, URL targetURL, byte[] data) throws IOException {
        HttpsURLConnection connection = null;
        try {
            connection = (HttpsURLConnection) targetURL.openConnection();
            connection.setRequestMethod("POST");
            connection.addRequestProperty("User-Agent", "https://github.com/crschnick/pdx_unlimiter");
            connection.addRequestProperty("Accept", "application/json");
            connection.addRequestProperty("Content-Length", String.valueOf(data.length));
            connection.addRequestProperty("rakaly-filename", sgName);

            String encoding = Base64.getEncoder().encodeToString(
                    (Settings.getInstance().getRakalyUserId().get() + ":" + Settings.getInstance().getRakalyApiKey().get())
                            .getBytes(StandardCharsets.UTF_8));
            connection.setRequestProperty("Authorization", "Basic " + encoding);
            connection.setRequestProperty("Content-Type", "application/zip");

            connection.setDoOutput(true);
            DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
            outputStream.write(data);

            int responseCode = connection.getResponseCode();
            InputStream is;
            if (responseCode != 200) {
                is = connection.getErrorStream();
            } else {
                is = connection.getInputStream();
            }

            ObjectMapper o = new ObjectMapper();
            JsonNode node = o.readTree(is.readAllBytes());
            String msg = node.get("msg").textValue();

            if (responseCode != 200) {
                throw new IOException(msg);
            }

            String saveId = node.get("save_id").textValue();
            return saveId;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

    }
}
