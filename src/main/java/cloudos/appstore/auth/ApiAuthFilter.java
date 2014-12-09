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

@Provider @Service
public class ApiAuthFilter extends AuthFilter<AppStoreAccount> {

    private static final Set<String> SKIP_AUTH_PATHS = new HashSet<String>(Arrays.asList(new String[] {
            ApiConstants.AUTH_ENDPOINT
    }));

    private static final Set<String> SKIP_AUTH_PREFIXES = new HashSet<String>(Arrays.asList(new String[] {
            ApiConstants.APPSTORE_ENDPOINT,
            ApiConstants.CLOUDS_API_ENDPOINT
    }));

    private static final Set<String> ADMIN_PATHS = new HashSet<String>(Arrays.asList(new String[] {
            ApiConstants.CLOUDS_ENDPOINT,
            ApiConstants.SEARCH_ENDPOINT
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
        if (principal.isAdmin()) return true;
        return !isPermitted(uri, ADMIN_PATHS);
    }

    private boolean isPermitted(String uri, Set<String> permitted) {
        for (String ok : permitted) {
            if (uri.startsWith(ok)) return true;
        }
        return false;
    }

}
