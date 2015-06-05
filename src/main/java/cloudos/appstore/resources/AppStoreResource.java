package cloudos.appstore.resources;

import cloudos.appstore.ApiConstants;
import cloudos.appstore.dao.*;
import cloudos.appstore.model.*;
import cloudos.appstore.model.app.AppLayout;
import cloudos.appstore.model.support.AppListing;
import cloudos.appstore.model.support.AppStoreQuery;
import cloudos.appstore.server.AppStoreApiConfiguration;
import com.qmino.miredot.annotations.ReturnType;
import com.sun.jersey.api.core.HttpContext;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.dao.SearchResults;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(ApiConstants.APPSTORE_ENDPOINT)
@Service @Slf4j
public class AppStoreResource {

    @Autowired private AppListingDAO appListingDAO;
    @Autowired private CloudAppDAO appDAO;
    @Autowired private CloudAppVersionDAO versionDAO;
    @Autowired private AppStorePublisherMemberDAO memberDAO;
    @Autowired private AppStorePublisherDAO publisherDAO;
    @Autowired private AppStoreApiConfiguration configuration;

    /**
     * Search the app store for apps
     * @param context used to retrieve the logged-in user session. OK if there is no session (allow anonymous)
     * @param query the query
     * @return a List of AppListing objects representing the apps found
     */
    @POST
    @ReturnType("java.util.List<cloudos.appstore.model.support.AppListing>")
    public Response findApps (@Context HttpContext context,
                              AppStoreQuery query) {

        final AppStoreAccount account = optionalUserPrincipal(context);
        List<AppStorePublisherMember> memberships = null;
        if (account != null) {
            memberships = memberDAO.findByAccount(account.getUuid());
        }

        query = (query == null) ? new AppStoreQuery() : query;
        final SearchResults<AppListing> apps = appListingDAO.search(account, memberships, query);

        return ok(apps);
    }

    /**
     * Find details about a particular app
     * @param context used to retrieve the logged-in user session. OK if there is no session (allow anonymous)
     * @param publisher the name of the app publisher
     * @param name the name of the app
     * @return a single AppListing, will also include the "availableVersions" field
     */
    @GET
    @Path("/{publisher}/{name}")
    @ReturnType("cloudos.appstore.model.support.AppListing")
    public Response findApp (@Context HttpContext context,
                             @PathParam("publisher") String publisher,
                             @PathParam("name") String name) {

        final CloudAppContext ctx = appContext(context, publisher, name);
        if (ctx.hasResponse()) return ctx.response;

        final List<AppStorePublisherMember> members = ctx.hasAccount() ? memberDAO.findByAccount(ctx.account.getUuid()) : null;
        final AppListing app = appListingDAO.findAppListing(ctx.publisher, name, ctx.account, members);
        if (app == null) return notFound();

        return ok(app);
    }

    /**
     * Find details about a particular app version
     * @param context used to retrieve the logged-in user session. OK if there is no session (allow anonymous)
     * @param publisher the name of the app publisher
     * @param name the name of the app
     * @param version the version of the app
     * @return a single AppListing, will also include the "availableVersions" field
     */
    @GET
    @Path("/{publisher}/{name}/{version}")
    @ReturnType("cloudos.appstore.model.support.AppListing")
    public Response findApp (@Context HttpContext context,
                             @PathParam("publisher") String publisher,
                             @PathParam("name") String name,
                             @PathParam("version") String version) {

        final CloudAppContext ctx = appContext(context, publisher, name);
        if (ctx.hasResponse()) return ctx.response;

        final AppListing app = appListingDAO.findAppListing(ctx.publisher, name, version, ctx.account, memberDAO.findByAccount(ctx.account.getUuid()));
        if (app == null) return notFound();

        return ok(app);
    }

    /**
     * Get an asset from a particular version
     * @param context used to retrieve the logged-in user session. OK if there is no session (allow anonymous)
     * @param publisher the name of the app publisher
     * @param name the name of the app
     * @param version the version of the app
     * @param asset name of the asset: smallIcon, largeIcon, or taskbarIcon
     * @return the asset as a data stream, with appropriate Content-Type set
     */
    @GET
    @Path("/{publisher}/{name}/{version}/{asset}")
    @ReturnType("cloudos.appstore.model.support.AppListing")
    public Response findAsset (@Context HttpContext context,
                               @PathParam("publisher") String publisher,
                               @PathParam("name") String name,
                               @PathParam("version") String version,
                               @PathParam("asset") String asset) {

        if (empty(asset)) return invalid("err.asset.empty");

        final CloudAppContext ctx = appContext(context, publisher, name);
        if (ctx.hasResponse()) return ctx.response;

        final CloudAppVersion appVersion = versionDAO.findByUuidAndVersion(ctx.app.getUuid(), version);
        if (appVersion == null) return notFound(publisher+"/"+name+"/any-published-version");

        final File assetFile = new AppLayout(configuration.getAppRepository(publisher), name, version).findLocalAsset(asset);
        if (assetFile == null) return notFound(publisher+"/"+name+"/"+version+"/"+asset);

        return streamFile(assetFile);
    }

    protected CloudAppContext appContext(HttpContext context, String publisher, String name) {
        return new CloudAppContext(publisherDAO, memberDAO, appDAO, context, publisher, name, true, true);
    }
}
