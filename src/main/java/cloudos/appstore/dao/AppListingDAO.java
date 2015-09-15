package cloudos.appstore.dao;

import cloudos.appstore.model.*;
import cloudos.appstore.model.app.AppLayout;
import cloudos.appstore.model.app.AppManifest;
import cloudos.appstore.model.support.AppListing;
import cloudos.appstore.model.support.AppStoreQuery;
import cloudos.appstore.server.AppStoreApiConfiguration;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.collections.Transformer;
import org.cobbzilla.wizard.dao.SearchResults;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@Repository @Slf4j
public class AppListingDAO {

    @Autowired protected CloudAppDAO appDAO;
    @Autowired protected CloudAppVersionDAO versionDAO;
    @Autowired protected AppStoreAccountDAO accountDAO;
    @Autowired protected AppStorePublisherDAO publisherDAO;
    @Autowired protected AppStoreApiConfiguration configuration;
    @Autowired private AppFootprintDAO footprintDAO;
    @Autowired private AppPriceDAO priceDAO;

    public AppListing findAppListing(AppStorePublisher appPublisher,
                                     String name,
                                     AppStoreAccount account,
                                     List<AppStorePublisherMember> memberships) {

        final CloudApp app = appDAO.findByPublisherAndName(appPublisher.getUuid(), name);
        if (app == null) return null;

        final AppListing appListing = findAppListing(account, memberships, app);
        if (appListing == null) return null;

        return new AppListing(appListing).setAvailableVersions(versionDAO.findPublishedVersions(app.getUuid()));
    }

    public AppListing findAppListing(AppStorePublisher appPublisher,
                                     String name,
                                     String version,
                                     AppStoreAccount account,
                                     List<AppStorePublisherMember> memberships) {
        final CloudApp app = appDAO.findByPublisherAndName(appPublisher.getUuid(), name);
        if (app == null) return null;

        final CloudAppVersion appVersion = versionDAO.findByUuidAndVersion(app.getUuid(), version);
        if (appVersion == null) return null;

        final AppListing appListing = findAppListing(account, memberships, app);
        if (appListing == null) return null;

        final String locale = account == null ? null : account.getLocale();

        // overwrite manifest with the one from this version
        final File appRepository = configuration.getAppRepository(appPublisher.getName());
        final AppLayout appLayout = new AppLayout(appRepository, app.getName(), version);
        final AppManifest manifest = loadManifest(appLayout.getVersionDir());
        appLayout.localizeAssets(manifest, locale);

        return new AppListing(appListing, appVersion, manifest);
    }

    protected AppListing findAppListing(AppStoreAccount account, List<AppStorePublisherMember> memberships, CloudApp app) {
        final String locale = account == null ? null : account.getLocale();
        switch (app.getVisibility()) {
            case everyone: return buildAppListing(app, locale);
            case members:
                if (empty(memberships)) return null;
                for (AppStorePublisherMember m : memberships) {
                    if (m.getPublisher().equals(app.getPublisher())) return buildAppListing(app, locale);
                }
                return null;
            default:
            case publisher:
                return account == null ? null : app.getPublisher().equals(account.getUuid()) ? buildAppListing(app, locale) : null;
        }
    }

    // Map of app->locale->listing
    private Map<String, Map<String, AppListing>> appCache = new ConcurrentHashMap<>();

    public void flush(CloudApp app) { flush(app.getUuid()); }
    public void flush(String uuid) { appCache.remove(uuid); }

    private static final Predicate SKIP_UNPUBLISHED = new Predicate() {
        @Override public boolean evaluate(Object object) { return object != AppListing.UNPUBLISHED; }
    };

    protected List<AppListing> toListing(List<CloudApp> apps, String locale) {
        // todo: cache AppToListingTransformers, they are immutable and reusable
        final List<AppListing> list = (List<AppListing>) CollectionUtils.collect(apps, new AppToListingTransformer(locale));
        CollectionUtils.filter(list, SKIP_UNPUBLISHED);
        return list;
    }

    private AppListing buildAppListing(CloudApp app, String locale) {

        if (locale == null) locale = AppManifest.DEFAULT_LOCALE;

        AppListing listing;
        Map<String, AppListing> listings = appCache.get(app.getUuid());
        if (listings != null) {
            listing = listings.get(locale);
            if (listing != null) return listing;
        } else {
            listings = new ConcurrentHashMap<>();
            appCache.put(app.getUuid(), listings);
        }

        listing = buildAppListing_internal(app, locale);
        listings.put(app.getUuid(), listing);

        return listing;
    }

    private AppListing buildAppListing_internal(CloudApp app, String locale) {

        final AppStorePublisher publisher = publisherDAO.findByUuid(app.getPublisher());
        final CloudAppVersion appVersion = versionDAO.findLatestPublishedVersion(app.getUuid());
        if (appVersion == null) {
            log.warn("buildAppListing_internal: No published version: "+app.getUuid());
            return AppListing.UNPUBLISHED;
        }

        final AppStoreAccount author = accountDAO.findByUuid(app.getAuthor());
        final AppStoreAccount approvedBy = accountDAO.findByUuid(appVersion.getApprovedBy());
        if (approvedBy == null) {
            log.warn("buildAppListing_internal: Published version not approved: "+app.getUuid());
            return AppListing.UNPUBLISHED;
        }

        final String version = appVersion.getVersion();
        final File appRepository = configuration.getAppRepository(publisher.getName());
        final AppLayout appLayout = new AppLayout(appRepository, app.getName(), version);
        final AppManifest manifest = loadManifest(appLayout.getVersionDir());
        appLayout.localizeAssets(manifest, locale);

        final AppListing listing = new AppListing()
                .setBundleUrl(configuration.getPublicBundleUrl(publisher.getName(), app.getName(), version))
                .setFootprint(footprintDAO.findByApp(app.getUuid()))
                .setPrices(priceDAO.findByApp(app.getUuid()));

        listing.getPrivateData()
                .setPublisher(publisher.publicView())
                .setApp(app)
                .setVersion(appVersion)
                .setAuthor(author.publicView())
                .setApprovedBy(approvedBy.publicView())
                .setManifest(manifest);

        return listing;
    }

    // allows tests to override this, like AppStoreQueryIT
    protected AppManifest loadManifest(File versionDir) {
        return AppManifest.load(versionDir);
    }

    public SearchResults<AppListing> search(final AppStoreAccount account,
                                            final List<AppStorePublisherMember> memberships,
                                            final AppStoreQuery query) {

        // Load all candidates
        // todo: cache these lookups for a bit, no need to hit the DB on every search
        final List<CloudApp> apps = appDAO.findVisibleToEveryone();
        if (account != null) {
            apps.addAll(appDAO.findVisibleToMember(memberships));
        }

        // Use locale from query, if not found use locale from account, if not found use default
        final String locale = query.hasLocale() ? query.getLocale() : account != null ? account.getLocale() : null;

        // Transform into listings (will also remove apps that somehow have no published version)
        final List<AppListing> candidates = toListing(apps, locale);

        // Filter to find matches
        final List<AppListing> matches = filter(candidates, query);

        // for now, always sort by name, ignoring sort field/order set in query
        Collections.sort(matches, AppListing.COMPARE_NAME);

        // select proper page of matches
        final List<AppListing> page = new ArrayList<>(query.getPageBufferSize());
        for (int i=query.getPageOffset(); i<query.getPageEndOffset(); i++) {
            if (i >= matches.size()) break;
            page.add(matches.get(i));
        }

        return new SearchResults<>(page, matches.size());
    }

    public List<AppListing> filter(List<AppListing> candidates, AppStoreQuery query) {
        return (List<AppListing>) CollectionUtils.select(candidates, new AppQueryPredicate(query));
    }

    @AllArgsConstructor
    private class AppQueryPredicate implements Predicate {
        @Getter @Setter private AppStoreQuery query;
        public boolean evaluate(Object o) { return ((AppListing) o).matches(query); }
    }

    @AllArgsConstructor
    private class AppToListingTransformer implements Transformer {
        public String locale;
        @Override public Object transform(Object o) {
            return buildAppListing((CloudApp) o, locale);
        }
    }
}
