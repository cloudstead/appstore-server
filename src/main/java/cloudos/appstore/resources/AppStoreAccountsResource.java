package cloudos.appstore.resources;

import com.sun.jersey.api.core.HttpContext;
import lombok.extern.slf4j.Slf4j;
import cloudos.appstore.ApiConstants;
import cloudos.appstore.model.AppStoreAccount;
import cloudos.appstore.model.AppStorePublisher;
import cloudos.appstore.model.AppStorePublisherMember;
import cloudos.appstore.dao.ApiTokenDAO;
import cloudos.appstore.dao.AppStoreAccountDAO;
import cloudos.appstore.dao.AppStorePublisherDAO;
import cloudos.appstore.dao.AppStorePublisherMemberDAO;
import org.cobbzilla.wizard.resources.ResourceUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(ApiConstants.ACCOUNTS_ENDPOINT)
@Service @Slf4j
public class AppStoreAccountsResource {

    @Autowired private ApiTokenDAO tokenDAO;
    @Autowired private AppStoreAccountDAO accountDAO;
    @Autowired private AppStorePublisherDAO publisherDAO;
    @Autowired private AppStorePublisherMemberDAO memberDAO;
    @Autowired private AppStorePublishersResource publishersResource;

    @GET
    public Response findAccount (@Context HttpContext context) {
        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();
        return Response.ok(account).build();
    }

    @GET
    @Path("/{uuid}")
    public Response findAccount (@Context HttpContext context,
                                 @PathParam("uuid") String uuid) {
        AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();
        if (!account.getUuid().equals(uuid)) {
            if (!account.isAdmin()) return ResourceUtil.forbidden();
            account = accountDAO.findByUuid(uuid);
            if (account == null) return ResourceUtil.notFound(uuid);
        }
        return Response.ok(account).build();
    }

    @DELETE
    public Response deleteAccount(@Context HttpContext context) {

        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();

        Response deleteStatus;

        // delete any publishers owned by the member (this will also delete all members and apps for those publishers)
        for (AppStorePublisher publisher : publisherDAO.findByOwner(account.getUuid())) {
            final String publisherUuid = publisher.getUuid();
            deleteStatus = publishersResource.deletePublisher(context, publisherUuid);
            if (deleteStatus.getStatus() != 200) {
                return deleteStatus;
            }
        }

        // find any other memberships (in publishers not owned by this account)
        final List<AppStorePublisherMember> members = memberDAO.findByAccount(account.getUuid());
        for (AppStorePublisherMember m : members) {
            memberDAO.delete(m.getUuid());
        }

        // nuke 'em
        accountDAO.delete(account.getUuid());

        // remove the current API token
        tokenDAO.cancel(account.getApiToken());

        return Response.ok().build();
    }
}
