package cloudos.appstore.mock;

import cloudos.appstore.dao.AppListingDAO;
import cloudos.appstore.model.AppStoreAccount;
import cloudos.appstore.model.AppStorePublisher;
import cloudos.appstore.model.AppStorePublisherMember;
import cloudos.appstore.model.CloudApp;
import cloudos.appstore.model.app.AppManifest;
import cloudos.appstore.model.support.AppListing;
import org.cobbzilla.util.io.StreamUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.wizardtest.RandomUtil.randomName;

public class MockAppListingDAO extends AppListingDAO {

    private List<AppListing> testApps = new ArrayList<>();

    public void addApp (AppListing app) { testApps.add(app); }

    @Override
    public AppListing findAppListing(AppStorePublisher appPublisher, String name, AppStoreAccount account, List<AppStorePublisherMember> memberships) {
        for (AppListing listing : testApps) {
            if (listing.getPublisher().equals(appPublisher.getName()) && listing.getName().equals(name)) {
                final CloudApp app = appDAO.findByPublisherAndName(appPublisher.getUuid(), listing.getName());
                return listing.setAvailableVersions(versionDAO.findPublishedVersions(app.getUuid()));
            }
        }
        return null;
    }

    @Override
    protected AppListing findAppListing(AppStoreAccount account, List<AppStorePublisherMember> memberships, CloudApp app) {
        for (AppListing listing : testApps) {
            if (listing.getName().equals(app.getName())) {
                return listing.setAvailableVersions(versionDAO.findPublishedVersions(app.getUuid()));
            }
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
    protected AppManifest loadManifest(File versionDir) {
        try {
            final AppManifest manifest = AppManifest.load(StreamUtil.loadResourceAsFile("apps/simple-app-manifest.json"));
            manifest.setName(manifest.getName()+"-"+randomName()); // give it a unique name
            return manifest;

        } catch (IOException e) {
            return die("loadManifest: error loading dummy manifest: "+e, e);
        }
    }
}
