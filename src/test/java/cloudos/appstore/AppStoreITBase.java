package cloudos.appstore;

import cloudos.appstore.client.AppStoreApiClient;
import cloudos.appstore.dao.AppStoreAccountDAO;
import cloudos.appstore.model.AppStoreAccount;
import cloudos.appstore.model.CloudAppStatus;
import cloudos.appstore.model.CloudAppVersion;
import cloudos.appstore.model.support.AppStoreAccountRegistration;
import cloudos.appstore.server.AppStoreApiConfiguration;
import cloudos.appstore.server.AppStoreApiServer;
import cloudos.appstore.test.AppStoreTestUtil;
import cloudos.appstore.test.AssetWebServer;
import lombok.Getter;
import org.apache.commons.io.FileUtils;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.util.system.CommandShell;
import org.cobbzilla.wizard.model.HashedPassword;
import org.cobbzilla.wizard.server.config.factory.ConfigurationSource;
import org.cobbzilla.wizardtest.resources.ApiDocsResourceIT;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class AppStoreITBase extends ApiDocsResourceIT<AppStoreApiConfiguration, AppStoreApiServer> {

    public static final String TEST_ENV_FILE = ".appstore-server-test.env";
    @Getter protected final Map<String, String> serverEnvironment = CommandShell.loadShellExportsOrDie(TEST_ENV_FILE);

    protected AppStoreApiClient appStoreClient = new AppStoreApiClient(getConnectionInfo(), getHttpClient()) {
        @Override protected String getTokenHeader() { return ApiConstants.H_TOKEN; }
        @Override public synchronized String getBaseUri() { return server.getClientUri(); }
    };
    private File appRepository;

    @Override protected String getTokenHeader() { return ApiConstants.H_TOKEN; }

    @Override protected List<ConfigurationSource> getConfigurations() {
        return getConfigurationSources("appstore-config-test.yml");
    }

    @Override protected Class<? extends AppStoreApiServer> getRestServerClass() { return AppStoreApiServer.class; }

    public static final String TEST_MANIFEST = "apps/simple-app-manifest.json";
    public static final String TEST_ICON = "apps/some-icon.png";

    protected static AssetWebServer webServer = new AssetWebServer();
    @BeforeClass public static void startTestWebserver() throws Exception { webServer.start(); }
    @AfterClass public static void stopTestWebserver() throws Exception { webServer.stop(); }

    public void publishApp(String appName, String version) throws Exception {
        appStoreClient.pushToken(adminToken);
        final CloudAppVersion appVersion = appStoreClient.updateAppStatus(appName, version, CloudAppStatus.published);
        assertEquals(CloudAppStatus.published, appVersion.getStatus());
    }

    protected String adminToken;
    protected AppStoreAccount admin;

    public boolean doCreateAdmin () { return true; }

    @Before public void createAdminUser () throws Exception {

        if (!doCreateAdmin()) return;

        final AppStoreAccountRegistration registration = AppStoreTestUtil.buildPublisherRegistration();

        adminToken = appStoreClient.registerAccount(registration).getToken();
        admin = appStoreClient.findAccount();
        admin.setAdmin(true);
        admin.setHashedPassword(new HashedPassword(registration.getPassword()));

        // crack open the application context to access the DAO directly and set the admin flag to true
        getBean(AppStoreAccountDAO.class).update(admin);

        // always start with a fresh/blank app repository
        appRepository = FileUtil.createTempDir("test-app-repository-");
        getConfiguration().getAppStore().setAppRepository(appRepository);

        appStoreClient.setToken(null);
    }

    @After public void cleanup () throws Exception {
        FileUtils.deleteQuietly(appRepository);
    }

}
