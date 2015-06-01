package cloudos.appstore.server;

import cloudos.appstore.ApiConstants;
import cloudos.appstore.model.app.AppLayout;
import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.mail.SimpleEmailMessage;
import org.cobbzilla.mail.sender.SmtpMailConfig;
import org.cobbzilla.mail.service.TemplatedMailSenderConfiguration;
import org.cobbzilla.wizard.server.config.DatabaseConfiguration;
import org.cobbzilla.wizard.server.config.HasDatabaseConfiguration;
import org.cobbzilla.wizard.server.config.RestServerConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.cobbzilla.util.io.FileUtil.mkdirOrDie;

@Configuration
public class AppStoreApiConfiguration extends RestServerConfiguration
        implements HasDatabaseConfiguration, TemplatedMailSenderConfiguration {

    @Setter private DatabaseConfiguration database;
    @Bean public DatabaseConfiguration getDatabase() { return database; }

    @Setter private AppStoreConfiguration appStore;
    @Bean public AppStoreConfiguration getAppStore() { return appStore; }

    @Getter @Setter private String emailTemplateRoot;
    @Getter @Setter private Map<String, SimpleEmailMessage> emailSenderNames = new HashMap<>();

    @Getter @Setter private SmtpMailConfig smtp;

    public String getInvitationActivationUrl(String code) {
        return new StringBuilder()
                .append(getPublicUriBase()).append(getHttp().getBaseUri())
                .append(ApiConstants.MEMBERS_ENDPOINT)
                .append("/activate/").append(code).toString();
    }

    public String getPublicBundleUrl(String appName, String version) {
        return getPublicUriBase()
                + appStore.getAssetUrlBase()
                + "/apps/" + appName + "/" + version + "/"
                + AppLayout.BUNDLE_TARBALL;
    }

    public File getAppRepository(String publisherName) {
        return mkdirOrDie(new File(getAppStore().getAppRepository(), publisherName));
    }

    public String getAssetUriBase() { return getPublicUriBase() + getAppStore().getAssetUrlBase(); }

}
