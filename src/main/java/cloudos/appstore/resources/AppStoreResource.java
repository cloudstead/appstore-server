package cloudos.appstore.resources;

import cloudos.appstore.ApiConstants;
import cloudos.appstore.dao.*;
import cloudos.appstore.model.*;
import cloudos.appstore.model.support.AppListing;
import com.sun.jersey.api.core.HttpContext;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.dao.SearchResults;
import org.cobbzilla.wizard.model.ResultPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

import static org.cobbzilla.wizard.resources.ResourceUtil.notFound;
import static org.cobbzilla.wizard.resources.ResourceUtil.userPrincipal;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(ApiConstants.APPSTORE_ENDPOINT)
@Service @Slf4j
public class AppStoreResource {

    @Autowired private PublishedAppDAO publishedAppDAO;
    @Autowired private AppStorePublisherMemberDAO memberDAO;
    @Autowired private AppStorePublisherDAO publisherDAO;
    @Autowired private AppFootprintDAO footprintDAO;
    @Autowired private AppPriceDAO priceDAO;

    @POST
    public Response findApps (@Context HttpContext context,
                              ResultPage page) {

        final AppStoreAccount account = userPrincipal(context);
        List<AppStorePublisherMember> memberships = null;
        if (account != null) {
            memberships = memberDAO.findByAccount(account.getUuid());
        }

        page = (page == null) ? new ResultPage() : page;
        final SearchResults<PublishedApp> apps = publishedAppDAO.search(account, memberships, page);
        final List<AppListing> listings = new ArrayList<>(apps.size());
        for (PublishedApp app : apps.getResults()) {
            final AppListing listing = getAppListing(app);
            listings.add(listing);
        }
        return Response.ok(new SearchResults<>(listings, apps.size())).build();
    }

    private AppListing getAppListing(PublishedApp app) {

        final String appName = app.getAppName();

        // todo: use promises to parallelize these lookups
        final AppStorePublisher publisher = publisherDAO.findByUuid(app.getPublisher());
        final List<AppPrice> prices = priceDAO.findByApp(appName);
        final AppFootprint footprint = footprintDAO.findByApp(appName);

        return new AppListing()
                .setApp(app)
                .setPublisher(publisher)
                .setFootprint(footprint)
                .setPrices(prices);
    }

    @GET
    @Path("/{publisher}/{name}/{version}")
    public Response findApp (@Context HttpContext context,
                             @PathParam("publisher") String publisher,
                             @PathParam("name") String name,
                             @PathParam("version") String version) {

        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();
        List<AppStorePublisherMember> memberships = null;
        if (account != null) {
            memberships = memberDAO.findByAccount(account.getUuid());
        }
        final AppStorePublisher appPublisher = publisherDAO.findByName(publisher);

        final PublishedApp app = publishedAppDAO.findByNameAndVersion(account, appPublisher, memberships, name, version);
        if (app == null) return notFound();

        final AppListing appListing = getAppListing(app);

        return Response.ok(appListing).build();
    }

}
