package cloudos.appstore.resources;

import cloudos.appstore.ApiConstants;
import cloudos.appstore.dao.ApiTokenDAO;
import cloudos.appstore.dao.CloudAccountDAO;
import cloudos.appstore.model.AppStoreCloudAccount;
import cloudos.appstore.model.support.ApiToken;
import cloudos.appstore.model.support.CloudAccountSessionRequest;
import cloudos.appstore.model.support.CloudOsSessionVerification;
import com.qmino.miredot.annotations.ReturnType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.cobbzilla.util.http.HttpMethods;
import org.cobbzilla.util.http.HttpRequestBean;
import org.cobbzilla.util.http.HttpResponseBean;
import org.cobbzilla.util.http.HttpUtil;
import org.cobbzilla.util.security.ShaUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;

import static org.cobbzilla.util.json.JsonUtil.toJsonOrDie;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(ApiConstants.CLOUDS_API_ENDPOINT)
@Service @Slf4j
public class CloudApiResource {

    @Autowired private CloudAccountDAO cloudAccountDAO;
    @Autowired private ApiTokenDAO tokenDAO;

    /**
     * Start a session for a CloudAccount
     * @param request the session start request
     * @return an API token to use for future API calls
     */
    @POST
    @Path("/session")
    @ReturnType("cloudos.appstore.model.support.ApiToken")
    public Response startSession (CloudAccountSessionRequest request) {

        final AppStoreCloudAccount account = cloudAccountDAO.findByUcid(request.getUcid());
        if (account == null) return notFound(request.getUcid());

        final String challenge = RandomStringUtils.randomAlphanumeric(32);
        final String hash = ShaUtil.sha256_hex(request.getData()+"_"+challenge);

        final CloudOsSessionVerification verification = new CloudOsSessionVerification(challenge);

        final HttpRequestBean requestBean = new HttpRequestBean<String>()
                .setUri(account.getUri())
                .setMethod(HttpMethods.POST)
                .setData(toJsonOrDie(verification));

        final HttpResponseBean response;
        try {
            response = HttpUtil.getResponse(requestBean);
        } catch (IOException e) {
            log.error("Error calling cloud verification URI, request="+requestBean+": "+e, e);
            return invalid("err.cloudSession.callback.error", e.toString());
        }

        if (response.getStatus() != 200) return invalid("err.cloudSession.callback.failed", response.toString());

        final String cloudOsHash = response.getEntityString();
        if (cloudOsHash == null || !cloudOsHash.equals(hash)) return forbidden();

        final ApiToken token = tokenDAO.generateNewToken(account.getUuid());

        return ok(token);
    }

}
