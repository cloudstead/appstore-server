package cloudos.appstore.main;

import org.cobbzilla.wizard.main.MainApiOptionsBase;

import static org.cobbzilla.util.system.CommandShell.hostname;

public class AppStoreMainOptions extends MainApiOptionsBase {

    public static final String DEFAULT_API_BASE_URI = "https://" + hostname() + "/appstore/";

    @Override protected String getDefaultApiBaseUri() { return DEFAULT_API_BASE_URI; }
    @Override protected String getPasswordEnvVarName() { return "APPSTORE_PASS"; }

}
