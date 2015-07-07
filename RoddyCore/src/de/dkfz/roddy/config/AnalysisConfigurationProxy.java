package de.dkfz.roddy.config;

import de.dkfz.roddy.config.validation.ConfigurationValidationError;
import de.dkfz.roddy.core.ExecutionContext;
import groovy.util.slurpersupport.NodeChild;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
*  The AC Proxy is used to enable lazy loading of analyis configuration objects.
 * It encapsulates a real AC object.
 *
 * Created by michael on 20.03.15.
*/
public class AnalysisConfigurationProxy extends AnalysisConfiguration {

    private AnalysisConfiguration analysisConfiguration;
    private AnalysisConfiguration parentConfiguration;
    private final String analysisID;
    private final String analysisCfg;
    private NodeChild analysisNode;

    public AnalysisConfigurationProxy(AnalysisConfiguration parentConfiguration, String analysisID, String analysisCfg, NodeChild analysisNode) {
        super(null, null, null, null, null, null, null);
        this.analysisNode = analysisNode;

//        InformationalConfigurationContent informationalConfigurationContent, String
//    } workflowClass, Map<String, TestDataOption> testdataOptions, Configuration parentConfiguration, List<String> listOfUsedTools, List<String> usedToolFolders, String cleanupScript) {
//        super(informationalConfigurationContent, workflowClass, testdataOptions, parentConfiguration, listOfUsedTools, usedToolFolders, cleanupScript);
        analysisConfiguration = null;
        this.parentConfiguration = parentConfiguration;
        this.analysisID = analysisID;
        this.analysisCfg = analysisCfg;
    }

    private synchronized AnalysisConfiguration checkAnalysisConfig() {
        if(analysisConfiguration == null) {
            analysisConfiguration = ConfigurationFactory.getInstance().lazyLoadAnalysisConfiguration(this);
        }
        return analysisConfiguration;
    }

    public AnalysisConfiguration getAnalysisConfiguration() {
        return analysisConfiguration;
    }

    public void setAnalysisConfiguration(AnalysisConfiguration analysisConfiguration) {
        this.analysisConfiguration = analysisConfiguration;
    }

    public AnalysisConfiguration getParentConfiguration() {
        return parentConfiguration;
    }

    public String getAnalysisID() {
        return analysisID;
    }

    public String getAnalysisCfg() {
        return analysisCfg;
    }

    public NodeChild getAnalysisNode() {
        return analysisNode;
    }

    @Override
    public String getWorkflowClass() {
        return checkAnalysisConfig().getWorkflowClass();
    }

    @Override
    public List<String> getListOfTestdataOptions() {
        return checkAnalysisConfig().getListOfTestdataOptions();
    }

    @Override
    public List<TestDataOption> getTestdataOptions() {
        return checkAnalysisConfig().getTestdataOptions();
    }

    @Override
    public TestDataOption getTestdataOption(String id) {
        return checkAnalysisConfig().getTestdataOption(id);
    }

    @Override
    public ToolEntry.ResourceSetSize getResourcesSize() {
        return checkAnalysisConfig().getResourcesSize();
    }

    @Override
    public boolean hasTestdataOption(String id) {
        return checkAnalysisConfig().hasTestdataOption(id);
    }

    @Override
    public void addTestDataOptions(List<TestDataOption> options) {
        checkAnalysisConfig().addTestDataOptions(options);
    }

    @Override
    public List<String> getListOfUsedTools() {
        return checkAnalysisConfig().getListOfUsedTools();
    }

    @Override
    public List<String> getUsedToolFolders() {
        return checkAnalysisConfig().getUsedToolFolders();
    }

    @Override
    public boolean hasCleanupScript() {
        return checkAnalysisConfig().hasCleanupScript();
    }

    @Override
    public String getCleanupScript() {
        return checkAnalysisConfig().getCleanupScript();
    }

    @Override
    public boolean isNative() {
        return checkAnalysisConfig().isNative();
    }

    @Override
    public void setNativeToolID(String id) {
        checkAnalysisConfig().setNativeToolID(id);
    }

    @Override
    public String getNativeToolID() {
        return checkAnalysisConfig().getNativeToolID();
    }

    @Override
    public void setTargetCommandFactory(String targetCommandFactory) {
        checkAnalysisConfig().setTargetCommandFactory(targetCommandFactory);
    }

    @Override
    public String getTargetCommandFactoryClass() {
        return checkAnalysisConfig().getTargetCommandFactoryClass();
    }

    @Override
    public InformationalConfigurationContent getInformationalConfigurationContent() {
        return checkAnalysisConfig().getInformationalConfigurationContent();
    }

    @Override
    public List<String> getImportConfigurations() {
        return checkAnalysisConfig().getImportConfigurations();
    }

    @Override
    public ConfigurationType getConfigurationLevel() {
        return checkAnalysisConfig().getConfigurationLevel();
    }

    @Override
    public void removeFilenamePatternsRecursively() {
        checkAnalysisConfig().removeFilenamePatternsRecursively();
    }

    @Override
    public RecursiveOverridableMapContainer<String, FilenamePattern, Configuration> getFilenamePatterns() {
        return checkAnalysisConfig().getFilenamePatterns();
    }

    @Override
    public RecursiveOverridableMapContainer<String, ToolEntry, Configuration> getTools() {
        return checkAnalysisConfig().getTools();
    }

    @Override
    public RecursiveOverridableMapContainer<String, Enumeration, Configuration> getEnumerations() {
        return checkAnalysisConfig().getEnumerations();
    }

    @Override
    public RecursiveOverridableMapContainerForConfigurationValues getConfigurationValues() {
        return checkAnalysisConfig().getConfigurationValues();
    }

    @Override
    public RecursiveOverridableMapContainer<String, ConfigurationValueBundle, Configuration> getConfigurationValueBundles() {
        return checkAnalysisConfig().getConfigurationValueBundles();
    }

    @Override
    public String getName() {
        return checkAnalysisConfig().getName();
    }

    @Override
    public String getID() {
        return checkAnalysisConfig().getID();
    }

    @Override
    public String getDescription() {
        return checkAnalysisConfig().getDescription();
    }

    @Override
    public String getConfiguredClass() {
        return checkAnalysisConfig().getConfiguredClass();
    }

    @Override
    public String getProjectName() {
        return checkAnalysisConfig().getProjectName();
    }

    @Override
    public List<Configuration> getContainerParents() {
        return checkAnalysisConfig().getContainerParents();
    }

    @Override
    public RecursiveOverridableMapContainer getContainer(String id) {
        return checkAnalysisConfig().getContainer(id);
    }

    @Override
    public void setParent(Configuration c) {
        super.setParent(c);
    }

    @Override
    public void addParent(Configuration p) {
        super.addParent(p);
    }

    @Override
    public Map<String, Configuration> getSubConfigurations() {
        return checkAnalysisConfig().getSubConfigurations();
    }

    @Override
    public List<Configuration> getListOfSubConfigurations() {
        return checkAnalysisConfig().getListOfSubConfigurations();
    }

    @Override
    public File getSourceToolPath(String tool) {
        return checkAnalysisConfig().getSourceToolPath(tool);
    }

    @Override
    public File getProcessingToolPath(ExecutionContext context, String tool) {
        return checkAnalysisConfig().getProcessingToolPath(context, tool);
    }

    @Override
    public String getProcessingToolMD5(String tool) {
        return checkAnalysisConfig().getProcessingToolMD5(tool);
    }

    @Override
    public boolean getPreventJobExecution() {
        return checkAnalysisConfig().getPreventJobExecution();
    }

    @Override
    public void disableJobExecution() {
        super.disableJobExecution();
    }

    @Override
    public boolean getUseCentralAnalysisArchive() {
        return checkAnalysisConfig().getUseCentralAnalysisArchive();
    }

    @Override
    public String getSSHExecutionUser() {
        return checkAnalysisConfig().getSSHExecutionUser();
    }

    @Override
    public boolean getShowSSHCalls() {
        return checkAnalysisConfig().getShowSSHCalls();
    }

    @Override
    public String toString() {
        return checkAnalysisConfig().toString();
    }

    @Override
    public void addValidationError(ConfigurationValidationError error) {
        super.addValidationError(error);
    }

    @Override
    public void addLoadError(ConfigurationLoadError error) {
        super.addLoadError(error);
    }

    @Override
    public List<ConfigurationLoadError> getListOfLoadErrors() {
        return checkAnalysisConfig().getListOfLoadErrors();
    }

    @Override
    public boolean isInvalid() {
        return checkAnalysisConfig().isInvalid();
    }
}
