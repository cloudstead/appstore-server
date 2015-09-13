package cloudos.appstore.main;

import cloudos.appstore.client.AppStoreApiClient;

import java.io.File;

import static org.cobbzilla.util.io.FileUtil.abs;

public class AppStoreAssetMain extends AppStoreMainBase<AppStoreAssetOptions> {

    public static void main (String[] args) { main(AppStoreAssetMain.class, args); }

    @Override protected void run() throws Exception {

        final AppStoreApiClient api = getApiClient();
        final AppStoreAssetOptions options = getOptions();

        final File asset = options.hasVersion()
                ? api.getAppAsset(options.getPublisher(), options.getApp(), options.getVersion(), options.getAsset())
                : api.getLatestAsset(options.getPublisher(), options.getApp(), options.getAsset());

        out(asset == null ? "asset not found" : abs(asset));
    }
}
