package net.awyvrix.modsorter.config;

import net.awyvrix.modsorter.sorter.Rule;

import java.util.HashMap;
import java.util.Map;

public class SorterRules {
    public int formatVersion = 1;
    public int databaseVersion = 1;
    public Map<String, Rule> mods = new HashMap<>();
}