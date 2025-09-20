package fun.sakuraspark.sakuraupdater;

import org.slf4j.Logger;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static fun.sakuraspark.sakuraupdater.utils.CommandUtils.*;

import com.mojang.logging.LogUtils;

import fun.sakuraspark.sakuraupdater.config.DataConfig;
import fun.sakuraspark.sakuraupdater.config.ServerConfig;
import fun.sakuraspark.sakuraupdater.config.DataConfig.FileData;
import fun.sakuraspark.sakuraupdater.config.DataConfig.PathData;
import fun.sakuraspark.sakuraupdater.network.FileServer;
import fun.sakuraspark.sakuraupdater.utils.FileUtils;
import fun.sakuraspark.sakuraupdater.utils.MD5;
import net.minecraft.commands.CommandSourceStack;
import static net.minecraft.commands.Commands.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.javafmlmod.FMLModContainer;

//@Mod.EventBusSubscriber(value = Dist.DEDICATED_SERVER, modid = SakuraUpdater.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SakuraUpdaterServer {

    private static final Logger LOGGER = LogUtils.getLogger();

    private FileServer file_server;

    public static SakuraUpdaterServer INSTANCE;

    SakuraUpdaterServer() {
        ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.COMMON, ServerConfig.SPEC);
        ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.SERVER, DataConfig.SPEC);
        INSTANCE = this;
        // fileServer = new FileServer(ConfigServer.SERVER_PORT.get(),
        // ConfigServer.SYNC_DIR.get());
    }

    public static SakuraUpdaterServer getInstance() {
        return INSTANCE;
    }

    private List<PathData> getPathDataList() {
        List<PathData> pathDataList = new ArrayList<>();
        for (String syncDir : ServerConfig.getSyncDirs()) {
            LOGGER.info("Processing sync directory: {}", syncDir);
            List<String> parts = List.of(syncDir.split(":"));
            if (parts.size() < 2) {
                LOGGER.warn("Invalid sync directory format: {}", syncDir);
                continue;
            }
            String targetPath = parts.get(0).trim();
            String model = parts.get(1).trim();
            List<String> sourcePath = parts.size() > 2 ? parts.subList(2, parts.size()) : List.of(targetPath);
            PathData data = new PathData();
            data.model = model;
            data.targetPath = targetPath;
            data.files = new ArrayList<>();
            for (String source : sourcePath) {
                LOGGER.debug("now scan {}", new File(source));
                FileUtils.getAllFiles(new File(source)).forEach(file -> {
                    FileData fileData = new FileData();
                    fileData.sourcePath = file.toString().replace(File.separator, "/");
                    fileData.targetPath = file.toString().replace(source, targetPath).replace(File.separator, "/");
                    fileData.md5 = MD5.calculateMD5(file);
                    data.files.add(fileData);

                });
            }
            pathDataList.add(data);

        }
        return pathDataList;
    }

    public void runServer() {
        if(file_server != null) {
            if (file_server.isRunning()) {
                LOGGER.warn("SakuraUpdater Server is already running!");
                return;
            }
            return;
        }
        file_server = new FileServer(ServerConfig.port);
        file_server.start();
        LOGGER.info("SakuraUpdater Server is running!");
    }

    // Register commands for the server
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                literal("sakuraupdater")
                        .then(createReloadCommand())
                        .then(createDataCommand())
                        .then(createCommitCommand()));
    }

    // 动态版本建议提供器
    private static final SuggestionProvider<CommandSourceStack> VERSION_SUGGESTIONS = (context, builder) -> {
        // 根据DataConfig.datalist动态生成版本建议
        DataConfig.datalist.forEach(data -> {
            if (data.version.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                builder.suggest(data.version);
            }
        });
        return builder.buildFuture();
    };

    // ----Create the /ssync reload command----
    private LiteralArgumentBuilder<CommandSourceStack> createReloadCommand() {
        return literal("reload")
                .then(createServerReloadCommand());
    }

    private LiteralArgumentBuilder<CommandSourceStack> createServerReloadCommand() {
        return literal("server")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    ServerConfig.onLoad(null);
                    sendSuccessMessage(context.getSource(), "SakuraUpdater server config reloaded!");
                    return 1;
                });
    }

    // ----Create the /ssync data command----
    private LiteralArgumentBuilder<CommandSourceStack> createDataCommand() {
        return literal("data")
                .requires(source -> source.hasPermission(2))
                .then(createDataEditCommand())
                .then(createDataListCommand())
                .then(createDataShowCommand())
                .then(createDataDeleteCommand())
                .then(createDataClearCommand());
    }

    // ----Create the /ssync data subcommands----
    private LiteralArgumentBuilder<CommandSourceStack> createDataEditCommand() {
        return literal("edit")
                .then(argument("version", string())
                        .suggests(VERSION_SUGGESTIONS)
                        .then(argument("description", string())
                                .executes(context -> {
                                    String version = getString(context, "version");
                                    String description = getString(context,
                                            "description");
                                    String timestamp = java.time.LocalDateTime.now()
                                            .format(DateTimeFormatter
                                                    .ofPattern("yyyy-MM-dd_HH:mm:ss"));

                                    if (!DataConfig.editData(version, timestamp, description, null)) {
                                        sendFailureMessage(context.getSource(),
                                                "Failed to add data: Version not already exists or invalid data.");
                                        return 0; // 如果编辑失败，返回0
                                    }
                                    sendSuccessMessage(context.getSource(),
                                            "SakuraUpdater server data edit completed!");
                                    return 1;
                                })));
    }

    // ----Create the /ssync data list command----
    private LiteralArgumentBuilder<CommandSourceStack> createDataListCommand() {
        return literal("list")
                .executes(context -> {
                    String dataList = buildDataListString();
                    sendSuccessMessage(context.getSource(), dataList);
                    return 1;
                });
    }

    private String buildDataListString() {
        StringBuilder dataList = new StringBuilder("SakuraUpdater server data:\n");
        DataConfig.datalist.forEach(data -> dataList.append(data.version)
                .append(" - ")
                .append(data.time)
                .append(" - ")
                .append(data.description)
                .append("\n"));
        return dataList.toString();
    }

    // ----Create the /ssync data show command----
    private LiteralArgumentBuilder<CommandSourceStack> createDataShowCommand() {
        return literal("show")
                .then(argument("version", string())
                        .suggests(VERSION_SUGGESTIONS)
                        .executes(context -> {
                            String version = getString(context, "version");
                            DataConfig.Data data = DataConfig.getDataByVersion(version);
                            if (data == null) {
                                sendFailureMessage(context.getSource(),
                                        "Data with version " + version + " not found.");
                                return 0; // 如果未找到数据，返回0
                            }
                            StringBuilder dataList = new StringBuilder("SakuraUpdater server data:\n");

                            for (PathData path : data.paths) {
                                dataList.append(path.targetPath)
                                        .append(" - ")
                                        .append(path.model)
                                        .append(":[");
                                for (FileData file : path.files) {
                                    dataList.append(" (")
                                            .append(file.targetPath)
                                            .append(", ")
                                            .append(file.sourcePath)
                                            .append(", ")
                                            .append(file.md5)
                                            .append("), ");
                                }
                                dataList.append("]\n");
                            }
                            String datastring = dataList.toString();
                            if (datastring == null) {
                                sendSuccessMessage(context.getSource(), "no files found in this version");
                                return 1; // 如果未找到数据，返回0
                            }
                            sendSuccessMessage(context.getSource(), datastring);
                            return 1;
                        }));
    }

    private LiteralArgumentBuilder<CommandSourceStack> createDataDeleteCommand() {
        return literal("delete")
                .then(argument("version", string())
                        .suggests(VERSION_SUGGESTIONS)
                        .executes(context -> {
                            String version = getString(context, "version");
                            DataConfig.removeData(version);
                            sendSuccessMessage(context.getSource(),
                                    "SakuraUpdater server data deleted!");
                            return 1;
                        }));
    }

    // ----Create the /ssync data clear command----
    private LiteralArgumentBuilder<CommandSourceStack> createDataClearCommand() {
        return literal("clear")
                .executes(context -> {
                    DataConfig.clearData();
                    sendSuccessMessage(context.getSource(), "SakuraUpdater server data cleared!");
                    return 1;
                });
    }

    // ----Create the /ssync commit command----
    private LiteralArgumentBuilder<CommandSourceStack> createCommitCommand() {
        return literal("commit")
                .requires(source -> source.hasPermission(2))
                .then(argument("version", string())
                        .suggests(VERSION_SUGGESTIONS)
                        .then(argument("description", greedyString())
                                .executes(context -> {
                                    String version = getString(context, "version");
                                    String description = getString(context, "description");
                                    String timestamp = java.time.LocalDateTime.now()
                                            .format(DateTimeFormatter
                                                    .ofPattern("yyyy-MM-dd_HH:mm:ss"));
                                    List<PathData> path_data;
                                    try {
                                        path_data = getPathDataList();



                                    } catch (Exception e) {
                                        sendFailureMessage(context.getSource(),
                                                "Failed to get file data: " + e.getMessage());
                                        return 0; // 如果获取文件数据失败，返回0
                                    }

                                    String fileContent = null;
                                    if (new File(description).exists()) {
                                        // 如果描述文件存在，读取内容并添加到提交数据中
                                        try {
                                            fileContent = Files.readString(Path.of(description));
                                        } catch (IOException e) {
                                            sendFailureMessage(context.getSource(),
                                                    "Description file is exist but failed to read description file: " + e.getMessage());
                                            return 0; // 如果读取失败，返回0
                                        }
                                    }

                                    if (!DataConfig.addData(version, timestamp, fileContent != null ? fileContent : description.replace("\\n", "\n"), path_data)) {
                                        sendFailureMessage(context.getSource(),
                                                "Failed to add commit: Version already exists or invalid data.");
                                        return 0; // 如果添加失败，返回0
                                    }
                                    sendSuccessMessage(context.getSource(),
                                            "SakuraUpdater server commit added!");
                                    return 1;
                                })));
    }

}
