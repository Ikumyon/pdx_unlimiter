package com.crschnick.pdx_unlimiter.app.util.integration;

import com.crschnick.pdx_unlimiter.app.core.ErrorHandler;
import com.crschnick.pdx_unlimiter.app.core.TaskExecutor;
import com.crschnick.pdx_unlimiter.app.core.settings.Settings;
import com.crschnick.pdx_unlimiter.app.gui.dialog.GuiConverterConfig;
import com.crschnick.pdx_unlimiter.app.installation.Game;
import com.crschnick.pdx_unlimiter.app.installation.GameInstallation;
import com.crschnick.pdx_unlimiter.app.savegame.SavegameEntry;
import com.crschnick.pdx_unlimiter.app.savegame.SavegameStorage;
import com.crschnick.pdx_unlimiter.core.info.ck3.Ck3SavegameInfo;
import com.crschnick.pdx_unlimiter.core.info.ck3.Ck3Tag;
import javafx.application.Platform;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.SystemUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ConverterHelper {

    private static void writeLine(BufferedWriter w, String key, Object value) throws IOException {
        w.write(key + " = \"" + value.toString() + "\"\n");
    }

    public static Map<String, String> loadConfig() {
        Map<String, String> map = new HashMap<>();
        var config = Settings.getInstance().ck3toeu4Dir.getValue()
                .resolve("CK3toEU4").resolve("configuration.txt");
        if (!Files.exists(config)) {
            return map;
        }

        try {
            var reader = Files.newBufferedReader(config);
            String line;
            while ((line = reader.readLine()) != null) {
                var split = line.split(" = ");
                map.put(split[0], split[1].replace("\"", ""));
            }
        } catch (IOException e) {
            ErrorHandler.handleException(e);
        }
        map.remove("CK3DocDirectory");
        map.remove("CK3directory");
        map.remove("EU4directory");
        map.remove("targetGameModPath");
        map.remove("SaveGame");
        map.remove("output_name");
        return map;
    }

    public static String getOutputName(SavegameEntry<Ck3Tag, Ck3SavegameInfo> entry) {
        var s = SavegameStorage.<Ck3Tag,Ck3SavegameInfo>get(Game.CK3).getFileSystemCompatibleName(entry);
        s = FilenameUtils.getBaseName(s);
        s = s.replace(" ", "_");
        return s;
    }

    public static String getEu4ModDir() {
        return GameInstallation.ALL.get(Game.EU4).getUserPath().resolve("mod").toString();
    }

    public static String getModOutputPath(SavegameEntry<Ck3Tag, Ck3SavegameInfo> entry) {
        return Settings.getInstance().ck3toeu4Dir.getValue()
                .resolve("CK3toEU4").resolve("output").resolve(getOutputName(entry)).toString();
    }

    public static void writeConfig(SavegameEntry<Ck3Tag, Ck3SavegameInfo> entry, Map<String, String> values) {
        var config = Settings.getInstance().ck3toeu4Dir.getValue()
                .resolve("CK3toEU4").resolve("configuration.txt");
        try {
            var writer = Files.newBufferedWriter(config);
            writeLine(writer, "CK3DocDirectory", GameInstallation.ALL.get(Game.CK3).getUserPath().toString());
            writeLine(writer, "CK3directory", GameInstallation.ALL.get(Game.CK3).getPath().toString());
            writeLine(writer, "EU4directory", GameInstallation.ALL.get(Game.EU4).getPath().toString());
            writeLine(writer, "targetGameModPath", getEu4ModDir());
            writeLine(writer, "SaveGame", SavegameStorage.ALL.get(Game.CK3).getSavegameFile(entry).toString());
            writeLine(writer, "output_name", getOutputName(entry));
            for (var e : values.entrySet()) {
                writeLine(writer, e.getKey(), e.getValue());
            }
            writer.close();
        } catch (IOException e) {
            ErrorHandler.handleException(e);
        }
    }

    public static void convertCk3ToEu4(SavegameEntry<Ck3Tag, Ck3SavegameInfo> entry) {
        if (Settings.getInstance().ck3toeu4Dir.getValue() == null) {
            GuiConverterConfig.showUsageDialog();
            return;
        }

        if (!GuiConverterConfig.showConfirmConversionDialog()) {
            return;
        }

        var values = ConverterHelper.loadConfig();
        if (!GuiConverterConfig.showConfig(values)) {
            return;
        }
        ConverterHelper.writeConfig(entry, values);

        TaskExecutor.getInstance().submitTask(() -> {
            try {
                var handle = new ProcessBuilder(Settings.getInstance().ck3toeu4Dir.getValue()
                        .resolve("CK3toEU4")
                        .resolve("CK3ToEU4Converter" + (SystemUtils.IS_OS_WINDOWS ? ".exe" : "")).toString())
                        .directory(Settings.getInstance().ck3toeu4Dir.getValue().resolve("CK3toEU4").toFile())
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start();

                try {
                    int returnCode = handle.waitFor();

                    FileUtils.copyDirectory(
                            Path.of(getModOutputPath(entry)).toFile(),
                            Path.of(getEu4ModDir()).resolve(getOutputName(entry)).toFile());
                    FileUtils.copyFile(
                            Path.of(getModOutputPath(entry) + ".mod").toFile(),
                            Path.of(getEu4ModDir()).resolve(getOutputName(entry) + ".mod").toFile());

                    Platform.runLater(() -> {
                        if (returnCode == 0) {
                            GuiConverterConfig.showConversionSuccessDialog();
                        } else {
                            GuiConverterConfig.showConversionErrorDialog();
                        }
                    });
                } catch (Exception e) {
                    ErrorHandler.handleException(e);
                }

            } catch (IOException e) {
                ErrorHandler.handleException(e);
            }
        }, true);
    }
}