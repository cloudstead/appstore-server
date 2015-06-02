package cloudos.appstore.dao;

import cloudos.appstore.model.*;
import cloudos.appstore.model.app.AppLayout;
import cloudos.appstore.model.app.AppManifest;
import cloudos.appstore.model.support.AppListing;
import cloudos.appstore.model.support.AppStoreObjectType;
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

        final AppManifest manifest = getManifest(appPublisher, app, version);
        return new AppListing(appListing, appVersion, manifest);
    }

    protected AppListing findAppListing(AppStoreAccount account, List<AppStorePublisherMember> memberships, CloudApp app) {
        switch (app.getVisibility()) {
            case everyone: return buildAppListing(app);
            case members:
                if (empty(memberships)) return null;
                for (AppStorePublisherMember m : memberships) {
                    if (m.getPublisher().equals(app.getPublisher())) return buildAppListing(app);
                }
                return null;
            default:
            case publisher:
                return app.getPublisher().equals(account.getUuid()) ? buildAppListing(app) : null;
        }
    }

    private Map<String, AppListing> appCache = new ConcurrentHashMap<>();

    public void flush(CloudApp app) { flush(app.getUuid()); }
    public void flush(String uuid) { appCache.remove(uuid); }

    private final Transformer TO_LISTING = new Transformer() {
        @Override public Object transform(Object o) { return buildAppListing((CloudApp) o); }
    };

    private static final Predicate SKIP_UNPUBLISHED = new Predicate() {
        @Override public boolean evaluate(Object object) { return object != AppListing.UNPUBLISHED; }
    };

    protected List<AppListing> toListing(List<CloudApp> apps) {
        final List<AppListing> list = (List<AppListing>) CollectionUtils.collect(apps, TO_LISTING);
        CollectionUtils.filter(list, SKIP_UNPUBLISHED);
        return list;
    }

    private AppListing buildAppListing(CloudApp app) {

        AppListing listing = appCache.get(app.getUuid());
        if (listing != null) return listing;

        listing = buildAppListing_internal(app);

        appCache.put(app.getUuid(), listing);

        return listing;
    }

    private AppListing buildAppListing_internal(CloudApp app) {

        final AppStorePublisher publisher = publisherDAO.findByUuid(app.getPublisher());
        final CloudAppVersion appVersion = versionDAO.findLatestPublishedVersion(app.getUuid());
        if (appVersion == null) {
            log.warn("buildAppListing: No published version: "+app.getUuid());
            return AppListing.UNPUBLISHED;
        }

        final AppStoreAccount author = accountDAO.findByUuid(app.getAuthor());
        final AppStoreAccount approvedBy = accountDAO.findByUuid(appVersion.getApprovedBy());

        final String version = appVersion.getVersion();
        final AppManifest manifest = getManifest(publisher, app, version);

        final AppListing listing = new AppListing()
                .setBundleUrl(configuration.getPublicBundleUrl(publisher.getName(), app.getName(), version))
                .setFootprint(footprintDAO.findByApp(app.getUuid()))
                .setPrices(priceDAO.findByApp(app.getUuid()));

        listing.getPrivateData()
                .setPublisher(publisher)
                .setApp(app)
                .setVersion(appVersion)
                .setAuthor(author)
                .setApprovedBy(approvedBy)
                .setManifest(manifest);

        return listing;
    }

    private AppManifest getManifest(AppStorePublisher publisher, CloudApp app, String version) {
        final File appRepository = configuration.getAppRepository(publisher.getName());
        final AppLayout appLayout = new AppLayout(appRepository, app.getName(), version);
        return AppManifest.load(appLayout.getVersionDir());
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

        // Transform into listings (will also remove apps that somehow have no published version)
        final List<AppListing> candidates = toListing(apps);

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

    private boolean matches(AppStoreQuery query, AppListing listing) {

        final String filter = query.getFilter();
        if (empty(filter)) return true;

        final AppStoreObjectType type = query.hasType() ? query.getType() : AppStoreObjectType.app;

        if (query.hasType()) {
            switch (type) {
                case account:
                    return matchesAccount(filter, listing);

                case publisher:
                    return matchesPublisher(filter, listing);

                case app:
                default:
                    return matchesApp(filter, listing);
            }

        } else {
            return matchesAccount(filter, listing)
                    || matchesPublisher(filter, listing)
                    || matchesApp(filter, listing);
        }
    }

    private boolean matchesAccount(String filter, AppListing listing) {
        final AppStoreAccount author = listing.getPrivateData().getAuthor();
        return author.getFirstName().contains(filter)
                || author.getLastName().contains(filter)
                || author.getFullName().contains(filter)
                || author.getName().contains(filter);
    }

    private boolean matchesPublisher(String filter, AppListing listing) {
        final AppStorePublisher publisher = listing.getPrivateData().getPublisher();
        return publisher.getName().contains(filter);
    }

    private boolean matchesApp(String filter, AppListing listing) {
        final CloudApp app = listing.getPrivateData().getApp();
        final AppMutableData assets = listing.getData();
        return app.getName().contains(filter)
                || (assets != null && (assets.getBlurb().contains(filter) || assets.getDescription().contains(filter)));
    }

    @AllArgsConstructor
    private class AppQueryPredicate implements Predicate {
        @Getter @Setter private AppStoreQuery query;
        public boolean evaluate(Object o) { return matches(query, (AppListing) o); }
    }
}
