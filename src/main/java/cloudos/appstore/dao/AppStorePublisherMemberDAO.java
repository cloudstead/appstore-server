package cloudos.appstore.dao;

import cloudos.appstore.model.AppStorePublisherMember;
import org.cobbzilla.wizard.dao.AbstractCRUDDAO;
import org.hibernate.criterion.Restrictions;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AppStorePublisherMemberDAO extends AbstractCRUDDAO<AppStorePublisherMember> {

    public AppStorePublisherMember findByAccountAndPublisher(String account, String publisher) {
        return uniqueResult(criteria().add(
                Restrictions.and(
                        Restrictions.eq("account", account),
                        Restrictions.eq("publisher", publisher)
                )));
    }

    public List<AppStorePublisherMember> findByAccount(String account) {
        return list(criteria().add(Restrictions.eq("account", account)));
    }

    public List<AppStorePublisherMember> findByPublisher(String publisher) {
        return list(criteria().add(Restrictions.eq("publisher", publisher)));
    }

    public AppStorePublisherMember findByActivationCode(String code) {
        return findByUniqueField("activation", code);
    }
}
