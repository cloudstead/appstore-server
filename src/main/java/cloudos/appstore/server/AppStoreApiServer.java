package cloudos.appstore.server;

import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.server.RestServerBase;
import org.cobbzilla.wizard.server.config.factory.ConfigurationSource;

import java.util.List;

@Slf4j
public class AppStoreApiServer extends RestServerBase<AppStoreApiConfiguration>  {

    private static final String[] API_CONFIG_YML = {"appstore-config.yml"};

    public static final AppStoreInitializer INITIALIZER = new AppStoreInitializer();

    @Override protected String getListenAddress() { return LOCALHOST; }

    public static void main(String[] args) throws Exception {
        final List<ConfigurationSource> configSources = getStreamConfigurationSources(AppStoreApiServer.class, API_CONFIG_YML);
        main(AppStoreApiServer.class, INITIALIZER, configSources);
    }

}
