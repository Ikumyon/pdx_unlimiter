package com.crschnick.pdxu.model.ck2;

import com.crschnick.pdxu.io.node.Node;
import com.crschnick.pdxu.model.GameDateType;
import com.crschnick.pdxu.model.GameVersion;
import com.crschnick.pdxu.model.SavegameInfo;
import com.crschnick.pdxu.model.SavegameInfoException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Ck2SavegameInfo extends SavegameInfo<Ck2Tag> {

    private Ck2Tag tag;
    private List<Ck2Tag> allTags;
    private GameVersion version;

    public Ck2SavegameInfo() {
    }

    public Ck2SavegameInfo(Node n) throws SavegameInfoException {
        try {
            ironman = false;
            date = GameDateType.CK3.fromString(n.getNodesForKey("date").get(0).getString());
            binary = false;

            tag = new Ck2Tag(n.getNodeForKey("player_realm").getString(), n.getNodeForKey("player_name").getString());

            allTags = new ArrayList<>();

            mods = null;

            dlcs = List.of();

            Pattern p = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+)");
            var vs = n.getNodesForKey("version").get(0).getString();
            Matcher m = p.matcher(vs);
            if (m.matches()) {
                version = new GameVersion(
                        Integer.parseInt(m.group(1)),
                        Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3)),
                        Integer.parseInt(m.group(4)));
            } else {
                throw new IllegalArgumentException("Invalid CK2 version: " + vs);
            }
        } catch (Throwable e) {
            throw new SavegameInfoException("Could not create savegame info of savegame", e);
        }
    }

    @Override
    public Ck2Tag getTag() {
        return tag;
    }

    @Override
    public GameVersion getVersion() {
        return version;
    }

    @Override
    public List<Ck2Tag> getAllTags() {
        return allTags;
    }
}
