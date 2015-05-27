package cloudos.appstore.main;

import cloudos.appstore.model.AppVisibility;
import cloudos.appstore.model.CloudAppStatus;
import cloudos.appstore.model.support.DefineCloudAppRequest;
import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.util.security.ShaUtil;
import org.cobbzilla.wizard.api.CrudOperation;
import org.kohsuke.args4j.Option;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class AppStoreAppsOptions extends AppStoreMainOptions {

    public static final String USAGE_OPERATION = "The operation to perform";
    public static final String OPT_OPERATION = "-o";
    public static final String LONGOPT_OPERATION = "--operation";
    @Option(name=OPT_OPERATION, aliases=LONGOPT_OPERATION, usage=USAGE_OPERATION)
    @Getter @Setter private CrudOperation operation = CrudOperation.read;

    public static final String USAGE_PUBLISHER = "Name of the publisher (if omitted, default publisher for account will be used)";
    public static final String OPT_PUBLISHER = "-p";
    public static final String LONGOPT_PUBLISHER = "--publisher";
    @Option(name=OPT_PUBLISHER, aliases=LONGOPT_PUBLISHER, usage=USAGE_PUBLISHER)
    @Getter @Setter private String publisher;
    public boolean hasPublisher() { return !empty(publisher); }

    public static final String USAGE_NAME = "Name of the app.";
    public static final String OPT_NAME = "-n";
    public static final String LONGOPT_NAME = "--name";
    @Option(name=OPT_NAME, aliases=LONGOPT_NAME, usage=USAGE_NAME)
    @Getter @Setter private String name;
    public boolean hasName () { return !empty(name); }

    public static final String USAGE_BUNDLE_URL = "URL of the app bundle, as prepared with the CloudOs Bundler";
    public static final String OPT_BUNDLE_URL = "-b";
    public static final String LONGOPT_BUNDLE_URL = "--bundle";
    @Option(name=OPT_BUNDLE_URL, aliases=LONGOPT_BUNDLE_URL, usage=USAGE_BUNDLE_URL)
    @Getter @Setter private String bundleUrl;
    public boolean hasBundleUrl () { return !empty(bundleUrl); }

    public static final String USAGE_BUNDLE_SHA = "SHA-256 digest of the app bundle.";
    public static final String OPT_BUNDLE_SHA = "-S";
    public static final String LONGOPT_BUNDLE_SHA = "--bundle-sha";
    @Option(name=OPT_BUNDLE_SHA, aliases=LONGOPT_BUNDLE_SHA, usage=USAGE_BUNDLE_SHA)
    @Getter @Setter private String bundleSha;
    public boolean hasBundleSha () { return !empty(bundleSha); }

    public static final String USAGE_VERSION = "Version (for version update and delete operations)";
    public static final String OPT_VERSION = "-r";
    public static final String LONGOPT_VERSION = "--version";
    @Option(name=OPT_VERSION, aliases=LONGOPT_VERSION, usage=USAGE_VERSION)
    @Getter @Setter private String version;
    public boolean hasVersion() { return !empty(version); }

    public static final String USAGE_ATTR_NAME = "Attribute name (for app update operations)";
    public static final String OPT_ATTR_NAME = "-A";
    public static final String LONGOPT_ATTR_NAME = "--attr-name";
    @Option(name=OPT_ATTR_NAME, aliases=LONGOPT_ATTR_NAME, usage=USAGE_ATTR_NAME)
    @Getter @Setter private String attributeName;
    public boolean hasAttributeName () { return !empty(attributeName); }

    public static final String USAGE_ATTR_VALUE = "Attribute value (for app update operations)";
    public static final String OPT_ATTR_VALUE = "-V";
    public static final String LONGOPT_ATTR_VALUE = "--attr-value";
    @Option(name=OPT_ATTR_VALUE, aliases=LONGOPT_ATTR_VALUE, usage=USAGE_ATTR_VALUE)
    @Getter @Setter private String attributeValue;
    public boolean hasAttributeValue () { return !empty(attributeValue); }

    public static final String USAGE_STATUS = "Status (for version update operations)";
    public static final String OPT_STATUS = "-U";
    public static final String LONGOPT_STATUS = "--status";
    @Option(name=OPT_STATUS, aliases=LONGOPT_STATUS, usage=USAGE_STATUS)
    @Getter @Setter private CloudAppStatus status;
    public boolean hasStatus() { return !empty(status); }

    public DefineCloudAppRequest getCloudAppRequest() throws Exception {
        if (!hasBundleSha()) bundleSha = ShaUtil.sha256_url(bundleUrl);
        final DefineCloudAppRequest request = new DefineCloudAppRequest()
                .setBundleUrl(bundleUrl)
                .setBundleUrlSha(bundleSha);
        if (hasAttributeName() && hasAttributeValue() && attributeName.equals("visibility")) {
            request.setVisibility(AppVisibility.valueOf(attributeValue));
        }
        return request;
    }

}
