/*
 * Copyright (c) 2002-2004
 * All rights reserved.
 */

package com.atlassian.jira.vcs.cvsimpl;

import java.io.File;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.util.Lock;
import com.atlassian.jira.util.LockException;
import net.sf.statcvs.input.CvsLogfileParser;
import net.sf.statcvs.input.EmptyRepositoryException;
import net.sf.statcvs.input.LogSyntaxException;
import net.sf.statcvs.input.RepositoryFileManager;
import net.sf.statcvs.model.CvsContent;
import net.sf.statcvs.util.CvsLogUtils;
import org.apache.log4j.Logger;
import org.netbeans.lib.cvsclient.CVSRoot;
import org.netbeans.lib.cvsclient.Client;
import org.netbeans.lib.cvsclient.admin.StandardAdminHandler;
import org.netbeans.lib.cvsclient.command.Builder;
import org.netbeans.lib.cvsclient.command.CommandAbortedException;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.command.GlobalOptions;
import org.netbeans.lib.cvsclient.command.log.RlogCommand;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.Connection;
import org.netbeans.lib.cvsclient.connection.ConnectionFactory;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CvsRepositoryUtilImpl implements CvsRepositoryUtil
{
    private static final Logger log = Logger.getLogger(CvsRepositoryUtilImpl.class);
    // TODO read from a System property
    private static final String LOCK_FILE_NAME_SUFFIX = ".write.lock";
    private static final int LOCK_OBTAIN_TIMEOUT = 10000; // 10 seconds
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");
    private static final String KEY_CVS_REPOSITORY_ENCODING_PREFIX = "jira.cvsrepository.";
    private static final String KEY_CVS_REPOSITORY_POSTFIX = ".encoding";

    /**
     * Checks for the validity of the given CVS log file
     *
     * @param logFile  the location of the CVS log file
     * @param fetchLog whether the cvs log is fetched into this file
     * @throws ValidationException if there is a problem. The exception contains a useful error message.
     */
    public void checkLogFilePath(File logFile, final boolean fetchLog) throws ValidationException
    {
        if (!logFile.exists())
        {
            throw new ValidationException("Please specify path to a file");
        }
        // If the file exists check that it is a file and not a directory
        if (logFile.exists() && !logFile.isFile())
        {
            throw new ValidationException("Please specify path to a file not a directory");
        }

        // Check if the file exists as only then the canRead() method will actually return useful resulsts
        if (logFile.exists() && !logFile.canRead())
        {
            throw new ValidationException("Cannot read from the specified file");
        }

        // Only need to check if we can write to the file if we will be fetching the log (as otherwise we will only
        // need to read the file)
        // Only check if we can write to the file if the file exists (as only then the canWrite() method
        // will return useful results)
        if (fetchLog && logFile.exists() && !logFile.canWrite())
        {
            throw new ValidationException("Cannot read from the specified file");
        }

        final File directory = logFile.getParentFile();
        if (directory == null)
        {
            return;
        }

        if (!directory.exists())
        {
            throw new ValidationException("The directory '" + directory.getAbsolutePath() + "' of the specified log file does not exist");
        }

        if (!directory.isDirectory())
        {
            throw new ValidationException("The parent path of the specified log file '" + directory.getAbsolutePath() + "' is not a directory");
        }

        if (!directory.canRead())
        {
            throw new ValidationException("Cannot read the directory '" + directory.getAbsolutePath() + "' containing log file");
        }

        // Note that even if we do not need to fetch the log (just parse it) we still need to be able to write into the directory
        // to create lock files
        if (!directory.canWrite())
        {
            throw new ValidationException("Cannot write to the directory '" + directory.getAbsolutePath() + "' containing log file");
        }
    }

    /**
     * Parses the commit information form the cvs log
     *
     * @param logFile        the file containing cvs log
     * @param moduleName     the name of the module which the cvs log file represents
     * @param repositoryPath the path used in cvs root while obtaining the log
     * @return CvsContent object represneting the commit information
     * @throws IOException
     * @throws LogSyntaxException
     */
    public CvsContent parseCvsLogs(final File logFile, final String moduleName, final String repositoryPath, final String repositoryName) throws IOException, LogSyntaxException, LockException
    {
        log.info("Parsing log.");

        CvsLogUtils.setCountLines(false);
        final Reader logReader = getReader(repositoryName, logFile);

        final RepositoryFileManager repFileMan = new RepositoryFileManager(null);
        // Create a builder that parses information of all files and parses all branches
        final net.sf.statcvs.input.Builder builder = new net.sf.statcvs.input.Builder(repFileMan, null, null, null);
        builder.setRevisionFilter(new JiraRevisionFilter());
        builder.buildModule(moduleName);
        builder.setRepository(repositoryPath);

        final Lock lock = getLock(logFile);
        try
        {
            // Try to obtain the lock for parsing the log file
            if (!lock.obtain(LOCK_OBTAIN_TIMEOUT))
            {
                throw new LockException("Could not obtain lock '" + lock.getLockFilePath() + "' in " + LOCK_OBTAIN_TIMEOUT + " msecs.");
            }
        }
        catch (final IOException e)
        {
            throw new LockException(e.getMessage(), e);
        }

        try
        {
            final long startTime = System.currentTimeMillis();
            CvsLogUtils.setCountLines(false);
            final CvsLogfileParser cvsLogfileParser = new CvsLogfileParser(logReader, builder);
            cvsLogfileParser.parse();
            final CvsContent cvsContent = builder.createCvsContent();
            log.info("Finished parsing log.");
            if (log.isDebugEnabled())
            {
                log.debug("Parsing cvs log took " + (System.currentTimeMillis() - startTime) + "ms.");

                final List<?> commits = cvsContent.getCommits();
                if (commits != null)
                {
                    log.debug("Found " + commits.size() + " relevant commits.");
                }
                else
                {
                    log.debug("No relevant commits found.");
                }
            }

            return cvsContent;
        }
        catch (final EmptyRepositoryException e)
        {
            if (log.isInfoEnabled())
            {
                log.info("No relevant commits found in cvs log file '" + logFile.getAbsolutePath() + "'.");
            }
            return new CvsContent();
        }
        finally
        {
            lock.release();
        }
    }

    private Reader getReader(final String repositoryName, final File logFile) throws FileNotFoundException, UnsupportedEncodingException
    {
        String encoding;
        if (repositoryName != null)
        {
            encoding = System.getProperty(KEY_CVS_REPOSITORY_ENCODING_PREFIX + repositoryName + KEY_CVS_REPOSITORY_POSTFIX);

            if (encoding != null)
            {
                if (log.isDebugEnabled())
                {
                    log.debug("Found forced encoding for '" + repositoryName + "' repository - using '" + encoding + "' to read file.");
                }
                // Useful to overcome JDK 1.3 bug for reading input streams
                return new InputStreamReader(new FileInputStream(logFile), encoding);
            }
        }

        if (log.isDebugEnabled())
        {
            log.debug("Did not find a forced encoding for '" + repositoryName + "' repository - using default.");
        }

        return new InputStreamReader(new FileInputStream(logFile));
    }

    /**
     * Fetches the CVS log using the rlog cvs command
     *
     * @param logFile    the file where the cvs log will be written
     * @param cvsRoot    the string representation of the CVS root
     * @param moduleName the name of the module the log of which we will be retrieving
     * @param password   the password for the repository (can be null)
     * @throws AuthenticationException
     * @throws CommandException
     * @throws IOException
     */
    public void updateCvs(final File logFile, final String cvsRoot, final String moduleName, final String password, final long cvsTimeout) throws AuthenticationException, CommandException, IOException, LockException
    {
        log.info("Fetching log.");
        final long startTime = System.currentTimeMillis();
        final String workingDirectory = logFile.getParent();

        if (workingDirectory == null)
        {
            throw new IOException("Absolute file path not specified : \"" + logFile.getName() + "\"");
        }
        // Create a lock to 'show' that we are updating the CVS log file
        final Lock lock = getLock(logFile);

        Connection con = null;
        boolean gotLock = false;
        try
        {
            con = openConnectionToRepository(cvsRoot, password);
            if (con != null)
            {
                final Client client = new Client(con, new StandardAdminHandler());
                client.setLocalPath(workingDirectory);

                try
                {
                    // Try to obtain the lock
                    gotLock = lock.obtain(LOCK_OBTAIN_TIMEOUT);
                    if (!gotLock)
                    {
                        throw new LockException("Could not obtain lock '" + lock.getLockFilePath() + "' in " + LOCK_OBTAIN_TIMEOUT + " msecs.");
                    }
                }
                catch (final IOException e)
                {
                    throw new LockException(e.getMessage() + ". In directory " + workingDirectory, e);
                }

                // Create a writer to write the cvs log that is retrieved from the CVS server
                // Ensure that the writer is instantiated only after we obtained the lock. Otherwise the cvs log file will be truncated
                // while we do not have the lock.
                final Writer cvsLogWriter = new BufferedWriter(new FileWriter(logFile));

                final RlogCommand rlogCommand = new RlogCommand()
                {
                    // Do not want to query whether the files in the working directory of the cvs client are 'registered' with the CVS repository
                    // all we want to do is get the log - so override the method and return 'false'
                    @Override
                    protected boolean assumeLocalPathWhenUnspecified()
                    {
                        return false;
                    }
                };

                final Collection<String> commandErrors = new LinkedList<String>();
                final Builder fileLogBuilder = new org.netbeans.lib.cvsclient.command.Builder()
                {
                    public void parseLine(final String line, final boolean isErrorMessage)
                    {
                        try
                        {
                            if (isErrorMessage)
                            {
                                if ((line != null) && (line.trim().length() > 0))
                                {
                                    commandErrors.add(line);
                                }
                            }
                            else
                            {
                                cvsLogWriter.write(line);
                                cvsLogWriter.write(LINE_SEPARATOR);
                            }
                        }
                        catch (final IOException e)
                        {
                            // TODO deal with the exception in client code
                            throw new CvsLogException("Error while writing the log to " + logFile.getAbsolutePath() + ".", e);
                        }
                    }

                    public void parseEnhancedMessage(final String key, final Object value)
                    {}

                    public void outputDone()
                    {}
                };

                rlogCommand.setBuilder(fileLogBuilder);
                rlogCommand.setModule(moduleName);

                // Do 'cvs rlog > logfile'
                final GlobalOptions globalOptions = new GlobalOptions();
                // Ensure no 'useless' information is printed
                globalOptions.setModeratelyQuiet(true);

                final ExecutorService executor = Executors.newSingleThreadExecutor();
                Boolean succeeded;
                try
                {
                    final Future<Boolean> futureResult = executor.submit(new Callable<Boolean>()
                    {
                        public Boolean call() throws Exception
                        {
                            return Boolean.valueOf(client.executeCommand(rlogCommand, globalOptions));
                        }
                    });
                    succeeded = futureResult.get(cvsTimeout, TimeUnit.MILLISECONDS); // 10 minutes
                }
                catch (final InterruptedException e)
                {
                    final String message = ComponentAccessor.getJiraAuthenticationContext().getI18nHelper().getText(
                        "admin.error.cvsmodules.operation.exceeded.timeout", String.valueOf(TimeUnit.MILLISECONDS.toSeconds(cvsTimeout)));
                    throw new CommandException(message, message);
                }
                catch (final TimeoutException e)
                {
                    final String message = ComponentAccessor.getJiraAuthenticationContext().getI18nHelper().getText(
                        "admin.error.cvsmodules.operation.exceeded.timeout", String.valueOf(TimeUnit.MILLISECONDS.toSeconds(cvsTimeout)));
                    throw new CommandException(message, message);
                }
                catch (final ExecutionException e)
                {
                    final Throwable targetException = e.getCause();
                    if (targetException instanceof CommandException)
                    {
                        throw ((CommandException) targetException);
                    }
                    else if (targetException instanceof AuthenticationException)
                    {
                        throw ((AuthenticationException) targetException);
                    }
                    else if (targetException instanceof RuntimeException)
                    {
                        throw ((RuntimeException) targetException);
                    }
                    else if (targetException instanceof Error)
                    {
                        throw ((Error) targetException);
                    }
                    else
                    {
                        // armour-piercing exception ... but what can you do? We have to account for an unexpected exception
                        throw new RuntimeException("Unexepected exception from CVS client", targetException);
                    }
                }
                finally
                {
                    executor.shutdown();
                }

                try
                {
                    cvsLogWriter.flush();
                }
                catch (final IOException e)
                {
                    log.error("Error while trying to write CVS log.", e);
                    throw e;
                }
                finally
                {
                    cvsLogWriter.close();
                }

                if (!commandErrors.isEmpty())
                {
                    final Iterator<String> iterator = commandErrors.iterator();
                    final StringBuilder result = new StringBuilder(iterator.next());
                    while (iterator.hasNext())
                    {
                        result.append(" ").append(iterator.next());
                    }

                    throw new CommandException(result.toString(), result.toString());
                }

                if ((succeeded == null) || !succeeded.booleanValue())
                {
                    final String message = "CVS rlog command failed but did not produce any errors.";
                    throw new CommandException(message, message);
                }

            }
            else
            {
                final String message = "Failed to open connection to CVS respository.";
                throw new CommandException(message, message);
            }
        }
        finally
        {
            // Only release the lock if we actually were successful at getting it.
            if (gotLock)
            {
                lock.release();
            }

            if ((con != null) && con.isOpen())
            {
                con.close();
            }
        }

        log.info("Finished fetching log.");
        if (log.isDebugEnabled())
        {
            log.debug("Cvs log took " + (System.currentTimeMillis() - startTime) + "ms.");
        }
    }

    public Connection openConnectionToRepository(final String cvsRoot, final String password) throws CommandAbortedException, AuthenticationException
    {
        final CVSRoot root = parseCvsRoot(cvsRoot);
        root.setPassword(password);
        final Connection connection = ConnectionFactory.getConnection(root);
        connection.open();
        return connection;
    }

    public CVSRoot parseCvsRoot(final String cvsRoot)
    {
        return CVSRoot.parse(cvsRoot);
    }

    public void checkCvsRoot(final String cvsRoot) throws ValidationException
    {
        try
        {
            final CVSRoot root = parseCvsRoot(cvsRoot);
            final String method = root.getMethod();
            if (!(isValidMethod(method) || root.isLocal()))
            {
                throw new ValidationException("Unsupported cvs protocol, method: " + method + " localRoot:" + root.isLocal());
            }
        }
        catch (final IllegalArgumentException e)
        {
            throw new ValidationException(e.getMessage(), e);
        }
    }

    private boolean isValidMethod(final String method)
    {
        return CVSRoot.METHOD_EXT.equals(method) || CVSRoot.METHOD_PSERVER.equals(method) || CVSRoot.METHOD_LOCAL.equals(method) || CVSRoot.METHOD_FORK.equals(method);
    }

    private Lock getLock(final File logfile)
    {
        return new Lock(logfile.getParent(), logfile.getName() + LOCK_FILE_NAME_SUFFIX);
    }
}