package cloudos.appstore.main;

import cloudos.appstore.client.AppStoreApiClient;
import cloudos.appstore.model.AppStoreAccount;
import cloudos.appstore.model.AppStorePublisher;
import org.cobbzilla.wizard.api.CrudOperation;
import org.cobbzilla.wizard.util.RestResponse;

import static cloudos.appstore.ApiConstants.*;
import static cloudos.appstore.main.AppStoreAppsOptions.LONGOPT_NAME;
import static cloudos.appstore.main.AppStoreAppsOptions.OPT_NAME;
import static org.cobbzilla.util.json.JsonUtil.fromJson;
import static org.cobbzilla.util.json.JsonUtil.toJson;

public class AppStoreAppsMain extends AppStoreMainBase<AppStoreAppsOptions> {

    public static void main (String[] args) { main(AppStoreAppsMain.class, args); }

    @Override protected void run() throws Exception {

        final AppStoreAppsOptions options = getOptions();
        final AppStoreApiClient api = getApiClient();
        final RestResponse response;
        String uri = APPS_ENDPOINT;

        final AppStoreAccount account = fromJson(api.get(ACCOUNTS_ENDPOINT).json, AppStoreAccount.class);

        final AppStorePublisher publisher = options.hasPublisher()
                ? fromJson(api.get(PUBLISHERS_ENDPOINT+"/"+options.getPublisher()).json, AppStorePublisher.class)
                : null;

        if (options.hasUuid()) uri += "/" + options.getUuid();

        final CrudOperation operation = options.getOperation();
        switch (operation) {
            case create:
                if (!options.hasName()) die(OPT_NAME+"/"+LONGOPT_NAME+" is required for "+operation);
                response = api.doPut(uri, toJson(options.getCloudApp(account)));
                break;

            case read:
                response = api.doGet(uri);
                break;

            case update:
                response = api.doPost(uri, toJson(options.getCloudApp(account)));
                break;

            case delete:
                response = api.doDelete(uri);
                break;

            default:
                die("invalid operation: "+ operation);
                return;
        }

        out(response.isSuccess() ? response.json : response.toString());
    }

}
