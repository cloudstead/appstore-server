package cloudos.appstore;

import cloudos.appstore.mock.MockCloudOsHandler;
import cloudos.appstore.model.AppStoreCloudAccount;
import cloudos.appstore.model.support.CloudAccountSessionRequest;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.network.PortPicker;
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

    public static final String DOC_TARGET = "CloudOs authentication";

    private Server mockCloudOs;
    private int mockCloudOsPort;
    private MockCloudOsHandler cloudOsHandler;

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

        apiDocs.startRecording(DOC_TARGET, "test register and authenticate cloud account");
        RestResponse response;

        apiDocs.addNote("As admin, register a cloud (normally the cloudstead-server would do this when the cloudos is launched");
        setToken(adminToken);
        final AppStoreCloudAccount cloudAccount = new AppStoreCloudAccount()
                .setUri("http://127.0.0.1:"+mockCloudOsPort+"/api/verify")
                .setUcid(UUID.randomUUID().toString());
        response = doPost(ApiConstants.CLOUDS_ENDPOINT, toJson(cloudAccount));
        assertEquals(201, response.status);

        setToken(null);

        apiDocs.addNote("Start a session, acting on behalf of that cloud (the appstore-server will callback to the cloudos to authenticate the request)");
        CloudAccountSessionRequest request = new CloudAccountSessionRequest(cloudAccount.getUcid());
        cloudOsHandler.setSessionRequest(request);
        response = doPost(ApiConstants.CLOUDS_API_ENDPOINT + "/session", toJson(request));
        assertEquals(200, response.status);
    }

}
