package cloudos.appstore.resources;

import cloudos.appstore.ApiConstants;
import cloudos.appstore.dao.*;
import cloudos.appstore.model.*;
import com.sun.jersey.api.core.HttpContext;
import lombok.extern.slf4j.Slf4j;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cloudos.appstore.ValidationConstants.ERR_APP_PUBLISHER_INVALID;
import static cloudos.appstore.ValidationConstants.ERR_APP_VERSION_ALREADY_EXISTS;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(ApiConstants.APPS_ENDPOINT)
@Service @Slf4j
public class CloudAppsResource {

    @Autowired private CloudAppDAO appDAO;
    @Autowired private CloudAppVersionDAO versionDAO;
    @Autowired private AppStorePublisherDAO publisherDAO;
    @Autowired private AppStorePublisherMemberDAO memberDAO;
    @Autowired private AppFootprintDAO footprintDAO;
    @Autowired private AppPriceDAO priceDAO;

    @PUT
    public Response defineApp(@Context HttpContext context,
                              @Valid CloudApp app) {

        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();

        // verify account is a member of the publisher of the app
        if (!isMember(account, app)) {
            throw new SimpleViolationException(ERR_APP_PUBLISHER_INVALID);
        }

        // validate assets
        final List<ConstraintViolationBean> violations = new ArrayList<>(3);

        // validate that this version does not exist
        final CloudApp existing = appDAO.findByName(app.getName());
        if (existing != null) {
            violations.add(new ConstraintViolationBean(ERR_APP_VERSION_ALREADY_EXISTS));
        }

        if (!violations.isEmpty()) return ResourceUtil.invalid(violations);

        app.setAuthor(account.getUuid());
        app.setActiveVersion(null); // new app -- cannot have an active version
        app = appDAO.create(app);

        return Response.ok(app).build();
    }

    @GET
    public Response findApps (@Context HttpContext context) {

        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();

        final List<AppStorePublisherMember> memberships = memberDAO.findActiveByAccount(account.getUuid());
        final Map<AppStorePublisher, List<CloudApp>> apps = new HashMap<>();

        for (AppStorePublisherMember m : memberships) {
            apps.put(publisherDAO.findByUuid(m.getPublisher()), appDAO.findByPublisher(m.getPublisher()));
        }

        return Response.ok(apps).build();
    }

    @GET
    @Path("/{uuid}")
    public Response findApp (@Context HttpContext context,
                             @PathParam("uuid") String uuid) {

        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();

        final CloudApp app = appDAO.findByUuid(uuid);
        if (app == null) return ResourceUtil.notFound(uuid);

        if (!isMember(account, app)) return ResourceUtil.forbidden();

        return Response.ok(app).build();
    }

    @POST
    @Path("/{uuid}")
    public Response updateApp(@Context HttpContext context,
                              @PathParam("uuid") String uuid,
                              @Valid CloudApp proposed) {

        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();

        // sanity check
        if (!proposed.getUuid().equals(uuid)) return ResourceUtil.forbidden();

        final CloudApp app = appDAO.findByUuid(uuid);
        if (app == null) return ResourceUtil.notFound(uuid);

        // sanity check publisher
        if (!proposed.getPublisher().equals(app.getPublisher())) {
            return ResourceUtil.forbidden();
        }

        if (!isMember(account, app)) return ResourceUtil.forbidden();

        // active version must be live
        final CloudAppVersion version = versionDAO.findByUuid(proposed.getActiveVersion());
        if (version.getAppStatus() != CloudAppStatus.PUBLISHED && !account.isAdmin()) return ResourceUtil.forbidden();

        // sanity checks
        proposed.setUuid(app.getUuid());

        final CloudApp updated = appDAO.update(proposed);

        return Response.ok(updated).build();
    }

    private boolean isMember(AppStoreAccount account, CloudApp app) {
        // caller must be a member of the publisher
        return AppStorePublishersResource.isMember(account.getUuid(), app.getPublisher(), memberDAO)
            || account.isAdmin(); // or an admin
    }

    @DELETE
    @Path("/{uuid}")
    public Response deleteApp(@Context HttpContext context,
                              @PathParam("uuid") String uuid) {

        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();

        final CloudApp app = appDAO.findByUuid(uuid);
        if (app == null) return ResourceUtil.notFound(uuid);

        // ensure that the caller is a member of the organization
        if (!isMember(account, app)) return ResourceUtil.forbidden();

        final List<CloudAppVersion> versions = versionDAO.findByApp(uuid);
        for (CloudAppVersion version : versions) {
            versionDAO.delete(version.getUuid());
        }

        appDAO.delete(uuid);

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

}
