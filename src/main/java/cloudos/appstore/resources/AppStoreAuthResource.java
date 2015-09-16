package cloudos.appstore.resources;

import cloudos.appstore.ApiConstants;
import cloudos.appstore.ValidationConstants;
import cloudos.appstore.dao.ApiTokenDAO;
import cloudos.appstore.dao.AppStoreAccountDAO;
import cloudos.appstore.dao.AppStorePublisherDAO;
import cloudos.appstore.dao.AppStorePublisherMemberDAO;
import cloudos.appstore.model.AppStoreAccount;
import cloudos.appstore.model.AppStorePublisher;
import cloudos.appstore.model.AppStorePublisherMember;
import org.cobbzilla.wizard.model.ApiToken;
import cloudos.appstore.model.support.AppStoreAccountRegistration;
import cloudos.appstore.model.support.RefreshTokenRequest;
import com.qmino.miredot.annotations.ReturnType;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.model.HashedPassword;
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

import static org.cobbzilla.wizard.resources.ResourceUtil.forbidden;
import static org.cobbzilla.wizard.resources.ResourceUtil.notFound;
import static org.cobbzilla.wizard.resources.ResourceUtil.ok;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(ApiConstants.AUTH_ENDPOINT)
@Service @Slf4j
public class AppStoreAuthResource {

    @Autowired private ApiTokenDAO tokenDAO;
    @Autowired private AppStoreAccountDAO accountDAO;
    @Autowired private AppStorePublisherDAO publisherDAO;
    @Autowired private AppStorePublisherMemberDAO memberDAO;

    /**
     * A simple health check. if this is reachable and returns 200, we know at least REST and Spring started OK.
     * @return an empty object
     */
    @GET public ApiToken check() { return new ApiToken(); }

    /**
     * Register a new account
     * @param registration The registration information
     * @return an token that can be used for future API calls
     */
    @PUT
    @ReturnType("cloudos.appstore.model.support.ApiToken")
    public Response register (@Valid AppStoreAccountRegistration registration) {

        final String email = registration.getEmail();
        final String name = registration.getName();

        final List<ConstraintViolationBean> violations = new ArrayList<>();

        final AppStoreAccount foundAccount = accountDAO.findByEmail(email);
        if (foundAccount != null) violations.add(new ConstraintViolationBean(ValidationConstants.ERR_EMAIL_NOT_UNIQUE));

        if (accountDAO.findByName(name) != null) violations.add(new ConstraintViolationBean(ValidationConstants.ERR_NAME_NOT_UNIQUE));
        if (publisherDAO.findByName(name) != null) violations.add(new ConstraintViolationBean(ValidationConstants.ERR_NAME_NOT_UNIQUE));

        final AppStoreAccount account = new AppStoreAccount().populate(registration);
        account.setHashedPassword(new HashedPassword(registration.getPassword()));

        if (!violations.isEmpty()) return ResourceUtil.invalid(violations);

        final AppStoreAccount createdAccount = accountDAO.create(account);
        final String uuid = createdAccount.getUuid();

        final AppStorePublisher publisher = new AppStorePublisher();
        publisher.setUuid(uuid);
        publisher.setOwner(uuid);
        publisher.setName(name);
        final AppStorePublisher createdPublisher = publisherDAO.create(publisher);

        final AppStorePublisherMember member = new AppStorePublisherMember();
        member.setAccount(createdAccount.getUuid());
        member.setPublisher(createdPublisher.getUuid());
        member.setActive(true);
        memberDAO.create(member);

        final ApiToken token = tokenDAO.generateNewToken(uuid);

        return ok(token);
    }

    /**
     * Get a new API token
     * @param tokenRequest Should either contain a login/pass, or an existing token
     * @return a new API token
     */
    @POST
    @ReturnType("cloudos.appstore.model.support.ApiToken")
    public Response refreshToken (RefreshTokenRequest tokenRequest) {

        // if we have a token, try to refresh it directly
        if (tokenRequest.hasToken()) {
            final ApiToken newToken = tokenDAO.refreshToken(tokenRequest.getApiToken());
            if (newToken != null) return ok(newToken);
        }

        // if that didn't work, or we didn't have a token, try email/password auth
        if (tokenRequest.hasEmail()) {

            AppStoreAccount found = accountDAO.findByEmail(tokenRequest.getEmail());
            if (found == null) found = accountDAO.findByName(tokenRequest.getEmail());

            if (found == null || !found.getHashedPassword().isCorrectPassword(tokenRequest.getPassword())) {
                return notFound();
            }

            final ApiToken newToken = tokenDAO.generateNewToken(found.getUuid());
            return ok(newToken);
        }

        // not enough to work with
        return forbidden();
    }

    /**
     * Cancel an API token
     * @param token the API token to cancel
     * @return nothing, just an HTTP status code
     */
    @DELETE
    @Path("/{token}")
    @ReturnType("java.lang.Void")
    public Response deleteToken (@PathParam("token") String token) {
        tokenDAO.cancel(token);
        return ok();
    }

}
