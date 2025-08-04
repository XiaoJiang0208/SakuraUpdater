package fun.sakuraspark.sakuraupdater.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

public class FileUtils {
    public static Logger LOGGER = LogUtils.getLogger();
    public static List<File> getAllFiles(File dir) {
            
        List<File> fileList = new ArrayList<>();
        if (dir == null || !dir.exists()) {
            LOGGER.warn(null == dir ? "Directory is null" : "Directory does not exist: {}", dir);
            return fileList;
        }
        if (dir.isDirectory()) { // 如果传入目录就遍历，否则直接添加文件
            // 如果是目录，获取所有文件和子目录
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        fileList.addAll(getAllFiles(file));
                    } else {
                        fileList.add(file);
                    }
                }
            }
        } else if (dir.isFile()) {
            fileList.add(dir);
        }
        return fileList;
    }
}
