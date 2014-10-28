package cloudos.appstore.resources;

import cloudos.appstore.ApiConstants;
import cloudos.appstore.dao.CloudAccountDAO;
import cloudos.appstore.model.AppStoreCloudAccount;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.dao.DAO;
import org.cobbzilla.wizard.resources.AbstractResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.Consumes;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(ApiConstants.CLOUDS_ENDPOINT)
@Service @Slf4j
public class CloudAccountsResource extends AbstractResource<AppStoreCloudAccount> {

    @Autowired private CloudAccountDAO cloudAccountDAO;

    @Override protected DAO<AppStoreCloudAccount> dao() { return cloudAccountDAO; }
    @Override protected String getEndpoint() { return ApiConstants.CLOUDS_ENDPOINT; }

}
