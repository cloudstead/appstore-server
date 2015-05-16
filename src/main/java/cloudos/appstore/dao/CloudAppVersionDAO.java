package cloudos.appstore.dao;

import cloudos.appstore.model.CloudAppVersion;
import org.cobbzilla.wizard.dao.AbstractCRUDDAO;
import org.hibernate.criterion.Restrictions;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CloudAppVersionDAO extends AbstractCRUDDAO<CloudAppVersion> {

    public List<CloudAppVersion> findByApp(String uuid) {
        return list(criteria().add(Restrictions.eq("app", uuid)));
    }

    public CloudAppVersion findByAppAndVersion(String uuid, String version) {
        return uniqueResult(criteria().add(Restrictions.and(
                Restrictions.eq("app", uuid),
                Restrictions.eq("version", version))));
    }

}
