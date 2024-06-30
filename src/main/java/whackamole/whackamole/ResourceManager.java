package whackamole.whackamole;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Properties;
import java.util.ResourceBundle;


public class ResourceManager {
    private static final String[] supportedLanguages = new String[] {
        "en_US", "de_DE", "nl_NL", "fr_FR", "es_ES", "ru_RU", "tr_TR", "zh_TW"
    };

    private static Optional<Properties> languageProperties = Optional.empty(); 
    private static Boolean languageLoadFailed = false;

    private static Optional<ResourceBundle> fallbackProperties = Optional.of(ResourceBundle.getBundle(Config.AppConfig.Language.toString()));

    private static void loadFallbackProperties() {
        fallbackProperties = Optional.of(ResourceBundle.getBundle(Config.AppConfig.Language.toString()));
    }

    /**
     * Bootstrap the languages folder creation at first use of the resource manager
     */
    private static void createLanguageFolder(File folder) {
        if (!folder.exists()) {
            try {
                folder.mkdirs();
                Logger.info("Langfolder created");
            } catch (Exception e) {
                languageLoadFailed = true;
                Logger.error("Cannot create the language folder. Permission denied!");
            }
        }
    }
    /**
     * Bootstrap the languages file creation at first use of the resource manager
     */
    private static void loadFiles(String[] supportedLanguages) {
        File langFolder = new File(Config.AppConfig.storageFolder + "/locales");

        // * Folder creation
        createLanguageFolder(langFolder);
        if (languageLoadFailed) {
            // * Failed to create the folder
            return;
        }

        // * Create language files from resource files
        for (String language : supportedLanguages) {
            File file = new File(langFolder + "/" + language + ".properties");
            try (var resource = ClassLoader.getSystemResourceAsStream(language + ".properties")) {
                if (!file.exists()) {
                    if (resource == null) {
                        Logger.error(String.format("Could not load in language resource %s!", language));
                    }
                    Files.copy(resource, file.toPath());
                } else {
                    var resultingData = mergeLanguageFile(resource, file);
                    var outputS = new FileOutputStream(file);
                    outputS.write(resultingData.getBytes("utf-8"));
                    outputS.flush();
                    outputS.close();
                }
            } catch (IOException e) {
                languageLoadFailed = true;
                Logger.error(String.format("Could not instantiate language file %s!", file.toPath()));
            }
        }
    }

    private static String mergeLanguageFile(InputStream sourceS, File userFile) throws IOException {
        try (var userS = new FileInputStream(userFile)) {
            return mergeLanguageFile(new String(sourceS.readAllBytes()), new String(userS.readAllBytes()));
        }
    }
    private static String mergeLanguageFile(String sourceData, String userData) {
        // TODO: Key reordering in the user file will result in duplicate keys
        // To combat this swithc over to linked list (allows insertions)

        boolean isCRLF = userData.contains("\n") ? userData.contains("\r") : true;

        String[] resourceData = sourceData.replaceAll("\r", "").split("\\n");
        String[] userFileData = userData.replaceAll("\r", "").split("\\n");
        var output = new LinkedList<String>(Arrays.asList(userFileData));
        
        
        // ? -2 = ignore line for mapping
        // ? -2 = section divider
        // ? -1 unknown in dst file
        // ? +X = mapped to line X in dst file
        int[] srcDstLineMapping = new int[resourceData.length];
        
        resourceMapping: 
        for (int i = 0; i < resourceData.length; i++) {
            var line = resourceData[i];
            if (! line.contains("=")) {
                srcDstLineMapping[i] = -2;
                continue;
            }
            
            var key = line.split("\\=")[0];
            for (int j = 0; j < userFileData.length; j++) {
                if (! userFileData[j].contains("=")) 
                    continue;
                
                if (userFileData[j].startsWith(key)) {
                    srcDstLineMapping[i] = j;
                    continue resourceMapping;
                }
            }
            srcDstLineMapping[i] = -1;
        }
        
        int lastKnownIndex = -1, offset = 0;
        for (int i = 0; i < srcDstLineMapping.length; i++) {
            var index = srcDstLineMapping[i];
            if (index == -2) {
                offset += 1;
                index += offset;
                // output.add(index, "");
                continue;
            }
            
            if (index == -1) {
                offset += 1;
                index = lastKnownIndex + 1;
                output.add(index, resourceData[i]);
                lastKnownIndex = index;
            } else {
                index += offset;
                lastKnownIndex = index;
            }
        }
        if (output.get(output.size()-1).equals("")) {
            output.remove(output.size()-1);
        }
        return String.join(isCRLF ? "\r\n" : "\n" , output);
    }



    /**
     * Try to load the language file from the file system.
     */
    private static void loadResource() {
        File langFile = new File(Config.AppConfig.storageFolder + "/locales", Config.AppConfig.Language + ".properties");
        if (! langFile.exists() && ! languageLoadFailed) {
            loadFiles(supportedLanguages);

            if (languageLoadFailed) {
                // * Failed to create user language files.
                return;
            }
        }
        try (var Istream = new FileInputStream(langFile)) {
            var properties = new Properties();
            properties.load(Istream);
            languageProperties = Optional.of(properties);
        } catch (IOException e) {
            languageLoadFailed = true;
            Logger.error(String.format("Failed to read from language file %s!", langFile.getPath()));
        }
    }

    private static Optional<Properties> getResource() {
        if (languageProperties.isEmpty() && ! languageLoadFailed) {
            loadResource();
        }
        return languageProperties;
    }

    public static String getProperty(String key) {
        return getResource().map(props -> props.getProperty(key))
                            .or(() -> fallbackProperties.map((props -> props.getString(key))))
                            .orElse("");
    }

    private static void resetState() {
        languageLoadFailed = false;
        languageProperties = Optional.empty();
    }

    public static void onLoad() {
        loadFallbackProperties();
        resetState();
    }
    
    public static void onReload() {
        loadFallbackProperties();
        resetState();
    }

}
