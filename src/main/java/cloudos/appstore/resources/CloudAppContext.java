package cloudos.appstore.resources;

import cloudos.appstore.dao.*;
import cloudos.appstore.model.AppStoreAccount;
import cloudos.appstore.model.AppStorePublisher;
import cloudos.appstore.model.AppStorePublisherMember;
import cloudos.appstore.model.CloudApp;
import com.sun.jersey.api.core.HttpContext;

import javax.ws.rs.core.Response;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

public class CloudAppContext {

    public AppStoreAccount account;
    public boolean hasAccount() { return account != null; }

    public AppStorePublisher publisher;
    public AppStorePublisherMember membership;
    public CloudApp app;

    public Response response = null;
    public boolean hasResponse() {
        return response != null;
    }

    public CloudAppContext(AppStorePublisherDAO publisherDAO,
                           AppStorePublisherMemberDAO memberDAO,
                           CloudAppDAO appDAO,
                           HttpContext context, String pubName, String appName) {
        this(publisherDAO, memberDAO, appDAO, context, pubName, appName, false, false);
    }

    public CloudAppContext(AppStorePublisherDAO publisherDAO,
                           AppStorePublisherMemberDAO memberDAO,
                           CloudAppDAO appDAO,
                           HttpContext context, String pubName, String appName, boolean allowNonmembers) {
        this(publisherDAO, memberDAO, appDAO, context, pubName, appName, allowNonmembers, false);
    }

    public CloudAppContext(AppStorePublisherDAO publisherDAO,
                           AppStorePublisherMemberDAO memberDAO,
                           CloudAppDAO appDAO,
                           HttpContext context, String pubName, String appName,
                           boolean allowNonmembers, boolean userOptional) {

        account = (AppStoreAccount) (userOptional ? optionalUserPrincipal(context) : userPrincipal(context));
        publisher = publisherDAO.findByName(pubName);
        if (publisher == null) {
            response = notFound(pubName);
            return;
        }

        if (account != null) {
            membership = memberDAO.findByAccountAndPublisher(account.getUuid(), publisher.getUuid());
            if (!account.isAdmin() && (membership == null || membership.inactive()) && !allowNonmembers) {
                response = forbidden();
                return;
            }
        }

        if (appName != null) {
            app = appDAO.findByPublisherAndName(publisher.getUuid(), appName);
            if (app == null) {
                response = notFound(appName);
                return;
            }

            switch (app.getVisibility()) {
                case everyone:
                    return;

                case members:
                    if (account == null || (!account.isAdmin() && (membership == null || membership.inactive()))) {
                        response = forbidden();
                    }
                    return;

                case publisher:
                    if (account == null || (!account.isAdmin() && !publisher.getOwner().equals(account.getUuid()))) {
                        response = forbidden();
                    }
                    return;

                default:
                    die("invalid visibility: " + app.getVisibility());
            }
        }
    }

    public void populateApp(AppStoreAccountDAO accountDAO, CloudAppVersionDAO versionDAO) {
        app.setPublishedBy(publisher);
        app.setAuthoredBy(accountDAO.findByUuid(app.getAuthor()));
        app.setVersions(versionDAO.findByApp(app.getUuid()));
    }
}
