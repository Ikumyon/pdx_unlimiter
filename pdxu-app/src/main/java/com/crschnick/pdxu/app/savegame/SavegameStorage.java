package com.crschnick.pdxu.app.savegame;

import com.crschnick.pdxu.app.core.ErrorHandler;
import com.crschnick.pdxu.app.core.IntegrityManager;
import com.crschnick.pdxu.app.core.settings.Settings;
import com.crschnick.pdxu.app.gui.game.GameGuiFactory;
import com.crschnick.pdxu.app.gui.game.ImageLoader;
import com.crschnick.pdxu.app.installation.Game;
import com.crschnick.pdxu.app.lang.LanguageManager;
import com.crschnick.pdxu.app.util.ConfigHelper;
import com.crschnick.pdxu.app.util.JsonHelper;
import com.crschnick.pdxu.app.util.integration.RakalyHelper;
import com.crschnick.pdxu.io.node.Node;
import com.crschnick.pdxu.io.parser.ParseException;
import com.crschnick.pdxu.io.savegame.SavegameParseResult;
import com.crschnick.pdxu.io.savegame.SavegameType;
import com.crschnick.pdxu.model.GameDate;
import com.crschnick.pdxu.model.GameDateType;
import com.crschnick.pdxu.model.SavegameInfo;
import com.crschnick.pdxu.model.SavegameInfoException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.scene.image.Image;
import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.function.FailableBiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public abstract class SavegameStorage<T, I extends SavegameInfo<T>> {


    public static final BidiMap<Game, SavegameStorage<?, ?>> ALL = new DualHashBidiMap<>();
    private final Logger logger;
    private final Class<I> infoClass;
    private final FailableBiFunction<Node, Boolean, I, SavegameInfoException> infoFactory;
    private final String name;
    private final GameDateType dateType;
    private final Path path;
    private final SavegameType type;
    private final ObservableSet<SavegameCollection<T, I>> collections = FXCollections.observableSet(new HashSet<>());

    public SavegameStorage(
            FailableBiFunction<Node, Boolean, I, SavegameInfoException> infoFactory,
            String name,
            GameDateType dateType,
            SavegameType type,
            Class<I> infoClass) {
        this.infoFactory = infoFactory;
        this.name = name;
        this.type = type;
        this.dateType = dateType;
        this.path = Settings.getInstance().storageDirectory.getValue().resolve(name);
        this.infoClass = infoClass;
        this.logger = LoggerFactory.getLogger("SavegameStorage (" + getName() + ")");
    }

    @SuppressWarnings("unchecked")
    public static <T, I extends SavegameInfo<T>> SavegameStorage<T, I> get(Game g) {
        return (SavegameStorage<T, I>) ALL.get(g);
    }

    @SuppressWarnings("unchecked")
    public static <T, I extends SavegameInfo<T>> SavegameStorage<T, I> get(SavegameType type) {
        var found = ALL.entrySet().stream()
                .filter(entry -> entry.getValue().type.equals(type))
                .findAny();
        return (SavegameStorage<T, I>) found.map(e -> e.getValue()).orElse(null);
    }

    public static void init() throws Exception {
        SavegameStorages.init();
        for (SavegameStorage<?, ?> s : ALL.values()) {
            s.loadData();
        }
    }

    public static void reset() {
        for (SavegameStorage<?, ?> s : ALL.values()) {
            s.saveData();
        }
        ALL.clear();
    }

    private synchronized void loadData() throws Exception {
        Files.createDirectories(getSavegameDataDirectory());

        JsonNode node;
        if (Files.exists(getDataFile())) {
            node = ConfigHelper.readConfig(getDataFile());
        } else {
            return;
        }

        {
            JsonNode c = node.required("campaigns");
            for (int i = 0; i < c.size(); i++) {
                String name = c.get(i).required("name").textValue();
                GameDate date = dateType.fromString(c.get(i).required("date").textValue());
                UUID id = UUID.fromString(c.get(i).required("uuid").textValue());
                if (!Files.isDirectory(getSavegameDataDirectory().resolve(id.toString()))) {
                    continue;
                }

                Instant lastDate = Instant.parse(c.get(i).required("lastPlayed").textValue());
                Image image = ImageLoader.loadImage(
                        getSavegameDataDirectory().resolve(id.toString()).resolve("campaign.png"));
                Integer branchId = Optional.ofNullable(c.get("branchId")).map(JsonNode::intValue).orElse(null);
                collections.add(new SavegameCampaign<>(lastDate, name, id, branchId, date, image));
            }
        }

        {
            // Legacy support, can be missing
            JsonNode f = Optional.ofNullable(node.get("folders")).orElse(JsonNodeFactory.instance.arrayNode());
            for (int i = 0; i < f.size(); i++) {
                String name = f.get(i).required("name").textValue();
                UUID id = UUID.fromString(f.get(i).required("uuid").textValue());
                if (!Files.isDirectory(getSavegameDataDirectory().resolve(id.toString()))) {
                    continue;
                }

                Instant lastDate = Instant.parse(f.get(i).required("lastPlayed").textValue());
                collections.add(new SavegameFolder<>(lastDate, name, id));
            }
        }


        for (SavegameCollection<T, I> collection : collections) {
            String typeName = collection instanceof SavegameCampaign ? "campaign" : "folder";
            var colFile = getSavegameDataDirectory().resolve(
                    collection.getUuid().toString()).resolve(typeName + ".json");
            JsonNode campaignNode = JsonHelper.read(colFile);
            StreamSupport.stream(campaignNode.required("entries").spliterator(), false).forEach(entryNode -> {
                UUID eId = UUID.fromString(entryNode.required("uuid").textValue());
                String name = Optional.ofNullable(entryNode.get("name")).map(JsonNode::textValue).orElse(null);
                GameDate date = dateType.fromString(entryNode.required("date").textValue());
                String checksum = entryNode.required("checksum").textValue();
                SavegameNotes notes = SavegameNotes.fromNode(entryNode.get("notes"));
                List<String> sourceFileChecksums = Optional.ofNullable(entryNode.get("sourceFileChecksums"))
                        .map(n -> StreamSupport.stream(n.spliterator(), false)
                                .map(sfc -> sfc.textValue())
                                .collect(Collectors.toList()))
                        .orElse(List.of());
                collection.add(new SavegameEntry<>(name, eId, checksum, date, notes, sourceFileChecksums));
            });
        }
    }

    private synchronized void saveData() {
        ObjectNode n = JsonNodeFactory.instance.objectNode();

        ArrayNode c = n.putArray("campaigns");
        getCollections().stream().filter(col -> col instanceof SavegameCampaign).forEach(col -> {
            SavegameCampaign<T, I> campaign = (SavegameCampaign<T, I>) col;
            ObjectNode campaignFileNode = JsonNodeFactory.instance.objectNode();
            ArrayNode entries = campaignFileNode.putArray("entries");
            campaign.getSavegames().stream()
                    .map(entry -> JsonNodeFactory.instance.objectNode()
                            .put("name", entry.getName())
                            .put("date", entry.getDate().toString())
                            .put("checksum", entry.getContentChecksum())
                            .put("uuid", entry.getUuid().toString())
                            .<ObjectNode>set("sourceFileChecksums", JsonNodeFactory.instance.arrayNode().addAll(
                                    entry.getSourceFileChecksums().stream()
                                            .map(s -> new TextNode(s))
                                            .collect(Collectors.toList())))
                            .<ObjectNode>set("notes", SavegameNotes.toNode(entry.getNotes())))
                    .forEach(entries::add);

            ConfigHelper.writeConfig(getSavegameDataDirectory()
                    .resolve(campaign.getUuid().toString()).resolve("campaign.json"), campaignFileNode);

            try {
                ImageLoader.writePng(campaign.getImage(), getSavegameDataDirectory()
                        .resolve(campaign.getUuid().toString()).resolve("campaign.png"));
            } catch (IOException e) {
                ErrorHandler.handleException(e);
            }

            ObjectNode campaignNode = JsonNodeFactory.instance.objectNode()
                    .put("name", campaign.getName())
                    .put("date", campaign.getDate().toString())
                    .put("lastPlayed", campaign.getLastPlayed().toString())
                    .put("uuid", campaign.getUuid().toString());
            if (campaign.isBranch()) {
                campaignNode.put("branchId", campaign.getBranchId());
            }
            c.add(campaignNode);
        });


        ArrayNode f = n.putArray("folders");
        getCollections().stream().filter(col -> col instanceof SavegameFolder).forEach(col -> {
            SavegameFolder<T, I> folder = (SavegameFolder<T, I>) col;
            ObjectNode folderFileNode = JsonNodeFactory.instance.objectNode();
            ArrayNode entries = folderFileNode.putArray("entries");
            folder.getSavegames().stream()
                    .map(entry -> JsonNodeFactory.instance.objectNode()
                            .put("name", entry.getName())
                            .put("date", entry.getDate().toString())
                            .put("checksum", entry.getContentChecksum())
                            .put("uuid", entry.getUuid().toString())
                            .<ObjectNode>set("sourceFileChecksums", JsonNodeFactory.instance.arrayNode().addAll(
                                    entry.getSourceFileChecksums().stream()
                                            .map(s -> new TextNode(s))
                                            .collect(Collectors.toList())))
                            .<ObjectNode>set("notes", SavegameNotes.toNode(entry.getNotes())))
                    .forEach(entries::add);

            ConfigHelper.writeConfig(getSavegameDataDirectory()
                    .resolve(folder.getUuid().toString()).resolve("folder.json"), folderFileNode);

            ObjectNode folderNode = JsonNodeFactory.instance.objectNode()
                    .put("name", folder.getName())
                    .put("lastPlayed", folder.getLastPlayed().toString())
                    .put("uuid", folder.getUuid().toString());
            f.add(folderNode);
        });

        ConfigHelper.writeConfig(getDataFile(), n);
    }

    synchronized Optional<SavegameFolder<T, I>> getOrCreateFolder(String name) {
        return this.collections.stream()
                .filter(f -> f instanceof SavegameFolder && f.getName().equals(name))
                .map(f -> (SavegameFolder<T, I>) f)
                .findAny()
                .or(() -> addNewFolder(name));
    }

    public synchronized Optional<SavegameFolder<T, I>> addNewFolder(String name) {
        var col = new SavegameFolder<T, I>(Instant.now(), name, UUID.randomUUID());
        try {
            Files.createDirectory(getSavegameDataDirectory().resolve(col.getUuid().toString()));
        } catch (IOException e) {
            ErrorHandler.handleException(e);
            return Optional.empty();
        }
        this.collections.add(col);
        return Optional.of(col);
    }

    private synchronized void addNewCampaign(
            UUID campainUuid,
            I info,
            Integer branchId) {
        logger.debug("Adding new campaign " + getDefaultCampaignName(info));
        var img = GameGuiFactory.<T, I>get(ALL.inverseBidiMap().get(this))
                .tagImage(info, info.getTag());
        SavegameCampaign<T, I> newCampaign = new SavegameCampaign<>(
                Instant.now(),
                getDefaultCampaignName(info),
                campainUuid,
                branchId,
                info.getDate(),
                img);
        this.collections.add(newCampaign);
    }

    synchronized void createNewBranch(SavegameEntry<T,I> e) {
        if (e.getInfo() == null) {
            return;
        }

        if (e.getInfo().isBinary() && !e.getInfo().isIronman()) {
            return;
        }

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(getSavegameFile(e));
        } catch (IOException ioException) {
            ErrorHandler.handleException(
                    ioException, "Couldn't parse savegame " + getSavegameFile(e), getSavegameFile(e));
            return;
        }

        var binary = type.isBinary(bytes);
        logger.debug("Adding new branch. Rewrite savegame=" + !binary);
        if (binary) {
            int newId = new Random().nextInt(Integer.MAX_VALUE);
            logger.debug("Adding new branch with id " + newId);
            var campaignUuid = UUID.randomUUID();
            var entryUuid = UUID.randomUUID();

            try {
                Path entryPath = getSavegameDataDirectory().resolve(campaignUuid.toString()).resolve(entryUuid.toString());
                FileUtils.forceMkdir(entryPath.toFile());
                var file = entryPath.resolve(getSaveFileName());
                Files.copy(getSavegameFile(e), file);
                JsonHelper.writeObject(e.getInfo(), entryPath.resolve(getInfoFileName()));
            } catch (IOException ex) {
                ErrorHandler.handleException(ex);
                return;
            }

            addNewCampaign(campaignUuid, e.getInfo(), newId);
            addNewEntryToCampaign(campaignUuid, entryUuid, checksum(bytes), e.getInfo(), null, null);
            return;
        }

        var struc = type.determineStructure(bytes);
        var r = struc.parse(bytes);
        r.visit(new SavegameParseResult.Visitor() {
            @Override
            public void success(SavegameParseResult.Success s) {
                try {
                    var c = s.content;
                    int branchId = new Random().nextInt(Integer.MAX_VALUE);
                    logger.debug("Adding new branch with id " + branchId);
                    struc.generateNewCampaignIdHeuristic(c, branchId);
                    UUID uuid = struc.getCampaignIdHeuristic(c);
                    logger.debug("Campaign id generated from branch id is " + uuid);

                    I info = infoFactory.apply(s.combinedNode(), false);
                    var entryUuid = UUID.randomUUID();
                    Path entryPath = getSavegameDataDirectory().resolve(uuid.toString()).resolve(entryUuid.toString());
                    FileUtils.forceMkdir(entryPath.toFile());
                    var file = entryPath.resolve(getSaveFileName());
                    struc.write(file, c);
                    JsonHelper.writeObject(info, entryPath.resolve(getInfoFileName()));
                    addNewCampaign(uuid, info, branchId);
                    addNewEntryToCampaign(uuid, entryUuid, checksum(bytes), info, null, null);
                } catch (Exception ex) {
                    ErrorHandler.handleException(ex, "Couldn't create campaign branch", getSavegameFile(e));
                }
            }
        });
    }

    public synchronized void addNewEntryToCampaign(
            UUID campainUuid,
            UUID entryUuid,
            String checksum,
            I info,
            String name,
            String sourceFileChecksum) {
        SavegameEntry<T, I> e = new SavegameEntry<>(
                name != null ? name : getDefaultEntryName(info),
                entryUuid,
                checksum,
                info.getDate(),
                SavegameNotes.empty(),
                sourceFileChecksum != null ? List.of(sourceFileChecksum) : List.of());
        if (this.getSavegameCollection(campainUuid).isEmpty()) {
            addNewCampaign(campainUuid, info, null);
        }

        SavegameCollection<T, I> c = this.getSavegameCollection(campainUuid).get();
        logger.debug("Adding new entry " + e.getName());
        c.add(e);
        c.onSavegamesChange();
    }

    public synchronized void addNewEntryToCollection(
            SavegameCollection<T, I> col,
            UUID entryUuid,
            String checksum,
            I info,
            String name,
            String sourceFileChecksum) {
        SavegameEntry<T, I> e = new SavegameEntry<>(
                name != null ? name : getDefaultEntryName(info),
                entryUuid,
                checksum,
                info.getDate(),
                SavegameNotes.empty(),
                sourceFileChecksum != null ? List.of(sourceFileChecksum) : List.of());
        logger.debug("Adding new entry " + e.getName());
        col.getSavegames().add(e);
        col.onSavegamesChange();
    }

    private String getDefaultEntryName(I info) {
        return info.getDate().toDisplayString(LanguageManager.getInstance().getActiveLanguage().getLocale());
    }

    protected abstract String getDefaultCampaignName(I info);

    public synchronized boolean contains(SavegameEntry<?, ?> e) {
        return collections.stream()
                .anyMatch(c -> c.getSavegames().stream().anyMatch(ce -> ce.getUuid().equals(e.getUuid())));
    }

    public synchronized SavegameCollection<T, I> getSavegameCollection(SavegameEntry<?, ?> e) {
        var campaign = collections.stream()
                .filter(c -> c.getSavegames().stream().anyMatch(ce -> ce.getUuid().equals(e.getUuid())))
                .findAny();
        return campaign.orElseThrow(() -> new IllegalArgumentException(
                "Could not find savegame collection for entry " + e.getName()));
    }

    synchronized void moveEntry(
            SavegameCollection<T, I> to, SavegameEntry<T, I> entry) {
        var from = getSavegameCollection(entry);
        if (from == to) {
            return;
        }

        var srcDir = getSavegameDataDirectory(entry).toFile();
        try {
            FileUtils.copyDirectory(
                    srcDir,
                    getSavegameDataDirectory().resolve(to.getUuid().toString()).resolve(entry.getUuid().toString()).toFile());
        } catch (IOException e) {
            ErrorHandler.handleException(e);
            return;
        }

        from.getSavegames().remove(entry);
        from.onSavegamesChange();
        to.getSavegames().add(entry);
        to.onSavegamesChange();

        try {
            FileUtils.deleteDirectory(srcDir);
        } catch (IOException e) {
            ErrorHandler.handleException(e);
        }
        if (from.getSavegames().size() == 0) {
            delete(from);
        }

        saveData();
    }

    synchronized void delete(SavegameCollection<T, I> c) {
        if (!this.collections.contains(c)) {
            return;
        }

        Path campaignPath = path.resolve(c.getUuid().toString());
        try {
            FileUtils.deleteDirectory(campaignPath.toFile());
        } catch (IOException e) {
            // Don't show the user this error. It sometimes happens when the file is
            // used by another process or even an antivirus program
            logger.error("Could not delete collection " + c.getName(), c);
        }

        this.collections.remove(c);

        saveData();
    }


    synchronized void delete(SavegameEntry<T, I> e) {
        SavegameCollection<T, I> c = getSavegameCollection(e);
        if (!this.collections.contains(c) || !c.getSavegames().contains(e)) {
            return;
        }

        Path campaignPath = path.resolve(c.getUuid().toString());
        try {
            FileUtils.deleteDirectory(campaignPath.resolve(e.getUuid().toString()).toFile());
        } catch (IOException ex) {
            // Don't show the user this error. It sometimes happens when the file is
            // used by another process or even an antivirus program
            logger.error("Could not delete entry " + e.getName(), ex);
        }

        c.getSavegames().remove(e);
        c.onSavegamesChange();
        if (c.getSavegames().size() == 0) {
            delete(c);
        }

        saveData();
    }

    public synchronized void loadEntry(SavegameEntry<T, I> e) {
        if (!e.canLoad()) {
            return;
        }


        var file = getSavegameFile(e);
        if (!Files.exists(file)) {
            e.fail();
            return;
        }

        if (Files.exists(getSavegameInfoFile(e))) {
            logger.debug("Info file already exists. Loading from file " + getSavegameInfoFile(e));
            try {
                e.startLoading();
                e.load(JsonHelper.readObject(infoClass, getSavegameInfoFile(e)));
                getSavegameCollection(e).onSavegameLoad(e);
                return;
            } catch (Exception ex) {
                ErrorHandler.handleException(ex);
            }
        }

        e.startLoading();

        SavegameParseResult result;
        boolean melted;
        try {
            var bytes = Files.readAllBytes(file);
            if (type.isBinary(bytes)) {
                bytes = RakalyHelper.toPlaintext(file);
                melted = true;
            } else {
                melted = false;
            }
            var struc = type.determineStructure(bytes);
            result = struc.parse(bytes);
        } catch (Exception ex) {
            ErrorHandler.handleException(ex);
            e.fail();
            return;
        }

        result.visit(new SavegameParseResult.Visitor() {
            @Override
            public void success(SavegameParseResult.Success s) {
                try {
                    logger.debug("Parsing was successful. Loading info ...");
                    I info = infoFactory.apply(s.combinedNode(), melted);
                    e.load(info);
                    getSavegameCollection(e).onSavegameLoad(e);


                    // Clear old info files
                    Files.list(getSavegameDataDirectory(e)).filter(p -> !p.equals(getSavegameFile(e))).forEach(p -> {
                        try {
                            logger.debug("Deleting old info file " + p.toString());
                            Files.delete(p);
                        } catch (IOException ioException) {
                            ErrorHandler.handleException(ioException);
                        }
                    });

                    logger.debug("Writing new info to file " + getSavegameInfoFile(e));
                    JsonHelper.writeObject(info, getSavegameInfoFile(e));
                } catch (Exception ex) {
                    ErrorHandler.handleException(ex);
                    e.fail();
                }
            }

            @Override
            public void error(SavegameParseResult.Error er) {
                e.fail();
                ErrorHandler.handleException(er.error, null, file);
            }

            @Override
            public void invalid(SavegameParseResult.Invalid iv) {
                e.fail();
                ErrorHandler.handleException(new ParseException(iv.message), null, file);
            }
        });
    }

    public synchronized Path getSavegameFile(SavegameEntry<?, ?> e) {
        return getSavegameDataDirectory(e).resolve("savegame." + type.getFileEnding());
    }

    public synchronized Path getSavegameInfoFile(SavegameEntry<T, I> e) {
        return getSavegameDataDirectory(e).resolve(getInfoFileName());
    }

    public synchronized Path getSavegameDataDirectory(SavegameEntry<?, ?> e) {
        Path campaignPath = path.resolve(getSavegameCollection(e).getUuid().toString());
        return campaignPath.resolve(e.getUuid().toString());
    }

    public synchronized Optional<SavegameCollection<T, I>> getSavegameCollection(UUID uuid) {
        for (SavegameCollection<T, I> c : collections) {
            if (c.getUuid().equals(uuid)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    public synchronized String getCollectionFileName(SavegameCollection<?, ?> c) {
        var name = c.getName();
        return SavegameContext.getForCollection(c).getInstallType().getCompatibleSavegameName(name);
    }

    public synchronized String getEntryFileName(SavegameEntry<?, ?> e) {
        var name = e.getName();
        return SavegameContext.getForSavegame(e).getInstallType().getCompatibleSavegameName(name);
    }

    public synchronized String getCompatibleName(SavegameEntry<?, ?> e, boolean includeEntryName) {
        var name = getSavegameCollection(e).getName() + (includeEntryName ?
                " (" + e.getName() + ")." : ".") + type.getFileEnding();
        return SavegameContext.getForSavegame(e).getInstallType().getCompatibleSavegameName(name);
    }

    public synchronized void copySavegameTo(SavegameEntry<T, I> e, Path destPath) throws IOException {
        Path srcPath = getSavegameFile(e);

        FileUtils.forceMkdirParent(destPath.toFile());
        FileUtils.copyFile(srcPath.toFile(), destPath.toFile(), false);
        destPath.toFile().setLastModified(Instant.now().toEpochMilli());
    }

    protected Optional<SavegameParseResult> importSavegame(
            Path file,
            String name,
            boolean checkDuplicate,
            String sourceFileChecksum,
            Long branchId) {
        var status = importSavegameData(file, name, checkDuplicate, sourceFileChecksum, branchId);
        saveData();
        return status;
    }

    private String getSaveFileName() {
        return "savegame." + type.getFileEnding();
    }

    private String getInfoFileName() {
        String cs = IntegrityManager.getInstance().getChecksum(ALL.inverseBidiMap().get(this));
        return "info_" + cs + ".json";
    }

    public synchronized void invalidateSavegameInfo(SavegameEntry<T, I> e) {
        if (Files.exists(getSavegameInfoFile(e))) {
            logger.debug("Invalidating " + getSavegameInfoFile(e));
            try {
                Files.delete(getSavegameInfoFile(e));
            } catch (Exception ex) {
                ErrorHandler.handleException(ex);
            }
        }
    }

    void reloadSavegame(SavegameEntry<T, I> e) {
        logger.debug("Reloading savegame");
        e.unload();

        invalidateSavegameInfo(e);

        loadEntry(e);
    }

    private String checksum(byte[] content) {
        MessageDigest d = null;
        try {
            d = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 missing!");
        }
        d.update(content);
        StringBuilder c = new StringBuilder();
        ByteBuffer b = ByteBuffer.wrap(d.digest());
        for (int i = 0; i < 16; i++) {
            var hex = String.format("%02x", b.get());
            c.append(hex);
        }
        return c.toString();
    }

    private Optional<SavegameParseResult> importSavegameData(
            Path file,
            String name,
            boolean checkDuplicate,
            String sourceFileChecksum,
            Long branchId) {
        logger.debug("Parsing file " + file.toString());
        final SavegameParseResult[] result = new SavegameParseResult[1];
        String checksum;
        byte[] bytes;
        boolean melted;
        try {
            bytes = Files.readAllBytes(file);
            checksum = checksum(bytes);
            logger.debug("Checksum is " + checksum);
            if (checkDuplicate) {
                var exists = getSavegameForChecksum(checksum);
                if (exists.isPresent()) {
                    logger.debug("Entry " + exists.get().getName() + " with checksum already in storage");
                    if (sourceFileChecksum != null) {
                        exists.get().addSourceFileChecksum(sourceFileChecksum);
                    }
                    return Optional.empty();
                } else {
                    logger.debug("No entry with checksum found");
                }
            }

            byte[] data;
            if (type.isBinary(bytes)) {
                data = RakalyHelper.toPlaintext(file);
                melted = true;
            } else {
                data = bytes;
                melted = false;
            }
            var struc = type.determineStructure(data);
            result[0] = struc.parse(data);
        } catch (Exception ex) {
            return Optional.of(new SavegameParseResult.Error(ex));
        }

        final SavegameParseResult[] resultToReturn = new SavegameParseResult[1];
        result[0].visit(new SavegameParseResult.Visitor() {
            @Override
            public void success(SavegameParseResult.Success s) {
                logger.debug("Parsing was successful. Loading info ...");
                I info = null;
                try {
                    info = infoFactory.apply(s.combinedNode(), melted);
                } catch (SavegameInfoException e) {
                    resultToReturn[0] = new SavegameParseResult.Error(e);
                    return;
                }

                UUID collectionUuid;
                if (info.isIronman() && branchId != null) {
                    var campaign = getCampaignForBranchId(branchId)
                            .orElseThrow(() -> new IllegalArgumentException("Unknown branch id"));
                    collectionUuid = campaign.getUuid();
                    logger.debug("Savegame has branch id in file name");
                } else {
                    collectionUuid = info.getCampaignHeuristic();
                }
                logger.debug("Collection UUID is " + collectionUuid.toString());

                UUID saveUuid = UUID.randomUUID();
                logger.debug("Generated savegame UUID " + saveUuid.toString());

                synchronized (this) {
                    Path entryPath = getSavegameDataDirectory().resolve(collectionUuid.toString()).resolve(saveUuid.toString());
                    try {
                        FileUtils.forceMkdir(entryPath.toFile());
                        var file = entryPath.resolve(getSaveFileName());
                        Files.write(file, bytes);
                        JsonHelper.writeObject(info, entryPath.resolve(getInfoFileName()));
                        addNewEntryToCampaign(collectionUuid, saveUuid, checksum, info, name, sourceFileChecksum);
                    } catch (Exception e) {
                        ErrorHandler.handleException(e);
                    }
                }
            }

            @Override
            public void error(SavegameParseResult.Error e) {
                logger.error("An error occured during parsing: " + e.error.getMessage());
                resultToReturn[0] = e;
            }

            @Override
            public void invalid(SavegameParseResult.Invalid iv) {
                logger.error("Savegame is invalid: " + iv.message);
                resultToReturn[0] = iv;
            }
        });
        return Optional.ofNullable(resultToReturn[0]);
    }

    public synchronized Optional<SavegameEntry<T, I>> getSavegameForChecksum(String cs) {
        return getCollections().stream().flatMap(SavegameCollection::entryStream)
                .filter(ch -> ch.getContentChecksum().equals(cs))
                .findAny();
    }

    public synchronized Optional<SavegameCollection<T, I>> getCampaignForBranchId(long id) {
        return getCollections().stream().filter(col -> {
            if (col instanceof SavegameCampaign) {
                SavegameCampaign<T,I> campaign = (SavegameCampaign<T, I>) col;
                return campaign.isBranch() && campaign.getBranchId() == id;
            }
            return false;
        }).findAny();
    }

    public String getEntryName(SavegameEntry<T, I> e) {
        String cn = getSavegameCollection(e).getName();
        String en = e.getName();
        return cn + " (" + en + ")";
    }

    public Optional<SavegameEntry<T,I>> getEntryForSourceFile(String sourceFileChecksum) {
        return getCollections().stream().flatMap(SavegameCollection::entryStream)
                .filter(ch -> ch.getSourceFileChecksums().contains(sourceFileChecksum))
                .findAny();
    }

    public boolean hasImportedSourceFile(String sourceFileChecksum) {
        return getCollections().stream().flatMap(SavegameCollection::entryStream)
                .anyMatch(ch -> ch.getSourceFileChecksums().contains(sourceFileChecksum));
    }

    public Path getSavegameDataDirectory() {
        return path;
    }

    public Path getDataFile() {
        return getSavegameDataDirectory().resolve("campaigns.json");
    }

    public ObservableSet<SavegameCollection<T, I>> getCollections() {
        return collections;
    }

    public String getName() {
        return name;
    }

    public SavegameType getType() {
        return type;
    }

    public FailableBiFunction<Node, Boolean, I, SavegameInfoException> getInfoFactory() {
        return infoFactory;
    }
}
