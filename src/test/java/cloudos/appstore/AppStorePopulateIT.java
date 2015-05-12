package cloudos.appstore;

import lombok.AllArgsConstructor;
import cloudos.appstore.model.*;
import cloudos.appstore.model.support.ApiToken;
import cloudos.appstore.test.AppStoreTestUtil;
import org.junit.Test;

import static cloudos.appstore.test.AppStoreTestUtil.assetUrl;
import static org.cobbzilla.util.security.ShaUtil.sha256_url;

public class AppStorePopulateIT extends AppStoreITBase {

    @AllArgsConstructor
    private class AppTemplate {
        public String name;
        public String description;
        public String smallIconUrl;
        public String largeIconUrl;
        public String serverConfigUrl;
        public int initialCost;
        public int monthlyCost;
        public boolean paymentRequired;
    }

    public AppTemplate[] APP_TEMPLATES = new AppTemplate[] {
        new AppTemplate("CloudFiles", "Store your files in the cloud",
                "assets/cloud_files_small.jpg", "assets/cloud_files_large.jpg", "assets/cloud_files_config.json",
                1000, 100, false)
    };

    @Test
    public void populate () throws Exception {
        final ApiToken token = AppStoreTestUtil.registerPublisher(appStoreClient);
        final AppStoreAccount account = appStoreClient.findAccount();
        appStoreClient.pushToken(token.getToken());

        for (AppTemplate template : APP_TEMPLATES) {
            buildApp(account, template);
        }
    }

    private void buildApp(AppStoreAccount account, AppTemplate template) throws Exception {
        CloudApp app = new CloudApp();
        app.setPublisher(account.getUuid());
        app.setName(template.name);
        app = appStoreClient.defineApp(app);

        CloudAppVersion version = new CloudAppVersion();
        version.setApp(app.getUuid());
        version.setAppStatus(CloudAppStatus.NEW);
        version.setVersion("0.0.1");

        final AppMutableData data = new AppMutableData();
        data.setDescription(template.description);
        data.setBlurb(template.description.substring(0, 10));
        data.setSmallIconUrl(assetUrl(template.smallIconUrl));
        data.setSmallIconUrlSha(sha256_url(data.getSmallIconUrl()));
        data.setLargeIconUrl(assetUrl(template.largeIconUrl));
        data.setLargeIconUrlSha(sha256_url(data.getLargeIconUrl()));
        version.setData(data);

        version.setBundleUrl(assetUrl(template.serverConfigUrl));
        version.setBundleUrlSha(sha256_url(version.getBundleUrl()));

        version = appStoreClient.defineAppVersion(version);

        final AppPrice price = new AppPrice();
        price.setCloudApp(app.getUuid());
        price.setIsoCurrency("USD");
        price.setInitialCost(template.initialCost);
        price.setMonthlyFixedCost(template.monthlyCost);
        appStoreClient.setAppPrice(price);

        appStoreClient.pushToken(adminToken);
        version.setAppStatus(CloudAppStatus.PUBLISHED);
        appStoreClient.updateAppVersion(version);
        appStoreClient.popToken();
    }

}
