package cloudos.appstore;

import cloudos.appstore.model.*;
import cloudos.appstore.model.support.ApiToken;
import cloudos.appstore.model.support.AppStoreAccountRegistration;
import cloudos.appstore.test.AppStoreTestUtil;
import org.cobbzilla.wizard.client.NotFoundException;
import org.junit.Test;

import static org.junit.Assert.*;

public class AppStoreCrudIT extends AppStoreITBase {

    public static final String DOC_TARGET = "account registration and app publishing";

    @Test
    public void testAppCrud () throws Exception {
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
        assertEquals(registration.getName(), publisher.getName());

        apiDocs.addNote("define a cloud app");
        final CloudApp app = AppStoreTestUtil.newCloudApp(appStoreClient, publisher.getUuid());
        assertEquals(app.getName(), app.getName());

        apiDocs.addNote("lookup the app we just defined");
        final CloudApp foundApp = appStoreClient.findApp(app.getUuid());
        assertNotNull(foundApp);
        assertEquals(app.getUuid(), foundApp.getUuid());

        apiDocs.addNote("define a version of the app");
        final CloudAppVersion version = AppStoreTestUtil.newCloudAppVersion(appStoreClient, foundApp);

        apiDocs.addNote("request to publish the app");
        version.setAppStatus(CloudAppStatus.PENDING);
        final CloudAppVersion updatedVersion = appStoreClient.updateAppVersion(version);
        assertEquals(CloudAppStatus.PENDING, updatedVersion.getAppStatus());

        apiDocs.addNote("admin approves the app");
        appStoreClient.pushToken(adminToken);
        updatedVersion.setAppStatus(CloudAppStatus.PUBLISHED);
        final CloudAppVersion adminEdited = appStoreClient.updateAppVersion(updatedVersion);
        assertEquals(CloudAppStatus.PUBLISHED, adminEdited.getAppStatus());

        apiDocs.addNote("verify that the admin is listed as the author");
        assertEquals(admin.getUuid(), appStoreClient.findAppVersion(adminEdited.getUuid()).getAuthor());
        appStoreClient.popToken();

        apiDocs.addNote("delete the account");
        appStoreClient.deleteAccount();

        appStoreClient.pushToken(adminToken);

        try {
            apiDocs.addNote("try to lookup the deleted account, should fail");
            AppStoreAccount wtf = appStoreClient.findAccount(accountUuid);
            fail("expected 404 response");
        } catch (NotFoundException expected) { /* noop */ }

        try {
            apiDocs.addNote("try to lookup the deleted publisher, should fail");
            appStoreClient.findPublisher(accountUuid);
            fail("expected 404 response");
        } catch (NotFoundException expected) { /* noop */ }

        try {
            apiDocs.addNote("try to lookup the app, should fail");
            appStoreClient.findApp(app.getUuid());
            fail("expected 404 response");
        } catch (NotFoundException expected) { /* noop */ }

        try {
            apiDocs.addNote("try to lookup the version, should fail");
            appStoreClient.findAppVersion(adminEdited.getUuid());
            fail("expected 404 response");
        } catch (NotFoundException expected) { /* noop */ }

        appStoreClient.popToken();
    }


}
