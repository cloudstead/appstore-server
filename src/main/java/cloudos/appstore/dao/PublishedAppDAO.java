package cloudos.appstore.dao;

import cloudos.appstore.model.PublishedApp;
import org.cobbzilla.wizard.dao.AbstractCRUDDAO;
import org.hibernate.criterion.Restrictions;
import org.springframework.stereotype.Repository;

@Repository
public class PublishedAppDAO extends AbstractCRUDDAO<PublishedApp> {

    @Override
    protected String formatBound(String entityAlias, String bound, String value) {
        return entityAlias + "."+bound+" "+value;
    }

    public PublishedApp findByApp(String uuid) {
        return uniqueResult(criteria().add(Restrictions.eq("app", uuid)));
    }
}
