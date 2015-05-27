package cloudos.appstore.dao;

import cloudos.appstore.model.CloudAppStatus;
import cloudos.appstore.model.CloudAppVersion;
import org.cobbzilla.wizard.dao.AbstractCRUDDAO;
import org.hibernate.criterion.Restrictions;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@Repository
public class CloudAppVersionDAO extends AbstractCRUDDAO<CloudAppVersion> {

    public List<CloudAppVersion> findByApp(String appUuid) {
        return list(criteria().add(Restrictions.eq("app", appUuid)));
    }

    public CloudAppVersion findByUuidAndVersion(String appUuid, String version) {
        return uniqueResult(criteria().add(
                Restrictions.and(
                        Restrictions.eq("app", appUuid),
                        Restrictions.eq("version", version))));
    }

    public List<CloudAppVersion> findPublishedVersions() {
        return list(criteria().add(Restrictions.eq("status", CloudAppStatus.published)));
    }

    public List<CloudAppVersion> findPublishedVersions(String appUuid) {
        return list(criteria().add(
                Restrictions.and(
                        Restrictions.eq("app", appUuid),
                        Restrictions.eq("status", CloudAppStatus.published))));
    }

    public CloudAppVersion findLatestPublishedVersion(String appUuid) {
        final SortedSet<CloudAppVersion> sorted = new TreeSet<>(CloudAppVersion.LATEST_VERSION_FIRST);
        sorted.addAll(findPublishedVersions(appUuid));
        return empty(sorted) ? null : sorted.first();
    }

}
