package cloudos.appstore.main;

import cloudos.appstore.model.AppStoreAccount;
import cloudos.appstore.model.support.AppStoreAccountRegistration;
import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.util.daemon.ZillaRuntime;
import org.cobbzilla.wizard.api.CrudOperation;
import org.cobbzilla.wizard.model.HashedPassword;
import org.kohsuke.args4j.Option;

import static cloudos.appstore.ApiConstants.ACCOUNTS_ENDPOINT;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class AppStoreAccountOptions extends AppStoreMainOptions {

    public static final String USAGE_NAME = "Name of the account. Required for create.";
    public static final String OPT_NAME = "-n";
    public static final String LONGOPT_NAME = "--name";
    @Option(name=OPT_NAME, aliases=LONGOPT_NAME, usage=USAGE_NAME)
    @Getter @Setter private String name;

    public static final String USAGE_UUID = "UUID of the account. Required for read and write operations.";
    public static final String OPT_UUID = "-U";
    public static final String LONGOPT_UUID = "--uuid";
    @Option(name=OPT_UUID, aliases=LONGOPT_UUID, usage=USAGE_UUID)
    @Getter @Setter private String uuid;
    public boolean hasUuid () { return !empty(uuid); }

    public static final String USAGE_OPERATION = "The operation to perform";
    public static final String OPT_OPERATION = "-o";
    public static final String LONGOPT_OPERATION = "--operation";
    @Option(name=OPT_OPERATION, aliases=LONGOPT_OPERATION, usage=USAGE_OPERATION)
    @Getter @Setter private CrudOperation operation = CrudOperation.read;

    public static final String USAGE_ADMIN = "Make user an admin";
    public static final String OPT_ADMIN = "-A";
    public static final String LONGOPT_ADMIN = "--admin";
    @Option(name=OPT_ADMIN, aliases=LONGOPT_ADMIN, usage=USAGE_ADMIN)
    @Getter @Setter private boolean admin = false;

    public static final String USAGE_EMAIL = "Recovery email for the account. Required for create.";
    public static final String OPT_EMAIL = "-e";
    public static final String LONGOPT_EMAIL = "--email";
    @Option(name=OPT_EMAIL, aliases=LONGOPT_EMAIL, usage=USAGE_EMAIL)
    @Getter @Setter private String email;

    public static final String USAGE_ACCOUNT_PASSWORD = "Password for the new account. Required for create.";
    public static final String OPT_ACCOUNT_PASSWORD = "-P";
    public static final String LONGOPT_ACCOUNT_PASSWORD = "--account-password";
    @Option(name=OPT_ACCOUNT_PASSWORD, aliases=LONGOPT_ACCOUNT_PASSWORD, usage=USAGE_ACCOUNT_PASSWORD)
    @Getter @Setter private String accountPassword;

    public static final String USAGE_FIRSTNAME = "First name of the account. Required for create.";
    public static final String OPT_FIRSTNAME = "-f";
    public static final String LONGOPT_FIRSTNAME = "--firstname";
    @Option(name=OPT_FIRSTNAME, aliases=LONGOPT_FIRSTNAME, usage=USAGE_FIRSTNAME)
    @Getter @Setter private String firstname;

    public static final String USAGE_LASTNAME = "Last name of the account. Required for create.";
    public static final String OPT_LASTNAME = "-l";
    public static final String LONGOPT_LASTNAME = "--lastname";
    @Option(name=OPT_LASTNAME, aliases=LONGOPT_LASTNAME, usage=USAGE_LASTNAME)
    @Getter @Setter private String lastname;

    public static final String USAGE_COUNTRYCODE = "Mobile phone country code. Required for create.";
    public static final String OPT_COUNTRYCODE = "-c";
    public static final String LONGOPT_COUNTRYCODE = "--countrycode";
    @Option(name=OPT_COUNTRYCODE, aliases=LONGOPT_COUNTRYCODE, usage=USAGE_COUNTRYCODE)
    @Getter @Setter private int countrycode;

    public static final String USAGE_MOBILEPHONE = "Mobile phone number. Required for create.";
    public static final String OPT_MOBILEPHONE = "-m";
    public static final String LONGOPT_MOBILEPHONE = "--mobilephone";
    @Option(name=OPT_MOBILEPHONE, aliases=LONGOPT_MOBILEPHONE, usage=USAGE_MOBILEPHONE)
    @Getter @Setter private String mobilephone;

    public static final String USAGE_TWOFACTOR = "Enable two-factor authentication for the account";
    public static final String OPT_TWOFACTOR = "-T";
    public static final String LONGOPT_TWOFACTOR = "--twofactor";
    @Option(name=OPT_TWOFACTOR, aliases=LONGOPT_TWOFACTOR, usage=USAGE_TWOFACTOR)
    @Getter @Setter private boolean twofactor = false;

    public static final String USAGE_SUSPENDED = "Suspend account";
    public static final String OPT_SUSPENDED = "-S";
    public static final String LONGOPT_SUSPENDED = "--suspend";
    @Option(name=OPT_SUSPENDED, aliases=LONGOPT_SUSPENDED, usage=USAGE_SUSPENDED)
    @Getter @Setter private boolean suspended = false;

    public String accountUri () {
        return ACCOUNTS_ENDPOINT + "/" + (hasUuid() ? getUuid() : ""); // empty string means use caller's uuid
    }

    public AppStoreAccountRegistration getRegistration () {
        return (AppStoreAccountRegistration) new AppStoreAccountRegistration()
                .setPassword(getPassword())
                .setTos(true)
                .setAccountName(name)
                .setAdmin(admin)
                .setEmail(email)
                .setFirstName(firstname)
                .setLastName(lastname)
                .setMobilePhoneCountryCode(countrycode)
                .setMobilePhone(mobilephone)
                .setTwoFactor(twofactor)
                .setSuspended(suspended);
    }

    public AppStoreAccount getAppStoreAccount () {
        return (AppStoreAccount) new AppStoreAccount()
                .setAccountName(name)
                .setAdmin(admin)
                .setEmail(email)
                .setHashedPassword(new HashedPassword(accountPassword))
                .setFirstName(firstname)
                .setLastName(lastname)
                .setMobilePhoneCountryCode(countrycode)
                .setMobilePhone(mobilephone)
                .setTwoFactor(twofactor)
                .setSuspended(suspended);
    }
}
