package cloudos.appstore;

import cloudos.appstore.model.support.AppListing;
import cloudos.appstore.test.AppStoreSeedData;
import org.cobbzilla.wizard.dao.SearchResults;
import org.cobbzilla.wizard.model.ResultPage;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AppStoreQueryIT extends AppStoreITBase {

    public static final String DOC_TARGET = "querying the app store";

    public static final int NUM_ACCOUNTS = 2;
    public static final int NUM_APPS = 8;
    public static final int NUM_VERSIONS = 2;

    protected AppStoreSeedData seedData;

    @Before
    public void populateAppStore () throws Exception {
        seedData = new AppStoreSeedData(appStoreClient, adminToken, NUM_ACCOUNTS, NUM_APPS, NUM_VERSIONS);
    }

    @Test
    public void testDefaultQuery() throws Exception {
        apiDocs.startRecording(DOC_TARGET, "test the default query");

        // ensure we are anonymous for this test
        appStoreClient.setToken(null);

        final SearchResults<AppListing> apps = appStoreClient.searchAppStore(ResultPage.DEFAULT_PAGE);
        assertEquals(ResultPage.DEFAULT_PAGE.getPageSize(), apps.getResults().size());
    }

}