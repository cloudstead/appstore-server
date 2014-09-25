package cloudos.appstore.dao;

import cloudos.appstore.model.AppFootprint;
import org.cobbzilla.wizard.dao.AbstractCRUDDAO;
import org.springframework.stereotype.Repository;

@Repository
public class AppFootprintDAO extends AbstractCRUDDAO<AppFootprint> {

    public AppFootprint findByApp(String uuid) {
        return findByUniqueField("cloudApp", uuid);
    }

}

