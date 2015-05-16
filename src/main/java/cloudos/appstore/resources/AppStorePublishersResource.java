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
import java.util.ArrayList;
import java.util.List;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(ApiConstants.PUBLISHERS_ENDPOINT)
@Service @Slf4j
public class AppStorePublishersResource {

    @Autowired private AppStorePublisherDAO publisherDAO;
    @Autowired private AppStorePublisherMemberDAO memberDAO;

    @Autowired private CloudAppDAO cloudAppDAO;
    @Autowired private CloudAppsResource appsResource;

    @GET
    public Response findPublishers (@Context HttpContext context) {

        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();

        final List<AppStorePublisher> publishers = new ArrayList<>();
        final List<AppStorePublisherMember> members = memberDAO.findByAccount(account.getUuid());
        for (AppStorePublisherMember m : members) {
            publishers.add(publisherDAO.findByUuid(m.getPublisher()));
        }

        return Response.ok(publishers).build();
    }

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

    @POST
    @Path("/{uuid}")
    public Response updatePublisher (@Context HttpContext context,
                                     @PathParam("uuid") String uuid,
                                     AppStorePublisher updated) {

        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();

        final AppStorePublisher publisher = publisherDAO.findByUuid(uuid);
        if (publisher == null) return ResourceUtil.notFound(uuid);

        if (!account.isAdmin()) {
            if (!isActiveMember(account.getUuid(), publisher.getUuid())) return ResourceUtil.notFound(uuid);
            updated.setOwner(publisher.getOwner());
        }

        return Response.ok(publisher).build();
    }

    private AppStorePublisherMember getMember(String account, String publisher) {
        return getMember(account, publisher, memberDAO);
    }

    public static AppStorePublisherMember getMember(String account, String publisher, AppStorePublisherMemberDAO memberDAO) {
        return memberDAO.findByAccountAndPublisher(account, publisher);
    }

    private boolean isMember (String account, String publisher) {
        return getMember(account, publisher) != null;
    }

    private boolean isActiveMember (String account, String publisher) {
        final AppStorePublisherMember member = getMember(account, publisher);
        return member != null && member.isActive();
    }

    public static boolean isMember(String account, String publisher, AppStorePublisherMemberDAO memberDAO) {
        return getMember(account, publisher, memberDAO) != null;
    }

    public static boolean isActiveMember(String account, String publisher, AppStorePublisherMemberDAO memberDAO) {
        final AppStorePublisherMember member = getMember(account, publisher, memberDAO);
        return member != null && member.isActive();
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
        final List<AppStorePublisherMember> members = memberDAO.findByPublisher(publisher.getUuid());
        for (AppStorePublisherMember m : members) {
            memberDAO.delete(m.getUuid());
        }

        // cannot delete "self" publisher
        if (!publisher.getUuid().equals(account.getUuid())) {
            publisherDAO.delete(uuid);
        }

        return Response.ok().build();
    }
}
