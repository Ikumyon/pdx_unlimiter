package com.crschnick.pdx_unlimiter.app.gui;

import com.crschnick.pdx_unlimiter.app.core.ErrorHandler;
import com.crschnick.pdx_unlimiter.app.core.PdxuInstallation;
import com.crschnick.pdx_unlimiter.app.core.SavegameManagerState;
import com.crschnick.pdx_unlimiter.app.core.settings.Settings;
import com.crschnick.pdx_unlimiter.app.gui.dialog.*;
import com.crschnick.pdx_unlimiter.app.installation.GameLauncher;
import com.crschnick.pdx_unlimiter.app.savegame.SavegameStorageIO;
import com.crschnick.pdx_unlimiter.app.util.Hyperlinks;
import com.crschnick.pdx_unlimiter.app.util.MemoryHelper;
import com.crschnick.pdx_unlimiter.app.util.ThreadHelper;
import com.jfoenix.controls.JFXButton;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import org.apache.commons.io.FileUtils;
import org.kordamp.ikonli.javafx.FontIcon;

import java.nio.file.Path;
import java.util.Optional;

public class GuiMenuBar {

    private static MenuBar createMenuBar() {

        Menu settings = new Menu("Settings");
        MenuItem c = new MenuItem("Change settings");
        c.setOnAction((a) -> {
            GuiSettings.showSettings();
        });
        settings.getItems().add(c);


        Menu savegames = new Menu("Storage");

        MenuItem menuItem2 = new MenuItem("Export storage...");
        menuItem2.setOnAction((a) -> {
            Optional<Path> path = GuiSavegameIO.showExportDialog();
            path.ifPresent(p -> {
                if (FileUtils.listFiles(p.toFile(), null, false).size() > 0) {
                    GuiErrorReporter.showSimpleErrorMessage("Selected directory is not empty!" +
                            " You can only export the storage into an empty directory");
                    return;
                }
                SavegameStorageIO.exportSavegameStorage(p);
            });
        });
        savegames.getItems().add(menuItem2);

        MenuItem sd = new MenuItem("Open storage directory");
        sd.setOnAction((a) -> {
            ThreadHelper.open(Settings.getInstance().storageDirectory.getValue());
        });
        savegames.getItems().add(sd);


        Menu about = new Menu("About");

        MenuItem src = new MenuItem("Contribute");
        src.setOnAction((a) -> {
            Hyperlinks.open(Hyperlinks.MAIN_PAGE);
        });

        MenuItem lc = new MenuItem("License");
        lc.setOnAction((a) -> {
            GuiDialogHelper.showText("License", "License", "license.txt");
        });
        MenuItem tc = new MenuItem("Third party software");
        tc.setOnAction((a) -> {
            GuiDialogHelper.showText("Third party information", "A list of all software used to create the Pdx-Unlimiter", "third_party.txt");
        });
        about.getItems().add(src);
        about.getItems().add(lc);
        about.getItems().add(tc);


        Menu help = new Menu("Help");

        MenuItem guide = new MenuItem("User Guide");
        guide.setOnAction((a) -> {
            Hyperlinks.open(Hyperlinks.GUIDE);
        });
        help.getItems().add(guide);

        MenuItem is = new MenuItem("Report an issue");
        is.setOnAction((a) -> {
            ErrorHandler.reportIssue();
        });
        help.getItems().add(is);

        MenuItem discord = new MenuItem("Discord server");
        discord.setOnAction((a) -> {
            Hyperlinks.open(Hyperlinks.DISCORD);
        });
        help.getItems().add(discord);

        MenuItem log = new MenuItem("Show log");
        log.setOnAction((a) -> {
            GuiLog.showLogDialog();
        });
        help.getItems().add(log);


        Menu dev = new Menu("Developer");

        MenuItem error = new MenuItem("Throw exception");
        error.setOnAction((a) -> {
            throw new RuntimeException("This is a test exception!");
        });
        dev.getItems().add(error);

        MenuItem gc = new MenuItem("Force GC");
        gc.setOnAction((a) -> {
            MemoryHelper.forceGC();
        });
        dev.getItems().add(gc);


        MenuBar menuBar = new MenuBar();
        menuBar.setUseSystemMenuBar(true);
        menuBar.getMenus().add(settings);
        menuBar.getMenus().add(savegames);
        menuBar.getMenus().add(about);
        menuBar.getMenus().add(help);
        if (PdxuInstallation.getInstance().isDeveloperMode()) {
            menuBar.getMenus().add(dev);
        }
        return menuBar;
    }

    private static Node createRightBar() {
        JFXButton m = new JFXButton("Switch game");
        m.setGraphic(new FontIcon());
        m.getStyleClass().add(PdxuStyle.CLASS_SWTICH_GAME);
        m.setOnAction(a -> GuiGameSwitcher.showGameSwitchDialog());
        m.setDisable(SavegameManagerState.get().current() == null);
        SavegameManagerState.get().onGameChange(n -> {
            Platform.runLater(() -> m.setDisable(n == null));
        });

        JFXButton importB = new JFXButton("Import");
        importB.setOnAction(e -> {
            GuiImporter.createImporterDialog();
            e.consume();
        });
        importB.setGraphic(new FontIcon());
        importB.getStyleClass().add(PdxuStyle.CLASS_IMPORT);
        importB.setDisable(SavegameManagerState.get().current() == null);
        SavegameManagerState.get().onGameChange(n -> {
            Platform.runLater(() -> importB.setDisable(n == null));
        });

        JFXButton launch = new JFXButton("Launch");
        launch.setOnAction(e -> {
            GameLauncher.startLauncher();
            e.consume();
        });
        launch.setGraphic(new FontIcon());
        launch.getStyleClass().add(PdxuStyle.CLASS_LAUNCH);
        launch.setDisable(SavegameManagerState.get().current() == null);
        SavegameManagerState.get().onGameChange(n -> {
            Platform.runLater(() -> launch.setDisable(n == null));
        });

        var box = new HBox(m, importB, launch);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    public static Node createMenu() {
        MenuBar leftBar = createMenuBar();

        StackPane spacer = new StackPane();
        Label game = new Label();
        SavegameManagerState.get().onGameChange(n -> {
            String name = n != null ? n.getFullName() : "None";
            Platform.runLater(() -> game.setText(name));
        });
        spacer.getChildren().add(game);
        spacer.setAlignment(Pos.CENTER);

        spacer.getStyleClass().add("menu-bar");
        HBox.setHgrow(spacer, Priority.SOMETIMES);

        StackPane s = new StackPane();
        s.getStyleClass().add("menu-bar");
        s.getChildren().add(createRightBar());

        return new HBox(leftBar, spacer, s);
    }
}
