package cloudos.appstore;

import cloudos.appstore.dao.AppStoreAccountDAO;
import cloudos.appstore.dao.AppStorePublisherDAO;
import cloudos.appstore.dao.AppStorePublisherMemberDAO;
import cloudos.appstore.model.*;
import cloudos.appstore.model.support.AppListing;
import cloudos.appstore.model.support.AppStoreAccountRegistration;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.MapBuilder;
import org.cobbzilla.util.security.ShaUtil;
import org.cobbzilla.util.system.CommandShell;
import org.cobbzilla.wizard.dao.SearchResults;
import org.cobbzilla.wizard.model.ResultPage;
import org.cobbzilla.wizard.model.SemanticVersion;
import org.hibernate.classic.Session;
import org.hibernate.jdbc.Work;
import org.junit.Test;
import org.springframework.orm.hibernate3.HibernateTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

import static org.junit.Assert.assertEquals;

@Slf4j
public class DbInit extends AppStoreITBase {

    private static final String[] APP_NAMES = new String[]{
            "email", "gitlab", "jira", "kanban", "kandan", "owncloud", "phabricator", "phplist",
            "roundcube", "roundcube-calendar", "limesurvey", "etherpad"
    };

    private static final Set<String> INTERACTIVE_APPS = new HashSet<>(Arrays.asList(new String[] {
            "gitlab", "jira", "kanban", "kandan", "owncloud", "phabricator", "phplist",
            "roundcube", "roundcube-calendar", "limesurvey", "etherpad"
    }));

    private Map<String, AppMutableData> appData = MapBuilder.build(new Object[][] {
            { "email", new AppMutableData()
                    .setBlurb("Provides services for sending and receiving Internet email")
                    .setDescription("Provides secure SMTP and IMAP services") },
            { "gitlab", new AppMutableData()
                    .setBlurb("A git repository manager, similar to Github")
                    .setDescription("A git repository manager, similar to Github") },
            { "jira", new AppMutableData()
                    .setBlurb("A popular issue tracker made by Atlassian")
                    .setDescription("A popular issue tracker made by Atlassian") },
            { "kanban", new AppMutableData()
                    .setBlurb("Simple project management with Kanban boards")
                    .setDescription("Simple project management with Kanban boards") },
            { "kandan", new AppMutableData()
                    .setBlurb("An instant messenger that's great for groups")
                    .setDescription("An instant messenger that's great for groups") },
            { "owncloud", new AppMutableData()
                    .setBlurb("Cloud file storage, similar to DropBox")
                    .setDescription("Cloud file storage, similar to DropBox") },
            { "phabricator", new AppMutableData()
                    .setBlurb("An open source software engineering platform")
                    .setDescription("An open source software engineering platform") },
            { "phplist", new AppMutableData()
                    .setBlurb("Email list manager. Great for marketing and communications.")
                    .setDescription("Email list manager. Great for marketing and communications.") },
            { "roundcube", new AppMutableData()
                    .setBlurb("Webmail for your cloudstead")
                    .setDescription("Webmail for your cloudstead") },
            { "roundcube-calendar", new AppMutableData()
                    .setBlurb("Web calendar for your cloudstead")
                    .setDescription("Web calendar for your cloudstead") },
            { "limesurvey", new AppMutableData()
                    .setBlurb("Web surveys, made easy.")
                    .setDescription("Create surveys, send them out and view reports on the responses") },
            { "etherpad", new AppMutableData()
                    .setBlurb("Collaborative document editing.")
                    .setDescription("Create documents and let multiple people work on them together") },
    });

    @Test public void initDatabase () throws Exception {
        // create admin user
        final Map<String, String> exports = CommandShell.loadShellExports(".cloudstead.env");
        final String user = exports.get("APPSTORE_ADMIN_USER");
        final String password = exports.get("APPSTORE_ADMIN_PASSWORD");
        final AppStoreAccountDAO accountDAO = getBean(AppStoreAccountDAO.class);
        final AppStoreAccountRegistration reg = (AppStoreAccountRegistration) new AppStoreAccountRegistration()
                .setTos(true)
                .setPassword(password)
                .setLastName("admin").setFirstName("admin")
                .setEmail(user+"@example.com")
                .setMobilePhoneCountryCode(1).setMobilePhone("n/a")
                .setName(user);

        adminToken = appStoreClient.registerAccount(reg).getToken();
        final AppStoreAccount account = accountDAO.findByName(user);

        account.setAdmin(true);
        accountDAO.update(account);

        // Curious about how to crack things wide open?
        // Here's how to run SQL statements through a raw JDBC connection, bypassing Hibernate validation
        @Cleanup final Session session = getBean(HibernateTemplate.class).getSessionFactory().openSession();
        session.doWork(new Work() {
            @Override public void execute(Connection connection) throws SQLException {
                @Cleanup PreparedStatement ps = connection.prepareStatement("update app_store_account set email = ? where name = ?");
                ps.setString(1, user); ps.setString(2, user);
                ps.execute();
                log.info("Successfully updated accounts, "+user+" is now the admin login");
            }
        });

        // nuke old admin stuff
        accountDAO.delete(admin.getUuid());
        getBean(AppStorePublisherDAO.class).delete(admin.getUuid());
        final AppStorePublisherMemberDAO memberDAO = getBean(AppStorePublisherMemberDAO.class);
        for (AppStorePublisherMember member : memberDAO.findByPublisher(admin.getUuid())) {
            memberDAO.delete(member.getUuid());
        }

        admin = account;
        setToken(adminToken);

        for (CloudApp app : getApps()) {
            app = appStoreClient.defineApp(app);
            final String bundleUrl = "http://cloudstead.io/downloads/" + app.getName() + "-bundle.tar.gz";
            CloudAppVersion version = (CloudAppVersion) new CloudAppVersion()
                    .setVersion(new SemanticVersion(1, 0, 0))
                    .setBundleUrl(bundleUrl)
                    .setBundleUrlSha(ShaUtil.sha256_url(bundleUrl))
                    .setData(getAppData(app))
                    .setApp(app.getUuid())
                    .setAuthor(admin.getUuid())
                    .setAppStatus(CloudAppStatus.NEW);
            version.setInteractive(INTERACTIVE_APPS.contains(app.getName()));
            version = appStoreClient.defineAppVersion(version);
            version.setAppStatus(CloudAppStatus.PUBLISHED);
            appStoreClient.updateAppVersion(version);
        }

        // query app store -- ensure all apps are found
        final SearchResults<AppListing> results = appStoreClient.searchAppStore(ResultPage.INFINITE_PAGE);
        assertEquals(APP_NAMES.length, results.getResults().size());

        log.info("completed!");
    }

    private AppMutableData getAppData(CloudApp app) throws Exception {
        final String largeIcon = "http://cloudstead.io/downloads/icons/"+app.getName()+"-large.png";
        final String smallIcon = "http://cloudstead.io/downloads/icons/"+app.getName()+"-small.png";
        final String smallIconUrlSha = ShaUtil.sha256_url(smallIcon);
        final AppMutableData mutableData = appData.get(app.getName())
                .setLargeIconUrl(largeIcon)
                .setLargeIconUrlSha(ShaUtil.sha256_url(largeIcon))
                .setSmallIconUrl(smallIcon)
                .setSmallIconUrlSha(smallIconUrlSha);
        if (INTERACTIVE_APPS.contains(app.getName())) {
            mutableData.setTaskbarIconUrl(smallIcon)
                       .setTaskbarIconUrlSha(smallIconUrlSha);
        }
        return mutableData;
    }

    private List<CloudApp> getApps() {
        final List<CloudApp> apps = new ArrayList<>();
        for (String name : APP_NAMES) {
            CloudApp app = new CloudApp()
                    .setAuthor(admin.getUuid())
                    .setPublisher(admin.getUuid())
                    .setName(name);
            apps.add(app);
        }
        return apps;
    }

}
