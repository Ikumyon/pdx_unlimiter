package com.crschnick.pdx_unlimiter.app.gui.dialog;

import com.crschnick.pdx_unlimiter.app.core.ComponentManager;
import com.crschnick.pdx_unlimiter.app.gui.GuiStyle;
import com.crschnick.pdx_unlimiter.app.gui.game.GameGuiFactory;
import com.crschnick.pdx_unlimiter.app.installation.Game;
import com.crschnick.pdx_unlimiter.gui_utils.GuiTooltips;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.layout.HBox;

public class GuiGameSwitcher {

    public static void showGameSwitchDialog() {
        Alert alert = GuiDialogHelper.createEmptyAlert();
        alert.setTitle("Select game");

        HBox games = new HBox();
        for (var game : Game.values()) {
            if (!game.isEnabled()) {
                continue;
            }

            var icon = GameGuiFactory.ALL.get(game).createIcon();
            ColorAdjust desaturate = new ColorAdjust();
            desaturate.setSaturation(-1);
            icon.getStyleClass().add(GuiStyle.CLASS_GAME_ICON);

            GuiTooltips.install(icon, game.getFullName());
            icon.setOnMouseClicked(e -> {
                ComponentManager.switchGame(game);
                alert.setResult(ButtonType.CLOSE);
            });

            icon.setOnMouseEntered(e -> {
                icon.setEffect(null);
            });
            icon.setOnMouseExited(e -> {
                icon.setEffect(desaturate);
            });
            games.getChildren().add(icon);
            icon.setEffect(desaturate);
        }
        games.setFillHeight(true);
        games.getStyleClass().add(GuiStyle.CLASS_GAME_ICON_BAR);

        alert.getDialogPane().getScene().getWindow().setOnCloseRequest(e -> alert.setResult(ButtonType.CLOSE));
        alert.getDialogPane().setContent(games);
        alert.showAndWait();
    }
}
