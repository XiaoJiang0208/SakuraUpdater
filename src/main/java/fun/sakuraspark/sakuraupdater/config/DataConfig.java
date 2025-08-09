package fun.sakuraspark.sakuraupdater.config;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.ibm.icu.impl.Pair;
import com.mojang.logging.LogUtils;

import fun.sakuraspark.sakuraupdater.SakuraUpdater;

@Mod.EventBusSubscriber(value = Dist.DEDICATED_SERVER, modid = SakuraUpdater.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class DataConfig {
    public static class Data {
        public String version;
        public String time;
        public String description;
        public List<PathData> paths; // 文件列表
    }
    public static class PathData {
        public String model;
        public String targetPath; // 目标路径
        public List<FileData> files; // 文件列表
    }
    public static class FileData {
        public String sourcePath; // 相对路径
        public String targetPath; // 目标路径
        public String md5; // 文件MD5
    }
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> DATA = BUILDER
            .comment("The last update time of the server, used for synchronization. Don't change this unless you know what you're doing.")
            .defineListAllowEmpty("DATA", List.of(), DataConfig::validateKeyMap);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static List<Data> datalist = new ArrayList<>();

    private static boolean validateKeyMap(final Object obj) {
        if (obj instanceof String path) {
            Gson GSON = new Gson();
            try {
                if(GSON.fromJson(path, Data.class)==null){ // 不许有空值
                    LOGGER.warn("{} format error, please check the config file", path);
                    return false;
                }
            } catch (JsonSyntaxException e) { //json格式不正确
                LOGGER.warn("{} format error, please check the config file", path, e);
                return false;
            }
            return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        datalist = DATA.get().stream()
                .map(data -> new Gson().fromJson(data, Data.class))
                .collect(Collectors.toList());
        LOGGER.debug("DataConfig loaded: {}", datalist);
    }

    public static boolean addData(String version, String time, String description, List<PathData> files) {
        try {
            for (Data data : datalist) {
                if (data.version.equals(version)) {
                    LOGGER.warn("Data with version {} already exists, not adding again.", version);
                    return false; // 如果版本号已存在，返回false
                }
            }
            Data data = new Data();
            data.version = version;
            data.time = time;
            data.description = description;
            data.paths = files != null ? files : new ArrayList<>(); // 如果files为null，初始化为空列表
            datalist.add(0, data); // 添加到列表的开头
            DATA.set(datalist.stream().map(d -> new Gson().toJson(d)).toList());
            SPEC.save();
            
            LOGGER.debug("Added new data: {}", data);
            return true; // 如果转换成功，返回true
        } catch (Exception e) {
            LOGGER.error("Error converting data to JSON: {}", e.getMessage());
            return false; // 如果转换失败，返回false
        }
    }

    public static boolean editData(String version, String time, String description, List<PathData> files) {
        for (Data data : datalist) {
            if (data.version.equals(version)) {
                data.time = time;
                data.description = description;
                data.paths = files != null ? files : data.paths; // 如果files为null，使用原始文件列表
                DATA.set(datalist.stream().map(d -> new Gson().toJson(d)).toList());
                SPEC.save();
                LOGGER.debug("Edited data: {}", data);
                return true; // 如果找到并编辑成功，返回true
            }
        }
        LOGGER.warn("Data with version {} not found, cannot edit.", version);
        return false; // 如果未找到数据，返回false
    }

    public static void clearData() {
        datalist.clear();
        DATA.set(List.of());
        SPEC.save();
        LOGGER.debug("Cleared all data.");
    }

    public static void removeData(String version) {
        datalist.removeIf(data -> data.version.equals(version)); // 根据版本号删除数据
        DATA.set(datalist.stream().map(d -> new Gson().toJson(d)).toList());
        SPEC.save();
        LOGGER.debug("Removed data with version: {}", version);
    }
    public static void removeLastData() {
        if (!datalist.isEmpty()) {
            Data removedData = datalist.remove(0); // 删除列表中的第一个元素
            DATA.set(datalist.stream().map(d -> new Gson().toJson(d)).toList());
            SPEC.save();
            LOGGER.debug("Removed last data: {}", removedData);
        } else {
            LOGGER.warn("No data to remove.");
        }
    }

    public static Data getDataByVersion(String version) {
        for (Data data : datalist) {
            if (data.version.equals(version)) {
                return data; // 返回匹配的Data对象
            }
        }
        LOGGER.warn("Data with version {} not found.", version);
        return null; // 如果未找到，返回null
    }

    public static Data getLastData() {
        if (datalist.isEmpty()) {
            LOGGER.warn("No data available. you need to commit your first update.");
            return null; // 如果列表为空，返回null
        }
        return datalist.get(0); // 返回列表中的第一个元素
    }

    public static String getLastVersion() {
        Data lastData = getLastData();
        if (lastData != null) {
            return lastData.version; // 返回最后一条数据的版本号
        }
        return null; // 如果没有数据，返回null
    }

    public static String getLastTime() {
        Data lastData = getLastData();
        if (lastData != null) {
            return lastData.time; // 返回最后一条数据的时间
        }
        return null; // 如果没有数据，返回null
    }

    public static String getLastDescription() {
        Data lastData = getLastData();
        if (lastData != null) {
            return lastData.description; // 返回最后一条数据的描述
        }
        return null; // 如果没有数据，返回null
    }
}
