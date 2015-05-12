package cloudos.appstore.main;

import cloudos.appstore.ApiConstants;
import cloudos.appstore.client.AppStoreApiClient;
import cloudos.appstore.model.AppStorePublisherMemberInvitation;

import static org.cobbzilla.util.json.JsonUtil.toJson;

public class AppStoreMemberMain extends AppStoreMainBase<AppStoreMemberOptions> {

    public static void main (String[] args) { main(AppStoreMemberMain.class, args); }

    @Override protected AppStoreMemberOptions initOptions() { return new AppStoreMemberOptions(); }

    @Override protected void run() throws Exception {

        final AppStoreApiClient api = getApiClient();
        final AppStoreMemberOptions options = getOptions();

        switch (options.getOperation()) {
            case create:
                final AppStorePublisherMemberInvitation invitation = new AppStorePublisherMemberInvitation()
                        .setAccountName(options.getName())
                        .setPublisherUuid(options.getPublisher());
                out(api.put(ApiConstants.MEMBERS_ENDPOINT + "/invite", toJson(invitation)).toString());
                break;

            case read:
                out(api.get(options.memberUri()).toString());
                break;

            case update:
                die("memberships cannot be modified, only created or deleted");
                break;

            case delete:
                out(api.delete(options.memberUri()).toString());
                break;

            default:
                die("unsupported operation: "+options.getOperation());
        }
    }
}
