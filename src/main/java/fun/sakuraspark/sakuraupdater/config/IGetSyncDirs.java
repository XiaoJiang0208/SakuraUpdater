package fun.sakuraspark.sakuraupdater.config;

import java.util.List;

public interface IGetSyncDirs {
    static List<String> getSyncDirs() {
        if (StandaloneServerConfig.isStandalone()) {
            return StandaloneServerConfig.getSyncDirs();
        } else {
            return ServerConfig.getSyncDirs();
        }
    }
}
