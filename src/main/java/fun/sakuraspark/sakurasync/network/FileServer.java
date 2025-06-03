package fun.sakuraspark.sakurasync.network;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import io.netty.bootstrap.ServerBootstrap;
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
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class FileServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final int port;
    private Channel serverChannel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    // 存储所有可用文件信息
    private final Map<String, File> availableFiles = new HashMap<>();
    
    public FileServer(int port) {
        this.port = port;
    }

    /**
     * 启动文件服务器
     */
    public void start() {
        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 100)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        // 添加基于长度的解码器，处理分包问题
                        p.addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
                        p.addLast(new LengthFieldPrepender(4));
                        p.addLast(new ByteArrayDecoder());
                        p.addLast(new ByteArrayEncoder());
                        p.addLast(new FileServerHandler());
                    }
                });

            ChannelFuture f = b.bind(port).sync();
            serverChannel = f.channel();
            LOGGER.info("文件服务器已启动，监听端口: {}", port);
        } catch (InterruptedException e) {
            LOGGER.error("Failed to start file server", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 关闭文件服务器
     */
    public void shutdown() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        LOGGER.info("文件服务器已关闭");
    }

    /**
     * 文件服务器处理器
     */
    private class FileServerHandler extends SimpleChannelInboundHandler<byte[]> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, byte[] msg) throws Exception {
            // 处理接收到的消息
            String request = new String(msg);
            LOGGER.debug("get request: {}", request);
            
            // 处理不同类型的请求
            if (request.startsWith("LIST")) {
                // 发送文件列表
                sendFileList(ctx);
            } else if (request.startsWith("GET:")) {
                // 发送请求的文件
                String fileName = request.substring(4);
                sendFile(ctx, fileName);
            } else if (request.startsWith("UPLOAD:")) {
                // 准备接收上传的文件
                // 解析文件名和文件大小
                String[] parts = request.substring(7).split(":");
                if (parts.length == 2) {
                    String fileName = parts[0];
                    // 文件上传准备
                    ctx.writeAndFlush(("READY:" + fileName).getBytes());
                }
            } else if (request.startsWith("FILE:")) {
                // 接收文件内容
                String fileName = request.substring(5, request.indexOf(":DATA:"));
                byte[] fileData = msg;
                saveUploadedFile(fileName, fileData);
                ctx.writeAndFlush("OK".getBytes());
            }
        }

        /**
         * 发送文件列表
         */
        private void sendFileList(ChannelHandlerContext ctx) {
            StringBuilder sb = new StringBuilder("FILES:");
            for (String fileName : availableFiles.keySet()) {
                sb.append(fileName).append(",");
            }
            ctx.writeAndFlush(sb.toString().getBytes());
        }

        /**
         * 发送文件
         */
        private void sendFile(ChannelHandlerContext ctx, String fileName) {
            File file = availableFiles.get(fileName);
            if (file == null || !file.exists()) {
                ctx.writeAndFlush(("ERROR:File not found: " + fileName).getBytes());
                return;
            }
            
            try {
                // 读取文件内容
                byte[] fileContent = Files.readAllBytes(file.toPath());
                
                // 构建响应消息
                String header = "FILE:" + fileName + ":SIZE:" + fileContent.length + ":DATA:";
                byte[] headerBytes = header.getBytes();
                
                // 发送文件内容
                byte[] message = new byte[headerBytes.length + fileContent.length];
                System.arraycopy(headerBytes, 0, message, 0, headerBytes.length);
                System.arraycopy(fileContent, 0, message, headerBytes.length, fileContent.length);
                
                ctx.writeAndFlush(message);
                LOGGER.info("已发送文件: {}, 大小: {} 字节", fileName, fileContent.length);
            } catch (Exception e) {
                LOGGER.error("发送文件失败: {}", fileName, e);
                ctx.writeAndFlush(("ERROR:" + e.getMessage()).getBytes());
            }
        }

        /**
         * 保存上传的文件
         */
        private void saveUploadedFile(String fileName, byte[] fileData) {
            try {
                File file = new File(fileName);
                // 确保父目录存在
                if (!file.getParentFile().exists()) {
                    file.getParentFile().mkdirs();
                }
                
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(fileData);
                }
                
                // 更新可用文件列表
                availableFiles.put(fileName, file);
                LOGGER.info("已保存上传的文件: {}, 大小: {} 字节", fileName, fileData.length);
            } catch (Exception e) {
                LOGGER.error("保存上传文件失败: {}", fileName, e);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOGGER.error("处理请求时出错", cause);
            ctx.close();
        }
    }
}
