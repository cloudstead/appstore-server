package cloudos.appstore.main;

import cloudos.appstore.client.AppStoreApiClient;
import cloudos.appstore.model.AppStoreAccount;
import org.cobbzilla.wizard.api.CrudOperation;
import org.cobbzilla.wizard.util.RestResponse;

import static cloudos.appstore.ApiConstants.ACCOUNTS_ENDPOINT;
import static cloudos.appstore.ApiConstants.APPS_ENDPOINT;
import static cloudos.appstore.main.AppStoreAppsOptions.*;
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

        final CrudOperation operation = options.getOperation();
        switch (operation) {
            case create:
                if (!options.hasBundleUrl()) die(OPT_BUNDLE_URL+"/"+LONGOPT_BUNDLE_URL+" is required for "+operation);
                response = api.doPost(uri, toJson(options.getCloudAppRequest(account)));
                break;

            case update:
                if (!options.hasStatus()) die(OPT_STATUS+"/"+LONGOPT_STATUS+" is required for "+operation);
                if (!options.hasName()) die(OPT_NAME+"/"+LONGOPT_NAME+" is required for "+operation);
                if (!options.hasVersion()) die(OPT_VERSION+"/"+LONGOPT_VERSION+" is required for "+operation);
                uri += "/" + options.getName() + "/versions/" + options.getVersion() + "/status";
                response = api.doPost(uri, toJson(options.getStatus()));
                break;

            case read:
                if (options.hasName()) uri += "/" + options.getName();
                if (options.hasVersion()) uri += "/versions/" + options.getVersion();
                response = api.doGet(uri);
                break;

            case delete:
                if (!options.hasName()) die(OPT_NAME+"/"+LONGOPT_NAME+" is required for "+operation);
                uri += "/" + options.getName();
                if (options.hasVersion()) uri += "/versions/" + options.getVersion();
                response = api.doDelete(uri);
                break;

            default:
                die("invalid operation: "+ operation);
                return;
        }

        if (response.isSuccess()) {
            out(response.json);
        } else {
            die(response.toString());
        }
    }

}
