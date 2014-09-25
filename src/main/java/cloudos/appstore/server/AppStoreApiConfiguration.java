package cloudos.appstore.server;

import lombok.Setter;
import org.cobbzilla.wizard.server.config.DatabaseConfiguration;
import org.cobbzilla.wizard.server.config.HasDatabaseConfiguration;
import org.cobbzilla.wizard.server.config.RestServerConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppStoreApiConfiguration extends RestServerConfiguration
        implements HasDatabaseConfiguration {

    @Setter private DatabaseConfiguration database;
    @Bean public DatabaseConfiguration getDatabase() { return database; }

    @Setter private AppStoreConfiguration appStore;
    @Bean public AppStoreConfiguration getAppStore() { return appStore; }

}
