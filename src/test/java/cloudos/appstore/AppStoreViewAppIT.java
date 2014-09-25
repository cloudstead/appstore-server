package cloudos.appstore;

import cloudos.appstore.model.AppFootprint;
import cloudos.appstore.model.AppPrice;
import cloudos.appstore.model.CloudApp;
import cloudos.appstore.test.AppStoreSeedData;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class AppStoreViewAppIT extends AppStoreITBase {

    public static final String DOC_TARGET = "view details for an app";

//    private static final CsFootprint FOOTPRINT = new CsFootprint()
//            .setCpus(1)
//            .setMemory(512)
//            .setDiskIoLevel(CsUsageLevel.LOW)
//            .setNetworkIoLevel(CsUsageLevel.LOW);

    static final ImmutableMap<String, AppPrice> PRICES =
            new ImmutableMap.Builder<String, AppPrice>()
                    .put("USD", new AppPrice()
                            .setPaymentRequired(true)
                            .setInitialCost(100)
                            .setMonthlyFixedCost(0)
                    )
                    .put("CAD", new AppPrice()
                            .setPaymentRequired(true)
                            .setInitialCost(120)
                            .setMonthlyFixedCost(20)
                    )
                    .put("BTC", new AppPrice()
                            .setPaymentRequired(false)
                            .setInitialCost(1)
                            .setMonthlyFixedCost(0)
                    )
                    .build();

    protected AppStoreSeedData seedData;

    @Before
    public void populateAppStore () throws Exception {
        seedData = new AppStoreSeedData(appStoreClient, adminToken, 1, 1, 1);
    }

    @Test
    public void testViewAppDetails() throws Exception {

        final CloudApp app = seedData.getFirstApp();

//        final AppFootprint footprint = new AppFootprint(FOOTPRINT);
        final AppFootprint footprint = new AppFootprint();
        footprint.setCloudApp(app.getUuid());
        appStoreClient.setAppFootprint(footprint);

        final AppFootprint appFootprint = appStoreClient.getAppFootprint(app);
        assertEquals(footprint.getCpus(), appFootprint.getCpus());

        for (String currency : PRICES.keySet()) {
            final AppPrice template = PRICES.get(currency);
            final AppPrice price = new AppPrice(template);
            price.setCloudApp(app.getUuid());
            price.setIsoCurrency(currency);
            appStoreClient.setAppPrice(price);
        }

        final AppPrice[] appPrices = appStoreClient.getAppPrices(app);
        final Map<String, AppPrice> foundPrices = new HashMap<>();
        for (AppPrice found : appPrices) {
            foundPrices.put(found.getIsoCurrency(), found);
        }

        assertEquals(true, foundPrices.get("USD").isPaymentRequired());
        assertEquals(100, foundPrices.get("USD").getInitialCost());
        assertEquals(0, foundPrices.get("USD").getMonthlyFixedCost());

        assertEquals(true, foundPrices.get("CAD").isPaymentRequired());
        assertEquals(120, foundPrices.get("CAD").getInitialCost());
        assertEquals(20, foundPrices.get("CAD").getMonthlyFixedCost());

        assertEquals(false, foundPrices.get("BTC").isPaymentRequired());
        assertEquals(1, foundPrices.get("BTC").getInitialCost());
        assertEquals(0, foundPrices.get("BTC").getMonthlyFixedCost());
    }

}
