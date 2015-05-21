package cloudos.appstore.resources;

import lombok.extern.slf4j.Slf4j;
import cloudos.appstore.ApiConstants;
import cloudos.appstore.dao.*;
import cloudos.appstore.model.*;
import cloudos.appstore.model.support.AppListing;
import org.cobbzilla.wizard.dao.SearchResults;
import org.cobbzilla.wizard.model.ResultPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(ApiConstants.APPSTORE_ENDPOINT)
@Service @Slf4j
public class AppStoreResource {

    @Autowired private PublishedAppDAO publishedAppDAO;
    @Autowired private CloudAppDAO cloudAppDAO;
    @Autowired private AppStorePublisherDAO publisherDAO;
    @Autowired private AppFootprintDAO footprintDAO;
    @Autowired private AppPriceDAO priceDAO;

    @POST
    public Response findApps (ResultPage page) {
        page = (page == null) ? new ResultPage() : page;
        final SearchResults<PublishedApp> apps = publishedAppDAO.search(page);
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
        final CloudApp cloudApp = cloudAppDAO.findByName(appName);
        final AppStorePublisher publisher = publisherDAO.findByUuid(cloudApp.getPublisher());
        final List<AppPrice> prices = priceDAO.findByApp(appName);
        final AppFootprint footprint = footprintDAO.findByApp(appName);

        return new AppListing()
                .setApp(app)
                .setPublisher(publisher)
                .setFootprint(footprint)
                .setPrices(prices);
    }

    @GET
    @Path("/{name}/{version}")
    public Response findApp (@PathParam("name") String name,
                             @PathParam("version") String version) {

        final PublishedApp app = publishedAppDAO.findByNameAndVersion(name, version);
        final AppListing appListing = getAppListing(app);

        return Response.ok(appListing).build();
    }

}
