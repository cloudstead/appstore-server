package cloudos.appstore.resources;

import cloudos.appstore.ApiConstants;
import cloudos.appstore.dao.AppListingDAO;
import cloudos.appstore.dao.AppStorePublisherDAO;
import cloudos.appstore.dao.AppStorePublisherMemberDAO;
import cloudos.appstore.model.AppStoreAccount;
import cloudos.appstore.model.AppStorePublisher;
import cloudos.appstore.model.AppStorePublisherMember;
import cloudos.appstore.model.support.AppListing;
import cloudos.appstore.model.support.AppStoreQuery;
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
import java.util.List;

import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(ApiConstants.APPSTORE_ENDPOINT)
@Service @Slf4j
public class AppStoreResource {

    @Autowired private AppListingDAO appListingDAO;
    @Autowired private AppStorePublisherMemberDAO memberDAO;
    @Autowired private AppStorePublisherDAO publisherDAO;

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

        final AppStoreAccount account = optionalUserPrincipal(context);
        List<AppStorePublisherMember> memberships = null;
        if (account != null) {
            memberships = memberDAO.findByAccount(account.getUuid());
        }
        final AppStorePublisher appPublisher = publisherDAO.findByName(publisher);

        final AppListing app = appListingDAO.findAppListing(appPublisher, name, account, memberships);
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
    public Response findApp (@Context HttpContext context,
                             @PathParam("publisher") String publisher,
                             @PathParam("name") String name,
                             @PathParam("version") String version) {

        final AppStoreAccount account = optionalUserPrincipal(context);
        List<AppStorePublisherMember> memberships = null;
        if (account != null) {
            memberships = memberDAO.findByAccount(account.getUuid());
        }
        final AppStorePublisher appPublisher = publisherDAO.findByName(publisher);

        final AppListing app = appListingDAO.findAppListing(appPublisher, name, version, account, memberships);
        if (app == null) return notFound();

        return ok(app);
    }

}
