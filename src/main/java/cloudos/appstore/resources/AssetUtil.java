package cloudos.appstore.resources;

import org.cobbzilla.util.security.ShaUtil;
import org.cobbzilla.wizard.validation.ConstraintViolationBean;

import java.util.Arrays;
import java.util.List;

import static cloudos.appstore.ValidationConstants.*;

public class AssetUtil {

    public static void verifyAsset(String field, String url, String sha, List<ConstraintViolationBean> violations, String[] allowedAssetSchemes) {
        boolean schemeOK = false;
        for (String scheme : allowedAssetSchemes) {
            if (url.startsWith(scheme+"://")) {
                schemeOK = true;
                break;
            }
        }
        if (!schemeOK) {
            violations.add(new ConstraintViolationBean(ERR_URL_INVALID_SCHEME_PREFIX+field, "Invalid scheme, allowed="+Arrays.toString(allowedAssetSchemes), url));
            return;
        }
        try {
            final String urlSha = ShaUtil.sha256_url(url);
            if (!urlSha.equals(sha)) {
                violations.add(new ConstraintViolationBean(ERR_SHA_MISMATCH_PREFIX+field, "SHA mismatch ("+urlSha+"), given URL="+url+", SHA="+sha, urlSha));
            }
        } catch (Exception e) {
            violations.add(new ConstraintViolationBean(ERR_SHA_CALCULATION_PREFIX+field, e.getLocalizedMessage(), url));
        }
    }


}
