package cloudos.appstore.main;

import cloudos.appstore.client.AppStoreApiClient;
import cloudos.model.auth.ApiToken;
import cloudos.appstore.model.support.RefreshTokenRequest;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.main.MainApiBase;
import org.cobbzilla.wizard.util.RestResponse;

import static cloudos.appstore.ApiConstants.AUTH_ENDPOINT;
import static cloudos.appstore.ApiConstants.H_TOKEN;
import static org.cobbzilla.util.json.JsonUtil.fromJson;

@Slf4j
public abstract class AppStoreMainBase<T extends AppStoreMainOptions> extends MainApiBase<T> {

    @Getter(value=AccessLevel.PROTECTED, lazy=true) private final AppStoreApiClient apiClient = initApiClient();
    private AppStoreApiClient initApiClient() { return new AppStoreApiClient(getOptions().getApiBase()); }

    @Override protected Object buildLoginRequest(AppStoreMainOptions options) {
        return new RefreshTokenRequest()
                .setEmail(options.getAccount())
                .setPassword(options.getPassword());
    }

    @Override protected String getApiHeaderTokenName() { return H_TOKEN; }
    @Override protected String getLoginUri() { return AUTH_ENDPOINT; }

    @Override protected String getSessionId(RestResponse response) throws Exception {
        return fromJson(response.json, ApiToken.class).getToken();
    }

    @Override protected void setSecondFactor(Object loginRequest, String token) {
        ((RefreshTokenRequest) loginRequest).setSecondFactor(token);
    }

}
