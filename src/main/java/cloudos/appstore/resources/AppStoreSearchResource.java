package cloudos.appstore.resources;

import cloudos.appstore.ApiConstants;
import cloudos.appstore.dao.AppStoreAccountDAO;
import cloudos.appstore.dao.AppStorePublisherDAO;
import cloudos.appstore.dao.CloudAccountDAO;
import cloudos.appstore.model.AppStoreAccount;
import cloudos.appstore.model.support.AppStoreQuery;
import com.sun.jersey.api.core.HttpContext;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.dao.SearchResults;
import org.cobbzilla.wizard.resources.ResourceUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(ApiConstants.SEARCH_ENDPOINT)
@Service @Slf4j
public class AppStoreSearchResource {

    @Autowired private AppStoreAccountDAO accountDAO;
    @Autowired private AppStorePublisherDAO publisherDAO;
    @Autowired private CloudAccountDAO cloudAccountDAO;

    @POST
    public Response search (@Context HttpContext context,
                            AppStoreQuery query) {

        // sanity check -- must be admin
        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();
        if (!account.isAdmin()) return ResourceUtil.forbidden();

        final SearchResults results;
        switch (query.getType()) {
            case account:
                results = accountDAO.search(query);
                break;
            case publisher:
                results = publisherDAO.search(query);
                break;
            case cloud:
                results = cloudAccountDAO.search(query);
                break;
            default:
                return ResourceUtil.invalid("err.type.invalid");
        }

        return Response.ok(results).build();
    }

}
