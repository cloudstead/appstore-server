package cloudos.appstore.resources;

import cloudos.appstore.ApiConstants;
import cloudos.appstore.dao.AppStoreAccountDAO;
import cloudos.appstore.dao.AppStorePublisherDAO;
import cloudos.appstore.dao.AppStorePublisherMemberDAO;
import cloudos.appstore.model.AppStoreAccount;
import cloudos.appstore.model.AppStorePublisher;
import cloudos.appstore.model.AppStorePublisherMember;
import cloudos.appstore.model.AppStorePublisherMemberInvitation;
import cloudos.appstore.server.AppStoreApiConfiguration;
import com.qmino.miredot.annotations.ReturnType;
import com.sun.jersey.api.core.HttpContext;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.mail.SimpleEmailMessage;
import org.cobbzilla.mail.TemplatedMail;
import org.cobbzilla.mail.service.TemplatedMailService;
import org.cobbzilla.wizard.resources.ResourceUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(ApiConstants.MEMBERS_ENDPOINT)
@Service @Slf4j
public class AppStorePublisherMembersResource {

    @Autowired private AppStoreApiConfiguration configuration;
    @Autowired private AppStoreAccountDAO accountDAO;
    @Autowired private AppStorePublisherDAO publisherDAO;
    @Autowired private AppStorePublisherMemberDAO memberDAO;
    @Autowired private TemplatedMailService mailService;

    public static final String T_INVITE_TO_PUBLISHER = "invite_to_publisher";

    /**
     * Get memberships for the account associated with the current session
     * @param context used to retrieve the logged-in user session
     * @return a List of AppStorePublisherMember objects
     */
    @GET
    @ReturnType("java.lang.List<cloudos.appstore.model.AppStorePublisherMember>")
    public Response currentMemberships (@Context HttpContext context) {
        final AppStoreAccount account = userPrincipal(context);
        return ok(memberDAO.findByAccount(account.getUuid()));
    }

    /**
     * Find memberships for the given account. Must be admin to lookup memberships for accounts other than your own
     * @param context used to retrieve the logged-in user session
     * @param uuid UUID of the account
     * @return a List of AppStorePublisherMember objects
     */
    @GET
    @Path("/account/{uuid}")
    @ReturnType("java.lang.List<cloudos.appstore.model.AppStorePublisherMember>")
    public Response findByAccount (@Context HttpContext context,
                                   @PathParam("uuid") String uuid) {
        final AppStoreAccount account = userPrincipal(context);
        if (!account.isAdmin() && !account.getUuid().equals(uuid)) return forbidden();

        return ok(memberDAO.findByAccount(account.getUuid()));
    }

    /**
     * Find memberships for the given publisher. Must be admin to lookup memberships for publishers that you do not own.
     * @param context used to retrieve the logged-in user session
     * @param uuid UUID of the publisher
     * @return a List of AppStorePublisherMember objects
     */
    @GET
    @Path("/publisher/{uuid}")
    @ReturnType("java.lang.List<cloudos.appstore.model.AppStorePublisherMember>")
    public Response findByPublisher (@Context HttpContext context,
                                     @PathParam("uuid") String uuid) {
        final AppStoreAccount account = userPrincipal(context);
        final AppStorePublisher publisher = publisherDAO.findByUuid(uuid);
        if (publisher == null) return forbidden();
        if (account.isAdmin() || publisher.getOwner().equals(account.getUuid())) {
            return ok(memberDAO.findByPublisher(publisher.getUuid()));
        }
        return forbidden();
    }

    /**
     * Find an individual membership record
     * @param context used to retrieve the logged-in user session
     * @param uuid UUID of the membership record
     * @return a AppStorePublisherMember object
     */
    @GET
    @Path("/member/{uuid}")
    @ReturnType("cloudos.appstore.model.AppStorePublisherMember")
    public Response findMembershipRecord (@Context HttpContext context,
                                          @PathParam("uuid") String uuid) {
        final AppStoreAccount account = userPrincipal(context);

        AppStorePublisherMember member = memberDAO.findByUuid(uuid);
        if (member == null) return notFound(uuid);

        if (!account.isAdmin()) {
            if (!member.getAccount().equals(account.getUuid())) return forbidden();
            final AppStorePublisher publisher = publisherDAO.findByUuid(member.getPublisher());
            if (!publisher.getOwner().equals(account.getUuid())) return forbidden();
        }
        return ok(member);
    }

    /**
     * Invite an account to join a publisher as a member
     * @param context used to retrieve the logged-in user session
     * @param invitation the invitation information
     * @return the new membership record (it must be accepted in order to be activated)
     */
    @PUT
    @Path("/invite")
    @ReturnType("cloudos.appstore.model.AppStorePublisherMember")
    public Response inviteMember (@Context HttpContext context,
                                  AppStorePublisherMemberInvitation invitation) {
        final AppStoreAccount account = userPrincipal(context);

        final AppStorePublisher publisher = publisherDAO.findByUuid(invitation.getPublisherUuid());
        if (publisher == null) return notFound(invitation.getPublisherUuid());

        final AppStoreAccount invitee = accountDAO.findByName(invitation.getAccountName());
        if (invitee == null) return notFound(invitation.getAccountName());

        if (!account.isAdmin() && !publisher.getOwner().equals(account.getUuid())) {
            // only owner can invite new members
            return forbidden();
        }

        AppStorePublisherMember member = memberDAO.findByAccountAndPublisher(invitee.getUuid(), publisher.getUuid());
        if (member == null) {
            member = new AppStorePublisherMember()
                    .setAccount(account.getUuid())
                    .setPublisher(publisher.getUuid())
                    .initNew();
        } else {
            // already accepted an invite?
            if (member.isActive()) return ResourceUtil.invalid("err.invite.alreadyMember");

            // resend invitation with new activation code
            member.newActivation();
        }

        memberDAO.upsert(member);

        sendInvitation(member, account, invitee, publisher);

        return ok(member);
    }

    public void sendInvitation(AppStorePublisherMember member,
                               AppStoreAccount inviter,
                               AppStoreAccount invitee,
                               AppStorePublisher publisher) {
        // Send invitation with activation code
        SimpleEmailMessage welcomeSender = configuration.getEmailSenderNames().get(T_INVITE_TO_PUBLISHER);
        final String code = member.getActivation();
        final TemplatedMail mail = new TemplatedMail()
                .setTemplateName(T_INVITE_TO_PUBLISHER)
                .setLocale(invitee.getLocale()) // todo: collect this at registration or auto-detect from browser
                .setFromName(welcomeSender.getFromName())
                .setFromEmail(welcomeSender.getFromEmail())
                .setToEmail(invitee.getEmail())
                .setToName(invitee.getFullName())
                .setParameter("inviter", inviter)
                .setParameter("invitee", invitee)
                .setParameter("publisher", publisher)
                .setParameter("invitationActivationUri", configuration.getInvitationActivationUrl(code));
        try {
            mailService.getMailSender().deliverMessage(mail);
        } catch (Exception e) {
            log.error("sendInvitation: error sending welcome email: "+e, e);
        }
    }

    /**
     * Activate a membership using the code provided in the invitation
     * @param context used to retrieve the logged-in user session
     * @param code the activation code
     * @return nothing, just an HTTP status code
     */
    @GET
    @Path("/activate/{code}")
    @ReturnType("java.lang.Void")
    public Response activateMembership(@Context HttpContext context,
                                       @PathParam("code") String code) {

        final AppStoreAccount account = userPrincipal(context);

        final AppStorePublisherMember member = memberDAO.findByActivationCode(code);
        if (member == null) return notFound(code);

        // Can only accept invites for yourself
        if (!member.getAccount().equals(account.getUuid())) return forbidden();

        member.setActive(true);
        member.setActivation(null);
        memberDAO.update(member);

        // todo: change invitationActivationUri to point to an ember app that will call the API,
        // instead of a link to this API call directly, which will require login anyway.
        return ok();
    }

    /**
     * Delete a membership
     * @param context used to retrieve the logged-in user session
     * @param uuid UUID of the membership record
     * @return
     */
    @DELETE
    @Path("/member/{uuid}")
    @ReturnType("java.lang.Void")
    public Response deleteMembershipRecord (@Context HttpContext context,
                                            @PathParam("uuid") String uuid) {
        final AppStoreAccount account = userPrincipal(context);

        AppStorePublisherMember member = memberDAO.findByUuid(uuid);
        if (member == null) return notFound(uuid);

        if (!account.isAdmin()) {
            if (!member.getAccount().equals(account.getUuid())) return forbidden();
            final AppStorePublisher publisher = publisherDAO.findByUuid(member.getPublisher());
            if (!publisher.getOwner().equals(account.getUuid())) return forbidden();
        }

        memberDAO.delete(member.getUuid());

        return Response.ok().build();
    }

}
