package cloudos.appstore.main;

import cloudos.appstore.model.AppStorePublisher;
import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.wizard.api.CrudOperation;
import org.kohsuke.args4j.Option;

import static cloudos.appstore.ApiConstants.PUBLISHERS_ENDPOINT;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.string.StringUtil.empty;

public class AppStorePublisherOptions extends AppStoreMainOptions {

    public static final String USAGE_NAME = "Name of the publisher. Required for update.";
    public static final String OPT_NAME = "-n";
    public static final String LONGOPT_NAME = "--name";
    @Option(name=OPT_NAME, aliases=LONGOPT_NAME, usage=USAGE_NAME)
    @Getter @Setter private String name;

    public static final String USAGE_UUID = "UUID of the publisher. Required for read and write operations.";
    public static final String OPT_UUID = "-U";
    public static final String LONGOPT_UUID = "--uuid";
    @Option(name=OPT_UUID, aliases=LONGOPT_UUID, usage=USAGE_UUID)
    @Getter @Setter private String uuid;
    public boolean hasUuid () { return !empty(uuid); }

    public static final String USAGE_OPERATION = "The operation to perform. Create is not supported.";
    public static final String OPT_OPERATION = "-o";
    public static final String LONGOPT_OPERATION = "--operation";
    @Option(name=OPT_OPERATION, aliases=LONGOPT_OPERATION, usage=USAGE_OPERATION)
    @Getter @Setter private CrudOperation operation = CrudOperation.read;

    public String publisherUri () { return PUBLISHERS_ENDPOINT + "/" + (hasUuid() ? getUuid() : die("no uuid")); }

    public AppStorePublisher getPublisher() { return new AppStorePublisher().setName(name); }

}
