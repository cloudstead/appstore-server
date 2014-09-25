package cloudos.appstore.auth;

import cloudos.appstore.model.AppStoreAccount;
import cloudos.appstore.dao.ApiTokenDAO;
import cloudos.appstore.dao.AppStoreAccountDAO;
import org.cobbzilla.wizard.filters.auth.AuthProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ApiAuthProvider implements AuthProvider<AppStoreAccount> {

    @Autowired private ApiTokenDAO apiTokenDAO;
    @Autowired private AppStoreAccountDAO accountDAO;

    @Override
    public AppStoreAccount find(String token) {

        final String tokenAccount = apiTokenDAO.findAccount(token);
        if (tokenAccount == null) return null;

        final AppStoreAccount account = accountDAO.findByUuid(tokenAccount);
        if (account == null) return null;

        return account;
    }
}
