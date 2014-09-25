package cloudos.appstore;

import cloudos.appstore.client.AppStoreApiClient;
import cloudos.appstore.server.AppStoreApiConfiguration;
import cloudos.appstore.server.AppStoreApiServer;
import cloudos.appstore.model.AppStoreAccount;
import cloudos.appstore.test.AppStoreTestUser;
import cloudos.appstore.test.AppStoreTestUtil;
import org.cobbzilla.wizard.server.config.factory.ConfigurationSource;
import org.cobbzilla.wizardtest.resources.ApiDocsResourceIT;
import org.junit.Before;

import java.util.List;

public class AppStoreITBase extends ApiDocsResourceIT<AppStoreApiConfiguration, AppStoreApiServer> {

    // baseUri must match what is in appstore-api-test.yml
    protected AppStoreApiClient appStoreClient = new AppStoreApiClient("http://localhost:9980", getHttpClient());

    @Override
    protected List<ConfigurationSource> getConfigurations() {
        return getConfigurationSources("/conf/appstore-api-test.yml");
    }

    @Override
    protected Class<? extends AppStoreApiServer> getRestServerClass() { return AppStoreApiServer.class; }

    protected String adminToken;
    protected AppStoreAccount admin;

    @Before
    public void createAdminUser () throws Exception {
        final AppStoreTestUser adminUser = AppStoreTestUtil.createAdminUser(appStoreClient, server);
        adminToken = adminUser.getToken();
        admin = adminUser.getAccount();
    }

}
