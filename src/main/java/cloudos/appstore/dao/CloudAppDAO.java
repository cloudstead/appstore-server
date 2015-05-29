package cloudos.appstore.dao;

import cloudos.appstore.model.*;
import org.cobbzilla.wizard.dao.AbstractCRUDDAO;
import org.hibernate.criterion.Restrictions;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public class CloudAppDAO extends AbstractCRUDDAO<CloudApp> {

    public List<CloudApp> findVisibleToEveryone() { return findByField("visibility", AppVisibility.everyone); }

    public List<CloudApp> findVisibleToMember(Collection<AppStorePublisherMember> memberships) {
        return list(criteria().add(Restrictions.and(
                Restrictions.eq("visibility", AppVisibility.members),
                Restrictions.in("publisher", AppStorePublisherMember.toPublisher(memberships)))));
    }

    public List<CloudApp> findVisibleToPublisher(Collection<AppStorePublisher> publishers) {
        return list(criteria().add(Restrictions.and(
                Restrictions.eq("visibility", AppVisibility.publisher),
                Restrictions.in("publisher", toUuid(publishers)))));
    }

    public List<CloudApp> findByPublisher(String publisher) {
        return list(criteria().add(Restrictions.eq("publisher", publisher)));
    }

    public CloudApp findByPublisherAndName(String publisher, String name) {
        return uniqueResult(criteria().add(Restrictions.and(
                Restrictions.eq("publisher", publisher),
                Restrictions.eq("name", name))));
    }

    /**
     * Return a list of apps owned by the publisher and visible to the account.
     * This will include all apps if the account is the owner of the publisher, if
     * the account is a member of the publisher, they will see apps with
     * visibility = everyone or members. If the account is not a member, they will only
     * see apps visible to everyone
     * @param publisher The publisher to list apps for
     * @param account The account making the request
     * @param membership The membership record if the account is a member of the publisher, null otherwise
     * @return A list of apps owned by the publisher that are visible to the account.
     */
    public List<CloudApp> findByPublisher(AppStorePublisher publisher,
                                          AppStoreAccount account,
                                          AppStorePublisherMember membership) {
        if (publisher.isOwner(account)) return findByPublisher(publisher.getUuid());
        if (membership != null && membership.isActive()
                && membership.getAccount().equals(account.getUuid())
                && membership.getPublisher().equals(publisher.getUuid())) {

            return list(criteria().add(Restrictions.and(
                    Restrictions.in("visibility", AppVisibility.EVERYONE_OR_MEMBER),
                    Restrictions.eq("publisher", publisher))));

        }
        return list(criteria().add(Restrictions.and(
                Restrictions.eq("visibility", AppVisibility.everyone),
                Restrictions.eq("publisher", publisher))));
    }

}
