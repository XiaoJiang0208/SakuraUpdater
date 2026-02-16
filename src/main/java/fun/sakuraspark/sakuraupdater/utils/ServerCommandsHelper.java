package fun.sakuraspark.sakuraupdater.utils;

import fun.sakuraspark.sakuraupdater.config.DataConfig;
import fun.sakuraspark.sakuraupdater.config.IGetSyncDirs;
import fun.sakuraspark.sakuraupdater.config.DataConfig.FileData;
import fun.sakuraspark.sakuraupdater.config.DataConfig.PathData;
import fun.sakuraspark.sakuraupdater.config.ServerConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 封装服务端命令的业务逻辑，与命令框架解耦。
 * context 参数获取和消息发送不在此处处理。
 */
public class ServerCommandsHelper {
    /**
     * 命令执行结果
     */
    public static class CommandResult {
        public final boolean success;
        public final String message;

        private CommandResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static CommandResult success(String message) {
            return new CommandResult(true, message);
        }

        public static CommandResult failure(String message) {
            return new CommandResult(false, message);
        }
    }

    // ---- data edit ----
    public static CommandResult editData(String version, String description) {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss"));
        if (!DataConfig.editData(version, timestamp, description, null)) {
            return CommandResult.failure("Failed to add data: Version not already exists or invalid data.");
        }
        return CommandResult.success("SakuraUpdater server data edit completed!");
    }

    // ---- data list ----
    public static CommandResult buildDataListString() {
        StringBuilder dataList = new StringBuilder("SakuraUpdater server data:\n");
        DataConfig.getAllVersions().forEach(version -> {
            DataConfig.Data data = DataConfig.getDataByVersion(version);
            dataList.append("Version: ").append(version)
                    .append(", Time: ").append(data.time)
                    .append(", Description: ").append(data.description)
                    .append("\n");
        });
        return CommandResult.success(dataList.toString());
    }

    // ---- data show ----
    public static CommandResult showData(String version) {
        DataConfig.Data data = DataConfig.getDataByVersion(version);
        if (data == null) {
            return CommandResult.failure("Data with version " + version + " not found.");
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
        return CommandResult.success(dataList.toString());
    }

    // ---- data delete ----
    public static CommandResult deleteData(String version) {
        if (!DataConfig.removeData(version)) {
            return CommandResult.success("SakuraUpdater server data deleted!");
        } else {
            return CommandResult.failure("Failed to delete data: Version not found.");
        }
    }

    // ---- data clear ----
    public static CommandResult clearData() {
        if (!DataConfig.clearData()) {
            return CommandResult.failure("Failed to clear data.");
        }
        return CommandResult.success("SakuraUpdater server data cleared!");
    }

    // ---- commit ----
    public static CommandResult commitData(String version, String description) {
        try{
            return commitData(version, description, getPathDataList());
        } catch (Exception e) {
            return CommandResult.failure("Failed to commit data: " + e.getMessage());
        }
    }
    public static CommandResult commitData(String version, String description, List<PathData> pathData) {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss"));

        String fileContent = null;
        if (new File(description).exists()) {
            try {
                fileContent = Files.readString(Path.of(description));
            } catch (IOException e) {
                return CommandResult.failure(
                        "Description file is exist but failed to read description file: " + e.getMessage());
            }
        }

        if (!DataConfig.addData(version, timestamp,
                fileContent != null ? fileContent : description.replace("\\n", "\n"),
                pathData)) {
            return CommandResult.failure("Failed to add commit: Version already exists or invalid data.");
        }
        return CommandResult.success("SakuraUpdater server commit added!");
    }

    // ---- 扫描同步目录，生成 PathData 列表 ----
    public static List<PathData> getPathDataList() {
        List<PathData> pathDataList = new ArrayList<>();
        for (String syncDir : IGetSyncDirs.getSyncDirs()) {
            List<String> parts = List.of(syncDir.split(":"));
            if (parts.size() < 2) {
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
}
