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

    @GET
    public Response currentMemberships (@Context HttpContext context) {
        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();
        return Response.ok(memberDAO.findByAccount(account.getUuid())).build();
    }

    @GET
    @Path("/account/{uuid}")
    public Response findByAccount (@Context HttpContext context,
                                   @PathParam("uuid") String uuid) {
        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();
        if (!account.isAdmin() && !account.getUuid().equals(uuid)) return ResourceUtil.forbidden();

        return Response.ok(memberDAO.findByAccount(account.getUuid())).build();
    }

    @GET
    @Path("/publisher/{uuid}")
    public Response findByPublisher (@Context HttpContext context,
                                     @PathParam("uuid") String uuid) {
        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();
        final AppStorePublisher publisher = publisherDAO.findByUuid(uuid);
        if (publisher == null) return ResourceUtil.forbidden();
        if (account.isAdmin() || publisher.getOwner().equals(account.getUuid())) {
            return Response.ok(memberDAO.findByPublisher(publisher.getUuid())).build();
        }
        return ResourceUtil.forbidden();
    }

    @GET
    @Path("/member/{uuid}")
    public Response findMembershipRecord (@Context HttpContext context,
                                          @PathParam("uuid") String uuid) {
        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();

        AppStorePublisherMember member = memberDAO.findByUuid(uuid);
        if (member == null) return ResourceUtil.notFound(uuid);

        if (!account.isAdmin()) {
            if (!member.getAccount().equals(account.getUuid())) return ResourceUtil.forbidden();
            final AppStorePublisher publisher = publisherDAO.findByUuid(member.getPublisher());
            if (!publisher.getOwner().equals(account.getUuid())) return ResourceUtil.forbidden();
        }
        return Response.ok(member).build();
    }

    @PUT
    @Path("/invite")
    public Response inviteMember (@Context HttpContext context,
                                  AppStorePublisherMemberInvitation invitation) {
        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();

        final AppStorePublisher publisher = publisherDAO.findByUuid(invitation.getPublisherUuid());
        if (publisher == null) return ResourceUtil.notFound(invitation.getPublisherUuid());

        final AppStoreAccount invitee = accountDAO.findByName(invitation.getAccountName());
        if (invitee == null) return ResourceUtil.notFound(invitation.getAccountName());

        if (!account.isAdmin() && !publisher.getOwner().equals(account.getUuid())) {
            // only owner can invite new members
            return ResourceUtil.forbidden();
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

        return Response.ok(member).build();
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

    @GET
    @Path("/activate/{code}")
    public Response activateMembership(@Context HttpContext context,
                                       @PathParam("code") String code) {

        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();

        final AppStorePublisherMember member = memberDAO.findByActivationCode(code);
        if (member == null) return ResourceUtil.notFound(code);

        // Can only accept invites for yourself
        if (!member.getAccount().equals(account.getUuid())) return ResourceUtil.forbidden();

        member.setActive(true);
        member.setActivation(null);
        memberDAO.update(member);

        // todo: change invitationActivationUri to point to an ember app that will call the API,
        // instead of a link to this API call directly, which will require login anyway.
        return Response.ok().build();
    }

    @DELETE
    @Path("/member/{uuid}")
    public Response deleteMembershipRecord (@Context HttpContext context,
                                            @PathParam("uuid") String uuid) {
        final AppStoreAccount account = (AppStoreAccount) context.getRequest().getUserPrincipal();

        AppStorePublisherMember member = memberDAO.findByUuid(uuid);
        if (member == null) return ResourceUtil.notFound(uuid);

        if (!account.isAdmin()) {
            if (!member.getAccount().equals(account.getUuid())) return ResourceUtil.forbidden();
            final AppStorePublisher publisher = publisherDAO.findByUuid(member.getPublisher());
            if (!publisher.getOwner().equals(account.getUuid())) return ResourceUtil.forbidden();
        }

        memberDAO.delete(member.getUuid());

        return Response.ok().build();
    }

}
