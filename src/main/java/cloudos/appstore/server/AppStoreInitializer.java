package cloudos.appstore.server;

import cloudos.appstore.dao.AppStoreAccountDAO;
import cloudos.appstore.model.AppStoreAccount;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.cobbzilla.wizard.model.HashedPassword;
import org.cobbzilla.wizard.server.RestServer;
import org.cobbzilla.wizard.server.RestServerLifecycleListenerBase;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.io.StreamUtil.loadResourceAsString;
import static org.cobbzilla.util.json.JsonUtil.fromJson;

@Slf4j
public class AppStoreInitializer extends RestServerLifecycleListenerBase<AppStoreApiConfiguration> {

    @Override public void onStart(RestServer server) {
        final AppStoreApiServer api = (AppStoreApiServer) server;
        try {
            initAccount(api);
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

}
