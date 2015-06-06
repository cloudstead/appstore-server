package cloudos.appstore.resources;

import cloudos.appstore.ApiConstants;
import cloudos.appstore.dao.*;
import cloudos.appstore.model.AppStoreAccount;
import cloudos.appstore.model.support.AppStoreObjectType;
import cloudos.appstore.model.support.AppStoreQuery;
import com.qmino.miredot.annotations.ReturnType;
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

import static org.cobbzilla.wizard.resources.ResourceUtil.forbidden;
import static org.cobbzilla.wizard.resources.ResourceUtil.ok;
import static org.cobbzilla.wizard.resources.ResourceUtil.userPrincipal;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(ApiConstants.SEARCH_ENDPOINT)
@Service @Slf4j
public class AppStoreSearchResource {

    @Autowired private AppStoreAccountDAO accountDAO;
    @Autowired private AppStorePublisherDAO publisherDAO;
    @Autowired private CloudAccountDAO cloudAccountDAO;
    @Autowired private CloudAppDAO appDAO;
    @Autowired private CloudAppVersionDAO versionDAO;

    /**
     * Admins only. Search just about any appstore object
     * @param context used to retrieve the logged-in user session
     * @param query the query
     * @return a List whose element type depends on the type of query
     */
    @POST
    @ReturnType("java.util.List")
    public Response search (@Context HttpContext context,
                            AppStoreQuery query) {

        // sanity check -- must be admin
        final AppStoreAccount account = userPrincipal(context);
        if (!account.isAdmin()) return forbidden();

        // search apps by default
        if (!query.hasType()) query.setType(AppStoreObjectType.app);

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
            case app:
                results = appDAO.search(query);
                break;
            case version:
                results = versionDAO.search(query);
                break;
            default:
                return ResourceUtil.invalid("err.type.invalid");
        }

        return ok(results);
    }

}
