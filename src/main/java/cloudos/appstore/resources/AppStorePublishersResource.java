package cloudos.appstore.resources;

import com.sun.jersey.api.core.HttpContext;
import lombok.extern.slf4j.Slf4j;
import cloudos.appstore.ApiConstants;
import cloudos.appstore.model.AppStoreAccount;
import cloudos.appstore.model.AppStorePublisher;
import cloudos.appstore.model.AppStorePublisherMember;
import cloudos.appstore.model.CloudApp;
import cloudos.appstore.dao.*;
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
@Path(ApiConstants.PUBLISHERS_ENDPOINT)
@Service @Slf4j
public class AppStorePublishersResource {

    @Autowired private ApiTokenDAO apiTokenDAO;
    @Autowired private AppStoreAccountDAO appStoreAccountDAO;
    @Autowired private AppStorePublisherDAO publisherDAO;
    @Autowired private AppStorePublisherMemberDAO publisherMemberDAO;

    @Autowired private CloudAppDAO cloudAppDAO;
    @Autowired private CloudAppsResource appsResource;

    @GET
    @Path("/{uuid}")
    public Response findPublisher (@Context HttpContext context,
                                   @PathParam("uuid") String uuid) {

        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();

        final AppStorePublisher publisher = publisherDAO.findByUuid(uuid);
        if (publisher == null) return ResourceUtil.notFound(uuid);

        if (!isMember(account.getUuid(), publisher.getUuid())) return ResourceUtil.notFound(uuid);

        return Response.ok(publisher).build();
    }

    private AppStorePublisherMember getMember(String account, String publisher) {
        return getMember(account, publisher, publisherMemberDAO);
    }

    public static AppStorePublisherMember getMember(String account, String publisher, AppStorePublisherMemberDAO memberDAO) {
        return memberDAO.findByAccountAndPublisher(account, publisher);
    }

    private boolean isMember (String account, String publisher) {
        return getMember(account, publisher) != null;
    }

    public static boolean isMember(String account, String publisher, AppStorePublisherMemberDAO memberDAO) {
        return getMember(account, publisher, memberDAO) != null;
    }

    @DELETE
    @Path("/{uuid}")
    public Response deletePublisher(@Context HttpContext context,
                                    @PathParam("uuid") String uuid) {

        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();
        final AppStorePublisher publisher = publisherDAO.findByUuid(uuid);

        if (!publisher.getOwner().equals(account.getUuid())) {
            return ResourceUtil.forbidden();
        }

        Response deleteResponse;
        final List<CloudApp> apps = cloudAppDAO.findByPublisher(uuid);
        for (CloudApp app : apps) {
            deleteResponse = appsResource.deleteApp(context, app.getUuid());
            if (deleteResponse.getStatus() != 200) return deleteResponse;
        }

        // find any memberships
        final List<AppStorePublisherMember> members = publisherMemberDAO.findByPublisher(publisher.getUuid());
        for (AppStorePublisherMember m : members) {
            publisherMemberDAO.delete(m.getUuid());
        }

        publisherDAO.delete(uuid);

        return Response.ok().build();
    }
}
