package cloudos.appstore.main;

import org.cobbzilla.wizard.client.ApiClientBase;

import static cloudos.appstore.ApiConstants.AUTH_ENDPOINT;
import static org.cobbzilla.util.json.JsonUtil.toJson;

public class AppStoreAccountMain extends AppStoreMainBase<AppStoreAccountOptions> {

    public static void main (String[] args) { main(AppStoreAccountMain.class, args); }

    @Override protected void run() throws Exception {
        final ApiClientBase api = getApiClient();
        final AppStoreAccountOptions options = getOptions();

        switch (options.getOperation()) {
            case create:
                out(api.put(AUTH_ENDPOINT, toJson(options.getRegistration())).toString());
                break;

            case read:
                out(api.get(options.accountUri()).toString());
                break;

            case update:
                out(api.post(options.accountUri(), toJson(options.getAppStoreAccount())).toString());
                break;

            case delete:
                out(api.delete(options.accountUri()).toString());
                break;

            default:
                die("unsupported operation: "+options.getOperation());
        }
    }
}
