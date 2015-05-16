package cloudos.appstore.dao;

import cloudos.appstore.model.AppStorePublisher;
import org.cobbzilla.wizard.dao.UniquelyNamedEntityDAO;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AppStorePublisherDAO extends UniquelyNamedEntityDAO<AppStorePublisher> {

    public AppStorePublisher findByName(String name) { return findByUniqueField("name", name); }

    public List<AppStorePublisher> findByOwner(String uuid) { return findByField("owner", uuid); }

}
