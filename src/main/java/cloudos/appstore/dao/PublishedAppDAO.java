package cloudos.appstore.dao;

import cloudos.appstore.model.*;
import cloudos.appstore.model.app.AppLayout;
import cloudos.appstore.model.app.AppManifest;
import cloudos.appstore.server.AppStoreApiConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.dao.SearchResults;
import org.cobbzilla.wizard.model.ResultPage;
import org.cobbzilla.wizard.model.SemanticVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.io.FileUtil.listDirs;

@Repository @Slf4j
public class PublishedAppDAO {

    @Autowired protected CloudAppDAO appDAO;
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
        final File appRepository = configuration.getAppStore().getAppRepository();
        for (File appDir : listDirs(appRepository)) {

            final String appName = appDir.getName();
            final AppLayout appLayout = new AppLayout(appRepository, appName);
            final List<SemanticVersion> versions = appLayout.getVersions();

            for (SemanticVersion version : versions) {
                final File versionDir = new File(appDir, version.toString());
                try {
                    final AppStoreAppMetadata metadata = AppStoreAppMetadata.read(versionDir);
                    if (!metadata.isPublished()) continue; // skip unpublished versions
                    sortedApps.add(buildPublishedApp(appName, metadata, versionDir));

                } catch (Exception e) {
                    log.warn("Error processing app ("+abs(versionDir)+"): "+e, e);
                }
            }
        }
        return new ArrayList<>(sortedApps);
    }

    private PublishedApp buildPublishedApp(String appName, AppStoreAppMetadata metadata, File versionDir) {

        final CloudApp cloudApp = appDAO.findByName(appName);
        if (cloudApp == null) die("buildPublishedApp: app not found: "+appName);

        final AppStoreAccount author = accountDAO.findByUuid(cloudApp.getAuthor());
        if (author == null) die("buildPublishedApp: account not found: "+cloudApp.getAuthor());

        final AppStorePublisher publisher = publisherDAO.findByUuid(cloudApp.getPublisher());
        if (publisher == null) die("buildPublishedApp: publisher not found: "+cloudApp.getPublisher());

        final AppManifest manifest = AppManifest.load(versionDir);
        final String version = versionDir.getName();

        final PublishedApp app = new PublishedApp()
                .setAppName(appName)
                .setVersion(version)
                .setAuthor(author.getName())
                .setPublisher(publisher.getName())
                .setApprovedBy(metadata.getApprovedBy())
                .setData(manifest.getAssets())
                .setInteractive(manifest.isInteractive())
                .setBundleUrl(configuration.getPublicBundleUrl(appName, version))
                .setBundleUrlSha(metadata.getBundleSha())
                .setStatus(metadata.getStatus());

        return app;
    }

    public SearchResults<PublishedApp> search(ResultPage query) {
        final List<PublishedApp> all = new ArrayList<>(getApps());
        final List<PublishedApp> found = new ArrayList<>();

        if (query.getHasSortField()) {
            switch (query.getSortField()) {
                case "name":
                    // default sort is already by name
                    if (query.getSortType() == ResultPage.SortOrder.DESC) {
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

        return new SearchResults<>(found, all.size());
    }

    private boolean matches(ResultPage page, PublishedApp app) {
        final String filter = page.getFilter();
        return app.getAppName().contains(filter)
                || app.getData().getBlurb().contains(filter)
                || app.getData().getDescription().contains(filter)
                || app.getAuthor().contains(filter);
    }

}
