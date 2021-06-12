package com.crschnick.pdxu.model.vic2;

import com.crschnick.pdxu.io.node.Node;
import com.crschnick.pdxu.model.GameDateType;
import com.crschnick.pdxu.model.GameVersion;
import com.crschnick.pdxu.model.SavegameInfo;
import com.crschnick.pdxu.model.SavegameInfoException;

import java.util.ArrayList;
import java.util.List;

public class Vic2SavegameInfo extends SavegameInfo<Vic2Tag> {

    private Vic2Tag tag;
    private List<Vic2Tag> allTags;
    private GameVersion version;

    public Vic2SavegameInfo() {
    }

    public Vic2SavegameInfo(Node n) throws SavegameInfoException {
        try {
            ironman = false;
            date = GameDateType.VIC2.fromString(n.getNodesForKey("date").get(0).getString());
            binary = false;

            allTags = new ArrayList<>();
            n.forEach((k, v) -> {
                if (!k.toUpperCase().equals(k) || k.length() != 3) {
                    return;
                }

                allTags.add(new Vic2Tag(k));
            });
            var playerTag = n.getNodeForKey("player").getString();
            tag = allTags.stream().filter(t -> t.getTagId().equals(playerTag))
                    .findAny()
                    .orElseThrow(() -> new SavegameInfoException("No player tag found"));

            mods = null;
            dlcs = List.of();

            // Hardcode version
            version = new GameVersion(3, 4, 0, 0);
        } catch (SavegameInfoException e) {
            throw e;
        } catch (Throwable e) {
            throw new SavegameInfoException("Could not create savegame info of savegame", e);
        }
    }

    @Override
    public Vic2Tag getTag() {
        return tag;
    }

    @Override
    public GameVersion getVersion() {
        return version;
    }

    @Override
    public List<Vic2Tag> getAllTags() {
        return allTags;
    }
}
