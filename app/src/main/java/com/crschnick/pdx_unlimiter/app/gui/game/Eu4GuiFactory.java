package com.crschnick.pdx_unlimiter.app.gui.game;

import com.crschnick.pdx_unlimiter.app.lang.PdxuI18n;
import com.crschnick.pdx_unlimiter.app.gui.GuiTooltips;
import com.crschnick.pdx_unlimiter.app.installation.Game;
import com.crschnick.pdx_unlimiter.app.installation.GameInstallation;
import com.crschnick.pdx_unlimiter.app.lang.GameLocalisation;
import com.crschnick.pdx_unlimiter.app.util.ColorHelper;
import com.crschnick.pdx_unlimiter.core.info.SavegameInfo;
import com.crschnick.pdx_unlimiter.core.info.eu4.Eu4SavegameInfo;
import com.crschnick.pdx_unlimiter.core.info.eu4.Eu4Tag;
import com.jfoenix.controls.JFXMasonryPane;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.text.Font;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static com.crschnick.pdx_unlimiter.app.gui.GuiStyle.*;
import static com.crschnick.pdx_unlimiter.app.gui.game.GameImage.*;

public class Eu4GuiFactory extends GameGuiFactory<Eu4Tag, Eu4SavegameInfo> {

    private static Region createRulerLabel(Eu4SavegameInfo.Ruler ruler, boolean isRuler) {
        VBox box = new VBox();
        var img = isRuler ? EU4_ICON_RULER : EU4_ICON_HEIR;

        var hb = new HBox(imageNode(img, CLASS_RULER_ICON), new Label(ruler.getName()));
        hb.setAlignment(Pos.CENTER);
        hb.setSpacing(5);
        box.getChildren().add(hb);

        box.alignmentProperty().set(Pos.CENTER);
        box.getChildren().add(createRulerStatsNode(ruler));
        box.getStyleClass().add(CLASS_RULER);
        GuiTooltips.install(box, ruler.getFullName());
        return box;
    }

    private static Region createRulerStatsNode(Eu4SavegameInfo.Ruler ruler) {
        return createPowersNode(ruler.getAdm(), ruler.getDip(), ruler.getMil());
    }

    private static Region createPowersNode(int admP, int dipP, int milP) {
        HBox box = new HBox();
        box.setAlignment(Pos.CENTER);
        Label adm = new Label(admP + "  ", imageNode(EU4_ICON_ADM, CLASS_POWER_ICON));
        box.getChildren().add(adm);

        Label dip = new Label(dipP + "  ", imageNode(EU4_ICON_DIP, CLASS_POWER_ICON));
        box.getChildren().add(dip);

        Label mil = new Label(String.valueOf(milP), imageNode(EU4_ICON_MIL, CLASS_POWER_ICON));
        box.getChildren().add(mil);
        return box;
    }

    private void createDiplomacyRow(
            JFXMasonryPane pane,
            SavegameInfo<Eu4Tag> info,
            Region icon,
            List<Eu4Tag> tags,
            String tooltip,
            String style) {
        if (tags.size() == 0) {
            return;
        }

        var row = TagRows.createTagRow(icon, tooltip, tags,
                t -> GameLocalisation.getLocalisedValue(t.getTag(), info),
                t -> GameImage.imageNode(Eu4TagRenderer.smallShieldImage(info, t), CLASS_TAG_ICON));
        row.getStyleClass().add(CLASS_DIPLOMACY_ROW);
        row.getStyleClass().add(style);
        addNode(pane, row);
    }

    private void addIntegerEntry(
            JFXMasonryPane pane,
            Image icon, int value, String tooltip, boolean showPlus) {
        var text = (showPlus && value > 0 ? "+" + value : String.valueOf(value));
        var ironman = new StackPane(new Label(text, GameImage.imageNode(icon, CLASS_IMAGE_ICON)));
        ironman.setAlignment(Pos.CENTER);
        GuiTooltips.install(ironman, tooltip);
        ironman.getStyleClass().add("number");
        addNode(pane, ironman);
    }

    private void addManpowerEntry(
            JFXMasonryPane pane,
            int value, int max) {
        var label = new Label(value + "k / " + max + "k",
                GameImage.imageNode(EU4_ICON_MANPOWER, CLASS_IMAGE_ICON));
        label.setMinWidth(Region.USE_PREF_SIZE);
        label.setEllipsisString("");

        var stack = new StackPane(label);
        stack.setAlignment(Pos.CENTER);
        stack.getStyleClass().add("number");
        stack.setMinWidth(label.getPrefWidth());
        GuiTooltips.install(stack, "Manpower / Max Manpower");
        addNode(pane, stack);
    }

    private void addDucatsEntry(
            JFXMasonryPane pane,
            int value, int loans) {
        var label = new Label(value + (loans != 0 ?  " / -" + loans : ""),
                GameImage.imageNode(EU4_ICON_DUCATS, CLASS_IMAGE_ICON));
        label.setMinWidth(Region.USE_PREF_SIZE);
        label.setEllipsisString("");

        var stack = new StackPane(label);
        stack.setAlignment(Pos.CENTER);
        stack.setMinWidth(label.getPrefWidth());
        stack.getStyleClass().add("number");
        GuiTooltips.install(stack, "Ducats in treasury" + (loans != 0 ? " / Owed ducats for loans" : ""));
        addNode(pane, stack);
    }

    private void addPowersEntry(
            JFXMasonryPane pane, int adm, int dip, int mil) {
        var label = createPowersNode(adm, dip, mil);
        var stack = new StackPane(label);
        stack.setAlignment(Pos.CENTER);
        stack.setMinWidth(label.getPrefWidth());
        stack.getStyleClass().add("number");
        addNode(pane, stack);
    }

    @Override
    public Image tagImage(SavegameInfo<Eu4Tag> info, Eu4Tag tag) {
        return Eu4TagRenderer.shieldImage(info, tag);
    }

    @Override
    public Font font() throws IOException {
        return Font.loadFont(
                Files.newInputStream(GameInstallation.ALL.get(Game.EU4).getPath().resolve("launcher-assets").resolve("font.ttf")), 12);
    }

    @Override
    public Pane background() {
        return GameImage.backgroundNode(EU4_BACKGROUND);
    }

    @Override
    public Background createEntryInfoBackground(SavegameInfo<Eu4Tag> info) {
        return new Background(new BackgroundFill(
                ColorHelper.withAlpha(ColorHelper.fromGameColor(info.getTag().getMapColor()), 0.33),
                CornerRadii.EMPTY, Insets.EMPTY));
    }

    @Override
    public void fillNodeContainer(SavegameInfo<Eu4Tag> i, JFXMasonryPane grid) {
        Eu4SavegameInfo info = (Eu4SavegameInfo) i;
        if (info.isObserver()) {
            super.fillNodeContainer(i, grid);
            return;
        }

        addNode(grid, createRulerLabel(info.getRuler(), true));
        if (info.getHeir().isPresent()) {
            addNode(grid, createRulerLabel(info.getHeir().get(), false));
        }

        if (info.isIronman()) {
            var ironman = new StackPane(imageNode(EU4_ICON_IRONMAN, CLASS_IMAGE_ICON, null));
            ironman.setAlignment(Pos.CENTER);
            GuiTooltips.install(ironman, PdxuI18n.get("IRONMAN"));
            addNode(grid, ironman);
        }

        if (info.isRandomNewWorld()) {
            var rnw = new StackPane(imageNode(EU4_ICON_RANDOM_NEW_WORLD, CLASS_IMAGE_ICON, null));
            rnw.setAlignment(Pos.CENTER);
            GuiTooltips.install(rnw, PdxuI18n.get("RNW"));
            addNode(grid, rnw);
        }

        if (info.isCustomNationInWorld()) {
            var cn = new StackPane(imageNode(EU4_ICON_CUSTOM_NATION, CLASS_IMAGE_ICON, null));
            cn.setAlignment(Pos.CENTER);
            GuiTooltips.install(cn, PdxuI18n.get("CUSTOM_NATION"));
            addNode(grid, cn);
        }

        if (info.isReleasedVassal()) {
            var rv = new StackPane(imageNode(EU4_ICON_RELEASED_VASSAL, CLASS_IMAGE_ICON, null));
            rv.setAlignment(Pos.CENTER);
            GuiTooltips.install(rv, PdxuI18n.get("RELEASED_VASSAL"));
            addNode(grid, rv);
        }

        for (Eu4SavegameInfo.War war : info.getWars()) {
            createDiplomacyRow(grid, i, imageNode(EU4_ICON_WAR, CLASS_IMAGE_ICON), war.getEnemies(),
                    war.getTitle(), CLASS_WAR);
        }

        super.fillNodeContainer(i, grid);

        createDiplomacyRow(grid, i, imageNode(EU4_ICON_ALLIANCE, CLASS_IMAGE_ICON), info.getAllies(),
                PdxuI18n.get("ALLIES"), CLASS_ALLIANCE);
        createDiplomacyRow(grid, i, imageNode(EU4_ICON_ROYAL_MARRIAGE, CLASS_IMAGE_ICON), info.getMarriages(),
                PdxuI18n.get("ROYAL_MARRIAGES"), CLASS_MARRIAGE);
        createDiplomacyRow(grid, i, imageNode(EU4_ICON_GUARANTEE, CLASS_IMAGE_ICON), info.getGuarantees(),
                PdxuI18n.get("GUARANTEES"), CLASS_GUARANTEE);
        createDiplomacyRow(grid, i, imageNode(EU4_ICON_VASSAL, CLASS_IMAGE_ICON), info.getVassals(),
                PdxuI18n.get("VASSALS"), CLASS_VASSAL);
        createDiplomacyRow(grid, i, imageNode(EU4_ICON_UNION_SENIOR, CLASS_IMAGE_ICON), info.getJuniorPartners(),
                PdxuI18n.get("PU_JUNIOR_PARTNERS"), CLASS_VASSAL);
        createDiplomacyRow(grid, i, imageNode(EU4_ICON_TRIBUTARY, CLASS_IMAGE_ICON), info.getTributaryJuniors(),
                PdxuI18n.get("TRIBUTARIES"), CLASS_VASSAL);
        createDiplomacyRow(grid, i, imageNode(EU4_ICON_MARCH, CLASS_IMAGE_ICON), info.getMarches(),
                PdxuI18n.get("MARCHES"), CLASS_VASSAL);
        createDiplomacyRow(grid, i, imageNode(EU4_ICON_DEV, CLASS_IMAGE_ICON), info.getMarches(),
                PdxuI18n.get("MARCHES"), CLASS_VASSAL);

        addIntegerEntry(grid, EU4_ICON_DEV, info.getTotalDev(), "Development", false);
        addIntegerEntry(grid, EU4_ICON_PRESTIGE, info.getPrestige(), "Prestige", true);
        addIntegerEntry(grid, EU4_ICON_STABILITY, info.getStability(), "Stability", true);
        addManpowerEntry(grid, info.getManpower(), info.getMaxManpower());
        addDucatsEntry(grid, info.getTreasuryMoney(), info.getLoanedMoney());
        addPowersEntry(grid, info.getAdm(), info.getDip(), info.getMil());
    }
}
