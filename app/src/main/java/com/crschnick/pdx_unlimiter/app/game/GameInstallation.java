package com.crschnick.pdx_unlimiter.app.game;

import com.crschnick.pdx_unlimiter.app.installation.ErrorHandler;
import com.crschnick.pdx_unlimiter.app.savegame.FileImportTarget;
import com.crschnick.pdx_unlimiter.app.savegame.SavegameCache;
import com.crschnick.pdx_unlimiter.app.util.JsonHelper;
import com.crschnick.pdx_unlimiter.app.util.WatcherHelper;
import com.crschnick.pdx_unlimiter.core.data.GameVersion;
import com.crschnick.pdx_unlimiter.core.savegame.SavegameInfo;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class GameInstallation {

    public static Eu4Installation EU4 = null;
    public static Hoi4Installation HOI4 = null;
    public static Ck3Installation CK3 = null;
    public static StellarisInstallation STELLARIS = null;

    protected ObjectProperty<List<FileImportTarget>> savegames = new SimpleObjectProperty<>();
    protected DistributionType distType;
    protected Path userDir;
    protected List<GameMod> mods = new ArrayList<>();
    protected GameVersion version;
    private Path path;
    private Path executable;
    private List<GameDlc> dlcs = new ArrayList<>();

    public GameInstallation(Path path, Path executable) {
        this.path = path;
        if (SystemUtils.IS_OS_WINDOWS) {
            this.executable = getPath().resolve(executable).resolveSibling(executable.getFileName().toString() + ".exe");
        } else if (SystemUtils.IS_OS_LINUX) {
            this.executable = getPath().resolve(executable);
        }
    }

    public static void initInstallations() throws Exception {
        if (EU4 != null) {
            EU4.loadData();
        }

        if (HOI4 != null) {
            HOI4.loadData();
        }

        if (STELLARIS != null) {
            STELLARIS.loadData();
        }

        if (CK3 != null) {
            CK3.loadData();
        }

        initInstallationsOptional();
    }

    public static void initInstallationsOptional() {
        if (EU4 != null) {
            try {
                EU4.initOptional();
            } catch (Exception e) {
                ErrorHandler.handleException(e);
            }
        }

        if (HOI4 != null) {
            try {
                HOI4.initOptional();
            } catch (Exception e) {
                ErrorHandler.handleException(e);
            }
        }

        if (STELLARIS != null) {
            try {
                STELLARIS.initOptional();
            } catch (Exception e) {
                ErrorHandler.handleException(e);
            }
        }

        if (CK3 != null) {
            try {
                CK3.initOptional();
            } catch (Exception e) {
                ErrorHandler.handleException(e);
            }
        }
    }

    private List<Path> getAllSavegameDirectories() {
        List<Path> savegameDirs = new ArrayList<>();
        savegameDirs.add(getSavegamesPath());
        savegameDirs.addAll(getDistType().getSavegamePaths());
        return savegameDirs;
    }

    public void initOptional() throws Exception {
        LoggerFactory.getLogger(getClass()).debug("Initializing optional data");
        loadDlcs();
        loadMods();
        savegames.set(getLatestSavegames());

        List<Path> savegameDirs = getAllSavegameDirectories();

        WatcherHelper.startWatchersInDirectories(this.getClass().getName(), savegameDirs, (p) -> {
            savegames.set(getLatestSavegames());
        });
        LoggerFactory.getLogger(getClass()).debug("Finished initializing optional data\n");
    }

    public void startLauncher() {
        getDistType().launch();
    }

    public void startDirect() {
        getDistType().launch();
    }

    public <T, I extends SavegameInfo<T>> Path getExportTarget(
            SavegameCache<?, ?, T, I> cache, GameCampaignEntry<T, I> e) {
        Path file = getSavegamesPath().resolve(cache.getFileName(e));
        return file;
    }

    public void writeDlcLoadFile(List<GameMod> mods, List<GameDlc> dlcs) throws IOException {
        var out = Files.newOutputStream(getUserPath().resolve("dlc_load.json"));
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        n.putArray("enabled_mods").addAll(mods.stream()
                .map(d -> FilenameUtils.separatorsToUnix(getUserPath().relativize(d.getModFile()).toString()))
                .map(JsonNodeFactory.instance::textNode)
                .collect(Collectors.toList()));
        n.putArray("disabled_dlcs").addAll(this.dlcs.stream()
                .filter(d -> d.isExpansion() && !dlcs.contains(d))
                .map(d -> FilenameUtils.separatorsToUnix(getPath().relativize(d.getInfoFilePath()).toString()))
                .map(JsonNodeFactory.instance::textNode)
                .collect(Collectors.toList()));
        JsonHelper.write(n, out);
    }

    private void loadDlcs() throws IOException {
        Files.list(getDlcPath()).forEach(f -> {
            try {
                GameDlc.fromDirectory(f).ifPresent(d -> dlcs.add(d));
            } catch (Exception e) {
                ErrorHandler.handleException(e);
            }
        });
    }

    private void loadMods() throws IOException {
        if (!Files.isDirectory(getUserPath().resolve("mod"))) {
            return;
        }

        Files.list(getUserPath().resolve("mod")).forEach(f -> {
            try {
                GameMod.fromFile(f).ifPresent(m -> {
                    if (Files.exists(m.getPath())) mods.add(m);
                    LoggerFactory.getLogger(getClass()).debug("Found mod " + m.getName() +
                            " at " + m.getModFile().toString() + ". Content exists: " + Files.exists(m.getPath()));
                });
            } catch (Exception e) {
                ErrorHandler.handleException(e);
            }
        });
    }

    private List<FileImportTarget> getLatestSavegames() {
        return getAllSavegameDirectories().stream()
                .map(FileImportTarget::createTargets)
                .map(List::stream)
                .flatMap(Stream::distinct)
                .sorted(Comparator.comparingLong(t -> {
                    try {
                        return t.getLastModified().toEpochMilli();
                    } catch (IOException e) {
                        ErrorHandler.handleException(e);
                        return 0;
                    }
                }))
                .collect(Collectors.toList());
    }

    protected Path replaceVariablesInPath(String value) {
        if (SystemUtils.IS_OS_WINDOWS) {
            value = value.replace("%USER_DOCUMENTS%",
                    Paths.get(System.getProperty("user.home"), "Documents").toString());
        } else if (SystemUtils.IS_OS_LINUX) {
            value = value.replace("$LINUX_DATA_HOME",
                    Paths.get(System.getProperty("user.home"), ".local", "share").toString());
        }
        return Path.of(value);
    }

    public Optional<GameDlc> getDlcForName(String name) {
        return dlcs.stream().filter(d -> d.getName().equals(name)).findAny();
    }

    public DistributionType getDistType() {
        return distType;
    }

    public Path getDlcPath() {
        return getPath().resolve("dlc");
    }

    public abstract void writeLaunchConfig(String name, Instant lastPlayed, Path path) throws IOException;

    public abstract Optional<GameMod> getModForName(String name);

    public abstract void startDirectly() throws IOException;

    public abstract void loadData() throws Exception;

    protected Path getLauncherDataPath() {
        return getPath();
    }

    public Path getPath() {
        return path;
    }

    public Path getModBasePath() {
        return path;
    }

    public Path getExecutable() {
        return executable;
    }

    public Path getUserPath() {
        return userDir;
    }

    public Path getSavegamesPath() {
        return getUserPath().resolve("save games");
    }

    public List<FileImportTarget> getSavegames() {
        return savegames.get();
    }

    public ObjectProperty<List<FileImportTarget>> savegamesProperty() {
        return savegames;
    }

    public GameVersion getVersion() {
        return version;
    }
}
