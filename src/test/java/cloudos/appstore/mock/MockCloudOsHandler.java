package cloudos.appstore.mock;

import cloudos.appstore.model.support.CloudAccountSessionRequest;
import cloudos.appstore.model.support.CloudOsSessionVerification;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.cobbzilla.util.json.JsonUtil;
import org.cobbzilla.util.security.ShaUtil;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;
import java.io.IOException;

@Slf4j
public class MockCloudOsHandler extends AbstractHandler {

    @Getter @Setter private CloudAccountSessionRequest sessionRequest;

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        if (target.equals("/api/verify")) {
            CloudOsSessionVerification verification;
            try {
                verification = JsonUtil.fromJson(IOUtils.toString(request.getInputStream()), CloudOsSessionVerification.class);
            } catch (Exception e) {
                throw new IOException("Error parsing JSON: "+e, e);
            }
            final String hash = ShaUtil.sha256_hex(sessionRequest.getData() + "_" + verification.getChallenge());

            response.setStatus(200);
            response.addHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(hash.length()));
            response.addHeader(HttpHeaders.CONTENT_TYPE, "text/plain");
            response.getWriter().write(hash);
            response.getWriter().flush();
        }
    }

}