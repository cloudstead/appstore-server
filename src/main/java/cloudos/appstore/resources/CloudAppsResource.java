package cloudos.appstore.resources;

import cloudos.appstore.ApiConstants;
import cloudos.appstore.dao.*;
import cloudos.appstore.model.*;
import cloudos.appstore.model.app.AppLayout;
import cloudos.appstore.model.app.AppManifest;
import cloudos.appstore.model.support.AppAssetUrlGenerator;
import cloudos.appstore.model.support.AppBundle;
import cloudos.appstore.model.support.DefineCloudAppRequest;
import cloudos.appstore.server.AppStoreApiConfiguration;
import com.qmino.miredot.annotations.ReturnType;
import com.sun.jersey.api.core.HttpContext;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.cobbzilla.util.io.Tarball;
import org.cobbzilla.util.security.ShaUtil;
import org.cobbzilla.wizard.model.SemanticVersion;
import org.cobbzilla.wizard.validation.ConstraintViolationBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(ApiConstants.APPS_ENDPOINT)
@Service @Slf4j
public class CloudAppsResource {

    @Autowired private AppStoreApiConfiguration configuration;
    @Autowired private AppStoreAccountDAO accountDAO;
    @Autowired private CloudAppDAO appDAO;
    @Autowired private CloudAppVersionDAO versionDAO;
    @Autowired private AppListingDAO appListingDAO;
    @Autowired private AppStorePublisherDAO publisherDAO;
    @Autowired private AppStorePublisherMemberDAO memberDAO;
    @Autowired private AppFootprintDAO footprintDAO;
    @Autowired private AppPriceDAO priceDAO;

    /**
     * Find all apps for a given publisher
     * @param context used to retrieve the logged-in user session
     * @param publisher name of the publisher
     * @return a list of CloudApps
     */
    @GET
    @Path("/{publisher}")
    @ReturnType("java.util.List<cloudos.appstore.model.CloudApp>")
    public Response findApps (@Context HttpContext context,
                              @PathParam("publisher") String publisher) {

        final CloudAppContext ctx = appContext(context, publisher, null, true);
        if (ctx.hasResponse()) return ctx.response;

        // findByPublisher enforces visibility limits on account
        final List<CloudApp> apps = appDAO.findByPublisher(ctx.publisher, ctx.account, ctx.membership);

        return ok(apps);
    }

    /**
     * Upload a new version of an app (this could be the very first version of an app, or an upgrade version)
     * @param context used to retrieve the logged-in user session
     * @param publisher the app will belong to this publisher
     * @param request the app definition request
     * @return the version of the app
     */
    @POST
    @Path("/{publisher}")
    @ReturnType("cloudos.appstore.model.CloudAppVersion")
    public Response defineAppVersion(@Context HttpContext context,
                                     @PathParam("publisher") String publisher,
                                     @Valid DefineCloudAppRequest request) {

        final CloudAppContext ctx = appContext(context, publisher, null);
        if (ctx.hasResponse()) return ctx.response;

        // todo: cache in an LRU cache, AppAssetUrlGenerators are immutable and can be reused
        final AppAssetUrlGenerator baseUrlGen = new AppStoreAssetUrlGenerator(publisher);

        final List<ConstraintViolationBean> violations = new ArrayList<>();
        final AppBundle bundle;
        try {
            bundle = new AppBundle(request.getBundleUrl(), request.getBundleUrlSha(), baseUrlGen, violations);
        } catch (Exception e) {
            log.error("defineAppVersion (violations="+violations+"): "+e, e);
            if (!empty(violations)) return invalid(violations);
            return serverError();
        }

        try {
            final AppManifest manifest = bundle.getManifest();

            ctx.app = appDAO.findByPublisherAndName(ctx.publisher.getUuid(), manifest.getName());
            if (ctx.app == null) {
                ctx.app = new CloudApp()
                        .setAuthor(ctx.account.getUuid())
                        .setPublisher(ctx.publisher.getUuid())
                        .setName(manifest.getName())
                        .setVisibility(request.getVisibility())
                        .setLevel(manifest.getLevel());
                ctx.app = appDAO.create(ctx.app);
            } else {
                if (ctx.app.getLevel() != manifest.getLevel()) return invalid("err.defineApp.cannotChangeLevel");
                if (ctx.app.getVisibility() != request.getVisibility()) return invalid("err.defineApp.cannotChangeVisibility");
            }

            // This is where it should live in the main repository... anything already there?
            final File appRepository = configuration.getAppRepository(ctx.publisher.getName());
            final AppLayout appLayout = new AppLayout(appRepository, manifest.getName());
            final List<SemanticVersion> existingVersions = appLayout.getVersions();

            // Pick a version that will work, either the one given, or the next available patch level
            SemanticVersion proposedVersion = manifest.getSemanticVersion();
            if (proposedVersion == null) {
                proposedVersion = SemanticVersion.incrementPatch(existingVersions.get(0));
            } else {
                while (existingVersions.contains(proposedVersion)) {
                    proposedVersion = SemanticVersion.incrementPatch(proposedVersion);
                }
            }
            if (!manifest.getSemanticVersion().equals(proposedVersion)) {
                manifest.setSemanticVersion(proposedVersion);
                bundle.writeManifest();
            }

            // re-roll the tarball
            final AppLayout finalAppLayout = new AppLayout(appRepository, manifest);
            final File bundleTarball;
            try {
                bundleTarball = Tarball.roll(bundle.getBundleDir());

            } catch (IOException e) {
                final String msg = "{err.defineApp.rerollingBundleTarball}";
                log.error(msg, e);
                return serverError();
            }

            if (!finalAppLayout.getVersionDir().exists() && !finalAppLayout.getVersionDir().mkdirs()) {
                final String msg = "{err.defineApp.creatingVersionDir}";
                log.error(msg + ": " + abs(finalAppLayout.getVersionDir()));
                return serverError();
            }
            if (!bundleTarball.renameTo(finalAppLayout.getBundleFile())) {
                final String msg = "{err.defineApp.renamingBundleTarball}";
                log.error(msg);
                return serverError();
            }

            finalAppLayout.writeManifest(manifest);
            final AppLayout bundleLayout = new AppLayout(manifest.getScrubbedName(), bundle.getBundleDir());
            if (manifest.hasAssets()) {
                if (!bundleLayout.copyAssets(finalAppLayout)) {
                    final String msg = "{err.defineApp.copyingAssets}";
                    log.error(msg);
                    return serverError();
                }
            }
            if (!bundleLayout.copyTranslations(finalAppLayout)) {
                final String msg = "{err.defineApp.copyingTranslations}";
                log.error(msg);
                return serverError();
            }

            // Is there a version?
            CloudAppVersion appVersion = versionDAO.findByUuidAndVersion(ctx.app.getUuid(), manifest.getVersion());
            if (appVersion != null) {
                // should never happen
                final String msg = "{err.defineApp.versionExists}";
                log.error(msg);
                return invalid(msg);
            }
            appVersion = new CloudAppVersion()
                    .setApp(ctx.app.getUuid())
                    .setVersion(manifest.getVersion())
                    .setAuthor(ctx.account.getUuid())
                    .setBundleSha(ShaUtil.sha256_file(finalAppLayout.getBundleFile()));
            appVersion = versionDAO.create(appVersion);

            appVersion.setApp(ctx.app.getName());
            return ok(appVersion);

        } finally {
            bundle.cleanup();
        }
    }

    /**
     * Retrieve info about an app
     * @param context used to retrieve the logged-in user session
     * @param publisher name of the publisher
     * @param name name of the app
     * @return The app information
     */
    @GET
    @Path("/{publisher}/{name}")
    @ReturnType("cloudos.appstore.model.CloudApp")
    public Response findApp(@Context HttpContext context,
                            @PathParam("publisher") String publisher,
                            @PathParam("name") String name) {
        final CloudAppContext ctx = appContext(context, publisher, name, true);
        if (ctx.hasResponse()) return ctx.response;
        ctx.populateApp(accountDAO, versionDAO);
        return ok(ctx.app);
    }

    /**
     * Retrieve an asset for the latest version of an app
     * @param context used to retrieve the logged-in user session
     * @param publisher name of the publisher
     * @param name name of the app
     * @param asset name of the asset to retrieve. Use 'bundle', 'largeIcon', 'smallIcon', or 'taskbarIcon'
     * @return The asset for the latest version as a stream, or 404 if not found
     */
    @GET
    @Path("/{publisher}/{name}/assets/{asset}")
    @ReturnType("cloudos.appstore.model.CloudAppVersion")
    public Response getAsset(@Context HttpContext context,
                             @PathParam("publisher") String publisher,
                             @PathParam("name") String name,
                             @PathParam("asset") String asset) {

        final CloudAppContext ctx = appContext(context, publisher, name, true);
        if (ctx.hasResponse()) return ctx.response;

        final CloudAppVersion appVersion = versionDAO.findLatestPublishedVersion(ctx.app.getUuid());
        if (appVersion == null) return notFound(name + "/any-published-version");

        if (empty(asset)) return invalid("err.asset.empty");

        return streamAsset(ctx, appVersion, asset);
    }

    /**
     * Retrieve an asset for a particular version of an app
     * @param context used to retrieve the logged-in user session
     * @param publisher name of the publisher
     * @param name name of the app
     * @param version version of the app
     * @param asset name of the asset to retrieve. Use 'bundle', 'largeIcon', 'smallIcon', or 'taskbarIcon'
     * @return The asset for the latest version as a stream, or 404 if not found
     */
    @GET
    @Path("/{publisher}/{name}/versions/{version}/assets/{asset}")
    @ReturnType("cloudos.appstore.model.CloudAppVersion")
    public Response getAsset(@Context HttpContext context,
                             @PathParam("publisher") String publisher,
                             @PathParam("name") String name,
                             @PathParam("version") String version,
                             @PathParam("asset") String asset) {

        final CloudAppContext ctx = appContext(context, publisher, name, true);
        if (ctx.hasResponse()) return ctx.response;

        final CloudAppVersion appVersion = versionDAO.findByUuidAndVersion(ctx.app.getUuid(), version);
        if (appVersion == null) return notFound(name + "/any-published-version");

        if (empty(asset)) return invalid("err.asset.empty");

        return streamAsset(ctx, appVersion, asset);
    }

    protected Response streamAsset(CloudAppContext ctx, CloudAppVersion appVersion, String asset) {
        final File appRepository = configuration.getAppRepository(ctx.publisher.getName());
        final AppLayout appLayout = new AppLayout(appRepository, ctx.app.getName(), appVersion.getVersion());
        final File assetFile;
        if (AppLayout.BUNDLE_TARBALL.startsWith(asset)) {
            return streamFile(appLayout.getBundleFile());
        } else {
            for (String assetType : AppMutableData.APP_ASSETS) {
                if (assetType.startsWith(asset)) {
                    assetFile = appLayout.findLocalAsset(assetType);
                    if (assetFile == null) return notFound(asset);
                    return streamFile(assetFile);
                }
            }
        }
        return notFound(asset);
    }

    /**
     * Retrieve a particular app version
     * @param context used to retrieve the logged-in user session
     * @param publisher name of the publisher
     * @param name name of the app
     * @param version version of the app
     * @return The version information
     */
    @GET
    @Path("/{publisher}/{name}/versions/{version}")
    @ReturnType("cloudos.appstore.model.CloudAppVersion")
    public Response getVersion(@Context HttpContext context,
                               @PathParam("publisher") String publisher,
                               @PathParam("name") String name,
                               @PathParam("version") String version) {

        final CloudAppContext ctx = appContext(context, publisher, name, true);
        if (ctx.hasResponse()) return ctx.response;

        final CloudAppVersion appVersion = versionDAO.findByUuidAndVersion(ctx.app.getUuid(), version);
        if (appVersion == null) return notFound(name + "/" + version);

        return ok(appVersion);
    }

    /**
     * Update an app attribute. Currently the only supported attribute is 'visibility'
     * Visibility is one of: everyone, members, publisher
     * Everyone = the app is public
     * Members = only accounts that have memberships with the app publisher may see the app
     * Publisher = only the owner of the publisher account can see the app.
     * for a fully private app, use the Self setting, and set the publisher to the default publisher associated with
     * your account (same name as your account name).
     * @param context used to retrieve the logged-in user session
     * @param publisher name of the publisher
     * @param name name of the app
     * @param attribute name of the attribute to update
     * @param value the value for the new attribute
     * @return the updated version information
     */
    @POST
    @Path("/{publisher}/{name}/attributes/{attribute}")
    @ReturnType("cloudos.appstore.model.CloudAppVersion")
    public Response updateAttribute(@Context HttpContext context,
                                    @PathParam("publisher") String publisher,
                                    @PathParam("name") String name,
                                    @PathParam("attribute") String attribute,
                                    String value) {

        final CloudAppContext ctx = appContext(context, publisher, name);
        if (ctx.hasResponse()) return ctx.response;

        if (empty(attribute)) return invalid("err.app.update.attribute.empty");

        switch (attribute) {
            case "visibility":
                ctx.app.setVisibility(AppVisibility.valueOf(value));
                appDAO.update(ctx.app);
                appListingDAO.flush(ctx.app);
                return ok(ctx.app);

            default:
                return invalid("err.app.update.attribute.invalid", attribute);
        }
    }

    /**
     * Update status of a particular app version
     * @param context used to retrieve the logged-in user session
     * @param publisher name of the publisher
     * @param name name of the app
     * @param version version of the app
     * @param status the new status for the app
     * @return the updated version information
     */
    @POST
    @Path("/{publisher}/{name}/versions/{version}/status")
    @ReturnType("cloudos.appstore.model.CloudAppVersion")
    public Response updateStatus(@Context HttpContext context,
                                 @PathParam("publisher") String publisher,
                                 @PathParam("name") String name,
                                 @PathParam("version") String version,
                                 CloudAppStatus status) {

        final CloudAppContext ctx = appContext(context, publisher, name);
        if (ctx.hasResponse()) return ctx.response;

        final CloudAppVersion appVersion = versionDAO.findByUuidAndVersion(ctx.app.getUuid(), version);
        if (appVersion == null) return notFound(name + "/" + version);

        final boolean shouldRefreshApps = appVersion.isPublished() || status.isPublished();

        try {
            switch (status) {
                case created:
                    log.warn("Cannot move an app back to 'created' status");
                    return invalid("{err.app.version.status.invalid}");

                case pending:
                    // they are requesting to publish the app, this is fine
                    appVersion.setStatus(status);
                    versionDAO.update(appVersion);
                    break;

                case published:
                    // admins do this to pending requests, makes the app publicly published
                    if (ctx.account.isAdmin()) {
                        appVersion.setStatus(status);
                        appVersion.setApprovedBy(ctx.account.getUuid());
                        versionDAO.update(appVersion);
                        break;
                    }
                    // non-admins are not allowed to publish
                    return forbidden();

                case retired:
                    // anyone can make an app retired.
                    // it will not be listed in app store search results and cannot be installed
                    appVersion.setStatus(status);
                    versionDAO.update(appVersion);
                    break;

                default:
                    return invalid("{err.app.version.status.invalid}");
            }

        } finally {
            if (shouldRefreshApps) appListingDAO.flush(ctx.app);
        }

        return ok(appVersion);
    }

    /**
     * Delete an app version
     * @param context used to retrieve the logged-in user session
     * @param publisher name of the publisher
     * @param name name of the app
     * @param version version of the app
     * @return upon success, status code 200 with an empty response
     */
    @DELETE
    @Path("/{publisher}/{name}/versions/{version}")
    @ReturnType("java.lang.Void")
    public Response deleteAppVersion(@Context HttpContext context,
                                     @PathParam("publisher") String publisher,
                                     @PathParam("name") String name,
                                     @PathParam("version") String version) {

        final CloudAppContext ctx = appContext(context, publisher, name);
        if (ctx.hasResponse()) return ctx.response;

        final CloudAppVersion appVersion = versionDAO.findByUuidAndVersion(ctx.app.getUuid(), version);
        if (appVersion == null) return notFound(name + "/" + version);

        final File appRepository = configuration.getAppRepository(ctx.publisher.getName());
        final AppLayout layout = new AppLayout(appRepository, name, version);
        if (layout.getVersionDir().exists()) {
            // todo: move it to a trash/archive folder it so we can "undo"?
            FileUtils.deleteQuietly(layout.getVersionDir());
        }

        versionDAO.delete(appVersion.getUuid());
        appListingDAO.flush(ctx.app);

        return ok();
    }

    /**
     * Delete an app and all of its versions.
     * @param context used to retrieve the logged-in user session
     * @param publisher name of the publisher
     * @param name name of the app
     * @return upon success, status code 200 with an empty response
     */
    @DELETE
    @Path("/{publisher}/{name}")
    @ReturnType("java.lang.Void")
    public Response deleteApp(@Context HttpContext context,
                              @PathParam("publisher") String publisher,
                              @PathParam("name") String name) {

        final CloudAppContext ctx = appContext(context, publisher, name);
        if (ctx.hasResponse()) return ctx.response;

        for (CloudAppVersion appVersion : versionDAO.findByApp(ctx.app.getUuid())) {
            versionDAO.delete(appVersion.getUuid());
        }

        // todo: move it to a trash/archive folder it so we can "undo"?
        final File appRepository = configuration.getAppRepository(ctx.publisher.getName());
        final AppLayout appLayout = new AppLayout(appRepository, name);
        FileUtils.deleteQuietly(appLayout.getAppDir());
        appDAO.delete(ctx.app.getUuid());
        appListingDAO.flush(ctx.app);

        return ok();
    }

    @GET
    @Path("/{publisher}/{name}"+ApiConstants.EP_FOOTPRINT)
    public Response getFootprints (@Context HttpContext context,
                                   @PathParam("publisher") String publisher,
                                   @PathParam("name") String name) {

        final CloudAppContext ctx = appContext(context, publisher, name, true);
        if (ctx.hasResponse()) return ctx.response;

        final AppFootprint footprint = footprintDAO.findByApp(ctx.app.getUuid());
        return ok(footprint);
    }

    @POST
    @Path("/{publisher}/{name}"+ApiConstants.EP_FOOTPRINT)
    public Response setFootprint (@Context HttpContext context,
                                  @PathParam("publisher") String publisher,
                                  @PathParam("name") String name,
                                  AppFootprint footprint) {

        final CloudAppContext ctx = appContext(context, publisher, name);
        if (ctx.hasResponse()) return ctx.response;

        final AppFootprint existing = footprintDAO.findByApp(ctx.app.getUuid());
        if (existing == null) {
            return Response.ok(footprintDAO.create(footprint)).build();
        } else {
            footprint.setUuid(existing.getUuid());
            return ok(footprintDAO.update(footprint));
        }
    }

    @GET
    @Path("/{publisher}/{name}"+ApiConstants.EP_PRICES)
    public Response getPrices (@Context HttpContext context,
                               @PathParam("publisher") String publisher,
                               @PathParam("name") String name) {

        final CloudAppContext ctx = appContext(context, publisher, name, true);
        if (ctx.hasResponse()) return ctx.response;

        final List<AppPrice> prices = priceDAO.findByApp(ctx.app.getUuid());
        return ok(prices);
    }

    @POST
    @Path("/{publisher}/{name}"+ApiConstants.EP_PRICES)
    public Response setPrice (@Context HttpContext context,
                              @PathParam("publisher") String publisher,
                              @PathParam("name") String name,
                              AppPrice price) {

        final CloudAppContext ctx = appContext(context, publisher, name);
        if (ctx.hasResponse()) return ctx.response;

        final AppPrice existing = priceDAO.findByAppAndCurrency(ctx.app.getUuid(), price.getIsoCurrency());
        if (existing == null) {
            return ok(priceDAO.create(price));
        } else {
            price.setUuid(existing.getUuid());
            return ok(priceDAO.update(price));
        }
    }

    protected CloudAppContext appContext(HttpContext context, String publisher, String name) {
        return appContext(context, publisher, name, false);
    }

    protected CloudAppContext appContext(HttpContext context, String publisher, String name, boolean allowNonmembers) {
        return new CloudAppContext(publisherDAO, memberDAO, appDAO, context, publisher, name, allowNonmembers);
    }

    @AllArgsConstructor
    private class AppStoreAssetUrlGenerator implements AppAssetUrlGenerator {
        private final String publisher;
        @Override public String generateBaseUrl(String app, String version) {
            return configuration.getPublisherAssetBase(publisher) + "/" + app + "/" + version + "/";
        }
    }

}
