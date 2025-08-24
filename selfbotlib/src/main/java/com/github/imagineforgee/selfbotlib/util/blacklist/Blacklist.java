package com.github.imagineforgee.selfbotlib.util.blacklist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Blacklist {
    private static final File FILE = new File("blacklist.json");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static final Set<String> blacklistedUsers = ConcurrentHashMap.newKeySet();
    private static final Set<String> blacklistedGuilds = ConcurrentHashMap.newKeySet();

    private static final reactor.core.scheduler.Scheduler ioScheduler = Schedulers.boundedElastic();

    private static final Sinks.Many<String> saveSink = Sinks.many().unicast().onBackpressureBuffer();

    static {
        load();
        startWatcher();
        watchSaveTrigger();
    }

    public static boolean isUserBlacklisted(String userId) {
        return userId != null && blacklistedUsers.contains(userId);
    }

    public static boolean isGuildBlacklisted(String guildId) {
        return guildId != null && blacklistedGuilds.contains(guildId);
    }

    public static void addUser(String userId) {
        if (userId != null && blacklistedUsers.add(userId)) {
            triggerSave();
        }
    }

    public static void removeUser(String userId) {
        if (userId != null && blacklistedUsers.remove(userId)) {
            triggerSave();
        }
    }

    public static void addGuild(String guildId) {
        if (guildId != null && blacklistedGuilds.add(guildId)) {
            triggerSave();
        }
    }

    public static void removeGuild(String guildId) {
        if (guildId != null && blacklistedGuilds.remove(guildId)) {
            triggerSave();
        }
    }

    public static Set<String> getBlacklistedUsers() {
        return Set.copyOf(blacklistedUsers);
    }

    public static Set<String> getBlacklistedGuilds() {
        return Set.copyOf(blacklistedGuilds);
    }

    // === Internals ===

    private static void triggerSave() {
        saveSink.tryEmitNext("save");
    }

    private static void watchSaveTrigger() {
        saveSink.asFlux()
                .sample(Duration.ofMillis(300))
                .flatMap(v -> Mono.fromRunnable(Blacklist::doSave).subscribeOn(ioScheduler))
                .subscribe();
    }

    private static void load() {
        if (!FILE.exists()) return;
        try (FileReader reader = new FileReader(FILE)) {
            BlacklistData data = gson.fromJson(reader, BlacklistData.class);
            blacklistedUsers.clear();
            blacklistedGuilds.clear();
            if (data != null) {
                blacklistedUsers.addAll(data.users);
                blacklistedGuilds.addAll(data.guilds);
            }
            System.out.println("✅ Loaded blacklist.json with " +
                    blacklistedUsers.size() + " users, " +
                    blacklistedGuilds.size() + " guilds.");
        } catch (IOException e) {
            System.err.println("❌ Failed to load blacklist.json: " + e.getMessage());
        }
    }

    private static void doSave() {
        try (FileWriter writer = new FileWriter(FILE)) {
            BlacklistData data = new BlacklistData();
            data.users.addAll(blacklistedUsers);
            data.guilds.addAll(blacklistedGuilds);
            gson.toJson(data, writer);
            System.out.println("💾 blacklist.json saved.");
        } catch (IOException e) {
            System.err.println("❌ Failed to save blacklist.json: " + e.getMessage());
        }
    }

    private static void startWatcher() {
        Mono.fromRunnable(() -> {
            try {
                WatchService watchService = FileSystems.getDefault().newWatchService();
                Path dir = FILE.toPath().getParent();
                if (dir == null) return;

                dir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

                while (true) {
                    WatchKey key = watchService.take();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY &&
                                event.context().toString().equals(FILE.getName())) {
                            System.out.println("🔄 Detected change in blacklist.json — reloading...");
                            load();
                        }
                    }
                    key.reset();
                }
            } catch (IOException | InterruptedException e) {
                System.err.println("⚠️ Watcher error: " + e.getMessage());
            }
        }).subscribeOn(ioScheduler).subscribe();
    }
}
