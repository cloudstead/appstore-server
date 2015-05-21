package cloudos.appstore;

import cloudos.appstore.dao.AppStoreAccountDAO;
import cloudos.appstore.server.AppStoreApiConfiguration;
import cloudos.appstore.server.AppStoreInitializer;
import org.cobbzilla.wizard.server.RestServer;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class DbInit extends AppStoreITBase {

    private final AppStoreInitializer initializer = new AppStoreInitializer();

    public boolean doCreateAdmin () { return false; }

    @Override public void onStart(RestServer<AppStoreApiConfiguration> server) {
        initializer.onStart(server);
        super.onStart(server);
    }

    @Test public void initDatabase () throws Exception {
        assertNotNull(getBean(AppStoreAccountDAO.class).findByEmail("_@_"));
    }

}
