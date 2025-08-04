package fun.sakuraspark.sakuraupdater.network;

public class MsgType {
    
    // 协议常量
    public static final byte MSG_TYPE_GET_UPDATE_LIST = 0x01;
    public static final byte MSG_TYPE_GET_FILE = 0x02;
    public static final byte MSG_TYPE_UPLOAD_FILE = 0x03;
    public static final byte MSG_TYPE_FILE = 0x04;
    public static final byte MSG_TYPE_UPDATE_LIST = 0x11;
    public static final byte MSG_TYPE_FILE_RESPONSE = 0x12;
    public static final byte MSG_TYPE_UPLOAD_READY = 0x13;
    public static final byte MSG_TYPE_UPLOAD_OK = 0x14;
    public static final byte MSG_TYPE_ERROR = 0x15;
}
