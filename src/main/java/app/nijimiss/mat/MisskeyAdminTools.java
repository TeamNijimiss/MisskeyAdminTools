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

import app.nijimiss.mat.core.database.MATSystemDataStore;
import app.nijimiss.mat.core.database.ReportsStore;
import app.nijimiss.mat.core.function.ReportWatcher;
import app.nijimiss.mat.core.function.WarningSender;
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

public class MisskeyAdminTools extends NeoModule {

    private static MisskeyAdminTools instance;
    private MATConfig config;
    private MATSystemDataStore systemDataStore;
    private ReportsStore reportsStore;
    private ApiRequestManager apiRequestManager;

    private ReportWatcher reportWatcher;

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

        try {
            systemDataStore = new MATSystemDataStore(getLauncher().getDatabaseConnector());
            systemDataStore.createTable();
            reportsStore = new ReportsStore(getLauncher().getDatabaseConnector());
            reportsStore.createTable();
        } catch (SQLException e) {
            getModuleLogger().error("Failed to create a table in the database.", e);
        }

        apiRequestManager = new ApiRequestManager(config.getAuthentication().getInstanceHostname());
        if (config.getOptions().getReportWatcher().getEnabled())
            reportWatcher = new ReportWatcher(systemDataStore,
                    reportsStore,
                    apiRequestManager,
                    new WarningSender(config.getAuthentication().getInstanceToken(),
                            apiRequestManager,
                            config.getOptions().getReportWatcher().getWarningSender().getWarningTemplate(),
                            config.getOptions().getReportWatcher().getWarningSender().getWarningItems()),
                    config.getAuthentication().getInstanceToken(),
                    config.getOptions().getReportWatcher().getSilenceRoleId(),
                    config.getOptions().getReportWatcher().getTargetReportChannel(),
                    config.getOptions().getReportWatcher().getExcludeDiscordRoles());
    }

    @Override
    public void onDisable() {
        reportWatcher.shutdown();
        apiRequestManager.shutdown();
    }

    public JDA getJDA() {
        return getLauncher().getDiscordApi().getGuildById(config.getDiscord().getTargetGuild()).getJDA();
    }

    public MATConfig getConfig() {
        return config;
    }
}
