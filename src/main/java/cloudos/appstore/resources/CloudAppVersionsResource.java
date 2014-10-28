package cloudos.appstore.resources;

import com.sun.jersey.api.core.HttpContext;
import lombok.extern.slf4j.Slf4j;
import cloudos.appstore.ApiConstants;
import cloudos.appstore.dao.AppStorePublisherMemberDAO;
import cloudos.appstore.dao.CloudAppDAO;
import cloudos.appstore.dao.CloudAppVersionDAO;
import cloudos.appstore.dao.PublishedAppDAO;
import cloudos.appstore.server.AppStoreApiConfiguration;
import cloudos.appstore.server.AppStoreConfiguration;
import cloudos.appstore.model.*;
import org.cobbzilla.util.string.StringUtil;
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
import java.util.ArrayList;
import java.util.List;

import static cloudos.appstore.ValidationConstants.*;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(ApiConstants.APP_VERSIONS_ENDPOINT)
@Slf4j @Service
public class CloudAppVersionsResource {

    @Autowired private CloudAppDAO appDAO;
    @Autowired private CloudAppVersionDAO versionDAO;
    @Autowired private PublishedAppDAO publishedAppDAO;
    @Autowired private AppStorePublisherMemberDAO memberDAO;
    @Autowired private AppStoreApiConfiguration configuration;

    @GET
    @Path("/{uuid}")
    public Response findAppVersion(@Context HttpContext context,
                                   @PathParam("uuid") String uuid) {

        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();

        final CloudAppVersion version = versionDAO.findByUuid(uuid);
        if (version == null) return ResourceUtil.notFound("version: "+uuid);

        final CloudApp app = appDAO.findByUuid(version.getApp());
        if (app == null) return ResourceUtil.notFound("app: "+version.getApp());

        if (!verifyMembership(app, account)) {
            return ResourceUtil.notFound("version: "+uuid);
        }

        return Response.ok(version).build();
    }

    @PUT
    public Response defineAppVersion(@Context HttpContext context,
                                     @Valid CloudAppVersion proposed) {

        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();

        final List<ConstraintViolationBean> violations = new ArrayList<>(3);

        // new apps should not have a UUID set
        if (proposed.hasUuid()) violations.add(new ConstraintViolationBean(ERR_PROPOSED_APP_HAS_UUID));

        final CloudApp app = appDAO.findByUuid(proposed.getApp());
        if (app == null) return ResourceUtil.notFound("app: "+proposed.getApp());

        // verify appropriate account membership and linked asset SHAs
        if (!verifyMembership(app, account)) {
            throw new SimpleViolationException(ERR_APP_PUBLISHER_INVALID);
        }
        verifyAssets(proposed, violations);

        if (!violations.isEmpty()) return ResourceUtil.invalid(violations);

        // new apps always start in new
        proposed.setAppStatus(CloudAppStatus.NEW);
        proposed.setAuthor(account.getUuid());
        final CloudAppVersion created = versionDAO.create(proposed);

        return Response.ok(created).build();
    }

    @POST
    @Path("/{uuid}")
    public Response updateAppVersion(@Context HttpContext context,
                                     @PathParam("uuid") String uuid,
                                     @QueryParam("force") String force,
                                     @Valid CloudAppVersion proposed) {

        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();

        final CloudAppVersion version = versionDAO.findByUuid(uuid);
        if (version == null) return ResourceUtil.notFound("version: "+proposed.getUuid());

        final CloudApp app = appDAO.findByUuid(proposed.getApp());
        if (app == null) return ResourceUtil.notFound("app: "+proposed.getApp());

        if (!verifyMembership(app, account)) {
            throw new SimpleViolationException(ERR_APP_PUBLISHER_INVALID);
        }

        // if the version is not changing, then the only things
        // you can change is the mutable data and the status
        if (proposed.getVersion().equals(version.getVersion())) {

            final List<ConstraintViolationBean> violations = new ArrayList<>(3);

            // server config CANNOT be changed, create a new version instead
            if (!proposed.getBundleUrlSha().equals(version.getBundleUrlSha())
                    || !proposed.getBundleUrl().equals(version.getBundleUrl())) {
                violations.add(new ConstraintViolationBean(ERR_APP_CANNOT_CHANGE_SERVER_CONFIG));
            }
            // force verifyAssets to return no violations for serverConfig (it will check existing assets)
            // we just validated it manually above
            proposed.setBundleUrlSha(version.getBundleUrlSha());
            proposed.setBundleUrl(version.getBundleUrl());

            verifyAssets(proposed, violations);
            if (!violations.isEmpty()) return ResourceUtil.invalid(violations);

            version.setData(proposed.getData());

            // is this a go-live request?
            if (   version.getAppStatus() != CloudAppStatus.PUBLISHED
                && proposed.getAppStatus() == CloudAppStatus.PUBLISHED) {

                // only admins can go live
                if (!account.isAdmin()) return ResourceUtil.forbidden();

                // update active version of app
                app.setActiveVersion(proposed.getUuid());
                appDAO.update(app);

                // publish the app (will create or overwrite)
                final PublishedApp pub = new PublishedApp(version);
                pub.setUuid(app.getUuid()); // ensures 1:1 between PublishedApp:CloudApp
                pub.setApprovedBy(account.getUuid());
                publishedAppDAO.upsert(pub);

                // is this a turn-down request?
            } else if (version.getAppStatus() == CloudAppStatus.PUBLISHED
                    && proposed.getAppStatus() != CloudAppStatus.PUBLISHED) {

                // are they turning down an active version?
                if (app.getActiveVersion().equals(version.getUuid())) {
                    // only proceed if the force param has been sent
                    if (StringUtil.empty(force)) throw new SimpleViolationException(ERR_APP_CANNOT_UNPUBLISH_ACTIVE_VERSION);
                    app.setActiveVersion(null);
                    appDAO.update(app);
                }
                publishedAppDAO.delete(app.getUuid());
            }

            version.setAppStatus(proposed.getAppStatus());
            version.setAuthor(account.getUuid());
            final CloudAppVersion updated = versionDAO.update(version);
            return Response.ok(updated).build();

        } else {
            // if the version is changing, we are really creating a new version
            proposed.setUuid(null);
            return defineAppVersion(context, proposed);
        }
    }

    @DELETE
    @Path("/{uuid}")
    public Response deleteAppVersion(@Context HttpContext context,
                                     @PathParam("uuid") String uuid) {

        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();

        final CloudAppVersion version = versionDAO.findByUuid(uuid);
        if (version == null) return ResourceUtil.notFound();

        final CloudApp app = appDAO.findByUuid(version.getApp());

        if (!verifyMembership(app, account)) {
            throw new SimpleViolationException(ERR_APP_PUBLISHER_INVALID);
        }

        if (app.getActiveVersion().equals(uuid)) {
            throw new SimpleViolationException(ERR_APP_CANNOT_UNPUBLISH_ACTIVE_VERSION);
        }

        versionDAO.delete(uuid);
        return Response.ok().build();
    }

    private boolean verifyMembership(CloudApp app, AppStoreAccount account) {
        return AppStorePublishersResource.isMember(account.getUuid(), app.getPublisher(), memberDAO) || account.isAdmin();
    }

    private void verifyAssets(CloudAppVersion proposed, List<ConstraintViolationBean> violations) {
        final AppStoreConfiguration storeConfig = configuration.getAppStore();
        final AppMutableData data = proposed.getData();
        if (data == null) {
            violations.add(new ConstraintViolationBean(ERR_DATA_EMPTY));
            return;
        }
        AssetUtil.verifyAsset(SMALL_ICON_URL, data.getSmallIconUrl(), data.getSmallIconUrlSha(), violations, storeConfig.getAllowedAssetSchemes());
        AssetUtil.verifyAsset(LARGE_ICON_URL, data.getLargeIconUrl(), data.getLargeIconUrlSha(), violations, storeConfig.getAllowedAssetSchemes());
        AssetUtil.verifyAsset(BUNDLE_URL, proposed.getBundleUrl(), proposed.getBundleUrlSha(), violations, storeConfig.getAllowedAssetSchemes());
    }

}