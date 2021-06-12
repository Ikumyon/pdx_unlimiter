package com.crschnick.pdxu.app.savegame;

import com.crschnick.pdxu.model.GameDate;
import com.crschnick.pdxu.model.SavegameInfo;
import javafx.beans.property.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SavegameEntry<T, I extends SavegameInfo<T>> implements Comparable<SavegameEntry<T, I>> {

    private final ObjectProperty<State> state;
    private final StringProperty name;
    private final UUID uuid;
    private final ObjectProperty<I> info;
    private final String contentChecksum;
    private final GameDate date;
    private final SavegameNotes notes;
    private final List<String> sourceFileChecksums;

    public SavegameEntry(SavegameEntry<T,I> otherEntry, UUID uuid) {
        this(otherEntry.getName(), uuid, otherEntry.getContentChecksum(),
                otherEntry.getDate(), otherEntry.getNotes(), otherEntry.getSourceFileChecksums());
    }

    public SavegameEntry(String name, UUID uuid,
                         String contentChecksum, GameDate date, SavegameNotes notes,
                         List<String> sourceFileChecksums) {
        this.state = new SimpleObjectProperty<>(State.UNLOADED);
        this.contentChecksum = contentChecksum;
        this.name = new SimpleStringProperty(name);
        this.uuid = uuid;
        this.info = new SimpleObjectProperty<>(null);
        this.date = date;
        this.notes = notes != null ? notes : SavegameNotes.empty();
        this.sourceFileChecksums = new ArrayList<>(sourceFileChecksums);
    }

    public void startLoading() {
        state.set(State.LOADING);
    }

    public void fail() {
        state.set(State.LOAD_FAILED);
    }

    public void load(I newInfo) {
        state.set(State.LOADED);
        info.set(newInfo);
    }

    public void unload() {
        state.set(State.UNLOADED);
        info.set(null);
    }

    public boolean canLoad() {
        return state.get().equals(State.UNLOADED);
    }

    @Override
    public int compareTo(SavegameEntry<T, I> o) {
        return o.getDate().compareTo(getDate());
    }

    public String getName() {
        return name.get();
    }

    public StringProperty nameProperty() {
        return name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public I getInfo() {
        return info.get();
    }

    public ReadOnlyObjectProperty<I> infoProperty() {
        return info;
    }

    public String getContentChecksum() {
        return contentChecksum;
    }

    public GameDate getDate() {
        return date;
    }

    public List<String> getSourceFileChecksums() {
        return sourceFileChecksums;
    }

    public void addSourceFileChecksum(String sourceFileChecksum) {
        this.sourceFileChecksums.add(sourceFileChecksum);
    }

    public SavegameNotes getNotes() {
        return notes;
    }

    public State getState() {
        return state.get();
    }

    public ReadOnlyObjectProperty<State> stateProperty() {
        return state;
    }

    public enum State {
        UNLOADED,
        LOADING,
        LOADED,
        LOAD_FAILED
    }
}
