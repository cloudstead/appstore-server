package cloudos.appstore.main;

import cloudos.appstore.client.AppStoreApiClient;
import cloudos.appstore.model.support.ApiToken;
import cloudos.appstore.model.support.RefreshTokenRequest;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import static cloudos.appstore.ApiConstants.AUTH_ENDPOINT;
import static org.cobbzilla.util.json.JsonUtil.fromJson;
import static org.cobbzilla.util.json.JsonUtil.toJson;

@Slf4j
public abstract class AppStoreMainBase<OPT extends AppStoreMainOptions> {

    @Getter private final OPT options = initOptions();
    protected abstract OPT initOptions();

    private final CmdLineParser parser = new CmdLineParser(getOptions());

    @Getter private String[] args;
    public void setArgs(String[] args) throws CmdLineException {
        this.args = args;
        parser.parseArgument(args);
    }

    @Getter(value= AccessLevel.PROTECTED, lazy=true) private final AppStoreApiClient apiClient = initApiClient();

    private AppStoreApiClient initApiClient() {
        return new AppStoreApiClient(getOptions().getApiBase());
    }

    protected static void main(Class<? extends AppStoreMainBase> clazz, String[] args) {
        try {
            AppStoreMainBase m = clazz.newInstance();
            m.setArgs(args);
            m.login();
            m.run();
        } catch (Exception e) {
            log.error("Unexpected error: "+e, e);
        }
    }

    protected abstract void run() throws Exception;

    protected void login () {
        log.info("logging in "+options.getAccount()+" ...");
        try {
            final RefreshTokenRequest loginRequest = new RefreshTokenRequest()
                    .setEmail(options.getAccount())
                    .setPassword(options.getPassword());
            final AppStoreApiClient api = getApiClient();
            final ApiToken token = fromJson(api.post(AUTH_ENDPOINT, toJson(loginRequest)).json, ApiToken.class);
            api.pushToken(token.getToken());

        } catch (Exception e) {
            throw new IllegalStateException("Error logging in: "+e, e);
        }
    }

}
