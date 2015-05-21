package cloudos.appstore.dao;

import cloudos.appstore.model.CloudAppStatus;
import cloudos.appstore.model.CloudAppVersion;
import org.cobbzilla.wizard.dao.AbstractCRUDDAO;
import org.hibernate.criterion.Restrictions;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CloudAppVersionDAO extends AbstractCRUDDAO<CloudAppVersion> {

    public List<CloudAppVersion> findByApp(String name) {
        return list(criteria().add(Restrictions.eq("app", name)));
    }

    public CloudAppVersion findByNameAndVersion(String name, String version) {
        return uniqueResult(criteria().add(
                Restrictions.and(
                        Restrictions.eq("app", name),
                        Restrictions.eq("version", version)
                )
        ));
    }

    public List<CloudAppVersion> findPublishedVersions() {
        return list(criteria().add(Restrictions.eq("status", CloudAppStatus.published)));
    }

}
