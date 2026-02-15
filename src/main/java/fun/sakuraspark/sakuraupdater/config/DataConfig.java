package fun.sakuraspark.sakuraupdater.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import javax.annotation.Nullable;

import org.sqlite.JDBC; // 为了让 ShadowJar 能够正确重定位，需要显式引用该类
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;

public class DataConfig {
    //TODO: 改为使用json存储
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

    private static Connection connection = null;

    /**
     * 连接到SQLite数据库
     */
    public static boolean connectToDatabase(String dburl){
        try {
            // 显式加载驱动类，确保 JDBC 驱动已注册
            // 这里的 String 会被 ShadowJar 插件忽略，导致重定位后无法找到类
            // 改用 .class 引用，ShadowJar 会自动处理重定位后的包名
            Class.forName("org.sqlite.JDBC");

            connection = DriverManager.getConnection("jdbc:sqlite:" + dburl);
            try (Statement stmt = connection.createStatement()) {
                // 当表不存在时创建表
                String sql = "CREATE TABLE IF NOT EXISTS updates (" +
                             "version TEXT PRIMARY KEY, " +
                             "time TEXT, " +
                             "description TEXT, " +
                             "data TEXT)"; // 使用 json 存储复杂对象
                stmt.execute(sql);
                stmt.close();
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * 关闭数据库连接
     */
    public static void closeDatabase() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (Exception e) {
        }
    }

    /**
     * 添加数据记录
     * @param version 版本号
     * @param time 时间
     * @param description 描述
     * @param files 文件列表
     * @return 是否添加成功
     */
    public static boolean addData(String version, String time, String description, List<PathData> files) {
        // 使用 PreparedStatement 防止注入和特殊字符错误，try-with-resources 会自动关闭它
        String sql = "INSERT INTO updates (version, time, description, data) VALUES (?, ?, ?, ?)";
        Gson gson = new Gson();
        String dataJson = gson.toJson(files);
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            
            pstmt.setString(1, version);
            pstmt.setString(2, time);
            pstmt.setString(3, description);
            pstmt.setString(4, dataJson);
            pstmt.executeUpdate();
        } catch (Exception e) {
            return false;
        }
        return true; // 如果转换成功，返回true/*  */
    }

    /**
     * 编辑数据记录
     * @param version 版本号
     * @param time 时间
     * @param description 描述
     * @param files 文件列表
     * @return 是否编辑成功
     */
    public static boolean editData(String version, String time, String description, List<PathData> files) {
        
        Gson gson = new Gson();
        String dataJson = gson.toJson(files);
        String sql = "UPDATE updates SET time = ?, description = ?, data = ? WHERE version = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, time);
            pstmt.setString(2, description);
            pstmt.setString(3, dataJson);
            pstmt.setString(4, version);
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                return false; // 如果没有找到对应的版本，返回false
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * 清空所有数据记录
     * @return 是否清空成功
     */
    public static boolean clearData() {
        try (Statement stmt = connection.createStatement()) {
            String sql = "DELETE FROM updates";
            stmt.executeUpdate(sql);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * 删除指定版本的数据记录
     * @param version 版本号
     */
    public static boolean removeData(String version) {
        String sql = "DELETE FROM updates WHERE version = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, version);
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                return false;
            } else {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 删除最新的一条数据记录
     */
    public static boolean removeLastData() {
        String sql = "DELETE FROM updates WHERE version = (SELECT version FROM updates ORDER BY time DESC LIMIT 1)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                return false;
            } else {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 根据版本号获取数据记录
     * @param version 版本号
     * @return 数据记录对象，找不到则返回null
     */
    @Nullable
    public static Data getDataByVersion(String version) {
        String sql = "SELECT version, time, description, data FROM updates WHERE version = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, version);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Data data = new Data();
                    data.version = rs.getString("version");
                    data.time = rs.getString("time");
                    data.description = rs.getString("description");
                    String dataJson = rs.getString("data");
                    Gson gson = new Gson();
                    data.paths = gson.fromJson(dataJson, new com.google.gson.reflect.TypeToken<List<PathData>>(){}.getType());
                    return data;
                } else {
                    return null;
                }
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取最新的一条数据记录
     * @return 最新的数据记录对象，找不到则返回null
     */
    @Nullable
    public static Data getLastData() {
        String sql = "SELECT version, time, description, data FROM updates ORDER BY time DESC LIMIT 1";
        try(PreparedStatement pstmt = connection.prepareStatement(sql);
            ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                Data data = new Data();
                data.version = rs.getString("version");
                data.time = rs.getString("time");
                data.description = rs.getString("description");
                String dataJson = rs.getString("data");
                Gson gson = new Gson();
                data.paths = gson.fromJson(dataJson, new com.google.gson.reflect.TypeToken<List<PathData>>(){}.getType());
                return data;
            } else {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取最新版本号
     * @return 最新的版本号字符串，找不到则返回null
     */
    @Nullable
    public static String getLastVersion() {
        String sql = "SELECT version FROM updates ORDER BY time DESC LIMIT 1";
        try(PreparedStatement pstmt = connection.prepareStatement(sql);
            ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                return rs.getString("version");
            } else {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取最新时间
     * @return 最新的时间字符串，找不到则返回null
     */
    @Nullable
    public static String getLastTime() {
        String sql = "SELECT time FROM updates ORDER BY time DESC LIMIT 1";
        try(PreparedStatement pstmt = connection.prepareStatement(sql);
            ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                return rs.getString("time");
            } else {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取最新描述
     * @return 最新的描述字符串，找不到则返回null
     */
    @Nullable
    public static String getLastDescription() {
        String sql = "SELECT description FROM updates ORDER BY time DESC LIMIT 1";
        try(PreparedStatement pstmt = connection.prepareStatement(sql);
            ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                return rs.getString("description");
            } else {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取所有版本号列表
     * @return 版本号列表
     */
    @Nullable
    public static List<String> getAllVersions() {
        String sql = "SELECT version FROM updates ORDER BY time DESC";
        List<String> versions = new java.util.ArrayList<>();
        try(PreparedStatement pstmt = connection.prepareStatement(sql);
            ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                versions.add(rs.getString("version"));
            }
        } catch (Exception e) {
            return null;
        }
        return versions;
    }
}
