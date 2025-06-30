# TryLibs

TryLibs is a utility library for Bukkit/Spigot/Paper Minecraft server plugins, providing common functionalities to streamline plugin development.

## Features

*   **Configuration Management:** Easy loading and saving of `config.yml` and custom configuration files.
*   **Database Handling:** Simplified database interaction for SQLite and MySQL, with support for various data types.
*   **ItemBuilder:** A fluent API for creating complex `ItemStack`s.
*   **Vault Integration:** Hooks into Vault for economy interactions (if Vault is present).
*   **Color Code Translation:** Utilities for translating chat color codes.

## Installation / Adding as a Dependency

It is recommended to install TryLibs as a standalone plugin on your server. Then, your plugin can depend on it.

### For Plugin Developers (Maven)

Add TryLibs as a dependency in your `pom.xml`:

```xml
<dependency>
    <groupId>com.trynocs</groupId>
    <artifactId>trylibs</artifactId>
    <version>1.0.0</version> <!-- Replace with the latest version -->
    <scope>provided</scope>
</dependency>
```

And ensure your plugin's `plugin.yml` declares a dependency on TryLibs:

```yaml
name: YourPluginName
version: YourPluginVersion
main: your.plugin.MainClass
depend: [TryLibs] # Or softdepend if TryLibs is optional
```

## Usage

TryLibs registers its API with Bukkit's Services Manager. To use TryLibs functionalities, you need to get the `TryLibsAPI` instance.

**Example in your plugin's `onEnable()`:**

```java
import com.trynocs.tryLibs.api.TryLibsAPI;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class YourPlugin extends JavaPlugin {

    private TryLibsAPI tryLibsAPI;

    @Override
    public void onEnable() {
        if (!setupTryLibs()) {
            getLogger().severe("TryLibs API could not be found. This plugin's functionality may be limited.");
            // You might want to disable your plugin or parts of it if TryLibs is a hard dependency
            // getServer().getPluginManager().disablePlugin(this);
            // return;
        }

        // Now you can use the API (if tryLibsAPI is not null)
        if (tryLibsAPI != null) {
            // Example: Access the configuration manager
            // FileConfiguration mainConfig = tryLibsAPI.getConfigManager().getConfig();
            // String someValue = mainConfig.getString("some.path.to.value");

            // Example: Access the database handler
            // tryLibsAPI.getDatabaseHandler().saveData(playerUUID, "some_key", "some_value");

            // Example: Use the ItemBuilder (often used directly by importing)
            // ItemStack customItem = new com.trynocs.tryLibs.utils.gui.ItemBuilder(Material.DIAMOND)
            //                            .setName(tryLibsAPI.translateColors("&bShiny Diamond"))
            //                            .addLoreLine(tryLibsAPI.translateColors("&7A very special diamond."))
            //                            .build();
        }
    }

    private boolean setupTryLibs() {
        // Check if the TryLibs plugin is present
        if (getServer().getPluginManager().getPlugin("TryLibs") == null) {
            getLogger().severe("TryLibs plugin is not installed or not loaded on the server. Please install TryLibs.");
            return false;
        }

        RegisteredServiceProvider<TryLibsAPI> provider = getServer().getServicesManager().getRegistration(TryLibsAPI.class);
        if (provider == null) {
            getLogger().warning("TryLibsAPI service not found. It might not have loaded yet or there's an issue.");
            // You could listen for the TryLibsInitializedEvent or implement a delayed retry here for robustness
            return false;
        }

        tryLibsAPI = provider.getProvider();
        if (tryLibsAPI == null) {
            getLogger().severe("TryLibsAPI provider is null, this indicates a problem with TryLibs service registration.");
            return false;
        }

        getLogger().info("Successfully hooked into TryLibsAPI.");
        return true;
    }

    @Override
    public void onDisable() {
        this.tryLibsAPI = null; // Clear the API instance
    }
}
```

### Note on Shading
It is generally **not recommended** to shade (embed) TryLibs into your plugin's JAR file. Instead, rely on the standalone TryLibs plugin being installed on the server. This ensures that all plugins use a consistent version of the library and avoids potential classloader conflicts or issues with multiple instances trying to manage the same resources.

## Building from Source

TryLibs uses Apache Maven.
1. Clone the repository: `git clone https://github.com/Trynocs/TryLibs.git`
2. Navigate to the directory: `cd TryLibs`
3. Build with Maven: `mvn clean package`

This will produce the `TryLibs-VERSION.jar` in the `target` directory.
```
