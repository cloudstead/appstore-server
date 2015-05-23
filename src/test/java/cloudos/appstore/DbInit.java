package cloudos.appstore;

import cloudos.appstore.dao.AppStoreAccountDAO;
import cloudos.appstore.dao.AppStorePublisherDAO;
import cloudos.appstore.dao.AppStorePublisherMemberDAO;
import cloudos.appstore.model.AppStoreAccount;
import cloudos.appstore.model.AppStorePublisher;
import cloudos.appstore.model.AppStorePublisherMember;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.cobbzilla.wizard.model.HashedPassword;
import org.junit.Test;

import static org.cobbzilla.util.io.StreamUtil.loadResourceAsString;
import static org.cobbzilla.util.json.JsonUtil.fromJson;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Slf4j
public class DbInit extends AppStoreITBase {

    public boolean doCreateAdmin () { return false; }

    @Test public void initDatabase () throws Exception {

        AppStoreAccount account = fromJson(loadResourceAsString("default-account.json"), AppStoreAccount.class);

        final AppStoreAccountDAO accountDAO = getBean(AppStoreAccountDAO.class);
        final AppStorePublisherDAO publisherDAO = getBean(AppStorePublisherDAO.class);
        final AppStorePublisherMemberDAO memberDAO = getBean(AppStorePublisherMemberDAO.class);

        account.setHashedPassword(new HashedPassword(RandomStringUtils.randomAlphanumeric(20)));
        account = accountDAO.create(account);
        assertNotNull(accountDAO.findByEmail(account.getEmail()));

        AppStorePublisher publisher = fromJson(loadResourceAsString("default-publisher.json"), AppStorePublisher.class);
        publisher.setOwner(account.getUuid());
        publisher.initUuid();
        publisher = publisherDAO.create(publisher);
        assertNotNull(publisherDAO.findByName(publisher.getName()));

        AppStorePublisherMember member = new AppStorePublisherMember()
                .setPublisher(publisher.getUuid())
                .setAccount(account.getUuid())
                .setActive(true);
        memberDAO.create(member);
        assertTrue(memberDAO.findByPublisher(publisher.getUuid()).size() == 1);
    }

}
