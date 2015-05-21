package cloudos.appstore.mock;

import cloudos.appstore.dao.PublishedAppDAO;
import cloudos.appstore.model.PublishedApp;

import java.util.ArrayList;
import java.util.List;

public class MockPublishedAppDAO extends PublishedAppDAO {

    private List<PublishedApp> testApps = new ArrayList<>();

    public void addApp (PublishedApp app) { testApps.add(app); }

    @Override public void flushApps() { testApps.clear(); }

    @Override protected List<PublishedApp> initPublishedApps() {
        return testApps;
    }

}
