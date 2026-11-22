package net.awyvrix.modsorter.config;

public class Config {
    public boolean useOverrides = true;
    public boolean alwaysOverride = true;

    public boolean deleteEmptyFolders = true;
    public boolean deleteClientMods = false;
    public boolean deleteServerMods = false;

    public boolean defragmentation = false;
    public boolean rewriteOnConflict = false;

    public boolean autoUpdateDatabase = true;
    public String database = "https://raw.githubusercontent.com/awyvrix/ModSorter/main/src/main/resources/data.json";
    public int timeout = 15;
}