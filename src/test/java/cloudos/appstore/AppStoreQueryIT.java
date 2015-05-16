package cloudos.appstore;

import cloudos.appstore.model.support.AppListing;
import cloudos.appstore.server.AppStoreInitializer;
import org.cobbzilla.wizard.dao.SearchResults;
import org.cobbzilla.wizard.model.ResultPage;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AppStoreQueryIT extends AppStoreITBase {

    public static final String DOC_TARGET = "querying the app store";

    @Before public void populateAppStore () throws Exception {
        new AppStoreInitializer().onStart(server);
    }

    @Test public void testDefaultQuery() throws Exception {
        apiDocs.startRecording(DOC_TARGET, "test the default query");

        // ensure we are anonymous for this test
        appStoreClient.setToken(null);

        final SearchResults<AppListing> apps = appStoreClient.searchAppStore(ResultPage.DEFAULT_PAGE);
        assertEquals(ResultPage.DEFAULT_PAGE.getPageSize(), apps.getResults().size());
    }

}