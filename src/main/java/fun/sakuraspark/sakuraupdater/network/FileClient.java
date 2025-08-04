package fun.sakuraspark.sakuraupdater.network;

import static fun.sakuraspark.sakuraupdater.network.MsgType.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.checkerframework.dataflow.qual.Pure;
import org.slf4j.Logger;
import org.stringtemplate.v4.compiler.CodeGenerator.primary_return;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.ibm.icu.impl.Pair;
import com.mojang.logging.LogUtils;

import fun.sakuraspark.sakuraupdater.config.DataConfig.Data;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
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
import io.netty.util.CharsetUtil;

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
        this.handler = new FileClientHandler(this,true);
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

    public boolean isConnected() {
        return channel != null && channel.isActive();
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
        this.handler = null;
        LOGGER.info("已断开文件服务器连接");
        
    }

    /**
     * 获取可用文件列表
     */
    @Nullable
    public Data getUpdateList() {
        if (channel == null || !channel.isActive()) {
            LOGGER.error("未连接到文件服务器");
            return null;
        }
        
        CompletableFuture<Data> future = new CompletableFuture<>();
        handler.setUpdateListFuture(future);
        
        ByteBuf buf = Unpooled.buffer(1);
        buf.writeByte(MSG_TYPE_GET_UPDATE_LIST);
        channel.writeAndFlush(buf);

        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.error("获取更新列表失败", e);
            return null;
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
        
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        handler.setFileDownloadFuture(future, saveDirectory);
        
        // 发送下载请求
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(MSG_TYPE_GET_FILE);
        buf.writeBytes(fileName.getBytes(CharsetUtil.UTF_8));
        channel.writeAndFlush(buf);

        try {
            boolean fileData = future.get(60, TimeUnit.SECONDS);
            if (!fileData) {
                LOGGER.error("下载文件失败: {}", fileName);
                return false;
            }
            return true; // 下载成功
        } catch (Exception e) {
            LOGGER.error("下载文件失败超时或失败: {}", fileName, e);
            return false;
        }
    }
    
    /**
     * 文件客户端处理器
     */
    private static class FileClientHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private CompletableFuture<Data> updateListFuture;
        private CompletableFuture<Boolean> fileDownloadFuture;
        private FileChannel fileChannel;
        private FileOutputStream fileOutputStream;
        private long receivedBytes = 0; // 已接收的字节数
        private long fileLength;
        private String saveDirectory;
        private boolean autoReconnect;
        private FileClient client;

        public FileClientHandler(FileClient client,boolean autoReconnect) {
            // 构造函数可以根据需要添加参数
            this.client = client;
            this.updateListFuture = null;
            this.autoReconnect = autoReconnect;
        }

        public void setUpdateListFuture(CompletableFuture<Data> future) {
            this.updateListFuture = future;
        }
        
        public void setFileDownloadFuture(CompletableFuture<Boolean> future, String saveDirectory) {
            this.fileDownloadFuture = future;
            this.saveDirectory = saveDirectory;
            this.fileChannel = null; // 重置文件通道
            this.fileOutputStream = null; // 重置文件输出流
        }
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            if (fileChannel != null) {
                // 如果正在下载文件，处理文件头
                handleFileContent(msg);
                return; // 直接返回，避免重复处理
            }
            if (msg.readableBytes() < 1) {
                return; // 没有数据可读
            }
            Byte response = msg.readByte();
            switch (response) {
                case MSG_TYPE_UPDATE_LIST:
                    handleUpdateList(msg);
                    break;
                case MSG_TYPE_FILE:
                    handleFileHeader(msg);
                    break;
                default:
                    break;
            }
        }
        
        /**
         * 处理文件列表响应
         */
        private void handleUpdateList(ByteBuf fileListStr) {
            if (updateListFuture == null) return;

            Gson gson = new Gson();
            String jsonStr = fileListStr.toString(CharsetUtil.UTF_8);
            try {
                Data fileList = gson.fromJson(jsonStr, Data.class);
                updateListFuture.complete(fileList);
            } catch (JsonSyntaxException e) { //json格式不正确
                LOGGER.error("{} server updatelist format error", e);
                updateListFuture.complete(null);
            }
        }

        private void handleFileHeader(ByteBuf msg) {
            if (fileDownloadFuture == null) return;

            fileLength = msg.readLong(); // 文件长度
            
            if (fileLength == -1) {
                String errorMsg = msg.readBytes(msg.readInt()).toString(CharsetUtil.UTF_8);
                LOGGER.error("server return error: {}", errorMsg);
                fileDownloadFuture.complete(false);
                return;
            }
            String fileName = msg.readBytes(msg.readInt()).toString(CharsetUtil.UTF_8); // 文件名

            fileOutputStream = null;
            try {
                File saveFile = new File(saveDirectory);
                if (saveFile.getParentFile() != null && !saveFile.getParentFile().exists()) {
                    saveFile.getParentFile().mkdirs(); // 确保目录存在
                }

                if (saveFile.exists()) {
                    saveFile.delete(); // 删除已存在的文件
                }
                fileOutputStream = new FileOutputStream(saveFile);
                fileChannel = fileOutputStream.getChannel(); // 获取文件通道
                if (msg.readableBytes() >0) {
                    handleFileContent(msg);
                }
            } catch (Exception e) {
                LOGGER.error("File download failed", e);
                fileDownloadFuture.complete(false);
                closeFileChannel();
            }
        }

        private void handleFileContent(ByteBuf msg) {
            if (fileChannel == null || fileOutputStream == null) return;
            try {
                int readableBytes = msg.readableBytes();
                ByteBuffer buffer = ByteBuffer.allocate(readableBytes);
                msg.readBytes(buffer);
                fileChannel.write(buffer); // 写入文件通道
                receivedBytes += readableBytes; // 更新已接收字节数
                if (receivedBytes >= fileLength) {
                    closeFileChannel(); // 完成下载后关闭文件通道
                    fileDownloadFuture.complete(true);
                }
            } catch (IOException e) {
                LOGGER.error("Write file content failed", e);
                closeFileChannel();
                fileChannel = null;
                fileDownloadFuture.complete(false);
            }
        }

        private void closeFileChannel() {
            if (fileChannel != null) {
                try {
                    fileChannel.close();
                } catch (IOException e) {
                    LOGGER.error("Close file channel failed", e);
                }
                fileChannel = null;
            }
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    LOGGER.error("Close file output stream failed", e);
                }
                fileOutputStream = null;
            }
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOGGER.error("Client exception", cause);
            ctx.close();
            
            // 完成所有未完成的future
            if (updateListFuture != null) {
                updateListFuture.complete(null);
            }
            if (fileDownloadFuture != null) {
                fileDownloadFuture.complete(null);
                closeFileChannel();
            }
            if(autoReconnect) {
                // 如果启用了自动重连，尝试重新连接
                LOGGER.info("Try reconnect...");
                client.connect();
            }
        }
    }
}
