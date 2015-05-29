package cloudos.appstore.resources;

import cloudos.appstore.ApiConstants;
import cloudos.appstore.dao.AppStorePublisherDAO;
import cloudos.appstore.dao.AppStorePublisherMemberDAO;
import cloudos.appstore.dao.CloudAppDAO;
import cloudos.appstore.model.AppStoreAccount;
import cloudos.appstore.model.AppStorePublisher;
import cloudos.appstore.model.AppStorePublisherMember;
import cloudos.appstore.model.CloudApp;
import com.qmino.miredot.annotations.ReturnType;
import com.sun.jersey.api.core.HttpContext;
import lombok.extern.slf4j.Slf4j;
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
@Path(ApiConstants.PUBLISHERS_ENDPOINT)
@Service @Slf4j
public class AppStorePublishersResource {

    @Autowired private AppStorePublisherDAO publisherDAO;
    @Autowired private AppStorePublisherMemberDAO memberDAO;

    @Autowired private CloudAppDAO cloudAppDAO;
    @Autowired private CloudAppsResource appsResource;

    /**
     * Find publishers that the current account is a member of
     * @param context used to retrieve the logged-in user session
     * @return a List of AppStorePublisher objects
     */
    @GET
    @ReturnType("java.lang.List<cloudos.appstore.model.AppStorePublisher>")
    public Response findPublishers (@Context HttpContext context) {

        final AppStoreAccount account = userPrincipal(context);

        final List<AppStorePublisherMember> members = memberDAO.findByAccount(account.getUuid());
        final List<AppStorePublisher> publishers = publisherDAO.findByUuids(AppStorePublisherMember.toPublisher(members));

        return ok(publishers);
    }

    /**
     * Find a single publisher by name or UUID
     * @param context used to retrieve the logged-in user session
     * @param uuid UUID or name of the publisher
     * @return the AppStorePublisher
     */
    @GET
    @Path("/{uuid}")
    @ReturnType("cloudos.appstore.model.AppStorePublisher")
    public Response findPublisher (@Context HttpContext context,
                                   @PathParam("uuid") String uuid) {

        final AppStoreAccount account = userPrincipal(context);

        AppStorePublisher publisher = publisherDAO.findByUuid(uuid);
        if (publisher == null) publisher = publisherDAO.findByName(uuid);
        if (publisher == null) return notFound(uuid);

        if (!isMember(account.getUuid(), publisher.getUuid())) return notFound(uuid);

        return ok(publisher);
    }

    /**
     * Update a publisher
     * @param context used to retrieve the logged-in user session
     * @param uuid UUID or name of the publisher
     * @param updated The new publisher information
     * @return The updated AppStorePublisher
     */
    @POST
    @Path("/{uuid}")
    @ReturnType("cloudos.appstore.model.AppStorePublisher")
    public Response updatePublisher (@Context HttpContext context,
                                     @PathParam("uuid") String uuid,
                                     AppStorePublisher updated) {

        final AppStoreAccount account = userPrincipal(context);

        final AppStorePublisher publisher = publisherDAO.findByUuid(uuid);
        if (publisher == null) return notFound(uuid);

        if (!account.isAdmin()) {
            if (!isActiveMember(account.getUuid(), publisher.getUuid())) return notFound(uuid);
            updated.setOwner(publisher.getOwner());
        }

        return ok(publisher);
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

    /**
     * Delete a publisher, including all apps and members
     * @param context used to retrieve the logged-in user session
     * @param uuid the UUID of the publisher to delete (cannot use name here)
     * @return nothing, just an HTTP status code
     */
    @DELETE
    @Path("/{uuid}")
    @ReturnType("java.lang.Void")
    public Response deletePublisher(@Context HttpContext context,
                                    @PathParam("uuid") String uuid) {

        final AppStoreAccount account = userPrincipal(context);
        final AppStorePublisher publisher = publisherDAO.findByUuid(uuid);

        if (!publisher.getOwner().equals(account.getUuid())) return forbidden();

        Response deleteResponse;
        final List<CloudApp> apps = cloudAppDAO.findByPublisher(uuid);
        for (CloudApp app : apps) {
            deleteResponse = appsResource.deleteApp(context, publisher.getName(), app.getName());
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

        return ok();
    }
}
