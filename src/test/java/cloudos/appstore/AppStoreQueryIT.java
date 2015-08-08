package cloudos.appstore;

import cloudos.appstore.dao.CloudAppDAO;
import cloudos.appstore.dao.CloudAppVersionDAO;
import cloudos.appstore.mock.MockAppListingDAO;
import cloudos.appstore.model.*;
import cloudos.appstore.model.app.AppLevel;
import cloudos.appstore.model.support.AppListing;
import cloudos.appstore.model.support.AppStoreQuery;
import org.cobbzilla.wizard.dao.SearchResults;
import org.cobbzilla.wizard.model.ResultPage;
import org.junit.Before;
import org.junit.Test;

import static org.cobbzilla.wizardtest.RandomUtil.randomName;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class AppStoreQueryIT extends AppStoreITBase {

    public static final String DOC_TARGET = "querying the app store";

    private static final int NUM_APPS = ResultPage.DEFAULT_PAGE.getPageSize() * 3;

    public static final String MATCH_NAME = "match-this";
    public static final String VERSION = "1.2.3";

    private AppStorePublisher publisher;
    private AppStoreAccount admin;

    @Before
    public void populateApps() throws Exception {

        appStoreClient.pushToken(adminToken);
        admin = appStoreClient.findAccount();
        publisher = appStoreClient.findPublisher(admin.getUuid());

        final CloudAppDAO cloudAppDAO = getBean(CloudAppDAO.class);
        final CloudAppVersionDAO versionDAO = getBean(CloudAppVersionDAO.class);
        final MockAppListingDAO publishedAppDAO = getBean(MockAppListingDAO.class);

        for (int i=0; i<NUM_APPS; i++) {
            final CloudApp app = cloudAppDAO.create(buildCloudApp(i));
            final CloudAppVersion appVersion = versionDAO.create(buildCloudAppVersion(app));
            publishedAppDAO.addApp(buildPublishedApp(app, appVersion));
        }
    }

    private CloudApp buildCloudApp(int i) {
        // every third app gets MATCH_NAME as part of its name
        return new CloudApp()
                .setAuthor(admin.getUuid())
                .setPublisher(publisher.getUuid())
                .setVisibility(AppVisibility.everyone)
                .setLevel(AppLevel.app)
                .setName(i + (i % 3 == 0 ? MATCH_NAME : "") + "-" + randomName());
    }

    private CloudAppVersion buildCloudAppVersion(CloudApp app) {
        return new CloudAppVersion(app.getName(), VERSION)
                .setApp(app.getUuid())
                .setBundleSha(randomName())
                .setApprovedBy(admin.getUuid())
                .setAuthor(admin.getUuid())
                .setStatus(CloudAppStatus.published);
    }

    private AppListing buildPublishedApp(CloudApp app, CloudAppVersion appVersion) {
        AppListing listing = new AppListing().setBundleUrl(randomName());
        listing.getPrivateData()
                .setPublisher(publisher.publicView())
                .setApp(app)
                .setVersion(appVersion)
                .setAuthor(admin.publicView())
                .setApprovedBy(admin.publicView());
        return listing;
    }

    @Test public void testQueries() throws Exception {
        apiDocs.startRecording(DOC_TARGET, "search the app store");

        // ensure we are anonymous for this test
        appStoreClient.setToken(null);

        SearchResults<AppListing> apps;
        AppStoreQuery query;

        apiDocs.addNote("default search");
        query = new AppStoreQuery();
        apps = appStoreClient.searchAppStore(query);
        assertEquals(query.getPageSize(), apps.getResults().size());

        apiDocs.addNote("find EVERYTHING search");
        apps = appStoreClient.searchAppStore(new AppStoreQuery(ResultPage.INFINITE_PAGE));
        assertEquals(NUM_APPS, apps.getResults().size());

        apiDocs.addNote("search using a query string");
        query = new AppStoreQuery(MATCH_NAME);
        apps = appStoreClient.searchAppStore(query);
        assertEquals(NUM_APPS/3, apps.getResults().size());

        apiDocs.addNote("view details for an app, including all available versions");
        final String appName = apps.getResults().get(0).getName();
        final AppListing appListing = appStoreClient.findAppListing(publisher.getName(), appName);
        assertEquals(appName, appListing.getName());
        assertNotNull(appListing.getAvailableVersions());
        assertEquals(1, appListing.getAvailableVersions().size());
        assertEquals(VERSION, appListing.getAvailableVersions().get(0).getVersion());
    }

}