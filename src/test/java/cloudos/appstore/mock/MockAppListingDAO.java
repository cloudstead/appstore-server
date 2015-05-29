package cloudos.appstore.mock;

import cloudos.appstore.dao.AppListingDAO;
import cloudos.appstore.model.*;
import cloudos.appstore.model.support.AppListing;
import cloudos.appstore.model.support.AppStoreQuery;
import org.cobbzilla.wizard.dao.SearchResults;

import java.util.ArrayList;
import java.util.List;

public class MockAppListingDAO extends AppListingDAO {

    private List<AppListing> testApps = new ArrayList<>();

    public void addApp (AppListing app) { testApps.add(app); }

    @Override
    public AppListing findAppListing(AppStorePublisher appPublisher, String name, AppStoreAccount account, List<AppStorePublisherMember> memberships) {
        for (AppListing listing : testApps) {
            if (listing.getPublisher().equals(appPublisher.getName())
                    && listing.getName().equals(name)) return listing;
        }
        return null;
    }

    @Override
    protected AppListing findAppListing(AppStoreAccount account, List<AppStorePublisherMember> memberships, CloudApp app) {
        for (AppListing listing : testApps) {
            if (listing.getName().equals(app.getName())) return listing;
        }
        return null;
    }

    @Override
    public AppListing findAppListing(AppStorePublisher appPublisher, String name, String version, AppStoreAccount account, List<AppStorePublisherMember> memberships) {
        for (AppListing listing : testApps) {
            if (listing.getPublisher().equals(appPublisher.getName())
                    && listing.getName().equals(name)
                    && listing.getVersion().equals(version)) return listing;
        }
        return null;
    }

    @Override public void flush(CloudApp app) {}

    @Override
    public SearchResults<AppListing> search(AppStoreAccount account, List<AppStorePublisherMember> memberships, AppStoreQuery query) {

        final SearchResults<AppListing> results = new SearchResults<>();
        for (AppListing listing : testApps) {
            if (listing.getName().contains(query.getFilter())) results.addResult(listing);
        }
        results.setTotalCount(testApps.size());
        return results;
    }

}
