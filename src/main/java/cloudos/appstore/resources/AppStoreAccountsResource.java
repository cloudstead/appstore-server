package cloudos.appstore.resources;

import com.qmino.miredot.annotations.ReturnType;
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

import static org.cobbzilla.wizard.resources.ResourceUtil.*;

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

    /**
     * View details for the account associated with the session
     * @param context used to retrieve the logged-in user session
     * @return the AppStoreAccount for the current session
     */
    @GET
    @ReturnType("cloudos.appstore.model.AppStoreAccount")
    public Response findAccount (@Context HttpContext context) {
        return ok(userPrincipal(context));
    }

    /**
     * View details for an account. Must be an admin to view accounts other than your own.
     * @param context used to retrieve the logged-in user session
     * @param uuid UUID of the account to find
     * @return the AppStoreAccount for the current session
     */
    @GET
    @Path("/{uuid}")
    @ReturnType("cloudos.appstore.model.AppStoreAccount")
    public Response findAccount (@Context HttpContext context,
                                 @PathParam("uuid") String uuid) {
        AppStoreAccount account = userPrincipal(context);

        if (!account.getUuid().equals(uuid)) {
            if (!account.isAdmin()) return forbidden();
            account = accountDAO.findByUuid(uuid);
            if (account == null) return ResourceUtil.notFound(uuid);
        }
        return ok(account);
    }

    /**
     * Update an account. Must be admin to update an account other than your own.
     * @param context used to retrieve the logged-in user session
     * @param uuid UUID of the account to update
     * @param updated the updated account
     * @return the AppStoreAccount for the current session
     */
    @POST
    @Path("/{uuid}")
    public Response updateAccount (@Context HttpContext context,
                                   @PathParam("uuid") String uuid,
                                   AppStoreAccount updated) {
        AppStoreAccount account = userPrincipal(context);

        if (!account.getUuid().equals(uuid)) {
            if (!account.isAdmin()) return forbidden();
            account = accountDAO.findByUuid(uuid);
            if (account == null) return notFound(uuid);
        }

        // non-admins cannot update these fields
        if (!account.isAdmin()) {
            updated.setAdmin(false);
            updated.setSuspended(account.isSuspended());
            updated.setEmailVerified(account.isEmailVerified());
            updated.setEmailVerificationCode(null);
            updated.setEmailVerificationCodeCreatedAt(null);
            updated.setHashedPassword(null);
            updated.setTosVersion(null);
            updated.setLastLogin(null);
            updated.setAuthId(null);
        }
        account.update(updated);
        accountDAO.update(account);
        return ok(account);
    }

    /**
     * Delete an account. Must be admin to delete an account other than your own.
     * @param context used to retrieve the logged-in user session
     * @param uuid UUID of the account to delete
     * @return nothing, just an HTTP status code
     */
    @DELETE
    @Path("/{uuid}")
    @ReturnType("java.lang.Void")
    public Response deleteAccount(@Context HttpContext context,
                                  @PathParam("uuid") String uuid) {

        AppStoreAccount account = userPrincipal(context);

        if (!account.isAdmin() && !account.getUuid().equals(uuid)) return forbidden();

        final AppStoreAccount toDelete = accountDAO.findByUuid(uuid);

        Response deleteStatus = deleteAccount(context, toDelete);
        if (deleteStatus != null) return deleteStatus;

        if (uuid.equals(account.getUuid())) {
            // remove the current API token
            tokenDAO.cancel(account.getApiToken());
        }

        return ok();
    }

    /**
     * Delete the account associated with the current session
     * @param context used to retrieve the logged-in user session
     * @return nothing, just an HTTP status code
     */
    @DELETE
    @ReturnType("java.lang.Void")
    public Response deleteAccount(@Context HttpContext context) {

        AppStoreAccount account = userPrincipal(context);

        Response deleteStatus = deleteAccount(context, account);
        if (deleteStatus != null) return deleteStatus;

        // remove the current API token
        tokenDAO.cancel(account.getApiToken());

        return Response.ok().build();
    }

    protected Response deleteAccount(@Context HttpContext context, AppStoreAccount account) {
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
        return null;
    }
}
