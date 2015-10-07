package cloudos.appstore.auth;

import cloudos.appstore.ApiConstants;
import cloudos.appstore.model.AppStoreAccount;
import com.sun.jersey.spi.container.ContainerRequest;
import lombok.Getter;
import org.cobbzilla.util.collection.SingletonSet;
import org.cobbzilla.wizard.filters.auth.AuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.ext.Provider;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Provider @Service
public class ApiAuthFilter extends AuthFilter<AppStoreAccount> {

    @Override protected String getAuthTokenHeader() { return ApiConstants.H_TOKEN; }

    @Getter private final Set<String> skipAuthPaths = new SingletonSet<>(ApiConstants.AUTH_ENDPOINT);

    @Getter private final Set<String> skipAuthPrefixes = new HashSet<>(Arrays.asList(new String[] {
            ApiConstants.APPSTORE_ENDPOINT,
            ApiConstants.CLOUDS_API_ENDPOINT
    }));

    private static final Set<String> ADMIN_PATHS = new HashSet<>(Arrays.asList(new String[] {
            ApiConstants.CLOUDS_ENDPOINT,
            ApiConstants.SEARCH_ENDPOINT
    }));

    @Autowired @Getter private ApiAuthProvider authProvider;

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
