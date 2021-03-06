/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.config.converters

import de.dkfz.roddy.Constants
import de.dkfz.roddy.Roddy
import de.dkfz.roddy.config.Configuration
import de.dkfz.roddy.config.ConfigurationConstants
import de.dkfz.roddy.config.ConfigurationValue
import de.dkfz.roddy.config.ConfigurationValueBundle
import de.dkfz.roddy.config.InformationalConfigurationContent
import de.dkfz.roddy.config.ToolEntry
import de.dkfz.roddy.core.ExecutionContext
import de.dkfz.roddy.execution.io.fs.BashCommandSet
import de.dkfz.roddy.execution.io.fs.FileSystemAccessProvider
import de.dkfz.roddy.tools.LoggerWrapper
import groovy.transform.CompileStatic

import java.util.logging.Level

/**
 * Converts a configuration object to bash script.
 * Created by heinold on 18.06.15.
 */
@groovy.transform.CompileStatic
class BashConverter extends ConfigurationConverter {

    final String separator = Constants.ENV_LINESEPARATOR;

    //TODO Use a pipeline converter interface with methods like "convertCValues, convertCValueBundles, convertTools"
    @Override
    String convert(ExecutionContext context, Configuration cfg) {
        StringBuilder text = createNewDocumentStringBuilder(context, cfg)

        text << appendConfigurationValues(context, cfg)

        text << appendConfigurationValueBundles(context, cfg)

        text << appendToolEntries(context, cfg)

        text << appendDebugVariables(cfg)

        text << appendPathVariables()

        text << separator << "";

        return text.toString();
    }

    StringBuilder createNewDocumentStringBuilder(ExecutionContext context, Configuration cfg) {
        final String separator = Constants.ENV_LINESEPARATOR;

        StringBuilder text = new StringBuilder();
        text << "#!/bin/bash" << separator; //Add a shebang line

        //TODO The output umask and the group should be taken from a central location.
        String umask = context.getUMask();
        String outputFileGroup = context.getOutputGroupString();
        boolean processSetUserGroup = cfg.getConfigurationValues().getBoolean(ConfigurationConstants.CVALUE_PROCESS_OPTIONS_SETUSERGROUP, true);
        boolean processSetUserMask = cfg.getConfigurationValues().getBoolean(ConfigurationConstants.CVALUE_PROCESS_OPTIONS_SETUSERMASK, true);
        text << separator << separator << new BashCommandSet().getCheckForInteractiveConsoleCommand() << separator << separator
        text << separator << "fi" << separator << separator;

        if (processSetUserMask) text << "\t umask " << umask << separator;
        return text
    }

    StringBuilder appendConfigurationValues(ExecutionContext context, Configuration cfg) {
        StringBuilder text = new StringBuilder();
        Map<String, ConfigurationValue> listOfSortedValues = getConfigurationValuesSortedByDependencies(cfg)
        for (ConfigurationValue cv : listOfSortedValues.values()) {
            boolean isValidationRule = cv.id.contains("cfgValidationRule");

            if (isValidationRule) {
                text << "# Validation rule!: " + cv.toString() << separator;
                continue;
            }

            text << convertConfigurationValue(cv, context) << separator;
        }
        return text;
    }

    StringBuilder appendConfigurationValueBundles(ExecutionContext context, Configuration cfg) {
        StringBuilder text = new StringBuilder();
        Map<String, ConfigurationValueBundle> cvBundles = cfg.getConfigurationValueBundles().getAllValues();
        for (String bKey : cvBundles.keySet()) {
            ConfigurationValueBundle bundle = cvBundles[bKey];
            text << "#<" << bKey << separator;
            for (String key : bundle.getKeys()) {
                text << convertConfigurationValue(bundle[key], context) << separator;
            }
            text << "#>" << bKey << separator;
        }
        return text;
    }

    StringBuilder appendToolEntries(ExecutionContext context, Configuration cfg) {
        StringBuilder text = new StringBuilder();
        //Store tools
        for (ToolEntry te : cfg.getTools().getAllValuesAsList()) {
            String id = te.getID();
            String valueName = createVariableName("TOOL_", id);
            text << valueName << '="' << cfg.getProcessingToolPath(context, id) << '"' << separator;
        }
        return text;
    }

    StringBuilder appendDebugVariables(Configuration cfg) {
        StringBuilder text = new StringBuilder();
        text << separator << separator

        for (List l in [
                [ConfigurationConstants.DEBUG_OPTIONS_USE_PIPEFAIL, true, "set -o pipefail"],
                [ConfigurationConstants.DEBUG_OPTIONS_USE_VERBOSE_OUTPUT, true, "set -v"],
                [ConfigurationConstants.DEBUG_OPTIONS_USE_EXECUTE_OUTPUT, true, "set -x"],
                [ConfigurationConstants.DEBUG_OPTIONS_USE_UNDEFINED_VARIABLE_BREAK, false, "set -u"],
                [ConfigurationConstants.DEBUG_OPTIONS_USE_EXIT_ON_ERROR, false, "set -e"],
                [ConfigurationConstants.DEBUG_OPTIONS_PARSE_SCRIPTS, false, "set -n"],
                [ConfigurationConstants.CVALUE_PROCESS_OPTIONS_QUERY_ENV, false, "env"],
                [ConfigurationConstants.CVALUE_PROCESS_OPTIONS_QUERY_ID, false, "id"],
        ]) {
            if (cfg.getConfigurationValues().getBoolean(l[0] as String, l[1] as Boolean)) text << separator << l[2] as String
        }
        return text;
    }

    StringBuilder appendPathVariables() {
        StringBuilder text = new StringBuilder();

        //Set a path if necessary.
        text << separator << new BashCommandSet().getSetPathCommand()
        return text;
    }

    Map<String, ConfigurationValue> getConfigurationValuesSortedByDependencies(Configuration cfg) {
        def values = cfg.getConfigurationValues().getAllValuesAsList();
        Map<String, ConfigurationValue> listOfUnsortedValues = [:]
        def listOfSortedValues = new LinkedHashMap<String, ConfigurationValue>();

        for (ConfigurationValue cv in values) {
            listOfUnsortedValues[cv.id] = cv;
        }

        boolean somethingChanged = true;
        int i = -1;
        while (somethingChanged) { //Passes
            somethingChanged = false;
            i++;
            if (LoggerWrapper.isVerbosityHigh())
                println "Pass ${i}, left ${listOfUnsortedValues.values().size()}";
            Map<String, ConfigurationValue> foundValues = [:];

            //TODO Add command manager specific arguments to the command manager class, leave central things here.
            //TODO How to figure out, where to put things like pid sample...
            List<String> valueBlacklist = ["PBS_JOBID", "PBS_ARRAYID", 'PWD', "PID", "pid", "sample", "run", "projectName", "testDataOptionID", "analysisMethodNameOnInput", "analysisMethodNameOnOutput"
                                           , "outputAnalysisBaseDirectory", "inputAnalysisBaseDirectory", "executionTimeString"]
            for (ConfigurationValue cv in listOfUnsortedValues.values()) {
                boolean isValidationRule = cv.id.contains("cfgValidationRule");
                if (isValidationRule)
                    continue;
                if (cv.toString().startsWith("#"))
                    continue;
                def dependencies = cv.getIDsForParrentValues();
                int noOfDependencies = dependencies.size();
                int noOfOriginalDependencies = dependencies.size();
                List<String> notFound = [];
                for (String dep : dependencies) {
                    if (listOfSortedValues.containsKey(dep) || valueBlacklist.contains(dep))
                        noOfDependencies--;
                    else
                        notFound << dep;
                }

                if (noOfDependencies > 0) {
                    logger.postRareInfo("CValue not accepted in dependecy resolution round: ${cv.id} = ${cv.value} $separator" + notFound.collect { "Could not resolve: ${it}" }.join(separator));
                    continue;
                }

                foundValues[cv.id] = cv;
                listOfSortedValues[cv.id] = cv;
            }
            if (foundValues.values().size() > 0)
                somethingChanged = true;
            listOfUnsortedValues -= foundValues;
        }

        if (LoggerWrapper.isVerbosityHigh())
            for (ConfigurationValue cv in listOfUnsortedValues.values()) {
                println "UP: ${cv.id} = ${cv.value}:";
            }
        listOfSortedValues += listOfUnsortedValues;
        return listOfSortedValues
    }

    @Override
    @CompileStatic
    StringBuilder convertConfigurationValue(ConfigurationValue cv, ExecutionContext context) {
        StringBuilder text = new StringBuilder();
        if (cv.toString().startsWith("#COMMENT")) {
            text << cv.toString();
        } else {
            String tmp;
            if (cv.type && cv.type.toLowerCase() == "basharray") {
                // Check, if it is already quoted.
                // If so, take the existing quotes.
                if (cv.value.startsWith("'") || cv.value.startsWith('"'))
                    return new StringBuilder("declare -x    ${cv.id}=${cv.toString()}".toString());
                // If not, quote
                return new StringBuilder("declare -x    ${cv.id}=\"${cv.toString()}\"".toString());
            } else if (cv.type && cv.type.toLowerCase() == "integer") {
                return new StringBuilder("declare -x -i ${cv.id}=${cv.toString()}".toString());
            }

            if (cv.type && cv.type.toLowerCase() == "path")
                tmp = "${cv.toFile(context)}".toString();
            else {
                if (cv.value.startsWith("-") || cv.value.startsWith("*"))
                    tmp = "\"${cv.toString()}\"".toString();
                else
                    tmp = "${cv.toString()}".toString();
            }
            text << "declare -x    ${cv.id}=";
            //TODO Important, this is a serious hack! It must be removed soon
            if (tmp.startsWith("bundledFiles/"))
                text << Roddy.getApplicationDirectory().getAbsolutePath() << FileSystemAccessProvider.getInstance().getPathSeparator();
            text << tmp;
        }
        return text;
    }

    public Configuration loadShellScript(String configurationFile) {
        if (!configurationFile) {
            throw new IOException("Configuration file must be specified.")
        }
        File cf = new File(configurationFile)
        if (!cf.canRead()) {
            throw new IOException("Configuration file is not readable.")
        }

        //Filter input from configuration file
        int commentCnt = 0;
        List<String> cfFiltered = []
        HashMap<String, List<String>> valueBundles = new HashMap<String, List<String>>();
        List<String> currentBundle = null;
        String currentBundleName = "";

        cf.readLines().each { String line ->
            String l = line.trim()
            if (!l) return;
            if (l.startsWith("#<")) {
                currentBundle = new ArrayList<String>();
                currentBundleName = l[2..-1];
            } else if (l.startsWith("#>")) {
                //cfFiltered << ";VALUE_BUNDLE;" + currentBundle[0];
                valueBundles[currentBundleName] = currentBundle;
                currentBundle = null;
            } else if (l[0] == "#") {
                //TODO Also store comments
                if (currentBundle != null) {
                    currentBundle << String.format("#COMMENT_%04d=%s", commentCnt, l);
                } else {
                    cfFiltered << String.format("#COMMENT_%04d=%s", commentCnt, l);
                }
                commentCnt++;
            } else if (l.indexOf("=") > 0) {
                if (currentBundle != null) {
                    currentBundle << l
                } else {
                    cfFiltered << l
                }
            }
        }

        Map<String, Integer> doubletteCounter = new HashMap<String, Integer>();
        InformationalConfigurationContent userConfig = new InformationalConfigurationContent(null, Configuration.ConfigurationType.OTHER, "userconfig_INVALIDNAME", "An imported configuration, please change the name and this description. Also set the classname and type as necessary.", null, null, "", null, null, "");
        Configuration newCfg = new Configuration(userConfig, (Map<String, Configuration>) null);
        Map<String, ConfigurationValue> cValues = newCfg.configurationValues.getMap();
        Map<String, ConfigurationValueBundle> cValueBundles = newCfg.configurationValueBundles.getMap();

        for (String cval in cfFiltered) {
            String[] cvarr = cval.split("=")
            String key = cvarr[0];
            int keyLen = key.length() + 1;
            String value = cval[keyLen..-1];

            try {
                String k2 = key;
                if (!cValues.containsKey(key)) {
                    doubletteCounter[key] = 0;
                } else {
                    doubletteCounter[key] = doubletteCounter[key] + 1;
                    k2 = String.format("%s_%04d", key, doubletteCounter[key]);
                }

                cValues[k2] = new ConfigurationValue(newCfg, k2, value);
            } catch (Exception ex) {
                logger.log(Level.SEVERE, ex.toString());
            }
        }

        valueBundles.each() {
            String bundleName, List<String> bundle ->
                Map<String, ConfigurationValue> bundleValues = new LinkedHashMap<String, ConfigurationValue>();
                try {
                    for (String cval in bundle) {
                        String[] cvarr = cval.split("=")
                        String key = cvarr[0];
                        bundleValues[key] = new ConfigurationValue(newCfg, key, cval[key.length() + 1..-1]);
                    }
                    cValueBundles[bundleName] = new ConfigurationValueBundle(bundleValues);
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, ex.toString());
                }
        }


        return newCfg
    }
}
