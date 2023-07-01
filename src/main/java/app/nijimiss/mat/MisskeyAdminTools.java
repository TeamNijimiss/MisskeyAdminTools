/*
 * Copyright 2023 Nafu Satsuki
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.nijimiss.mat;

import app.nijimiss.mat.core.database.AccountsStore;
import app.nijimiss.mat.core.database.EmojiStore;
import app.nijimiss.mat.core.database.MATSystemDataStore;
import app.nijimiss.mat.core.database.ReportsStore;
import app.nijimiss.mat.core.function.emoji.EmojiManager;
import app.nijimiss.mat.core.function.link.DiscordMisskeyAccountLinker;
import app.nijimiss.mat.core.function.report.NewReportWatcher;
import app.nijimiss.mat.core.function.report.ReportWatcher;
import app.nijimiss.mat.core.function.role.OldDataImporter;
import app.nijimiss.mat.core.function.role.RoleSynchronizer;
import app.nijimiss.mat.core.requests.ApiRequestManager;
import net.dv8tion.jda.api.JDA;
import page.nafuchoco.neobot.api.ConfigLoader;
import page.nafuchoco.neobot.api.NeoBot;
import page.nafuchoco.neobot.api.module.NeoModule;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.Arrays;

public class MisskeyAdminTools extends NeoModule {
    private static MisskeyAdminTools instance;
    private MATConfig config;
    private MATSystemDataStore systemDataStore;
    private ReportsStore reportsStore;
    private AccountsStore accountsStore;
    private EmojiStore emojiStore;
    private ApiRequestManager apiRequestManager;

    private ReportWatcher reportWatcher;
    private NewReportWatcher newReportWatcher;
    private DiscordMisskeyAccountLinker accountLinker;
    private RoleSynchronizer roleSynchronizer;
    private EmojiManager emojiManager;

    public static MisskeyAdminTools getInstance() {
        if (instance == null)
            instance = (MisskeyAdminTools) NeoBot.getModuleManager().getModule("MisskeyAdminTools");
        return instance;
    }

    @Override
    public void onEnable() {
        instance = (MisskeyAdminTools) getLauncher().getModuleManager().getModule("MisskeyAdminTools");

        var configFile = new File(getDataFolder(), "config.yaml");
        if (!configFile.exists()) {
            try (InputStream original = getResources("config.yaml")) {
                Files.copy(original, configFile.toPath());
                getModuleLogger().info("The configuration file was not found, so a new file was created.");
            } catch (IOException e) {
                getModuleLogger().error("The correct configuration file could not be retrieved from the executable.\n" +
                        "If you have a series of problems, please contact the developer.", e);
            }
        }
        config = ConfigLoader.loadConfig(configFile, MATConfig.class);

        // Initialize the database.
        try {
            systemDataStore = new MATSystemDataStore(getLauncher().getDatabaseConnector());
            systemDataStore.createTable();
            reportsStore = new ReportsStore(getLauncher().getDatabaseConnector());
            reportsStore.createTable();
            accountsStore = new AccountsStore(getLauncher().getDatabaseConnector());
            accountsStore.createTable();
            emojiStore = new EmojiStore(getLauncher().getDatabaseConnector());
            emojiStore.createTable();
        } catch (SQLException e) {
            getModuleLogger().error("Failed to create a table in the database.", e);
        }

        // Start the function.
        apiRequestManager = new ApiRequestManager(config.getAuthentication().getInstanceHostname(),
                config.getAuthentication().getInstanceToken());
        if (config.getFunction().getReportWatcher()) {
            reportWatcher = new ReportWatcher(systemDataStore, reportsStore, apiRequestManager);
            newReportWatcher = new NewReportWatcher(systemDataStore, reportsStore, apiRequestManager);
        }
        if (config.getFunction().getAccountLinker()) {
            accountLinker = new DiscordMisskeyAccountLinker(systemDataStore, accountsStore, apiRequestManager);
            registerCommand(accountLinker);

            if (config.getFunction().getRoleSynchronizer()) {
                roleSynchronizer = new RoleSynchronizer(accountsStore, apiRequestManager);
                accountLinker.registerHandler(roleSynchronizer);
            }
        }
        if (config.getFunction().getEmojiManager()) {
            emojiManager = new EmojiManager(emojiStore, apiRequestManager);
            registerCommand(emojiManager);
        }

        var migrateFolder = new File(getDataFolder(), "migrate");
        if (!migrateFolder.exists())
            migrateFolder.mkdirs();
        OldDataImporter importer = new OldDataImporter(accountsStore);
        Arrays.stream(migrateFolder.listFiles()).forEach(importer::load);
    }

    @Override
    public void onDisable() {
        reportWatcher.shutdown();
        newReportWatcher.shutdown();
        apiRequestManager.shutdown();
    }

    public JDA getJDA() {
        return getLauncher().getDiscordApi().getShardById(0);
    }

    public MATConfig getConfig() {
        return config;
    }
}
