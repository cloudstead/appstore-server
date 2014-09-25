package cloudos.appstore.auth;

import com.sun.jersey.spi.container.ContainerRequest;
import edu.emory.mathcs.backport.java.util.Arrays;
import lombok.Getter;
import cloudos.appstore.ApiConstants;
import cloudos.appstore.model.AppStoreAccount;
import org.cobbzilla.wizard.filters.auth.AuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.ext.Provider;
import java.util.HashSet;
import java.util.Set;

@Provider
@Service
public class ApiAuthFilter extends AuthFilter<AppStoreAccount> {

    private static final Set<String> SKIP_AUTH_PATHS = new HashSet<String>(Arrays.asList(new String[] {
            ApiConstants.AUTH_ENDPOINT
    }));

    private static final Set<String> SKIP_AUTH_PREFIXES = new HashSet<String>(Arrays.asList(new String[] {
            ApiConstants.APPSTORE_ENDPOINT
    }));

    private static final Set<String> PUBLISHER_PERMITTED = new HashSet<String>(Arrays.asList(new String[] {
            ApiConstants.PUBLISHERS_ENDPOINT,
            ApiConstants.APPS_ENDPOINT,
            ApiConstants.APP_VERSIONS_ENDPOINT,
            ApiConstants.ACCOUNTS_ENDPOINT
    }));

    private static final Set<String> CONSUMER_PERMITTED = new HashSet<String>(Arrays.asList(new String[] {

    }));

    @Autowired @Getter private ApiAuthProvider authProvider;

    public ApiAuthFilter() {
        setAuthTokenHeader(ApiConstants.H_TOKEN);
        setSkipAuthPaths(SKIP_AUTH_PATHS);
        setSkipAuthPrefixes(SKIP_AUTH_PREFIXES);
    }

    @Override
    protected boolean isPermitted(AppStoreAccount principal, ContainerRequest request) {
        final String uri = request.getRequestUri().getPath();
        if (principal.isPublisher()) return isPermitted(uri, PUBLISHER_PERMITTED);
        if (principal.isConsumer()) return isPermitted(uri, CONSUMER_PERMITTED);
        return false;
    }

    private boolean isPermitted(String uri, Set<String> permitted) {
        for (String ok : permitted) {
            if (uri.startsWith(ok)) return true;
        }
        return false;
    }

}
