package cloudos.appstore.server;

import lombok.Getter;
import lombok.Setter;

import java.io.File;

public class AppStoreConfiguration {

    public static final String[] DEFAULT_SCHEMES = { "http", "https" };

    @Getter @Setter private String[] allowedAssetSchemes = DEFAULT_SCHEMES;
    @Getter @Setter private File appRepository;


}
