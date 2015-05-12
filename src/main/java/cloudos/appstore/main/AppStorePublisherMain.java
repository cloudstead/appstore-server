package cloudos.appstore.main;

import org.cobbzilla.wizard.client.ApiClientBase;

import static org.cobbzilla.util.json.JsonUtil.toJson;

public class AppStorePublisherMain extends AppStoreMainBase<AppStorePublisherOptions> {

    public static void main (String[] args) { main(AppStorePublisherMain.class, args); }

    @Override protected AppStorePublisherOptions initOptions() { return new AppStorePublisherOptions(); }

    @Override protected void run() throws Exception {
        final ApiClientBase api = getApiClient();
        final AppStorePublisherOptions options = getOptions();

        switch (options.getOperation()) {
            case create:
                die("publishers cannot be created. create a new account instead.");
                break;

            case read:
                out(api.get(options.publisherUri()).toString());
                break;

            case update:
                out(api.post(options.publisherUri(), toJson(options.getPublisher())).toString());
                break;

            case delete:
                out(api.delete(options.publisherUri()).toString());
                break;

            default:
                die("unsupported operation: "+options.getOperation());
        }
    }
}
