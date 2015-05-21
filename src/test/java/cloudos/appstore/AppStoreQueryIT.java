package cloudos.appstore;

import cloudos.appstore.dao.CloudAppDAO;
import cloudos.appstore.dao.CloudAppVersionDAO;
import cloudos.appstore.mock.MockPublishedAppDAO;
import cloudos.appstore.model.*;
import cloudos.appstore.model.support.AppListing;
import org.cobbzilla.wizard.dao.SearchResults;
import org.junit.Before;
import org.junit.Test;

import static org.cobbzilla.wizardtest.RandomUtil.randomName;
import static org.junit.Assert.assertEquals;

public class AppStoreQueryIT extends AppStoreITBase {

    public static final String DOC_TARGET = "querying the app store";

    private static final int NUM_APPS = AppListing.DEFAULT_PAGE.getPageSize() * 3;

    private AppStorePublisher publisher;
    private AppStoreAccount admin;

    @Before
    public void populateApps() throws Exception {

        appStoreClient.pushToken(adminToken);
        admin = appStoreClient.findAccount();
        publisher = appStoreClient.findPublisher(admin.getUuid());

        final CloudAppDAO cloudAppDAO = getBean(CloudAppDAO.class);
        final CloudAppVersionDAO versionDAO = getBean(CloudAppVersionDAO.class);
        final MockPublishedAppDAO publishedAppDAO = getBean(MockPublishedAppDAO.class);

        for (int i=0; i<NUM_APPS; i++) {
            CloudApp app = cloudAppDAO.create(buildCloudApp(i));
            CloudAppVersion appVersion = versionDAO.create(buildCloudAppVersion(app));
            publishedAppDAO.addApp(buildPublishedApp(appVersion));
        }
    }

    private CloudApp buildCloudApp(int i) {
        return (CloudApp) new CloudApp()
                .setAuthor(admin.getUuid())
                .setPublisher(publisher.getUuid())
                .setName(i + "-" + randomName());
    }

    private CloudAppVersion buildCloudAppVersion(CloudApp app) {
        return new CloudAppVersion(app.getName(), "1.2.3")
                .setBundleSha(randomName())
                .setApprovedBy(admin.getUuid())
                .setStatus(CloudAppStatus.published);
    }

    private PublishedApp buildPublishedApp(CloudAppVersion appVersion) {
        return new PublishedApp()
                .setAppName(appVersion.getApp())
                .setVersion(appVersion.getVersion())
                .setStatus(CloudAppStatus.published)
                .setInteractive(true)
                .setBundleUrl(randomName())
                .setBundleUrlSha(appVersion.getBundleSha())
                .setApprovedBy(appVersion.getApprovedBy())
                .setPublisher(publisher.getUuid())
                .setAuthor(admin.getUuid())
                .setData(new AppMutableData().setBlurb(randomName()).setDescription(randomName()));
    }

    @Test public void testDefaultQuery() throws Exception {
        apiDocs.startRecording(DOC_TARGET, "test the default query");

        // ensure we are anonymous for this test
        appStoreClient.setToken(null);

        final SearchResults<AppListing> apps = appStoreClient.searchAppStore(AppListing.DEFAULT_PAGE);
        assertEquals(AppListing.DEFAULT_PAGE.getPageSize(), apps.getResults().size());
    }

}