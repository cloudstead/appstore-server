package cloudos.appstore.resources;

import lombok.extern.slf4j.Slf4j;
import cloudos.appstore.ApiConstants;
import cloudos.appstore.ValidationConstants;
import cloudos.appstore.dao.ApiTokenDAO;
import cloudos.appstore.dao.AppStoreAccountDAO;
import cloudos.appstore.dao.AppStorePublisherDAO;
import cloudos.appstore.dao.AppStorePublisherMemberDAO;
import cloudos.appstore.model.AppStoreAccount;
import cloudos.appstore.model.AppStorePublisher;
import cloudos.appstore.model.AppStorePublisherMember;
import cloudos.appstore.model.support.ApiToken;
import cloudos.appstore.model.support.AppStoreAccountRegistration;
import cloudos.appstore.model.support.RefreshTokenRequest;
import org.cobbzilla.wizard.resources.ResourceUtil;
import org.cobbzilla.wizard.validation.ConstraintViolationBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(ApiConstants.AUTH_ENDPOINT)
@Service @Slf4j
public class AppStoreAuthResource {

    @Autowired private ApiTokenDAO tokenDAO;
    @Autowired private AppStoreAccountDAO accountDAO;
    @Autowired private AppStorePublisherDAO publisherDAO;
    @Autowired private AppStorePublisherMemberDAO memberDAO;

    @PUT
    public Response register (@Valid AppStoreAccountRegistration registration) {

        final String email = registration.getEmail();

        final List<ConstraintViolationBean> violations = new ArrayList<>();

        final AppStoreAccount foundAccount = accountDAO.findByEmail(email);
        if (foundAccount != null) violations.add(new ConstraintViolationBean(ValidationConstants.ERR_EMAIL_NOT_UNIQUE));

        // A single account can be both a publisher and a consumer, BUT you must register separately for each
        if (!registration.hasOneTos()) {
            violations.add(new ConstraintViolationBean(ValidationConstants.ERR_EXACTLY_ONE_TOS_REQUIRED));
            return ResourceUtil.invalid(violations); // cannot proceed -- registration is "too" invalid
        }

        final AppStoreAccount account = new AppStoreAccount();

        if (registration.isPublisher()) {
            final AppStorePublisher foundPublisher = publisherDAO.findByName(registration.getPublisherName());
            if (foundPublisher != null) violations.add(new ConstraintViolationBean(ValidationConstants.ERR_PUBLISHER_NAME_NOT_UNIQUE));
            account.setPublisherTos(registration.getPublisherTos());
        } else {
            account.setConsumerTos(registration.getConsumerTos());
        }

        if (!violations.isEmpty()) return ResourceUtil.invalid(violations);

        account.setEmail(email);
        account.getPassword().setPassword(registration.getPassword());
        final AppStoreAccount createdAccount = accountDAO.create(account);
        final String uuid = createdAccount.getUuid();

        if (registration.isPublisher()) {
            final AppStorePublisher publisher = new AppStorePublisher();
            publisher.setUuid(uuid);
            publisher.setOwner(uuid);
            publisher.setName(registration.getPublisherName());
            final AppStorePublisher createdPublisher = publisherDAO.create(publisher);

            final AppStorePublisherMember member = new AppStorePublisherMember();
            member.setAccount(createdAccount.getUuid());
            member.setPublisher(createdPublisher.getUuid());
            memberDAO.create(member);
        }

        ApiToken token = tokenDAO.generateNewToken(uuid);

        return Response.ok(token).build();
    }

    @POST
    public Response refreshToken (RefreshTokenRequest tokenRequest) {

        // if we have a token, try to refresh it directly
        if (tokenRequest.hasToken()) {
            final ApiToken newToken = tokenDAO.refreshToken(tokenRequest.getApiToken());
            if (newToken != null) Response.ok(newToken).build();
        }

        // if that didn't work, or we didn't have a token, try email/password auth
        if (tokenRequest.hasEmail()) {

            final AppStoreAccount found = accountDAO.findByEmail(tokenRequest.getEmail());

            if (found == null || !found.getPassword().isCorrectPassword(tokenRequest.getPassword())) {
                return ResourceUtil.notFound();
            }

            final ApiToken newToken = tokenDAO.generateNewToken(found.getUuid());
            return Response.ok(newToken).build();
        }

        // not enough to work with
        return ResourceUtil.forbidden();
    }

}
