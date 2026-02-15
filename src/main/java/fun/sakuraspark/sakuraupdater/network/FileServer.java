package fun.sakuraspark.sakuraupdater.network;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import fun.sakuraspark.sakuraupdater.config.DataConfig;
import fun.sakuraspark.sakuraupdater.config.DataConfig.Data;
import fun.sakuraspark.sakuraupdater.config.DataConfig.FileData;
import fun.sakuraspark.sakuraupdater.config.DataConfig.PathData;



public class FileServer {
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(FileServer.class);
    private final int port;
    private HttpServer httpServer;
    private boolean isRunning = false;

    // 存储所有可用文件信息
    private final Map<String, File> availableFiles = new HashMap<>();

    public FileServer(int port) {
        this.port = port;
    }

    /**
     * 启动文件服务器
     */
    public void start() {
        try {
            httpServer = HttpServer.create(new InetSocketAddress(port), 0);
            
            // 创建不同的处理器
            httpServer.createContext("/heartbeat", new HeartBeatHandler());
            httpServer.createContext("/updateList", new UpdateListHandler());
            httpServer.createContext("/file", new FileDownloadHandler());
            httpServer.createContext("/upload", new FileUploadHandler());
            
            // 设置线程池大小
            httpServer.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(10));
            
            httpServer.start();
            isRunning = true;
            LOGGER.info("File server started on port: {}", port);
        } catch (IOException e) {
            LOGGER.error("Failed to start file server", e);
            shutdown();
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    /**
     * 关闭文件服务器
     */
    public void shutdown() {
        isRunning = false;
        if (httpServer != null) {
            httpServer.stop(0);
        }
        LOGGER.info("File server stopped.");
    }


    /**
     * 心跳处理器
     */
    private class HeartBeatHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                String response = "OK";
                byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain;charset=utf-8");
                exchange.sendResponseHeaders(200, responseBytes.length);
                
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
                
                LOGGER.info("Heartbeat received and responded.");
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    /**
     * 更新列表处理器
     */
    private class UpdateListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String requestBody = readRequestBody(exchange);
                    JsonObject jsonRequest = JsonParser.parseString(requestBody).getAsJsonObject();
                    String version = jsonRequest.has("version") ? jsonRequest.get("version").getAsString() : null; //获取版本号
                    
                    Data data = null;
                    if (version == null || version.isEmpty()) {
                        data = DataConfig.getLastData();
                    } else {
                        data = DataConfig.getDataByVersion(version);
                    }
                    
                    String response;
                    if (data == null) {
                        response = "{}";
                    } else {
                        response = new Gson().toJson(data);
                    }
                    
                    byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json;charset=utf-8");
                    exchange.sendResponseHeaders(200, responseBytes.length);
                    
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(responseBytes);
                    }
                    
                    LOGGER.debug("Sent update list for version: {}", version);
                } catch (Exception e) {
                    LOGGER.error("Error processing update list request", e);
                    sendError(exchange, 400, "Invalid request format");
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    /**
     * 文件下载处理器
     */
    private class FileDownloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String requestBody = readRequestBody(exchange);
                    JsonObject jsonRequest = JsonParser.parseString(requestBody).getAsJsonObject();
                    String fileName = jsonRequest.has("file") ? jsonRequest.get("file").getAsString() : null;
                    
                    if (fileName == null || fileName.isEmpty()) {
                        sendError(exchange, 400, "Missing file parameter");
                        return;
                    }
                    
                    // 验证文件是否在允许列表中
                    boolean fileFound = false;
                    Data lastData = DataConfig.getLastData();
                    if (lastData != null) {
                        for (PathData pathData : lastData.paths) {
                            for (FileData fileData : pathData.files) {
                                if (fileData.sourcePath.equals(fileName)) {
                                    fileFound = true;
                                    break;
                                }
                            }
                            if (fileFound) {
                                break;
                            }
                        }
                    }
                    
                    if (!fileFound) {
                        sendError(exchange, 403, "File not found in list");
                        return;
                    }
                    
                    File file = new File(fileName);
                    if (!file.exists() || !file.isFile()) {
                        LOGGER.warn("This file in list but not found in local, please don't forget commit: {}", fileName);
                        sendError(exchange, 404, "File not found in server");
                        return;
                    }
                    
                    try {
                        long fileSize = file.length();
                        exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                        exchange.getResponseHeaders().set("Content-Length", String.valueOf(fileSize));
                        String encodedFileName = java.net.URLEncoder.encode(file.getName(), StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"");
                        exchange.sendResponseHeaders(200, fileSize);
                        
                        try (OutputStream os = exchange.getResponseBody();
                             FileInputStream fis = new FileInputStream(file)) {
                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            while ((bytesRead = fis.read(buffer)) != -1) {
                                os.write(buffer, 0, bytesRead);
                            }
                        }
                        
                        LOGGER.debug("Send file success: {}", fileName);
                    } catch (Exception e) {
                        LOGGER.error("Send file failed: {}", fileName, e);
                        if (!exchange.getResponseHeaders().containsKey("Content-Type")) {
                            sendError(exchange, 500, "Internal Server Error");
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Error processing file download request", e);
                    sendError(exchange, 400, "Invalid request format");
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    /**
     * 文件上传处理器
     */
    private class FileUploadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String requestBody = readRequestBody(exchange);
                    JsonObject jsonRequest = JsonParser.parseString(requestBody).getAsJsonObject();
                    String fileName = jsonRequest.has("file") ? jsonRequest.get("file").getAsString() : null;
                    String fileContent = jsonRequest.has("content") ? jsonRequest.get("content").getAsString() : null;
                    
                    if (fileName == null || fileName.isEmpty()) {
                        sendError(exchange, 400, "Missing file parameter");
                        return;
                    }
                    
                    if (fileContent == null) {
                        sendError(exchange, 400, "Missing content parameter");
                        return;
                    }
                    
                    saveUploadedFileFromJson(fileName, fileContent);
                    
                    JsonObject response = new JsonObject();
                    response.addProperty("status", "success");
                    response.addProperty("message", "File uploaded successfully");
                    response.addProperty("file", fileName);
                    
                    String responseStr = new Gson().toJson(response);
                    byte[] responseBytes = responseStr.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json;charset=utf-8");
                    exchange.sendResponseHeaders(200, responseBytes.length);
                    
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(responseBytes);
                    }
                    
                    LOGGER.info("File uploaded successfully: {}", fileName);
                } catch (Exception e) {
                    LOGGER.error("Failed to upload file", e);
                    sendError(exchange, 500, "Failed to upload file");
                }
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    /**
     * 发送错误响应
     */
    private void sendError(HttpExchange exchange, int code, String message) throws IOException {
        String response = message;
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain;charset=utf-8");
        exchange.sendResponseHeaders(code, responseBytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    /**
     * 保存上传的文件（从JSON base64内容）
     */
    private void saveUploadedFileFromJson(String fileName, String fileContent) {
        try {
            File file = new File(fileName);
            // 确保父目录存在
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            
            // 解析 Base64 编码的内容
            byte[] decodedContent = java.util.Base64.getDecoder().decode(fileContent);
            
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(decodedContent);
            }
            
            // 更新可用文件列表
            availableFiles.put(fileName, file);
            LOGGER.debug("已保存上传的文件: {}", fileName);
        } catch (Exception e) {
            LOGGER.error("保存上传文件失败: {}", fileName, e);
        }
    }

    /**
     * 读取请求体
     */
    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            byte[] buffer = new byte[8192];
            StringBuilder sb = new StringBuilder();
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                sb.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
            }
            return sb.toString();
        }
    }
}
