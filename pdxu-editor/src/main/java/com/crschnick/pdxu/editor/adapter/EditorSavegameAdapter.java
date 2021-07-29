package com.crschnick.pdxu.editor.adapter;

import com.crschnick.pdxu.app.installation.Game;
import com.crschnick.pdxu.editor.EditorState;
import com.crschnick.pdxu.editor.node.EditorRealNode;
import com.crschnick.pdxu.io.node.NodePointer;
import javafx.scene.layout.Region;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface EditorSavegameAdapter {

    Game getGame();

    Map<Game, EditorSavegameAdapter> ALL = ServiceLoader.load(EditorSavegameAdapter.class)
            .stream().map(prov -> prov.get()).collect(Collectors.toMap(adap -> adap.getGame(), Function.identity()));

    Map<String, NodePointer> createCommonJumps(EditorState state) throws Exception;

    Map<String, Runnable> createScripts(EditorState state) throws Exception;

    NodePointer createNodeJump(EditorState state, EditorRealNode node) throws Exception;

    javafx.scene.Node createNodeTag(EditorState state, EditorRealNode node, Region valueDisplay) throws Exception;
}
