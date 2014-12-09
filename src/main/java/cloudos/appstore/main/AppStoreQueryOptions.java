package cloudos.appstore.main;

import cloudos.appstore.model.support.AppStoreObjectType;
import cloudos.appstore.model.support.AppStoreQuery;
import lombok.Getter;
import lombok.Setter;
import org.kohsuke.args4j.Option;

public class AppStoreQueryOptions extends PagedAppStoreMainOptions {

    public static final String USAGE_TYPE = "What to search for";
    public static final String OPT_TYPE = "-t";
    public static final String LONGOPT_TYPE = "--type";
    @Option(name=OPT_TYPE, aliases=LONGOPT_TYPE, usage=USAGE_TYPE, required=true)
    @Getter @Setter private AppStoreObjectType type;

    public AppStoreQuery getQueryObject() { return new AppStoreQuery(getType(), super.getPage()); }

}
