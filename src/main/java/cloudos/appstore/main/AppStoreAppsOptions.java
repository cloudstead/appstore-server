package cloudos.appstore.main;

import cloudos.appstore.model.AppStoreAccount;
import cloudos.appstore.model.CloudApp;
import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.wizard.api.CrudOperation;
import org.kohsuke.args4j.Option;

import static org.cobbzilla.util.string.StringUtil.empty;

public class AppStoreAppsOptions extends AppStoreMainOptions {

    public static final String USAGE_OPERATION = "The operation to perform";
    public static final String OPT_OPERATION = "-o";
    public static final String LONGOPT_OPERATION = "--operation";
    @Option(name=OPT_OPERATION, aliases=LONGOPT_OPERATION, usage=USAGE_OPERATION)
    @Getter @Setter private CrudOperation operation = CrudOperation.read;

    public static final String USAGE_UUID = "UUID of the app";
    public static final String OPT_UUID = "-u";
    public static final String LONGOPT_UUID = "--uuid";
    @Option(name=OPT_UUID, aliases=LONGOPT_UUID, usage=USAGE_UUID)
    @Getter @Setter private String uuid;
    public boolean hasUuid () { return !empty(uuid); }

    public static final String USAGE_NAME = "Name of the app.";
    public static final String OPT_NAME = "-n";
    public static final String LONGOPT_NAME = "--name";
    @Option(name=OPT_NAME, aliases=LONGOPT_NAME, usage=USAGE_NAME)
    @Getter @Setter private String name;
    public boolean hasName () { return !empty(name); }

    public static final String USAGE_PUBLISHER = "UUID of the publisher";
    public static final String OPT_PUBLISHER = "-p";
    public static final String LONGOPT_PUBLISHER = "--publisher";
    @Option(name=OPT_PUBLISHER, aliases=LONGOPT_PUBLISHER, usage=USAGE_PUBLISHER)
    @Getter @Setter private String publisher;
    public boolean hasPublisher() { return !empty(publisher); }

    public static final String USAGE_ACTIVE_VERSION = "The active version of this app. A CloudAppVersion must exist with this version string";
    public static final String OPT_ACTIVE_VERSION = "-r";
    public static final String LONGOPT_ACTIVE_VERSION = "--version";
    @Option(name=OPT_ACTIVE_VERSION, aliases=LONGOPT_ACTIVE_VERSION, usage=USAGE_ACTIVE_VERSION)
    @Getter @Setter private String activeVersion;

    public CloudApp getCloudApp(AppStoreAccount account) {

        final CloudApp app = (CloudApp) new CloudApp()
                .setAuthor(account.getUuid())
                .setPublisher(empty(publisher) ? account.getUuid() : publisher)
                .setActiveVersion(activeVersion)
                .setName(name);

        if (hasUuid()) {
            app.setUuid(uuid);
        } else {
            app.initUuid();
        }

        return app;
    }

}
