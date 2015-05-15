package cloudos.appstore.main;

import cloudos.appstore.ApiConstants;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.json.JsonUtil;
import org.cobbzilla.wizard.client.ApiClientBase;
import org.cobbzilla.wizard.util.RestResponse;

@Slf4j
public class AppStoreQueryMain extends AppStoreMainBase<AppStoreQueryOptions> {

    public static void main(String[] args) { main(AppStoreQueryMain.class, args); }

    @Override protected void run() throws Exception {

        final ApiClientBase api = getApiClient();
        final AppStoreQueryOptions options = getOptions();

        final RestResponse response;
        response = api.post(ApiConstants.SEARCH_ENDPOINT, JsonUtil.toJson(options.getQueryObject()));

        log.info("Search results:\n"+response.json+"\n");
    }

}
