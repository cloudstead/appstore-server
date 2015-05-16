package cloudos.appstore.server;

import cloudos.appstore.dao.AppStoreAccountDAO;
import cloudos.appstore.dao.CloudAppDAO;
import cloudos.appstore.dao.CloudAppVersionDAO;
import cloudos.appstore.dao.PublishedAppDAO;
import cloudos.appstore.model.AppStoreAccount;
import cloudos.appstore.model.CloudApp;
import cloudos.appstore.model.CloudAppVersion;
import cloudos.appstore.model.PublishedApp;
import org.apache.commons.lang3.RandomStringUtils;
import org.cobbzilla.util.json.JsonUtil;
import org.cobbzilla.wizard.model.HashedPassword;
import org.cobbzilla.wizard.server.RestServer;
import org.cobbzilla.wizard.server.RestServerLifecycleListenerBase;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.io.FileUtil.chopSuffix;
import static org.cobbzilla.util.io.StreamUtil.loadResourceAsString;
import static org.cobbzilla.util.json.JsonUtil.fromJson;

public class AppStoreInitializer extends RestServerLifecycleListenerBase<AppStoreApiConfiguration> {

    private AppStoreAccount defaultAccount;

    @Override public void onStart(RestServer server) {
        final AppStoreApiServer api = (AppStoreApiServer) server;
        try {
            defaultAccount = initAccount(api);
            initApps(defaultAccount, api);
        } catch (Exception e) {
            die("onStart error: "+e, e);
        }
    }

    private AppStoreAccount initAccount(AppStoreApiServer server) throws Exception {
        final AppStoreAccount account = fromJson(loadResourceAsString("default-account.json"), AppStoreAccount.class);
        final AppStoreAccountDAO accountDAO = server.getBean(AppStoreAccountDAO.class);
        final AppStoreAccount found = accountDAO.findByName(account.getName());
        if (found != null) return found;
        account.setHashedPassword(new HashedPassword(RandomStringUtils.randomAlphanumeric(20)));
        return accountDAO.create(account);
    }

    private void initApps(AppStoreAccount defaultAccount, AppStoreApiServer server) throws Exception{

        final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(getClass().getClassLoader());
        final CloudAppDAO appDAO = server.getBean(CloudAppDAO.class);
        final PublishedAppDAO publishedAppDAO = server.getBean(PublishedAppDAO.class);
        final CloudAppVersionDAO appVersionDAO = server.getBean(CloudAppVersionDAO.class);

        for (Resource r : resolver.getResources("default-apps/*.json")) {

            final String name = chopSuffix(r.getFilename()); // remove .json to get the app name
            CloudApp app = appDAO.findByName(name);
            if (app == null) {
                app = (CloudApp) new CloudApp()
                        .setPublisher(defaultAccount.getUuid())
                        .setAuthor(defaultAccount.getUuid())
                        .setName(name);
                app = appDAO.create(app);
            }

            final CloudAppVersion appVersion = JsonUtil.fromJson(r.getInputStream(), CloudAppVersion.class);
            CloudAppVersion version = appVersionDAO.findByAppAndVersion(app.getUuid(), appVersion.getVersion());
            if (version == null) {
                appVersion.setApp(app.getUuid());
                appVersion.setAuthor(defaultAccount.getUuid());
                version = appVersionDAO.create(appVersion);
            }

            PublishedApp publishedApp = publishedAppDAO.findByApp(app.getUuid());
            if (publishedApp == null || publishedApp.getSemanticVersion().compareTo(appVersion.getSemanticVersion()) < 0) {
                publishedApp = new PublishedApp(version);
                publishedApp.setApprovedBy(defaultAccount.getUuid());
                publishedApp = publishedAppDAO.create(publishedApp);
                app.setActiveVersion(publishedApp.getVersion());
            }
        }
    }

}
