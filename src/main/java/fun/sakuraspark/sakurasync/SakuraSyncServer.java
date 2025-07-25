package fun.sakuraspark.sakurasync;

import org.slf4j.Logger;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import com.mojang.logging.LogUtils;

import fun.sakuraspark.sakurasync.config.DataConfig;
import fun.sakuraspark.sakurasync.config.ServerConfig;
import fun.sakuraspark.sakurasync.config.DataConfig.FileData;
import fun.sakuraspark.sakurasync.network.FileServer;
import fun.sakuraspark.sakurasync.utils.FileUtils;
import fun.sakuraspark.sakurasync.utils.MD5;
import static fun.sakuraspark.sakurasync.utils.CommandUtils.*;

import net.minecraft.commands.CommandSourceStack;
import static net.minecraft.commands.Commands.*;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

//@Mod.EventBusSubscriber(value = Dist.DEDICATED_SERVER, modid = SakuraSync.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SakuraSyncServer {

    private static final Logger LOGGER = LogUtils.getLogger();

    private FileServer file_server;

    public static SakuraSyncServer INSTANCE;

    SakuraSyncServer() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ServerConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, DataConfig.SPEC);
        INSTANCE = this;
        // fileServer = new FileServer(ConfigServer.SERVER_PORT.get(),
        // ConfigServer.SYNC_DIR.get());
    }

    public static SakuraSyncServer getInstance() {
        return INSTANCE;
    }

    private List<FileData> getFileDataList() {
        List<FileData> fileDataList = new ArrayList<>();
        for (String syncDir : ServerConfig.getSyncDirs()) {
            String[] parts = syncDir.split(":");
            if (parts.length < 2) {
                LOGGER.warn("Invalid sync directory format: {}", syncDir);
                continue;
            }
            String sourcePath = parts[0].trim();
            String model = parts[1].trim();
            String targetPath = parts.length > 2 ? parts[2].trim() : sourcePath;
            LOGGER.debug("now scan {}", new File(sourcePath).getAbsolutePath());
            FileUtils.getAllFiles(new File(sourcePath)).forEach(file -> {
                FileData data = new FileData();
                // 获取相对路径
                data.path = file.toString();
                data.model = model;
                data.targetPath = targetPath + File.separator + file.getName();
                data.md5 = MD5.calculateMD5(file);
                fileDataList.add(data);
            });

        }
        return fileDataList;
    }

    public void runServer() {
        if(file_server != null) {
            if (file_server.isRunning()) {
                LOGGER.warn("SakuraSync Server is already running!");
                return;
            }
            return;
        }
        file_server = new FileServer(ServerConfig.port);
        file_server.start();
        LOGGER.info("SakuraSync Server is running!");
    }

    // Register commands for the server
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                literal("ssync")
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
                    sendSuccessMessage(context.getSource(), "SakuraSync server config reloaded!");
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
                                            "SakuraSync server data edit completed!");
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
        StringBuilder dataList = new StringBuilder("SakuraSync server data:\n");
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
                            StringBuilder dataList = new StringBuilder("SakuraSync server data:\n");

                            for (FileData file : data.files) {
                                dataList.append(file.path)
                                        .append(" - ")
                                        .append(file.model)
                                        .append(" - ")
                                        .append(file.targetPath)
                                        .append(" - ")
                                        .append(file.md5)
                                        .append("\n");
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
                                    "SakuraSync server data deleted!");
                            return 1;
                        }));
    }

    // ----Create the /ssync data clear command----
    private LiteralArgumentBuilder<CommandSourceStack> createDataClearCommand() {
        return literal("clear")
                .executes(context -> {
                    DataConfig.clearData();
                    sendSuccessMessage(context.getSource(), "SakuraSync server data cleared!");
                    return 1;
                });
    }

    // ----Create the /ssync commit command----
    private LiteralArgumentBuilder<CommandSourceStack> createCommitCommand() {
        return literal("commit")
                .requires(source -> source.hasPermission(2))
                .then(argument("version", string())
                        .suggests(VERSION_SUGGESTIONS)
                        .then(argument("description", string())
                                .executes(context -> {
                                    String version = getString(context, "version");
                                    String description = getString(context, "description");
                                    String timestamp = java.time.LocalDateTime.now()
                                            .format(DateTimeFormatter
                                                    .ofPattern("yyyy-MM-dd_HH:mm:ss"));
                                    List<FileData> dd;
                                    try {
                                        dd = getFileDataList();
                                    } catch (Exception e) {
                                        sendFailureMessage(context.getSource(),
                                                "Failed to get file data: " + e.getMessage());
                                        return 0; // 如果获取文件数据失败，返回0
                                    }

                                    if (!DataConfig.addData(version, timestamp, description, dd)) {
                                        sendFailureMessage(context.getSource(),
                                                "Failed to add commit: Version already exists or invalid data.");
                                        return 0; // 如果添加失败，返回0
                                    }
                                    sendSuccessMessage(context.getSource(),
                                            "SakuraSync server commit added!");
                                    return 1;
                                })));
    }

}
