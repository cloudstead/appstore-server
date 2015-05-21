package cloudos.appstore.mock;

import cloudos.appstore.dao.PublishedAppDAO;
import cloudos.appstore.model.PublishedApp;

import java.util.List;

public class MockPublishedAppDAO extends PublishedAppDAO {

    @Override
    protected List<PublishedApp> initPublishedApps() {
        // todo: return fixed list of test apps
        return super.initPublishedApps();
    }
}
