package cloudos.appstore.resources;

import cloudos.appstore.ApiConstants;
import cloudos.appstore.dao.*;
import cloudos.appstore.model.*;
import cloudos.appstore.model.app.AppLayout;
import cloudos.appstore.model.app.AppManifest;
import cloudos.appstore.model.support.AppBundle;
import cloudos.appstore.model.support.CloudAppVersion;
import cloudos.appstore.model.support.DefineCloudAppRequest;
import cloudos.appstore.server.AppStoreApiConfiguration;
import com.qmino.miredot.annotations.ReturnType;
import com.sun.jersey.api.core.HttpContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.cobbzilla.util.io.Tarball;
import org.cobbzilla.util.security.ShaUtil;
import org.cobbzilla.wizard.model.SemanticVersion;
import org.cobbzilla.wizard.resources.ResourceUtil;
import org.cobbzilla.wizard.validation.ConstraintViolationBean;
import org.cobbzilla.wizard.validation.SimpleViolationException;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cloudos.appstore.ValidationConstants.ERR_APP_CANNOT_DELETE_ACTIVE_VERSION;
import static cloudos.appstore.ValidationConstants.ERR_APP_PUBLISHER_INVALID;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.string.StringUtil.empty;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(ApiConstants.APPS_ENDPOINT)
@Service @Slf4j
public class CloudAppsResource {

    @Autowired private AppStoreApiConfiguration configuration;
    @Autowired private CloudAppDAO appDAO;
    @Autowired private PublishedAppDAO publishedAppDAO;
    @Autowired private AppStorePublisherDAO publisherDAO;
    @Autowired private AppStorePublisherMemberDAO memberDAO;
    @Autowired private AppFootprintDAO footprintDAO;
    @Autowired private AppPriceDAO priceDAO;

    /**
     * Find all apps that are editable by the current user. The versions for each app will *not* be populated.
     * @param context used to retrieve the logged-in user session
     * @return a Map of AppStorePublisher-&gt;List&lt;CloudApp&gt;
     */
    @GET
    @ReturnType("java.util.Map<cloudos.appstore.model.AppStorePublisher, cloudos.appstore.model.CloudApp>")
    public Response findApps (@Context HttpContext context) {

        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();

        final List<AppStorePublisherMember> memberships = memberDAO.findActiveByAccount(account.getUuid());
        final Map<AppStorePublisher, List<CloudApp>> apps = new HashMap<>();

        for (AppStorePublisherMember m : memberships) {
            apps.put(publisherDAO.findByUuid(m.getPublisher()), appDAO.findByPublisher(m.getPublisher()));
        }

        return Response.ok(apps).build();
    }

    /**
     * Find a single app and all of its versions
     * @param context used to retrieve the logged-in user session
     * @param name name of the app to find
     * @return the CloudApp
     */
    @GET
    @Path("/{name}")
    @ReturnType("cloudos.appstore.model.CloudApp")
    public Response findApp (@Context HttpContext context,
                             @PathParam("name") String name) {

        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();

        final CloudApp app = appDAO.findByName(name);
        if (app == null) return ResourceUtil.notFound(name);

        if (!isMember(account, app)) return ResourceUtil.forbidden();

        final File appRepository = configuration.getAppStore().getAppRepository();
        final AppLayout appLayout = new AppLayout(appRepository, app.getName());
        app.setVersions(appLayout.getVersions());

        return Response.ok(app).build();
    }

    /**
     * Upload a new version of an app (this could be the very first version of an app, or an upgrade version)
     * @param context used to retrieve the logged-in user session
     * @param request the app to define
     * @return the version of the app
     */
    @POST
    @ReturnType("cloudos.appstore.model.support.CloudAppVersion")
    public Response defineAppVersion(@Context HttpContext context,
                                     @Valid DefineCloudAppRequest request) {

        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();

        final List<ConstraintViolationBean> violations = new ArrayList<>();
        final AppBundle bundle;
        try {
            bundle = new AppBundle(request.getBundleUrl(), request.getBundleUrlSha(), configuration.getAppStore().getAssetUrlBase(), violations);
        } catch (Exception e) {
            log.error("defineAppVersion (violations="+violations+"): "+e, e);
            if (!empty(violations)) return ResourceUtil.invalid(violations);
            return Response.serverError().build();
        }

        final AppManifest manifest = bundle.getManifest();
        final String publisherName = request.hasPublisher() ? request.getPublisher() : account.getName();

        AppStorePublisher publisher = publisherDAO.findByName(publisherName);
        if (publisher == null) {
            bundle.cleanup();
            return ResourceUtil.invalid();
        }

        CloudApp app = appDAO.findByName(manifest.getName());
        if (app == null) {
            app = (CloudApp) new CloudApp()
                    .setAuthor(account.getUuid())
                    .setPublisher(publisher.getUuid())
                    .setName(manifest.getName());
            app = appDAO.create(app);
        } else {
            if (!publisher.getUuid().equals(app.getPublisher())) {
                // cannot change ownership this way
                bundle.cleanup();
                return ResourceUtil.invalid("{err.defineApp.cannotChangePublisher}");
            }
            if (!isMember(account, app)) {
                bundle.cleanup();
                return ResourceUtil.forbidden(); // no permissions on this app
            }
        }

        // This is where it should live in the main repository... anything already there?
        final File appRepository = configuration.getAppStore().getAppRepository();
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
        manifest.setVersion(proposedVersion);
        bundle.writeManifest();

        // re-roll the tarball
        final AppLayout finalAppLayout = new AppLayout(appRepository, manifest);
        final File bundleTarball;
        try {
            bundleTarball = Tarball.roll(bundle.getBundleDir());

        } catch (IOException e) {
            bundle.cleanup();
            final String msg = "{err.defineApp.rerollingBundleTarball}";
            log.error(msg, e);
            return Response.serverError().build();
        }

        if (!finalAppLayout.getVersionDir().exists() && !finalAppLayout.getVersionDir().mkdirs()) {
            final String msg = "{err.defineApp.creatingVersionDir}";
            log.error(msg+": "+abs(finalAppLayout.getVersionDir()));
            return Response.serverError().build();
        }
        if (!bundleTarball.renameTo(finalAppLayout.getBundleFile())) {
            final String msg = "{err.defineApp.renamingBundleTarball}";
            log.error(msg);
            return Response.serverError().build();
        }

        // write appstore metadata
        final AppStoreAppMetadata metadata = new AppStoreAppMetadata()
                .setBundleSha(ShaUtil.sha256_file(finalAppLayout.getBundleFile()))
                .setStatus(CloudAppStatus.created);
        metadata.write(finalAppLayout.getVersionDir());

        return Response.ok(new CloudAppVersion(app.getName(), proposedVersion)).build();
    }

    /**
     * Retrieve metadata associated with a particular app version
     * @param context used to retrieve the logged-in user session
     * @param name name of the app
     * @param version version of the app
     * @return The AppStoreAppMetadata associated with the app version
     */
    @GET
    @Path("/{name}/versions/{version}/metadata")
    @ReturnType("cloudos.appstore.model.AppStoreAppMetadata")
    public Response updateStatus(@Context HttpContext context,
                                 @PathParam("name") String name,
                                 @PathParam("version") String version) {

        final File appRepository = configuration.getAppStore().getAppRepository();
        final AppLayout layout = new AppLayout(appRepository, name, version);
        if (!layout.exists()) return ResourceUtil.notFound(name+"/"+version);
        return Response.ok(AppStoreAppMetadata.read(layout.getVersionDir())).build();
    }

    /**
     * Update status of a particular app version
     * @param context used to retrieve the logged-in user session
     * @param name name of the app
     * @param version version of the app
     * @param status the new status for the app
     * @return the new status for the app
     */
    @POST
    @Path("/{name}/versions/{version}/status")
    public Response updateStatus(@Context HttpContext context,
                                 @PathParam("name") String name,
                                 @PathParam("version") String version,
                                 CloudAppStatus status) {

        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();

        final CloudApp app = appDAO.findByName(name);
        if (!isMember(account, app)) throw new SimpleViolationException(ERR_APP_PUBLISHER_INVALID);

        final AppLayout layout = new AppLayout(configuration.getAppStore().getAppRepository(), name, version);

        final File versionDir = layout.getVersionDir();
        if (!versionDir.exists()) return ResourceUtil.notFound(version);

        final AppStoreAppMetadata metadata = AppStoreAppMetadata.read(versionDir);
        if (metadata.getStatus() == status) {
            log.info("No status change, returning ("+status+")");
            return Response.notModified().build();
        }

        final boolean shouldRefreshApps = metadata.isPublished() || status.isPublished();

        try {
            switch (status) {
                case created:
                    log.warn("Cannot move an app back to 'created' status");
                    return ResourceUtil.invalid("{err.app.version.status.invalid}");

                case pending:
                    // they are requesting to publish the app, this is fine
                    metadata.setStatus(status);
                    metadata.write(versionDir);
                    return Response.ok(status).build();

                case published:
                    // admins do this to pending requests, makes the app publicly published
                    if (account.isAdmin()) {
                        metadata.setStatus(status);
                        metadata.setApprovedBy(account.getUuid());
                        metadata.write(versionDir);
                        return Response.ok(status).build();
                    }
                    // non-admins are not allowed to publish
                    return ResourceUtil.forbidden();

                case hidden:
                    // anyone can make an app hidden.
                    // it will not be listed in app store search results, but can still be installed if caller knows the name + version
                    metadata.setStatus(status);
                    metadata.write(versionDir);
                    return Response.ok(status).build();

                case retired:
                    // anyone can make an app retired.
                    // it will not be listed in app store search results and cannot be installed
                    metadata.setStatus(status);
                    metadata.write(versionDir);
                    return Response.ok(status).build();

                default:
                    return ResourceUtil.invalid("{err.app.version.status.invalid}");
            }

        } finally {
            if (shouldRefreshApps) publishedAppDAO.flushApps();
        }
    }

    @DELETE
    @Path("/{name}/versions/{version}")
    public Response deleteAppVersion(@Context HttpContext context,
                                     @PathParam("name") String name,
                                     @PathParam("version") String version) {

        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();

        final CloudApp app = appDAO.findByName(name);
        if (!isMember(account, app)) throw new SimpleViolationException(ERR_APP_PUBLISHER_INVALID);

        if (app.getActiveVersion().equals(version)) throw new SimpleViolationException(ERR_APP_CANNOT_DELETE_ACTIVE_VERSION);

        final AppLayout layout = new AppLayout(configuration.getAppStore().getAppRepository(), name, version);
        if (layout.getVersionDir().exists()) {
            // todo: move it to a trash/archive folder it so we can "undo"?
            FileUtils.deleteQuietly(layout.getVersionDir());
        }

        return Response.ok().build();
    }

    @DELETE
    @Path("/{name}")
    public Response deleteApp(@Context HttpContext context,
                              @PathParam("name") String name) {

        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();

        final CloudApp app = appDAO.findByName(name);
        if (app == null) return ResourceUtil.notFound(name);

        // ensure that the caller is a member of the organization
        if (!isMember(account, app)) return ResourceUtil.forbidden();

        // todo: move it to a trash/archive folder it so we can "undo"?
        final AppLayout appLayout = new AppLayout(configuration.getAppStore().getAppRepository(), name);
        FileUtils.deleteQuietly(appLayout.getAppDir());
        appDAO.delete(app.getUuid());

        return Response.ok().build();
    }

    @GET
    @Path("/{uuid}"+ApiConstants.EP_FOOTPRINT)
    public Response getFootprints (@Context HttpContext context,
                                   @PathParam("uuid") String uuid) {

        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();

        final CloudApp app = appDAO.findByUuid(uuid);
        if (app == null) return ResourceUtil.notFound(uuid);

        // ensure that the caller is a member of the organization
        if (!isMember(account, app)) return ResourceUtil.forbidden();

        final AppFootprint footprint = footprintDAO.findByApp(uuid);
        return Response.ok(footprint).build();
    }

    @POST
    @Path("/{uuid}"+ApiConstants.EP_FOOTPRINT)
    public Response setFootprint (@Context HttpContext context,
                                  @PathParam("uuid") String uuid,
                                  AppFootprint footprint) {

        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();

        final CloudApp app = appDAO.findByUuid(uuid);
        if (app == null) return ResourceUtil.notFound(uuid);

        // ensure that the caller is a member of the organization
        if (!isMember(account, app)) return ResourceUtil.forbidden();

        final AppFootprint existing = footprintDAO.findByApp(footprint.getCloudApp());
        if (existing == null) {
            return Response.ok(footprintDAO.create(footprint)).build();
        } else {
            footprint.setUuid(existing.getUuid());
            return Response.ok(footprintDAO.update(footprint)).build();
        }
    }

    @GET
    @Path("/{uuid}"+ApiConstants.EP_PRICES)
    public Response getPrices (@Context HttpContext context,
                               @PathParam("uuid") String uuid) {

        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();

        final CloudApp app = appDAO.findByUuid(uuid);
        if (app == null) return ResourceUtil.notFound(uuid);

        // ensure that the caller is a member of the organization
        if (!isMember(account, app)) return ResourceUtil.forbidden();

        final List<AppPrice> prices = priceDAO.findByApp(uuid);
        return Response.ok(prices).build();
    }

    @POST
    @Path("/{uuid}"+ApiConstants.EP_PRICES)
    public Response setPrice (@Context HttpContext context,
                              @PathParam("uuid") String uuid,
                              AppPrice price) {

        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();

        final CloudApp app = appDAO.findByUuid(uuid);
        if (app == null) return ResourceUtil.notFound(uuid);

        // ensure that the caller is a member of the organization
        if (!isMember(account, app)) return ResourceUtil.forbidden();

        final AppPrice existing = priceDAO.findByAppAndCurrency(price.getCloudApp(), price.getIsoCurrency());
        if (existing == null) {
            return Response.ok(priceDAO.create(price)).build();
        } else {
            price.setUuid(existing.getUuid());
            return Response.ok(priceDAO.update(price)).build();
        }
    }

    private boolean isMember(AppStoreAccount account, CloudApp app) {
        // caller must be a member of the publisher
        return AppStorePublishersResource.isMember(account.getUuid(), app.getPublisher(), memberDAO)
                || account.isAdmin(); // or an admin
    }

    protected CloudApp findAppByUuidOrName(@PathParam("uuid") String uuid) {
        CloudApp app = appDAO.findByUuid(uuid);
        if (app == null) {
            app = appDAO.findByName(uuid);
            if (app == null) return null;
        }
        return app;
    }

}
