package fun.sakuraspark.sakuraupdater.network;

import static fun.sakuraspark.sakuraupdater.network.MsgType.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;

import fun.sakuraspark.sakuraupdater.config.DataConfig;
import fun.sakuraspark.sakuraupdater.config.DataConfig.Data;
import fun.sakuraspark.sakuraupdater.config.DataConfig.FileData;
import fun.sakuraspark.sakuraupdater.config.DataConfig.PathData;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
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
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.CharsetUtil;



public class FileServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final int port;
    private Channel serverChannel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
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
                        p.addLast(new ChunkedWriteHandler());
                        p.addLast(new FileServerHandler());
                    }
                });

            ChannelFuture f = b.bind(port).sync();
            serverChannel = f.channel();
            isRunning = true;
            LOGGER.info("File server started on port: {}", port);
        } catch (InterruptedException e) {
            shutdown();
            LOGGER.error("Failed to start file server", e);
            Thread.currentThread().interrupt();
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
    private class FileServerHandler extends SimpleChannelInboundHandler<ByteBuf> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            // 处理接收到的消息
            Byte request = msg.readByte();

            // 处理不同类型的请求
            switch (request) {
                case MSG_TYPE_GET_UPDATE_LIST:
                    sendUpdateList(ctx, msg);
                    break;
                case MSG_TYPE_GET_FILE:
                    sendFile(ctx, msg);
                    break;
                default:
                    break;
            }
            // if (msg.readByte() == MSG_TYPE_GET_UPDATE_LIST) {
            //     // 发送文件列表
            //     sendUpdateList(ctx);
            // } else if (msg.readByte() == MSG_TYPE_GET_FILE) {
            //     // 发送请求的文件
            //     sendFile(ctx, msg);
            // } else if (msg.readByte() == MSG_TYPE_UPLOAD_FILE) {
            //     // 准备接收上传的文件
            //     // 解析文件名和文件大小
            //     String[] parts = request.substring(7).split(":");
            //     if (parts.length == 2) {
            //         String fileName = parts[0];
            //         // 文件上传准备
            //         ctx.writeAndFlush(("READY:" + fileName).getBytes());
            //     }
            // } else if (request.startsWith("FILE:")) {
            //     // 接收文件内容
            //     String fileName = request.substring(5, request.indexOf(":DATA:"));
            //     byte[] fileData = msg;
            //     saveUploadedFile(fileName, fileData);
            //     ctx.writeAndFlush("OK".getBytes());
            // }
        }

        /**
         * 发送最新数据
         */ 
        private void sendUpdateList(ChannelHandlerContext ctx, ByteBuf msg) {
            StringBuilder sb = new StringBuilder();
            String version = msg.readableBytes() > 0 ? msg.toString(CharsetUtil.UTF_8) : null; // 获取版本号
            Data data = null;
            if (version != null && !version.isEmpty()) {
                data = DataConfig.getDataByVersion(version);
            } else {
                data = DataConfig.getLastData();
            }
            if (data == null) {
                sb.append("{}");
            } else {
                sb.append(new Gson().toJson(data));
            }
            ByteBuf response = Unpooled.buffer(1);
            response.writeByte(MSG_TYPE_UPDATE_LIST);
            response.writeBytes(sb.toString().getBytes(CharsetUtil.UTF_8));
            ctx.writeAndFlush(response);
        }
        

        /**
         * 发送文件
         */
        private void sendFile(ChannelHandlerContext ctx, ByteBuf filePath) {
            String file_name = filePath.toString(CharsetUtil.UTF_8);
            boolean fileFound = false;
            for (PathData pathData : DataConfig.getLastData().paths) {
                for (FileData fileData : pathData.files) {
                    // 检查文件名是否匹配
                    if (fileData.sourcePath.equals(file_name)) {
                        fileFound = true;
                        break;
                    }
                }
                if (fileFound) {
                    break;
                }
            }
            
            if (!fileFound) {
                // 发送错误响应
                ByteBuf response = Unpooled.buffer();
                response.writeByte(MSG_TYPE_FILE);
                response.writeLong(-1); // 文件大小为-1表示错误
                response.writeInt("File not found in list".getBytes(CharsetUtil.UTF_8).length);
                response.writeBytes("File not found in list".getBytes(CharsetUtil.UTF_8));
                ctx.writeAndFlush(response);
                return;
            }

            File file = new File(file_name);

            if (file == null || !file.exists()) {
                // 发送错误响应
                ByteBuf response = Unpooled.buffer();
                response.writeByte(MSG_TYPE_FILE);
                response.writeLong(-1); // 文件大小为-1表示错误
                response.writeInt("File not found in server".getBytes(CharsetUtil.UTF_8).length);
                response.writeBytes("File not found in server".getBytes(CharsetUtil.UTF_8));
                ctx.writeAndFlush(response);
                LOGGER.warn("This file in list but not found in local: {}", file_name);
                return;
            }
            
            try {
                RandomAccessFile raf = new RandomAccessFile(file, "r");
                long file_length = raf.length();

                // 1. 发送消息头 (类型, 文件名长度, 文件名, 文件内容长度)
                ByteBuf header = Unpooled.buffer();
                header.writeByte(MSG_TYPE_FILE);
                header.writeLong(file_length);
                header.writeInt(file.getName().getBytes(CharsetUtil.UTF_8).length);
                header.writeBytes(file.getName().getBytes(CharsetUtil.UTF_8));
                ctx.write(header); // 使用 write 而不是 writeAndFlush
                
                // 2. 发送文件内容
                // 使用 ChunkedFile 进行分块传输
                ChannelFuture sendFileFuture = ctx.writeAndFlush(new ChunkedFile(raf, 0, file_length, 8192));
                
                sendFileFuture.addListener((ChannelFutureListener) future -> {
                    try {
                        if (future.isSuccess()) {
                            LOGGER.debug("Send file success: {}, size: {} byte", file_name, file_length);
                        } else {
                            LOGGER.error("Send file failed: {}", file_name, future.cause());
                        }
                    } finally {
                        // ChunkedFile 会自动关闭 RandomAccessFile，但为了安全起见还是手动关闭
                        try {
                            if (raf.getChannel().isOpen()) {
                                raf.close();
                            }
                        } catch (Exception e) {
                            LOGGER.debug("Close file handle failed", e);
                        }
                    }
                });
            } catch (Exception e) {
                LOGGER.error("Send file failed: {}", file_name, e);
                // 发送错误响应
                ByteBuf response = Unpooled.buffer();
                response.writeByte(MSG_TYPE_FILE);
                response.writeLong(-1); // 文件大小为-1表示错误
                response.writeInt(file_name.getBytes(CharsetUtil.UTF_8).length);
                response.writeBytes(file_name.getBytes(CharsetUtil.UTF_8));
                ctx.writeAndFlush(response);
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
