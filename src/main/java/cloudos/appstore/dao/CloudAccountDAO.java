package cloudos.appstore.dao;

import cloudos.appstore.model.AppStoreCloudAccount;
import org.cobbzilla.wizard.dao.AbstractCRUDDAO;
import org.springframework.stereotype.Repository;

@Repository
public class CloudAccountDAO extends AbstractCRUDDAO<AppStoreCloudAccount> {

    public AppStoreCloudAccount findByUcid (String ucid) {
        return findByUniqueField("ucid", ucid);
    }

}
