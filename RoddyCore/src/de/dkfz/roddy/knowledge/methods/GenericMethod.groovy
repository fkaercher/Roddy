/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.knowledge.methods

import de.dkfz.roddy.execution.jobs.JobManager
import de.dkfz.roddy.tools.*
import de.dkfz.roddy.config.Configuration
import de.dkfz.roddy.config.ConfigurationConstants
import de.dkfz.roddy.config.ToolEntry
import de.dkfz.roddy.core.ExecutionContext
import de.dkfz.roddy.core.ExecutionContextError
import de.dkfz.roddy.execution.jobs.Job
import de.dkfz.roddy.execution.jobs.JobResult
import de.dkfz.roddy.knowledge.files.BaseFile
import de.dkfz.roddy.knowledge.files.FileGroup
import de.dkfz.roddy.knowledge.files.FileObject
import de.dkfz.roddy.knowledge.files.IndexedFileObjects
import de.dkfz.roddy.knowledge.files.FileObjectTupleFactory

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

import static de.dkfz.roddy.execution.jobs.JobConstants.*

/**
 * Class for generic, configurable methods
 * This class is very magic and does quite a lot of stuff.
 * Unfortunately it is also very heavy and bulky... So be advised to take your time when digging through this.
 *
 * The class is externally called via two static methods. Internally, for convenience, an object is created
 * and used for the furhter process.
 */
@groovy.transform.CompileStatic
class GenericMethod {

    private static final LoggerWrapper logger = LoggerWrapper.getLogger(GenericMethod.class.getSimpleName());

    /**
     * This method is basically a wrapper around callGenericToolOrToolArray.
     * Both methods were so similar, that it made no sense to keep both.
     * callGenericToolOrToolArray can handle single job calls very well.
     * @param toolName The tool id to call
     * @param input The basic input object TODO should be extended or changed to handle FileGroups as well.
     * @param additionalInput Any additional input to the job. The input must fit the tool i/o specs in your xml file.
     * @return
     */
    public static <F extends FileObject> F callGenericTool(String toolName, BaseFile input, Object... additionalInput) {
        F result = callGenericToolOrToolArray(toolName, null, input, additionalInput);
        return result;
    }

    /**
     * This is Roddys most magic method!
     * It uses all the input parameters and the tool description and magically assembles a full job object.
     * The generated tool object is run() at the end.
     *
     * @param toolName The tool id to call
     * @param arrayIndices If it is an array, we need indices
     * @param input The basic input object TODO should be extended or changed to handle FileGroups as well.
     * @param additionalInput Any additional input to the job. The input must fit the tool i/o specs in your xml file.
     * @return
     */
    public static <F extends FileObject> F callGenericToolOrToolArray(String toolName, List<String> arrayIndices, BaseFile input, Object... additionalInput) {
        new GenericMethod(toolName, arrayIndices, input, additionalInput)._callGenericToolOrToolArray();
    }

    /**
     * The context object by which this GenericMethod object is created
     */
    private final ExecutionContext context

    /**
     * The context configuration (a shortcut)
     */
    private final Configuration configuration

    /**
     * The tools (id) which is called
     */
    private final String toolName

    /**
     * The tool entry taken from the contexts configuration and identified by toolName
     */
    private final ToolEntry calledTool

    /**
     * If this is an array job, this variable contains the arrays indices
     */
    private final List<String> arrayIndices

    /**
     * The primary input object. It must not be null! The context object is taken from this input object.
     */
    private final FileObject inputObject

    /**
     * Currently supported are filegroups and basefiles as input objects. In both cases this variable is filled being inputObject for BaseFile objects and .getFirst() for FileGroups
     */
    private final BaseFile firstInputFile

    /**
     * Any additional passed input objects like filegroups or string parameters.
     */
    private final Object[] additionalInput

    /**
     * A combined list of all input values. This list also contains entries from e.g. the configuration and e.g. from the command factory.
     */
    private final List<FileObject> allInputValues = []

    /**
     * A list of all input files (e.g. also taken from input file groups.
     */
    private final List<BaseFile> allInputFiles = [];

    /**
     * The final map of parameters which will be passed to the job.
     */
    private final Map<String, Object> parameters = [:]

    /**
     * A list of all created file objects, also the files from file groups.
     */
    private final List<FileObject> allCreatedObjects = [];

    private GenericMethod(String toolName, List<String> arrayIndices, FileObject inputObject, Object... additionalInput) {
        this.additionalInput = additionalInput
        this.inputObject = inputObject
        this.allInputValues << inputObject;
        if (inputObject instanceof FileGroup) {
            this.firstInputFile = (inputObject as FileGroup).getFilesInGroup().get(0); // Might be null at some point... Should we throw something?
        } else if (inputObject instanceof BaseFile) {
            this.firstInputFile = inputObject as BaseFile
        } else {
            // This is not supported yet! Throw an exception.
            throw new RuntimeException("It is not allowed to use GenericMethod objects without input objects.")
        }
        this.context = inputObject.getExecutionContext();
        this.arrayIndices = arrayIndices
        this.toolName = toolName
        this.configuration = context.getConfiguration();
        this.calledTool = configuration.getTools().getValue(toolName);
    }

    private <F extends FileObject> F _callGenericToolOrToolArray() {

        context.setCurrentExecutedTool(calledTool);

        // Check if method may be executed
        if (!calledTool.isToolGeneric()) {
            logger.severe("Tried to call a non generic tool via the generic call method");
            throw new RuntimeException("Not able to context tool " + toolName);
        }

        assembleJobParameters()

        assembleInputFilesAndParameters()

        applyParameterConstraints()

        F outputObject = createOutputObject();

        List<BaseFile> filesToVerify = fillListOfCreatedObjects(outputObject)

        F result;

        // Call the job as either an array or a single job.
        if (arrayIndices != null) {
            result = createAndRunArrayJob(filesToVerify) as F
        } else {
            result = createAndRunSingleJob(filesToVerify, outputObject) as F
        }

        // Finally check the result and append an error to the context.
        if (result == null) {
            def errMsg = "The job callGenericTool(${toolName}, ...) returned null."
            logger.warning(errMsg);
            context.addErrorEntry(ExecutionContextError.EXECUTION_JOBFAILED.expand(errMsg))
        }

        return result;
    }

    private void assembleJobParameters() {
        // Assemble initial parameters
        if (toolName) {
            parameters[PRM_TOOLS_DIR] = configuration.getProcessingToolPath(context, toolName).getParent();
            parameters[PRM_TOOL_ID] = toolName;
        }

        // Assemble additional parameters
        for (Object entry in additionalInput) {
            if (entry instanceof BaseFile)
                allInputValues << (BaseFile) entry;
            else if (entry instanceof FileGroup) {
                //Take a group and store all files in that group.
                allInputValues << (FileGroup) entry;

            } else {
                String[] split = entry.toString().split("=");
                if (split.length != 2)
                    throw new RuntimeException("Not able to convert entry ${entry.toString()} to parameter.")
                parameters[split[0]] = split[1];
            }
        }
    }

    private void assembleInputFilesAndParameters() {
        for (FileObject fo in allInputValues) {
            if (fo instanceof BaseFile)
                allInputFiles << (BaseFile) fo;
            else if (fo instanceof FileGroup)
                allInputFiles.addAll(((FileGroup) fo).getFilesInGroup());
        }

        for (int i = 0; i < calledTool.getInputParameters(context).size(); i++) {
            ToolEntry.ToolParameter toolParameter = calledTool.getInputParameters(context)[i];
            if (toolParameter instanceof ToolEntry.ToolFileParameter) {
                ToolEntry.ToolFileParameter _tp = (ToolEntry.ToolFileParameter) toolParameter;
                //TODO Check if input and output parameters match and also check for array indices and item count. Throw a proper error message.
                if (!allInputValues[i].class == _tp.fileClass)
                    logger.severe("Class mismatch for " + allInputValues[i] + " should be of class " + _tp.fileClass);
                if (_tp.scriptParameterName) {
                    parameters[_tp.scriptParameterName] = ((BaseFile) allInputValues[i]);
                }
                _tp.constraints.each {
                    ToolEntry.ToolConstraint constraint ->
                        constraint.apply(firstInputFile);
                }
            } else if (toolParameter instanceof ToolEntry.ToolTupleParameter) {
                ToolEntry.ToolTupleParameter _tp = (ToolEntry.ToolTupleParameter) toolParameter;
                logger.severe("Tuples must not be used as an input parameter for tool ${toolName}.l");
            } else if (toolParameter instanceof ToolEntry.ToolFileGroupParameter) {
                ToolEntry.ToolFileGroupParameter _tp = toolParameter as ToolEntry.ToolFileGroupParameter;
                if (!allInputValues[i].class == _tp.groupClass)
                    logger.severe("Class mismatch for ${allInputValues[i]} should be of class ${_tp.groupClass}");
                if (_tp.passOptions == ToolEntry.ToolFileGroupParameter.PassOptions.parameters) {
                    int cnt = 0;
                    for (BaseFile bf in (List<BaseFile>) ((FileGroup) allInputValues[i]).getFilesInGroup()) {
                        parameters[_tp.scriptParameterName + "_" + cnt] = bf;
                        cnt++;
                    }
                } else { //Arrays
                    int cnt = 0;
                    parameters[_tp.scriptParameterName] = ((FileGroup) allInputValues[i]).getFilesInGroup();//paths;
                }
            }
        }
    }

    private void applyParameterConstraints() {
        for (int i = 0; i < calledTool.getOutputParameters(context.getConfiguration()).size(); i++) {
            ToolEntry.ToolParameter toolParameter = calledTool.getOutputParameters(context.getConfiguration())[i];
            if (toolParameter instanceof ToolEntry.ToolFileParameter) {
                ToolEntry.ToolFileParameter _tp = (ToolEntry.ToolFileParameter) toolParameter;
                for (ToolEntry.ToolConstraint constraint in _tp.constraints) {
                    constraint.apply(firstInputFile);
                }
            }
        }
    }

    private <F extends FileObject> F createOutputObject(String arrayIndex = null) {
        F outputObject = null;
        def configuration = firstInputFile.getExecutionContext().getConfiguration()
        if (calledTool.getOutputParameters(configuration).size() == 1) {
            ToolEntry.ToolParameter tparm = calledTool.getOutputParameters(configuration)[0];
            if (tparm instanceof ToolEntry.ToolFileParameter) {
                outputObject = createOutputFile(tparm) as F

            } else if (tparm instanceof ToolEntry.ToolTupleParameter) {
                outputObject = createOutputTuple(tparm as ToolEntry.ToolTupleParameter) as F

            } else if (tparm instanceof ToolEntry.ToolFileGroupParameter) {
                outputObject = createOutputFileGroup(tparm as ToolEntry.ToolFileGroupParameter) as F
            }
        }

        if (arrayIndex != null) {
            for (FileObject fo in allCreatedObjects) {
                if (!(fo instanceof BaseFile))
                    continue;

                BaseFile bf = (BaseFile) fo;
                String newPath = bf.getAbsolutePath().replace(ConfigurationConstants.CVALUE_PLACEHOLDER_RODDY_JOBARRAYINDEX, arrayIndex);
                bf.setPath(new File(newPath));
            }
        }
        return outputObject;
    }

    private FileObject createOutputFile(ToolEntry.ToolFileParameter tparm) {
        ToolEntry.ToolFileParameter fileParameter = tparm;
        BaseFile bf = convertToolFileParameterToBaseFile(fileParameter)
        for (ToolEntry.ToolFileParameter childFileParameter in fileParameter.getChildFiles()) {
            try {
                if (childFileParameter.parentVariable == null) {
                    continue;
                }
                Field _field = null;
                Method _method = null;
                try {
                    _field = bf.getClass().getField(childFileParameter.parentVariable);
                } catch (Exception ex) {
                }
                try {
                    String setterMethod = "set" + childFileParameter.parentVariable[0].toUpperCase() + childFileParameter.parentVariable[1..-1];
                    _method = bf.getClass().getMethod(setterMethod, childFileParameter.fileClass);
                } catch (Exception ex) {
                }
                if (_field == null && _method == null) {
                    try {
                        context.addErrorEntry(ExecutionContextError.EXECUTION_FILECREATION_FIELDINACCESSIBLE.expand("Class ${bf.getClass().getName()} field ${childFileParameter.parentVariable}"));
                    } catch (Exception ex) {
                    }
                    continue;
                }
                BaseFile childFile = convertToolFileParameterToBaseFile(childFileParameter, bf, [bf]);
                allCreatedObjects << childFile;
                if (_field)
                    _field.set(bf, childFile);
                else
                    _method.invoke(bf, childFile);
            } catch (Exception ex) {
                println ex;
            }
        }
        return fileParameter.fileClass.cast(bf) as FileObject;
    }

    private FileObject createOutputTuple(ToolEntry.ToolTupleParameter tfg) {
        //TODO Auto recognize tuples?
        List<FileObject> filesInTuple = [];
        for (ToolEntry.ToolFileParameter fileParameter in tfg.files) {
            BaseFile bf = convertToolFileParameterToBaseFile(fileParameter);
            filesInTuple << bf;
            allCreatedObjects << bf;
        }
        return FileObjectTupleFactory.createTuple(filesInTuple);
    }

    private FileObject createOutputFileGroup(ToolEntry.ToolFileGroupParameter tfg) {
        List<BaseFile> filesInGroup = [];

        for (ToolEntry.ToolFileParameter fileParameter in tfg.files) {
            BaseFile bf = convertToolFileParameterToBaseFile(fileParameter)
            filesInGroup << bf;
            allCreatedObjects << bf;
        }

        Constructor cGroup = tfg.groupClass.getConstructor(List.class);
        FileGroup bf = cGroup.newInstance(filesInGroup);
        return tfg.groupClass.cast(bf) as FileObject;
    }

    private List<BaseFile> fillListOfCreatedObjects(FileObject outputObject) {
        allCreatedObjects << outputObject;

        //Verifiable output files:
        List<BaseFile> filesToVerify = [];
//        allCreatedObjects.each { FileObject fo -> if (fo instanceof BaseFile && !((BaseFile) fo).isTemporaryFile()) filesToVerify << (BaseFile) fo; }
        return allCreatedObjects.findAll { FileObject fo -> fo instanceof BaseFile && !((BaseFile) fo).isTemporaryFile() } as List<BaseFile> //filesToVerify << (BaseFile) fo; }
//        filesToVerify
    }

    private FileObject createAndRunArrayJob(List<BaseFile> filesToVerify) {
        JobResult jobResult = new Job(context, context.createJobName(firstInputFile, toolName), toolName, arrayIndices, parameters, allInputFiles, filesToVerify).run();

        Map<String, FileObject> outputObjectsByArrayIndex = [:];
        IndexedFileObjects indexedFileObjects = new IndexedFileObjects(arrayIndices, outputObjectsByArrayIndex, context);
        // Run array job and afterwards create output files for all sub jobs. The values in filesToVerify will be used and the path names will be corrected.
        int i = 1;
        for (String arrayIndex in arrayIndices) {
            List<FileObject> newObjects = [];
            outputObjectsByArrayIndex[arrayIndex] = createOutputObject(arrayIndex);
            JobResult jr = JobManager.getInstance().convertToArrayResult(jobResult.job, jobResult, i++);
            for (FileObject fo : newObjects) {
                fo.setCreatingJobsResult(jr);
            }
        }
        return indexedFileObjects;
    }

    private FileObject createAndRunSingleJob(List<BaseFile> filesToVerify, FileObject outputObject) {
        JobResult jobResult = new Job(context, context.createJobName(firstInputFile, toolName), toolName, parameters, allInputFiles, filesToVerify).run();

        if (allCreatedObjects) {
            for (FileObject fo in allCreatedObjects) {
                if (fo == null)
                    continue;
                fo.setCreatingJobsResult(jobResult);
            }
        }
        return outputObject;
    }

    private BaseFile convertToolFileParameterToBaseFile(ToolEntry.ToolFileParameter fileParameter) {
        convertToolFileParameterToBaseFile(fileParameter, firstInputFile, allInputFiles)
    }

    private BaseFile convertToolFileParameterToBaseFile(ToolEntry.ToolFileParameter fileParameter, BaseFile firstInputFile, List<BaseFile> allInputFiles) {
        Constructor c = searchBaseFileConstructorForConstructionHelperObject(fileParameter.fileClass);
        BaseFile bf;
        try {
            if (c == null) {
                // Actually this must not be the case! If a developer has its custom class, it must provide this constructor.

                //TODO URGENT
                //The underlying error is that a configuration file has e.g. a typo, or not? Such kind of errors are user errors, where there is a clear cause (line X in file Y contains garbage Z). Ideally we would just display an error message with as much information possible to allow the user to fix the error, but no stack trace.
                //Do you think it would make sense to separate out two groups of Exceptions, one that shows only the error message containing all information required to fix the input problem caused by the user, and the othor more fatal class of exceptions raised by the workflow or RoddyCore, that indicates real programming errors and are displayed with a full stack trace?

                context.addErrorEntry(ExecutionContextError.EXECUTION_FILECREATION_NOCONSTRUCTOR.expand("File object of type ${fileParameter?.fileClass} with input ${firstInputFile?.class} needs a constructor which takes a ConstuctionHelper object."));
                throw new RuntimeException("Could not find valid constructor for type  ${fileParameter?.fileClass} with input ${firstInputFile?.class}.");
            } else {
                BaseFile.ConstructionHelperForGenericCreation helper = new BaseFile.ConstructionHelperForGenericCreation(firstInputFile, allInputFiles as List<FileObject>, calledTool, toolName, fileParameter.scriptParameterName, fileParameter.filenamePatternSelectionTag, firstInputFile.fileStage, null);
                bf = c.newInstance(helper);
            }
        } catch (Exception ex) {
            context.addErrorEntry(ExecutionContextError.EXECUTION_FILECREATION_NOCONSTRUCTOR.expand("Error during constructor call."));
            throw (ex);
        }

        if (!fileParameter.checkFile)
            bf.setAsTemporaryFile();

        if (allInputFiles.size() > 1)
            bf.setParentFiles(allInputFiles, true);

        if (fileParameter.scriptParameterName) {
            parameters[fileParameter.scriptParameterName] = bf;
        }
        bf
    }

    /**
     * Get the BaseFile extending classes constructor for Construction Helper Objects
     * @param classToSearch
     * @return
     */
    public static Constructor<BaseFile> searchBaseFileConstructorForConstructionHelperObject(Class classToSearch) {
        try {
            return classToSearch.getConstructor(BaseFile.ConstructionHelperForBaseFiles);
        } catch (Exception e) {
            logger.severe("There was no valid constructor found for class ${classToSearch?.name}! Roddy needs a constructor which accepts a construction helper object.")
            logger.postSometimesInfo(e.message)
            logger.postSometimesInfo(RoddyIOHelperMethods.getStackTraceAsString(e))
            return null
        }
    }
}
