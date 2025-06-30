<<<<<<< Updated upstream
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
=======
# TryLibs - Utility Library for Bukkit/Spigot/Paper Plugins

TryLibs is a utility library that provides common functionality for Minecraft plugins.

## For Plugin Developers

### Proper Dependency Management

To use TryLibs in your plugin, you need to:

1. Add TryLibs as a dependency in your plugin.yml:

```yaml
depend: [TryLibs]
```

2. Use one of these methods to access TryLibs:

```java
// Method 1: Standard way (throws exception if not ready)
TryLibs tryLibs = TryLibs.getPlugin();

// Method 2: Safe way (returns null if not ready)
TryLibs tryLibs = TryLibs.getPluginSafe();
if (tryLibs != null) {
    // Use TryLibs
}

// Method 3: Check initialization status
if (TryLibs.isInitialized()) {
    TryLibs tryLibs = TryLibs.getPlugin();
    // Use TryLibs
}

// Method 4: Listen for initialization event
@EventHandler
public void onTryLibsInitialized(TryLibs.TryLibsInitializedEvent event) {
    // TryLibs is now fully initialized and ready to use
    TryLibs tryLibs = TryLibs.getPlugin();
    // Use TryLibs
}
```

### IMPORTANT: Do Not Shade TryLibs Into Your Plugin

A common error is including (shading) TryLibs classes in your own plugin JAR file. This causes classloader conflicts where TryLibs is loaded twice:
- Once as the actual plugin
- Once from your plugin's classloader

This results in the error: `TryLibs is not properly initialized!` even though TryLibs is installed and enabled.

#### How to Fix:

1. In your Maven pom.xml, mark TryLibs as `provided` scope:
>>>>>>> Stashed changes

```xml
<dependency>
    <groupId>com.trynocs</groupId>
    <artifactId>trylibs</artifactId>
<<<<<<< Updated upstream
    <version>1.0.0</version> <!-- Replace with the latest version -->
=======
    <version>1.0.0</version>
>>>>>>> Stashed changes
    <scope>provided</scope>
</dependency>
```

<<<<<<< Updated upstream
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
=======
2. In Gradle:

```groovy
dependencies {
    compileOnly 'com.trynocs:trylibs:1.0.0'
}
```

3. If using Maven Shade plugin, exclude TryLibs:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.5.3</version>
    <configuration>
        <artifactSet>
            <excludes>
                <exclude>com.trynocs:trylibs</exclude>
            </excludes>
        </artifactSet>
    </configuration>
</plugin>
```

### Gradle Shadow Plugin

If using the Gradle Shadow plugin, exclude TryLibs so it is not shaded into your final jar:

```groovy
dependencies {
    compileOnly 'com.trynocs:trylibs:1.0.0'
}

shadowJar {
    dependencies {
        exclude(dependency('com.trynocs:trylibs:.*'))
>>>>>>> Stashed changes
    }
}
```

<<<<<<< Updated upstream
### Note on Shading
It is generally **not recommended** to shade (embed) TryLibs into your plugin's JAR file. Instead, rely on the standalone TryLibs plugin being installed on the server. This ensures that all plugins use a consistent version of the library and avoids potential classloader conflicts or issues with multiple instances trying to manage the same resources.

## Building from Source

TryLibs uses Apache Maven.
1. Clone the repository: `git clone https://github.com/Trynocs/TryLibs.git`
2. Navigate to the directory: `cd TryLibs`
3. Build with Maven: `mvn clean package`

This will produce the `TryLibs-VERSION.jar` in the `target` directory.
```
=======
### Common Error: TryLibs instance is not yet initialized

If you see logs like "[TryLibs DEBUG] TryLibs class first loaded by classloader: PluginClassLoader{plugin=BPEconomy ...}", it often means your plugin is shading or bundling TryLibs classes.  
• Ensure your plugin.yml includes "depend: [TryLibs]"  
• Mark TryLibs as "provided" in your pom.xml  
• Do not package TryLibs classes inside your own plugin's jar  

### Troubleshooting

If you see errors about TryLibs not being initialized:

1. Make sure you have `depend: [TryLibs]` in your plugin.yml
2. Check for classloader conflicts with `TryLibs.isEmbeddedMode()`
3. Consider using the safe access methods shown above
4. Use `TryLibs.getInitializationState()` to check the current initialization state
5. Ensure you're not trying to access TryLibs in static initializers

## Developers

### Building From Source

```bash
mvn clean package
```

### API Documentation

See the JavaDocs for detailed API documentation.
>>>>>>> Stashed changes
