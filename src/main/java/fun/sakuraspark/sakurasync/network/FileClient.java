package fun.sakuraspark.sakurasync.network;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;

public class FileClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final String host;
    private final int port;
    private Channel channel;
    private EventLoopGroup group;
    private FileClientHandler handler;

    public FileClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * 连接到文件服务器
     */
    public boolean connect() {
        group = new NioEventLoopGroup();
        
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
                        p.addLast(new LengthFieldPrepender(4));
                        p.addLast(new ByteArrayDecoder());
                        p.addLast(new ByteArrayEncoder());
                        p.addLast(handler);
                    }
                });
            
            ChannelFuture f = b.connect(host, port).sync();
            channel = f.channel();
            LOGGER.info("已连接到文件服务器: {}:{}", host, port);
            return true;
        } catch (Exception e) {
            LOGGER.error("连接到文件服务器失败: {}:{}", host, port, e);
            disconnect();
            return false;
        }
    }
    
    /**
     * 断开连接
     */
    public void disconnect() {
        if (channel != null) {
            channel.close();
        }
        if (group != null) {
            group.shutdownGracefully();
        }
        LOGGER.info("已断开文件服务器连接");
    }

    /**
     * 获取可用文件列表
     */
    public List<String> getFileList() {
        if (channel == null || !channel.isActive()) {
            LOGGER.error("未连接到文件服务器");
            return new ArrayList<>();
        }
        
        CompletableFuture<List<String>> future = new CompletableFuture<>();
        handler.setFileListFuture(future);
        
        channel.writeAndFlush("LIST".getBytes());
        
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.error("获取文件列表失败", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 下载文件
     */
    public boolean downloadFile(String fileName, String saveDirectory) {
        if (channel == null || !channel.isActive()) {
            LOGGER.error("未连接到文件服务器");
            return false;
        }
        
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        handler.setFileDownloadFuture(future);
        
        channel.writeAndFlush(("GET:" + fileName).getBytes());
        
        try {
            byte[] fileData = future.get(60, TimeUnit.SECONDS);
            if (fileData == null) {
                LOGGER.error("下载文件失败: {}", fileName);
                return false;
            }
            
            // 保存文件
            File saveDir = new File(saveDirectory);
            if (!saveDir.exists()) {
                saveDir.mkdirs();
            }
            
            // 处理文件路径，确保目录存在
            String localFileName = new File(fileName).getName();
            File saveFile = new File(saveDir, localFileName);
            
            Files.write(saveFile.toPath(), fileData);
            LOGGER.info("文件已保存: {}", saveFile.getAbsolutePath());
            return true;
        } catch (Exception e) {
            LOGGER.error("下载文件失败: {}", fileName, e);
            return false;
        }
    }
    
    /**
     * 上传文件
     */
    public boolean uploadFile(File file, String remotePath) {
        if (channel == null || !channel.isActive()) {
            LOGGER.error("未连接到文件服务器");
            return false;
        }
        
        try {
            // 读取文件内容
            byte[] fileContent = Files.readAllBytes(file.toPath());
            
            // 发送上传请求
            String remoteName = remotePath.isEmpty() ? file.getName() : remotePath;
            channel.writeAndFlush(("UPLOAD:" + remoteName + ":" + fileContent.length).getBytes());
            
            // 等待服务器准备好接收
            CountDownLatch latch = new CountDownLatch(1);
            handler.setUploadLatch(latch);
            
            if (!latch.await(10, TimeUnit.SECONDS)) {
                LOGGER.error("上传文件超时: {}", remoteName);
                return false;
            }
            
            // 发送文件内容
            String header = "FILE:" + remoteName + ":DATA:";
            byte[] headerBytes = header.getBytes();
            
            byte[] message = new byte[headerBytes.length + fileContent.length];
            System.arraycopy(headerBytes, 0, message, 0, headerBytes.length);
            System.arraycopy(fileContent, 0, message, headerBytes.length, fileContent.length);
            
            channel.writeAndFlush(message);
            
            // 等待上传完成确认
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            handler.setUploadFuture(future);
            
            boolean result = future.get(60, TimeUnit.SECONDS);
            if (result) {
                LOGGER.info("文件上传成功: {}", remoteName);
            } else {
                LOGGER.error("文件上传失败: {}", remoteName);
            }
            
            return result;
        } catch (Exception e) {
            LOGGER.error("上传文件失败: {}", file.getName(), e);
            return false;
        }
    }
    
    /**
     * 文件客户端处理器
     */
    private static class FileClientHandler extends SimpleChannelInboundHandler<byte[]> {
        private CompletableFuture<List<String>> fileListFuture;
        private CompletableFuture<byte[]> fileDownloadFuture;
        private CompletableFuture<Boolean> uploadFuture;
        private CountDownLatch uploadLatch;
        
        public void setFileListFuture(CompletableFuture<List<String>> future) {
            this.fileListFuture = future;
        }
        
        public void setFileDownloadFuture(CompletableFuture<byte[]> future) {
            this.fileDownloadFuture = future;
        }
        
        public void setUploadFuture(CompletableFuture<Boolean> future) {
            this.uploadFuture = future;
        }
        
        public void setUploadLatch(CountDownLatch latch) {
            this.uploadLatch = latch;
        }
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, byte[] msg) throws Exception {
            String response = new String(msg);
            
            if (response.startsWith("FILES:")) {
                // 处理文件列表
                handleFileList(response.substring(6));
            } else if (response.startsWith("FILE:")) {
                // 处理文件下载
                handleFileDownload(msg);
            } else if (response.startsWith("ERROR:")) {
                // 处理错误
                String error = response.substring(6);
                LOGGER.error("服务器错误: {}", error);
                if (fileDownloadFuture != null) {
                    fileDownloadFuture.complete(null);
                }
                if (uploadFuture != null) {
                    uploadFuture.complete(false);
                }
            } else if (response.startsWith("READY:")) {
                // 服务器准备好接收上传
                if (uploadLatch != null) {
                    uploadLatch.countDown();
                }
            } else if (response.equals("OK")) {
                // 上传成功
                if (uploadFuture != null) {
                    uploadFuture.complete(true);
                }
            }
        }
        
        /**
         * 处理文件列表响应
         */
        private void handleFileList(String fileListStr) {
            if (fileListFuture == null) return;
            
            List<String> fileList = new ArrayList<>();
            if (!fileListStr.isEmpty()) {
                String[] files = fileListStr.split(",");
                for (String file : files) {
                    if (!file.isEmpty()) {
                        fileList.add(file);
                    }
                }
            }
            
            fileListFuture.complete(fileList);
        }
        
        /**
         * 处理文件下载响应
         */
        private void handleFileDownload(byte[] response) {
            if (fileDownloadFuture == null) return;
            
            try {
                String responseStr = new String(response);
                int dataIndex = responseStr.indexOf(":DATA:");
                if (dataIndex == -1) {
                    fileDownloadFuture.complete(null);
                    return;
                }
                
                // 提取文件头信息
                String header = responseStr.substring(0, dataIndex + 6);
                
                // 提取文件数据
                int headerLength = header.getBytes().length;
                byte[] fileData = new byte[response.length - headerLength];
                System.arraycopy(response, headerLength, fileData, 0, fileData.length);
                
                fileDownloadFuture.complete(fileData);
            } catch (Exception e) {
                LOGGER.error("处理文件下载响应失败", e);
                fileDownloadFuture.complete(null);
            }
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOGGER.error("客户端异常", cause);
            ctx.close();
            
            // 完成所有未完成的future
            if (fileListFuture != null) {
                fileListFuture.complete(new ArrayList<>());
            }
            if (fileDownloadFuture != null) {
                fileDownloadFuture.complete(null);
            }
            if (uploadFuture != null) {
                uploadFuture.complete(false);
            }
            if (uploadLatch != null) {
                uploadLatch.countDown();
            }
        }
    }
}
