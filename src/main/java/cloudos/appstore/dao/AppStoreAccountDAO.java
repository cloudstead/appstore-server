package cloudos.appstore.dao;

import cloudos.appstore.model.AppStoreAccount;
import org.cobbzilla.util.collection.MapBuilder;
import org.cobbzilla.wizard.dao.UniquelyNamedEntityDAO;
import org.cobbzilla.wizard.validation.UniqueValidatorDao;
import org.cobbzilla.wizard.validation.UniqueValidatorDaoHelper;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class AppStoreAccountDAO extends UniquelyNamedEntityDAO<AppStoreAccount> implements UniqueValidatorDao {

    public AppStoreAccount findByEmail(String email) { return findByUniqueField("email", email); }
    public AppStoreAccount findByMobilePhone(String mobilePhone) { return findByUniqueField("mobilePhone", mobilePhone); }

    @Override
    protected Map<String, UniqueValidatorDaoHelper.Finder<AppStoreAccount>> getUniqueHelpers() {

        final Map<String, UniqueValidatorDaoHelper.Finder<AppStoreAccount>> helpers = super.getUniqueHelpers();

        helpers.putAll(MapBuilder.<String, UniqueValidatorDaoHelper.Finder<AppStoreAccount>>build(new Object[][]{
                {"email", new UniqueValidatorDaoHelper.Finder<AppStoreAccount>() {
                    @Override public AppStoreAccount find(Object query) { return findByEmail(query.toString()); }
                }},
                {"mobilePhone", new UniqueValidatorDaoHelper.Finder<AppStoreAccount>() {
                    @Override public AppStoreAccount find(Object query) { return findByMobilePhone(query.toString()); }
                }},
        }));

        return helpers;
    }

}
