package cloudos.appstore.main;

import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.util.string.StringUtil;
import org.kohsuke.args4j.Option;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;

public class AppStoreMainOptions {

    public static final String PASSWORD_ENV_VAR = "CAS_PASS";

    public static final String USAGE_ACCOUNT = "The account name. Required. The password must be in the "+PASSWORD_ENV_VAR+" environment variable";
    public static final String OPT_ACCOUNT = "-a";
    public static final String LONGOPT_ACCOUNT = "--account";
    @Option(name=OPT_ACCOUNT, aliases=LONGOPT_ACCOUNT, usage=USAGE_ACCOUNT, required=true)
    @Getter @Setter private String account;

    public static final String USAGE_API_BASE = "The server's API base URI. Default is http://127.0.0.1:4003";
    public static final String OPT_API_BASE = "-s";
    public static final String LONGOPT_API_BASE = "--server";
    @Option(name=OPT_API_BASE, aliases=LONGOPT_API_BASE, usage=USAGE_API_BASE)
    @Getter @Setter private String apiBase = "http://127.0.0.1:4003";

    @Getter private final String password = initPassword();

    private String initPassword() {
        final String pass = System.getenv(PASSWORD_ENV_VAR);
        if (StringUtil.empty(pass)) die("No " + PASSWORD_ENV_VAR + " defined in environment");
        return pass;
    }
}
