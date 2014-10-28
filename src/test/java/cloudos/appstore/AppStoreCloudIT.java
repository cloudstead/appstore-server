package cloudos.appstore;

import cloudos.appstore.mock.MockCloudOsHandler;
import cloudos.appstore.model.AppStoreCloudAccount;
import cloudos.appstore.model.support.CloudAccountSessionRequest;
import cloudos.appstore.test.AppStoreSeedData;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.system.PortPicker;
import org.cobbzilla.wizard.util.RestResponse;
import org.eclipse.jetty.server.Server;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.cobbzilla.util.json.JsonUtil.toJson;
import static org.junit.Assert.assertEquals;

@Slf4j
public class AppStoreCloudIT extends AppStoreITBase {

    public static final int NUM_ACCOUNTS = 2;
    public static final int NUM_APPS = 8;
    public static final int NUM_VERSIONS = 2;

    protected AppStoreSeedData seedData;

    private Server mockCloudOs;
    private int mockCloudOsPort;
    private MockCloudOsHandler cloudOsHandler;

    @Before public void populateAppStore () throws Exception {
        seedData = new AppStoreSeedData(appStoreClient, adminToken, NUM_ACCOUNTS, NUM_APPS, NUM_VERSIONS);
    }

    @Before public void setupMockCloudOs () throws Exception {
        mockCloudOsPort = PortPicker.pick();
        mockCloudOs = new Server(mockCloudOsPort);
        cloudOsHandler = new MockCloudOsHandler();
        mockCloudOs.setHandler(cloudOsHandler);
        mockCloudOs.start();
        while (!mockCloudOs.isRunning()) {
            log.info("waiting for mock cloudos to start");
        }
    }

    @After public void tearDown () throws Exception { mockCloudOs.stop(); }

    @Test public void testCloudAccount () throws Exception {

        RestResponse response;

        // As admin, register a cloud (normally the cloudstead-server would do this when the cloudos is launched)
        setToken(adminToken);
        final AppStoreCloudAccount cloudAccount = new AppStoreCloudAccount()
                .setUri("http://127.0.0.1:"+mockCloudOsPort+"/api/verify")
                .setUcid(UUID.randomUUID().toString());
        response = doPost(ApiConstants.CLOUDS_ENDPOINT, toJson(cloudAccount));
        assertEquals(201, response.status);

        setToken(null);

        // Start a session, acting on behalf of that cloud (the appstore-server will callback to the cloudos to authenticate the request)
        CloudAccountSessionRequest request = new CloudAccountSessionRequest(cloudAccount.getUcid());
        cloudOsHandler.setSessionRequest(request);
        response = doPost(ApiConstants.CLOUDS_API_ENDPOINT + "/session", toJson(request));
        assertEquals(200, response.status);
    }

}
