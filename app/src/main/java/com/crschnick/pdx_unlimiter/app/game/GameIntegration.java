package com.crschnick.pdx_unlimiter.app.game;

import com.crschnick.pdx_unlimiter.app.gui.GameGuiFactory;
import com.crschnick.pdx_unlimiter.app.installation.ErrorHandler;
import com.crschnick.pdx_unlimiter.app.installation.Settings;
import com.crschnick.pdx_unlimiter.app.savegame.SavegameCache;
import com.crschnick.pdx_unlimiter.eu4.savegame.SavegameInfo;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class GameIntegration<E extends GameCampaignEntry<? extends SavegameInfo>,C extends GameCampaign<E>> {

    private static SimpleObjectProperty<GameIntegration<? extends GameCampaignEntry<? extends SavegameInfo>,
            ? extends GameCampaign<? extends GameCampaignEntry<? extends SavegameInfo>>>> current = new SimpleObjectProperty<>();


    private static List<GameIntegration<? extends GameCampaignEntry<? extends SavegameInfo>,
                ? extends GameCampaign<? extends GameCampaignEntry<? extends SavegameInfo>>>> ALL;

    private static SimpleObjectProperty<? extends GameCampaign<? extends GameCampaignEntry<? extends SavegameInfo>>> globalSelectedCampaign =
            new SimpleObjectProperty<>();
    private static SimpleObjectProperty<? extends GameCampaignEntry<? extends SavegameInfo>> globalSelectedEntry =
            new SimpleObjectProperty<>();

    public static Eu4Integration EU4;
    public static Hoi4Integration HOI4;

    public static void init() {
        ALL = new ArrayList<>();
        Settings s = Settings.getInstance();
        if (Settings.getInstance().getEu4().isPresent()) {
            EU4 = new Eu4Integration();
            ALL.add(EU4);
            if (s.getActiveGame().equals(s.getEu4())) {
                current.set(EU4);
            }
        }
        if (Settings.getInstance().getHoi4().isPresent()) {
            HOI4 = new Hoi4Integration();
            ALL.add(HOI4);
            if (s.getActiveGame().equals(s.getHoi4())) {
                current.set(HOI4);
            }
        }
    }

    public static List<GameIntegration<?,?>> getAvailable() {
        return (List<GameIntegration<?, ?>>) ALL;
    }

    public static <E extends GameCampaignEntry<? extends SavegameInfo>,
            C extends GameCampaign<E>> ReadOnlyObjectProperty<C> globalSelectedCampaignProperty() {
        return (SimpleObjectProperty<C>) globalSelectedCampaign;
    }

    private static <E extends GameCampaignEntry<? extends SavegameInfo>,
            C extends GameCampaign<E>> SimpleObjectProperty<C> globalSelectedCampaignPropertyInternal() {
        return (SimpleObjectProperty<C>) globalSelectedCampaign;
    }



    public static <E extends GameCampaignEntry<? extends SavegameInfo>, C extends GameCampaign<E>>
    ReadOnlyObjectProperty<E> globalSelectedEntryProperty() {
        return (SimpleObjectProperty<E>) globalSelectedEntry;
    }

    private static <E extends GameCampaignEntry<? extends SavegameInfo>, C extends GameCampaign<E>>
    SimpleObjectProperty<E> globalSelectedEntryPropertyInternal() {
        return (SimpleObjectProperty<E>) globalSelectedEntry;
    }

    public static <E extends GameCampaignEntry<? extends SavegameInfo>,
            C extends GameCampaign<E>> GameIntegration<E,C> current() {
        return (GameIntegration<E, C>) current.get();
    }

    public static SimpleObjectProperty<GameIntegration<? extends GameCampaignEntry<? extends SavegameInfo>,
            ? extends GameCampaign<? extends GameCampaignEntry<? extends SavegameInfo>>>> currentGameProperty() {
        return current;
    }

    public static GameIntegration<?,?> getForInstallation(GameInstallation i) {
        for (var g : ALL) {
            if (g.getInstallation().equals(i)) {
                return g;
            }
        }
        throw new IllegalArgumentException();
    }

    public static GameIntegration<?,?> getForSavegameCache(SavegameCache c) {
        for (var g : ALL) {
            if (g.getSavegameCache().equals(c)) {
                return g;
            }
        }
        throw new IllegalArgumentException();
    }

    protected SimpleObjectProperty<C> selectedCampaign = new SimpleObjectProperty<>();
    protected SimpleObjectProperty<E> selectedEntry = new SimpleObjectProperty<>();

    public abstract String getName();

    public abstract GameInstallation getInstallation();

    public final void launchGame() {
        getInstallation().start(false);
    }

    public final Optional<Path> exportCampaignEntry() {
        try {
            var path = getInstallation().getSavegamesPath().resolve(getSavegameCache().getFileName(selectedEntry.get()));
            getSavegameCache().exportSavegame(selectedEntry.get(),
                    path);
            return Optional.of(path);
        } catch (IOException e) {
            ErrorHandler.handleException(e);
            return Optional.empty();
        }
    }

    public final void launchCampaignEntry() {
        if (selectedEntry.get() == null) {
            return;
        }

        Optional<Path> p = exportCampaignEntry();
        if (p.isPresent()) {
            try {
                writeLaunchConfig(selectedEntry.get(), p.get());
            } catch (IOException ioException) {
                ErrorHandler.handleException(ioException);
                return;
            }
        }
        selectedCampaign.get().lastPlayedProperty().setValue(Instant.now());
        getInstallation().start(true);
    }

    protected abstract void writeLaunchConfig(E entry, Path path) throws IOException;

    public abstract boolean isVersionCompatible(E entry);

    public abstract GameGuiFactory<E,C> getGuiFactory();

    public abstract SavegameCache<? extends SavegameInfo,E,C> getSavegameCache();

    public void openCampaignEntry(E entry) {
        try {
            Desktop.getDesktop().open(getSavegameCache().getPath(entry).toFile());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void selectCampaign(C c) {
        if (this.selectedCampaign.get() == c) {
            return;
        }

        this.selectedEntry.set(null);
        globalSelectedEntryPropertyInternal().set(null);
        this.selectedCampaign.set(c);
        globalSelectedCampaignPropertyInternal().set((GameCampaign<GameCampaignEntry<? extends SavegameInfo>>) c);
        LoggerFactory.getLogger(GameIntegration.class).debug("Selecting campaign " + (c != null ? c.getName() : "null"));
    }

    public void selectEntry(E e) {
        if (this.selectedEntry.get() == e) {
            return;
        }

        if (e != null) {
            this.selectedCampaign.set(getSavegameCache().getCampaign(e));
            globalSelectedCampaignPropertyInternal().set((GameCampaign<GameCampaignEntry<? extends SavegameInfo>>) getSavegameCache().getCampaign(e));
        }
        this.selectedEntry.set(e);
        globalSelectedEntryPropertyInternal().set(e);

        LoggerFactory.getLogger(GameIntegration.class).debug("Selecting campaign entry " + (e != null ? e.getName() : "null"));
    }

    public static void selectIntegration(GameIntegration<?,?> newInt) {
        if (current.get() == newInt) {
            return;
        }

        current().selectCampaign(null);
        current.set(newInt);
        Settings.getInstance().setActiveGame(current().getInstallation().getPath());
    }
}
