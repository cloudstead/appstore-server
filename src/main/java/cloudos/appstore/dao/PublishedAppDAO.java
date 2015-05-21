package cloudos.appstore.dao;

import cloudos.appstore.model.*;
import cloudos.appstore.model.app.AppLayout;
import cloudos.appstore.model.app.AppManifest;
import cloudos.appstore.server.AppStoreApiConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.dao.SearchResults;
import org.cobbzilla.wizard.model.ResultPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.string.StringUtil.empty;

@Repository @Slf4j
public class PublishedAppDAO {

    @Autowired protected CloudAppDAO appDAO;
    @Autowired protected CloudAppVersionDAO versionDAO;
    @Autowired protected AppStoreAccountDAO accountDAO;
    @Autowired protected AppStorePublisherDAO publisherDAO;
    @Autowired protected AppStoreApiConfiguration configuration;

    // for now we just keep them all in memory
    private final AtomicReference<List<PublishedApp>> apps = new AtomicReference<>();

    public PublishedApp findByNameAndVersion(String name, String version) {
        for (PublishedApp app : getApps()) {
            if (app.getAppName().equals(name) && app.getVersion().equals(version)) return app;
        }
        return null;
    }

    protected List<PublishedApp> getApps () {
        List<PublishedApp> appList = apps.get();
        if (appList == null) {
            synchronized (apps) {
                appList = apps.get();
                if (appList == null) {
                    appList = initPublishedApps();
                    apps.set(appList);
                }
            }
        }
        return appList;
    }

    public void flushApps () { apps.set(null); }

    protected List<PublishedApp> initPublishedApps() {

        final Set<PublishedApp> sortedApps = new TreeSet<>(PublishedApp.COMPARATOR_NAME);
        for (CloudAppVersion appVersion : versionDAO.findPublishedVersions()) {
            try {
                sortedApps.add(buildPublishedApp(appVersion));
            } catch (Exception e) {
                log.warn("Error processing app ("+appVersion+"): "+e, e);
            }
        }
        return new ArrayList<>(sortedApps);
    }

    private PublishedApp buildPublishedApp(CloudAppVersion appVersion) {

        final String appName = appVersion.getApp();
        final String version = appVersion.getVersion();

        final CloudApp cloudApp = appDAO.findByName(appName);
        if (cloudApp == null) die("buildPublishedApp: app not found: "+ appName);

        final AppStoreAccount author = accountDAO.findByUuid(cloudApp.getAuthor());
        if (author == null) die("buildPublishedApp: account not found: "+cloudApp.getAuthor());

        final AppStorePublisher publisher = publisherDAO.findByUuid(cloudApp.getPublisher());
        if (publisher == null) die("buildPublishedApp: publisher not found: "+cloudApp.getPublisher());

        final File appRepository = configuration.getAppStore().getAppRepository();
        final AppLayout appLayout = new AppLayout(appRepository, appName, version);

        final AppManifest manifest = AppManifest.load(appLayout.getVersionDir());

        final PublishedApp app = new PublishedApp()
                .setAppName(appName)
                .setVersion(version)
                .setAuthor(author.getName())
                .setPublisher(publisher.getName())
                .setApprovedBy(appVersion.getApprovedBy())
                .setData(manifest.getAssets())
                .setInteractive(manifest.isInteractive())
                .setBundleUrl(configuration.getPublicBundleUrl(appName, version))
                .setBundleUrlSha(appVersion.getBundleSha())
                .setStatus(appVersion.getStatus());

        return app;
    }

    public SearchResults<PublishedApp> search(ResultPage query) {
        final List<PublishedApp> all = new ArrayList<>(getApps());
        final List<PublishedApp> found = new ArrayList<>();

        if (query.getHasSortField()) {
            switch (query.getSortField()) {
                case "name":
                    // default sort is by name ascending, reverse if they want it descending
                    if (query.getSortType().isDescending()) {
                        Collections.reverse(all);
                    }
                    break;

                default:
                    die("Invalid sort field: " + query.getSortField());
            }
        }

        // find all matches
        for (PublishedApp app : all) if (matches(query, app)) found.add(app);

        // select proper page of matches
        final List<PublishedApp> page = new ArrayList<>(query.getPageSize());
        for (int i=query.getPageOffset(); i<query.getPageOffset()+query.getPageSize(); i++) {
            if (i >= found.size()) break;
            page.add(found.get(i));
        }

        return new SearchResults<>(page, all.size());
    }

    private boolean matches(ResultPage page, PublishedApp app) {
        final String filter = page.getFilter();
        return empty(filter)
                || app.getAppName().contains(filter)
                || app.getData().getBlurb().contains(filter)
                || app.getData().getDescription().contains(filter)
                || app.getAuthor().contains(filter);
    }

}
