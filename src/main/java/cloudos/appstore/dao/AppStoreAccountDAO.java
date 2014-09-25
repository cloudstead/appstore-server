package cloudos.appstore.dao;

import cloudos.appstore.model.AppStoreAccount;
import org.cobbzilla.wizard.dao.AbstractCRUDDAO;
import org.cobbzilla.wizard.validation.UniqueValidatorDao;
import org.springframework.stereotype.Repository;

@Repository
public class AppStoreAccountDAO extends AbstractCRUDDAO<AppStoreAccount> implements UniqueValidatorDao {

    public AppStoreAccount findByEmail(String email) { return findByUniqueField("email", email); }

    @Override
    public boolean isUnique(String uniqueFieldName, Object uniqueValue) {
        if (uniqueValue == null) return true;
        if (uniqueFieldName.endsWith("email")) return findByEmail(uniqueValue.toString()) == null;
        throw new IllegalArgumentException("entityExists: unsupported uniqueFieldName: "+uniqueFieldName);
    }

    @Override
    public boolean isUnique(String uniqueFieldName, Object uniqueValue, String idFieldName, Object idValue) {
        if (uniqueValue == null) return true;
        if (uniqueFieldName.endsWith("email")) {
            final AppStoreAccount account = findByEmail(uniqueValue.toString());
            if (account == null) return true;

            switch (idFieldName) {
                case "uuid": return account.getUuid().equals(idValue);
                default: throw new IllegalArgumentException("isUnique: unsupported idFieldName: "+idFieldName);
            }
        }
        throw new IllegalArgumentException("isUnique: unsupported uniqueFieldName: "+uniqueFieldName);
    }

}
