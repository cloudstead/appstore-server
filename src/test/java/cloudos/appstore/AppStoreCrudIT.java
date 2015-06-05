package cloudos.appstore;

import cloudos.appstore.model.*;
import cloudos.appstore.model.support.ApiToken;
import cloudos.appstore.model.support.AppStoreAccountRegistration;
import cloudos.appstore.test.AppStoreTestUtil;
import cloudos.appstore.test.TestApp;
import lombok.Cleanup;
import org.cobbzilla.wizard.api.NotFoundException;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

public class AppStoreCrudIT extends AppStoreITBase {

    public static final String DOC_TARGET = "account registration and app publishing";

    private static TestApp testApp;

    @BeforeClass public static void setupApp() throws Exception {
        testApp = webServer.buildAppTarball(TEST_MANIFEST, null, TEST_ICON);
    }

    @Test public void testAppCrud () throws Exception {
        apiDocs.startRecording(DOC_TARGET, "register an account and publish an app");

        final AppStoreAccountRegistration registration = AppStoreTestUtil.buildPublisherRegistration();

        apiDocs.addNote("send the registration request");
        final ApiToken token = appStoreClient.registerAccount(registration);
        assertNotNull(token);
        assertNotNull(token.getToken());

        apiDocs.addNote("lookup the account we just registered");
        final AppStoreAccount found = appStoreClient.findAccount();
        assertEquals(registration.getEmail(), found.getEmail());
        assertNull(found.getHashedPassword());
        assertNotNull(found.getUuid());
        final String accountUuid = found.getUuid();

        apiDocs.addNote("lookup the publisher associated with the account");
        final AppStorePublisher publisher = appStoreClient.findPublisher(accountUuid);
        final String pubName = publisher.getName();
        assertEquals(registration.getName(), pubName);

        apiDocs.addNote("define a cloud app");
        CloudAppVersion appVersion = AppStoreTestUtil.newCloudApp(appStoreClient, pubName, testApp.getBundleUrl(), testApp.getBundleUrlSha());
        assertEquals(testApp.getNameAndVersion(), appVersion.getApp()+"/"+appVersion.getVersion());

        apiDocs.addNote("lookup the app we just defined");
        final String appName = testApp.getManifest().getName();
        final CloudApp foundApp = appStoreClient.findApp(pubName, appName);
        assertNotNull(foundApp);

        apiDocs.addNote("request the bundle asset for this version");
        apiDocs.setBinaryResponse(".tar.gz tarball");
        @Cleanup("delete") final File bundleFile = appStoreClient.getAppBundle(pubName, appName, appVersion.getVersion());
        assertNotNull(bundleFile);
        assertTrue(bundleFile.length() > 0);

        apiDocs.addNote("request the taskbarIcon asset for this version");
        apiDocs.setBinaryResponse("PNG image");
        File taskbarIcon = appStoreClient.getAppAsset(pubName, appName, appVersion.getVersion(), "taskbarIcon");
        assertNotNull(taskbarIcon);
        assertTrue(taskbarIcon.length() > 0);

        apiDocs.addNote("request to publish the app");
        final String version = testApp.getManifest().getVersion();
        appVersion = appStoreClient.updateAppStatus(pubName, appName, version, CloudAppStatus.pending);
        assertEquals(CloudAppStatus.pending, appVersion.getStatus());

        apiDocs.addNote("admin approves the app (note the session token -- it's different because this call is made by an admin user)");
        publishApp(pubName, appName, version);

        apiDocs.addNote("verify that the admin is listed as the author");
        assertEquals(admin.getUuid(), appStoreClient.findVersion(pubName, appName, version).getApprovedBy());
        appStoreClient.popToken();

        apiDocs.addNote("request the latest bundle asset");
        apiDocs.setBinaryResponse(".tar.gz tarball");
        @Cleanup("delete") final File latestBundleFile = appStoreClient.getLatestAppBundle(pubName, appName);
        assertNotNull(latestBundleFile);
        assertTrue(bundleFile.length() > 0);

        apiDocs.addNote("request the latest taskbarIcon asset");
        apiDocs.setBinaryResponse("PNG image");
        taskbarIcon = appStoreClient.getLatestAsset(pubName, appName, "taskbarIcon");
        assertNotNull(taskbarIcon);
        assertTrue(taskbarIcon.length() > 0);

        apiDocs.addNote("delete the version");
        appStoreClient.deleteVersion(pubName, appName, version);
        try {
            apiDocs.addNote("try to lookup the version, should fail");
            appStoreClient.findVersion( pubName, appName, version);
            fail("expected 404 response");
        } catch (NotFoundException expected) { /* noop */ }


        apiDocs.addNote("delete the app");
        appStoreClient.deleteApp(pubName, appName);
        try {
            apiDocs.addNote("try to lookup the app, should fail");
            appStoreClient.findApp(pubName, appName);
            fail("expected 404 response");
        } catch (NotFoundException expected) { /* noop */ }

        apiDocs.addNote("delete the account");
        appStoreClient.deleteAccount();

        appStoreClient.pushToken(adminToken);

        try {
            apiDocs.addNote("as admin, try to lookup the deleted account, should fail");
            appStoreClient.findAccount(accountUuid);
            fail("expected 404 response");
        } catch (NotFoundException expected) { /* noop */ }

        try {
            apiDocs.addNote("as admin, try to lookup the deleted publisher, should fail");
            appStoreClient.findPublisher(accountUuid);
            fail("expected 404 response");
        } catch (NotFoundException expected) { /* noop */ }

        appStoreClient.popToken();
    }

}
