package cloudos.appstore.main;

import lombok.Getter;
import lombok.Setter;
import org.kohsuke.args4j.Option;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class AppStoreAssetOptions extends AppStoreMainOptions {

    public static final String USAGE_PUBLISHER = "Name of the app publisher";
    public static final String OPT_PUBLISHER = "-P";
    public static final String LONGOPT_PUBLISHER = "--publisher";
    @Option(name=OPT_PUBLISHER, aliases=LONGOPT_PUBLISHER, usage=USAGE_PUBLISHER, required=true)
    @Getter @Setter private String publisher;

    public static final String USAGE_APPNAME = "Name of the app";
    public static final String OPT_APPNAME = "-A";
    public static final String LONGOPT_APPNAME = "--app";
    @Option(name=OPT_APPNAME, aliases=LONGOPT_APPNAME, usage=USAGE_APPNAME, required=true)
    @Getter @Setter private String app;

    public static final String USAGE_VERSION = "Name of the app";
    public static final String OPT_VERSION = "-A";
    public static final String LONGOPT_VERSION = "--app";
    @Option(name=OPT_VERSION, aliases=LONGOPT_VERSION, usage=USAGE_VERSION)
    @Getter @Setter private String version;
    public boolean hasVersion () { return !empty(version); }

    public static final String USAGE_ASSET = "Name of the asset";
    public static final String OPT_ASSET = "-n";
    public static final String LONGOPT_ASSET = "--asset";
    @Option(name=OPT_ASSET, aliases=LONGOPT_ASSET, usage=USAGE_ASSET, required=true)
    @Getter @Setter private String asset;

}
