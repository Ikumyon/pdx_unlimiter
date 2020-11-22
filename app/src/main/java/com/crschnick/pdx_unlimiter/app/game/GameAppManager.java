package com.crschnick.pdx_unlimiter.app.game;

import javafx.beans.property.SimpleObjectProperty;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class GameAppManager {

    private static GameAppManager INSTANCE = new GameAppManager();

    private SimpleObjectProperty<GameApp> activeGame = new SimpleObjectProperty<>(null);

    public static void init() {
        Thread t = new Thread(() -> {
            while (true) {
                if ((INSTANCE.activeGame.get() == null)) {
                    var process = Stream.of(GameInstallation.EU4, GameInstallation.HOI4,
                            GameInstallation.CK3, GameInstallation.STELLARIS)
                            .filter(Objects::nonNull)
                            .map(g -> new GameApp(ProcessHandle.allProcesses()
                                    .filter(p -> p.info().command().isPresent())
                                    .filter(p -> p.info().command().get().contains(g.getExecutable().toString()))
                                    .findAny().orElse(null), g)
                            )
                            .filter(ga -> ga.getProcess() != null)
                            .findAny();
                    if (process.isPresent()) {
                        INSTANCE.activeGame.set(process.get());
                        INSTANCE.activeGame.get().onStart();
                        GameIntegration.selectIntegration(GameIntegration.getForInstallation(process.get().getInstallation()));
                    }
                } else {
                    if (!INSTANCE.activeGame.get().isAlive()) {
                        INSTANCE.activeGame.get().onShutdown();
                        INSTANCE.activeGame.set(null);
                    }
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    public static GameAppManager getInstance() {
        return INSTANCE;
    }

    private GameAppManager() {
    }

    public GameApp getActiveGame() {
        return activeGame.get();
    }

    public SimpleObjectProperty<GameApp> activeGameProperty() {
        return activeGame;
    }
}
