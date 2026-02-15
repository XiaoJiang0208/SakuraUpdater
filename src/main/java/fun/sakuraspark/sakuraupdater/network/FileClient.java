package fun.sakuraspark.sakuraupdater.network;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.logging.LogUtils;

import fun.sakuraspark.sakuraupdater.config.DataConfig.Data;

public class FileClient {
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(FileClient.class);
    private final String host;
    private final int port;
    private String baseUrl;

    public FileClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.baseUrl = "http://" + host + ":" + port;
    }

    /**
     * 心跳检测
     */
    public boolean heartbeat() {
        try {
            URL url = new URL(baseUrl + "/heartbeat");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(3000);
            if (conn.getResponseCode()==200) {
                return true;   
            }
            conn.disconnect();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 获取可用文件列表
     */
    public Data getUpdateList() {
        return getUpdateList(null);
    }
    
    /**
     * 获取指定版本的更新列表
     */
    @Nullable
    public Data getUpdateList(String version) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(baseUrl + "/updateList");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json;charset=utf-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            
            // 构建请求JSON
            JsonObject requestJson = new JsonObject();
            if (version != null && !version.isEmpty()) {
                requestJson.addProperty("version", version);
            }
            
            String requestBody = new Gson().toJson(requestJson);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
            }
            
            if (conn.getResponseCode() == 200) {
                String response = readInputStream(conn.getInputStream());
                Gson gson = new Gson();
                try {
                    Data data = gson.fromJson(response, Data.class);
                    LOGGER.debug("get update list for version: {}", version);
                    return data;
                } catch (JsonSyntaxException e) {
                    LOGGER.error("Cannot parse update list JSON", e);
                    return null;
                }
            } else {
                LOGGER.error("Failed to get update list: HTTP {}", conn.getResponseCode());
                return null;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to get update list", e);
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
    
    /**
     * 下载文件
     */
    public boolean downloadFile(String fileName, String saveDirectory) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(baseUrl + "/file");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json;charset=utf-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(120000);
            
            // 构建请求JSON
            JsonObject requestJson = new JsonObject();
            requestJson.addProperty("file", fileName);
            
            String requestBody = new Gson().toJson(requestJson);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
            }
            
            if (conn.getResponseCode() == 200) {
                File saveFile = new File(saveDirectory);
                if (saveFile.getParentFile() != null && !saveFile.getParentFile().exists()) {
                    saveFile.getParentFile().mkdirs();
                }
                
                if (saveFile.exists()) {
                    saveFile.delete();
                }
                
                try (InputStream is = conn.getInputStream();
                     FileOutputStream fos = new FileOutputStream(saveFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }
                
                LOGGER.debug("Download success: {}", fileName);
                return true;
            } else {
                LOGGER.error("Download failed: HTTP {}", conn.getResponseCode());
                return false;
            }
        } catch (Exception e) {
            LOGGER.error("Download failed: {}", fileName, e);
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
    
    /**
     * 上传文件
     */
    public boolean uploadFile(String fileName, String fileSourcePath) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(baseUrl + "/upload");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json;charset=utf-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(120000);
            
            // 读取文件内容并进行Base64编码
            File sourceFile = new File(fileSourcePath);
            byte[] fileContent = new byte[(int) sourceFile.length()];
            try (FileInputStream fis = new FileInputStream(sourceFile)) {
                fis.read(fileContent);
            }
            String encodedContent = java.util.Base64.getEncoder().encodeToString(fileContent);
            
            // 构建请求JSON
            JsonObject requestJson = new JsonObject();
            requestJson.addProperty("file", fileName);
            requestJson.addProperty("content", encodedContent);
            
            String requestBody = new Gson().toJson(requestJson);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
            }
            
            if (conn.getResponseCode() == 200) {
                String response = readInputStream(conn.getInputStream());
                LOGGER.debug("File upload success: {}", fileName);
                return true;
            } else {
                LOGGER.error("File upload failed: HTTP {}", conn.getResponseCode());
                return false;
            }
        } catch (Exception e) {
            LOGGER.error("File upload failed: {}", fileName, e);
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
    
    /**
     * 读取输入流
     */
    private String readInputStream(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
            sb.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
