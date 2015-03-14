package cloudos.appstore.dao;

import net.rubyeye.xmemcached.MemcachedClient;
import net.rubyeye.xmemcached.MemcachedClientBuilder;
import net.rubyeye.xmemcached.XMemcachedClientBuilder;
import cloudos.appstore.model.support.ApiToken;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;

@Repository
public class ApiTokenDAO {

    public static final int EXPIRATION = (int) TimeUnit.DAYS.toSeconds(1);

    private final MemcachedClient client;

    public ApiTokenDAO() throws Exception {
        final MemcachedClientBuilder builder = new XMemcachedClientBuilder("127.0.0.1:11211");
        client = builder.build();
        client.setPrimitiveAsString(true);
    }

    public ApiToken generateNewToken (String accountUuid) {
        final ApiToken token = newToken();
        try {
            if (!client.add(token.getToken(), EXPIRATION, accountUuid)) {
                die("generateNewToken: error writing to memcached: call returned false");
            }
        } catch (Exception e) {
            die("generateNewToken: error writing to memcached: "+e, e);
        }
        return token;
    }

    private ApiToken newToken() {
        final ApiToken token = new ApiToken();
        token.init();
        return token;
    }

    public String findAccount (String token) {
        try {
            return client.get(token);
        } catch (Exception e) {
            return die("findAccount: error reading from memcached: "+e, e);
        }
    }

    public void cancel(String token) {
        try {
            client.delete(token);
        } catch (Exception e) {
            die("cancel: error deleting from memcached: " + e, e);
        }
    }

    public ApiToken refreshToken(ApiToken token) {
        final ApiToken newToken = newToken();
        try {
            final String account = client.get(token.getToken());

            if (account == null) return null; // token not found

            // create new token
            if (client.set(newToken.getToken(), EXPIRATION, account)) return null;

            // delete old token (ignore return code)
            client.delete(token.getToken());

            return newToken;

        } catch (Exception e) {
            die("refreshToken: error talking to memcached: "+e, e); return null;
        }
    }
}
