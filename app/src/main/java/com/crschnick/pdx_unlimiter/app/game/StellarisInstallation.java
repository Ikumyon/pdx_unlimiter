package com.crschnick.pdx_unlimiter.app.game;

import java.nio.file.Path;

public class StellarisInstallation extends GameInstallation {

    public StellarisInstallation(Path path) {
        super(path);
    }

    @Override
    public void start(boolean continueLast) {

    }

    @Override
    public void init() throws Exception {

    }

    @Override
    public boolean isValid() {
        return false;
    }

    @Override
    public Path getExecutable() {
        return null;
    }

    @Override
    public Path getUserPath() {
        return null;
    }

    @Override
    public Path getSavegamesPath() {
        return null;
    }
}
