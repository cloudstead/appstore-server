package cloudos.appstore.dao;

import cloudos.appstore.model.CloudApp;
import org.cobbzilla.wizard.dao.UniquelyNamedEntityDAO;
import org.hibernate.criterion.Restrictions;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CloudAppDAO extends UniquelyNamedEntityDAO<CloudApp> {

    public List<CloudApp> findByPublisher(String publisher) {
        return list(criteria().add(Restrictions.eq("publisher", publisher)));
    }

    public CloudApp findByName(String name) {
        return uniqueResult(criteria().add(Restrictions.eq("name", name)));
    }

}
