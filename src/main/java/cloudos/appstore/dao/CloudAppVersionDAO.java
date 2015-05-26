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

    public List<CloudAppVersion> findByApp(String name) {
        return list(criteria().add(Restrictions.eq("app", name)));
    }

    public CloudAppVersion findByNameAndVersion(String name, String version) {
        return uniqueResult(criteria().add(
                Restrictions.and(
                        Restrictions.eq("app", name),
                        Restrictions.eq("version", version))));
    }

    public List<CloudAppVersion> findPublishedVersions() {
        return list(criteria().add(Restrictions.eq("status", CloudAppStatus.published)));
    }

    public List<CloudAppVersion> findPublishedVersions(String name) {
        return list(criteria().add(
                Restrictions.and(
                        Restrictions.eq("app", name),
                        Restrictions.or(
                                Restrictions.eq("status", CloudAppStatus.hidden),
                                Restrictions.eq("status", CloudAppStatus.published)))));
    }

    public CloudAppVersion findLatestPublishedVersion(String name) {
        final SortedSet<CloudAppVersion> sorted = new TreeSet<>(CloudAppVersion.LATEST_VERSION_FIRST);
        sorted.addAll(findPublishedVersions(name));
        return empty(sorted) ? null : sorted.first();
    }

}
