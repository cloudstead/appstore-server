package cloudos.appstore.resources;

import cloudos.appstore.ApiConstants;
import cloudos.appstore.dao.CloudAccountDAO;
import cloudos.appstore.model.AppStoreCloudAccount;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.dao.DAO;
import org.cobbzilla.wizard.resources.AbstractResource;
import org.cobbzilla.wizard.resources.ResourceUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(ApiConstants.CLOUDS_ENDPOINT)
@Service @Slf4j
public class CloudAccountsResource extends AbstractResource<AppStoreCloudAccount> {

    @Autowired private CloudAccountDAO cloudAccountDAO;

    @Override protected DAO<AppStoreCloudAccount> dao() { return cloudAccountDAO; }
    @Override protected String getEndpoint() { return ApiConstants.CLOUDS_ENDPOINT; }

    @POST
    @Path("/{ucid}")
    public Response findByUcid (@PathParam("ucid") String ucid) {

        final AppStoreCloudAccount cloudAccount = cloudAccountDAO.findByUcid(ucid);
        if (cloudAccount == null) return ResourceUtil.notFound(ucid);

        return Response.ok(cloudAccount).build();
    }
}
