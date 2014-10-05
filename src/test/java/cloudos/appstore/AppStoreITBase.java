package cloudos.appstore;

import cloudos.appstore.client.AppStoreApiClient;
import cloudos.appstore.model.AppStoreAccount;
import cloudos.appstore.server.AppStoreApiConfiguration;
import cloudos.appstore.server.AppStoreApiServer;
import cloudos.appstore.test.AppStoreTestUser;
import cloudos.appstore.test.AppStoreTestUtil;
import lombok.Getter;
import org.cobbzilla.util.system.CommandShell;
import org.cobbzilla.wizard.server.config.factory.ConfigurationSource;
import org.cobbzilla.wizardtest.resources.ApiDocsResourceIT;
import org.junit.Before;

import java.util.List;
import java.util.Map;

public class AppStoreITBase extends ApiDocsResourceIT<AppStoreApiConfiguration, AppStoreApiServer> {

    public static final String TEST_ENV_FILE = ".appstore-server-test.env";
    @Getter protected final Map<String, String> serverEnvironment = CommandShell.loadShellExportsOrDie(TEST_ENV_FILE);

    // baseUri must match what is in appstore-config-test.yml
    protected AppStoreApiClient appStoreClient = new AppStoreApiClient("http://localhost:9980", getHttpClient());

    @Override protected List<ConfigurationSource> getConfigurations() {
        return getConfigurationSources("/conf/appstore-config-test.yml");
    }

    @Override protected Class<? extends AppStoreApiServer> getRestServerClass() { return AppStoreApiServer.class; }

    protected String adminToken;
    protected AppStoreAccount admin;

    @Before public void createAdminUser () throws Exception {
        final AppStoreTestUser adminUser = AppStoreTestUtil.createAdminUser(appStoreClient, server);
        adminToken = adminUser.getToken();
        admin = adminUser.getAccount();
    }

}
