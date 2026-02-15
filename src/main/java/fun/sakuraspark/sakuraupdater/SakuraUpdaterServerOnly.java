package fun.sakuraspark.sakuraupdater;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fun.sakuraspark.sakuraupdater.config.DataConfig;
import fun.sakuraspark.sakuraupdater.config.StandaloneServerConfig;
import fun.sakuraspark.sakuraupdater.network.FileServer;
import fun.sakuraspark.sakuraupdater.utils.ServerCommandsHelper;
import net.minecraftforge.common.ForgeConfig.Server;

/**
 * 独立模式入口 - 脱离 Forge/Minecraft 运行
 * 使用 YAML 配置替代 ForgeConfigSpec
 */
public class SakuraUpdaterServerOnly {

    public static class CommandDispatcher {
        private String command;
        private List<CommandDispatcher> arge_list = new ArrayList<>();
        private Consumer<String> command_handler;

        /**
         * 创建一个新的命令分发器
         * 
         * @param command 命令中需要消费的头部命令，例如 "data list" 中的 "data"
         */
        CommandDispatcher(String command) {
            this.command = command;
        }

        /**
         * 添加一个子命令分发器，例如 "data list" 中的 "list" 可以作为 "data" 的子命令
         * 
         * @param handler 子命令分发器
         * @return 当前命令分发器，方便链式调用
         */
        public CommandDispatcher then(CommandDispatcher handler) {
            this.arge_list.add(handler);
            return this;
        }

        /**
         * 设置命令处理器，当命令被正确解析时调用
         * 
         * @param handler 处理器，接受完整的命令字符串作为参数
         * @return 当前命令分发器，方便链式调用
         */
        public CommandDispatcher execute(Consumer<String> handler) {
            this.command_handler = handler;
            return this;
        }

        /**
         * 分发命令，根据输入的命令字符串解析并调用相应的处理器
         * 链式传递下去的是命令字符串中去掉当前命令头部的部分，例如 "sakuraupdater data list" 传递给 "data" 的是 "list"
         * 
         * @param command 输入的完整命令字符串
         */
        public void dispatch(String command) {
            // 这里可以实现一个简单的命令解析器，根据输入的命令调用 ServerCommandsHelper 中的方法
            // 例如，输入 "data list" 可以调用 ServerCommandsHelper.buildDataListString()
            String[] parts = command.split(" ", 2);
            if (parts[0].equals(this.command)) {
                if (this.command_handler != null) {
                    this.command_handler.accept(command);
                }
                if (parts.length > 1) {
                    for (CommandDispatcher dispatcher : arge_list) {
                        dispatcher.dispatch(parts[1]);
                    }
                }
            }
        }

    }

    private static final Logger LOGGER = LoggerFactory.getLogger(SakuraUpdaterServerOnly.class);

    public static void main(String[] args) {
        LOGGER.info("SakuraUpdater Standalone Server is starting...");

        // 1. 加载 YAML 配置
        StandaloneServerConfig.initialize();

        // 2. 连接数据库
        if (!DataConfig.connectToDatabase("sakuraupdater-database.db")) {
            LOGGER.error("Failed to connect to SakuraUpdater database!");
            return;
        }

        // 3. 启动文件服务器
        int port = StandaloneServerConfig.getPort();
        LOGGER.info("Starting file server on port {}...", port);
        FileServer fileServer = new FileServer(port);
        fileServer.start();

        // 4. 设置命令分发器
        CommandDispatcher rootDispatcher = new CommandDispatcher("sakuraupdater")
                .then(new CommandDispatcher("data")
                        .then(new CommandDispatcher("list").execute(cmd -> {
                            ServerCommandsHelper.CommandResult result = ServerCommandsHelper.buildDataListString();
                            if (result.success) {
                                LOGGER.info(result.message);
                            } else {
                                LOGGER.warn(result.message);
                            }
                        }))
                        .then(new CommandDispatcher("edit").execute(cmd -> {
                            // 这里可以解析 cmd 获取版本和描述等参数，然后调用 ServerCommandsHelper.editData()
                            // 例如，命令格式可以是 "sakuraupdater data edit <version> <description>"
                            String[] parts = cmd.split(" ", 2);
                            if (parts.length < 2) {
                                LOGGER.warn(
                                        "Invalid command format. Usage: sakuraupdater data edit <version> <description>");
                                return;
                            }
                            String version = parts[1];
                            String description = parts[2];
                            ServerCommandsHelper.CommandResult result = ServerCommandsHelper.editData(version,
                                    description);
                            if (result.success) {
                                LOGGER.info(result.message);
                            } else {
                                LOGGER.warn(result.message);
                            }
                        }))
                        .then(new CommandDispatcher("show").execute(cmd -> {
                            String[] parts = cmd.split(" ", 2);
                            if (parts.length < 2) {
                                LOGGER.warn(
                                        "Invalid command format. Usage: sakuraupdater data show <version>");
                                return;
                            }
                            ServerCommandsHelper.CommandResult result = ServerCommandsHelper.showData(parts[1]);
                            if (result.success) {
                                LOGGER.info(result.message);
                            } else {
                                LOGGER.warn(result.message);
                            }
                        }))
                        .then(new CommandDispatcher("delete").execute(cmd -> {
                            // 这里可以解析 cmd 获取版本参数，然后调用 ServerCommandsHelper.deleteData()
                            String[] parts = cmd.split(" ", 2);
                            if (parts.length < 2) {
                                LOGGER.warn(
                                        "Invalid command format. Usage: sakuraupdater data delete <version>");
                                return;
                            }
                            ServerCommandsHelper.CommandResult result = ServerCommandsHelper.deleteData(parts[1]);
                            if (result.success) {
                                LOGGER.info(result.message);
                            } else {
                                LOGGER.warn(result.message);
                            }
                        }))
                        .then(new CommandDispatcher("clear").execute(cmd -> {
                            ServerCommandsHelper.CommandResult result = ServerCommandsHelper.clearData();
                            if (result.success) {
                                LOGGER.info(result.message);
                            } else {
                                LOGGER.warn(result.message);
                            }
                        })))
                .then(new CommandDispatcher("commit").execute(cmd -> {
                    // description 是支持空格的，所以只分割两部分，第一部分是版本号，第二部分是描述
                    String[] parts = cmd.split(" ", 2);
                    if (parts.length < 2) {
                        LOGGER.warn(
                                "Invalid command format. Usage: sakuraupdater commit <version> <description>");
                        return;
                    }
                    String version = parts[1];
                    String description = parts[2];
                    ServerCommandsHelper.CommandResult result = ServerCommandsHelper.commitData(version,
                            description);
                    if (result.success) {
                        LOGGER.info(result.message);
                    } else {
                        LOGGER.warn(result.message);
                    }
                }));

        // 5. 监听控制台输入，分发命令
        System.out.println("""
                    ░█▀▀░█▀█░█░█░█░█░█▀▄░█▀█░█░█░█▀█░█▀▄░█▀█░▀█▀░█▀▀░█▀▄
                    ░▀▀█░█▀█░█▀▄░█░█░█▀▄░█▀█░█░█░█▀▀░█░█░█▀█░░█░░█▀▀░█▀▄
                    ░▀▀▀░▀░▀░▀░▀░▀▀▀░▀░▀░▀░▀░▀▀▀░▀░░░▀▀░░▀░▀░░▀░░▀▀▀░▀░▀
                    SakuraUpdater Server is ready.
                """);
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("> ");
                String input = scanner.nextLine();
                rootDispatcher.dispatch(input);
            }
        } catch (Exception e) {
            LOGGER.error("Error while reading console input: ", e);
        }
    }
}
