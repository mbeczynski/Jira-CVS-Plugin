/*
 * Copyright (c) 2002-2004
 * All rights reserved.
 */

package com.atlassian.jira.vcs.cvsimpl;

import com.atlassian.jira.util.JiraKeyUtils;
import com.atlassian.jira.util.LockException;
import com.atlassian.jira.vcs.AbstractRepository;
import com.atlassian.jira.vcs.Repository;
import com.atlassian.jira.vcs.RepositoryBrowser;
import com.atlassian.jira.vcs.RepositoryException;
import com.atlassian.jira.vcs.RepositoryManager;
import com.atlassian.jira.vcs.viewcvs.ViewCvsBrowser;
import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.util.TextUtils;
import net.sf.statcvs.input.CommitListBuilder;
import net.sf.statcvs.input.LogSyntaxException;
import net.sf.statcvs.model.Commit;
import net.sf.statcvs.model.CvsContent;
import net.sf.statcvs.model.CvsRevision;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class CvsRepository extends AbstractRepository
{
    private static final Logger log = Logger.getLogger(CvsRepository.class);

    /**
     * The path to the CVS log file to parse
     */
    private String cvsLogFilePath;

    /**
     * The CVS root of the repository
     * This is needed for parsing the log file as well as fetching the log
     */
    private String cvsRoot;

    /**
     * The name of the moduleName in CVS
     * This is needed for parsing the log file as well as fetching the log
     */
    private String moduleName;

    /**
     * The password for cvs repository authentication (used only when fetching the log from the CVS repository)
     */
    private String password;

    /**
     * Whether to fetch the log from the CVS repository
     */
    private boolean fetchLog;

    /**
     * The CVS timeout
     * This determines how long it take for CVS to timeout in MS.
     */
    private long cvsTimeout;

    /**
     * The parsed CVS commit information
     */
    private CvsContent content;

    // Was used in JIRA 2.6
    public static final String KEY_BASEDIR = "cvsbasedir"; // Not used by anything except an upgrade task since JIRA 3.0

    public static final long CVS_OPERATION_TIMEOUT_DEFAULT = 10 * 60 * 1000; // 10 minutes

    public static final String KEY_LOG_FILE_PATH = "cvslogfilepath";
    public static final String KEY_CVS_ROOT = "cvsroot";
    public static final String KEY_MODULE_NAME = "cvsmodulename";
    public static final String KEY_PASSWORD = "cvspassword";
    public static final String KEY_FETCH_LOG = "cvsfetchlog";
    public static final String KEY_CVS_TIMEOUT = "cvstimeout";

    // not used yet
    // public static final String KEY_UPDATEDELAY = "cvsupdatedelay";

    // Used to write cv log when log file path is null
    private File tempFile;

    private final CvsRepositoryUtil cvsRepositoryUtil;

    public CvsRepository(final PropertySet propertySet, CvsRepositoryUtil cvsRepositoryUtil)
    {
        super(propertySet);
        this.cvsRepositoryUtil = cvsRepositoryUtil;

        this.cvsLogFilePath = propertySet.getString(KEY_LOG_FILE_PATH);
        this.cvsRoot = propertySet.getString(KEY_CVS_ROOT);
        this.moduleName = propertySet.getString(KEY_MODULE_NAME);
        this.password = propertySet.getString(KEY_PASSWORD);
        this.fetchLog = Boolean.parseBoolean(propertySet.getString(KEY_FETCH_LOG));
        this.cvsTimeout = NumberUtils.toLong(propertySet.getString(KEY_CVS_TIMEOUT), CVS_OPERATION_TIMEOUT_DEFAULT);

        // Check if this repository has the repository browser
        String repositoryBrowserType = propertySet.getString(Repository.KEY_REPOSITTORY_BROWSER_TYPE);
        if (TextUtils.stringSet(repositoryBrowserType))
        {
            if (RepositoryBrowser.VIEW_CVS_TYPE.equals(repositoryBrowserType))
            {
                try
                {
                    Map<String, String> browserParams = new HashMap<String, String>();
                    browserParams.put(ViewCvsBrowser.ROOT_PARAMETER, propertySet.getString(ViewCvsBrowser.ROOT_PARAMETER));
                    setRepositoryBrowser(new ViewCvsBrowser(propertySet.getString(ViewCvsBrowser.KEY_BASE_URL), browserParams));
                }
                catch (MalformedURLException e)
                {
                    log.error("Error while creating ViewCVS Repository Browser.", e);
                }
            }
        }
    }

    public String getCvsLogFilePath()
    {
        return cvsLogFilePath;
    }

    public void setCvsLogFilePath(String cvsLogFilePath)
    {
        this.cvsLogFilePath = cvsLogFilePath;
    }

    public String getCvsRoot()
    {
        return cvsRoot;
    }

    public void setCvsRoot(String cvsRoot)
    {
        this.cvsRoot = cvsRoot;
    }

    public String getModuleName()
    {
        return moduleName;
    }

    public void setModuleName(String moduleName)
    {
        this.moduleName = moduleName;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public String getPassword()
    {
        return password;
    }

    public long getCvsTimeout()
    {
        return cvsTimeout;
    }

    public void setCvsTimeout(long cvsTimeout)
    {
        this.cvsTimeout = cvsTimeout;
    }

    public String getCvsTimeoutStringInSeconds()
    {
        return String.valueOf(TimeUnit.MILLISECONDS.toSeconds(cvsTimeout));
    }

    public boolean fetchLog()
    {
        return fetchLog;
    }

    public void setFetchLog(boolean fetchLog)
    {
        this.fetchLog = fetchLog;
    }

    private void parseCvsLogs(String filename) throws IOException, LogSyntaxException, LockException
    {
        this.content = cvsRepositoryUtil.parseCvsLogs(new File(filename), moduleName, cvsRepositoryUtil.parseCvsRoot(cvsRoot).getRepository(), getName());
    }

    /**
     * Returns the filename where the log has been written.
     *
     * @return the filename where the log has been written
     * @throws CommandException        if update of CVS repository fails
     * @throws AuthenticationException if update of CVS repository fails
     * @throws IOException             if cannot create file or update of CVS repository fails
     * @throws LockException           if update of CVS repository fails
     */
    public synchronized String updateCvs() throws CommandException, AuthenticationException, IOException, LockException
    {
        if (fetchLog)
        {
            // Test for null path. If path is null then use a temporary file
            final File outputFile;
            if (cvsLogFilePath == null || cvsLogFilePath.length() <= 0)
            {
                // See if we already have a temporary file to use.
                if (tempFile == null)
                {
                    // If not, create one and 'remember' it for future use
                    log.debug("No temp file found - creating one");
                    outputFile = java.io.File.createTempFile("cvs-", ".log");
                    outputFile.deleteOnExit();
                    tempFile = outputFile;
                }
                else
                {
                    // If so use it, to prevent generating a lot of temporary files
                    log.debug("Found a temp file to use.");
                    outputFile = tempFile;
                }

                log.info("Repository has no log file path set - using temporary file '" + outputFile.getAbsolutePath() + "'.");
            }
            else
            {
                outputFile = new File(cvsLogFilePath);
            }

            cvsRepositoryUtil.updateCvs(outputFile, cvsRoot, moduleName, password, cvsTimeout);
            return outputFile.getAbsolutePath();
        }
        else
        {
            log.debug("Not fetching log as the option is disabled.");
            return cvsLogFilePath;
        }
    }

    /**
     * Return a list of {@link Commit} objects
     *
     * @param issueKey issue key
     */
    public List<Commit> getCommitsForIssue(final String issueKey) throws RepositoryException
    {
        log.debug("Starting commit matching.");
        long t0 = System.currentTimeMillis();
        // We should always let the VcsService update the cvs logs, doing it synchronously here can lock
        // up the UI, JRA-8857
        if (content == null)
        {
            return null;
        }

        CommitListBuilder commitListBuilder = new CommitListBuilder(content.getRevisions())
        {
            protected void processRevision(CvsRevision rev)
            {
                // Check if the key can be found in the commit message
                if (JiraKeyUtils.isKeyInString(issueKey, rev.getComment()))
                {
                    super.processRevision(rev);
                }
            }
        };

        @SuppressWarnings ({ "unchecked" }) List<Commit> matchingCommits = commitListBuilder.createCommitList();
        List<Commit> cvsCommits = new ArrayList<Commit>(matchingCommits);

        if (log.isDebugEnabled())
        {
            log.debug("Finished commit matching.");
            log.debug("Matching took " + (System.currentTimeMillis() - t0) + "ms and matched " + cvsCommits.size() + " commits.");
        }

        return cvsCommits;
    }

    public synchronized void updateRepository() throws CommandException, AuthenticationException, IOException, LockException, LogSyntaxException
    {
        final String cvsLog = updateCvs();
        parseCvsLogs(cvsLog);
    }

    public String getType()
    {
        return RepositoryManager.CVS_TYPE;
    }

    public String toString()
    {
        return super.toString() + ",cvsLogFilePath=" + cvsLogFilePath + ",cvsRoot=" + cvsRoot + ",moduleName=" + moduleName + ",password=" + password + ", fetchLog=" + fetchLog;
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof CvsRepository)) return false;
        if (!super.equals(o)) return false;

        final CvsRepository cvsRepository = (CvsRepository) o;

        if (fetchLog != cvsRepository.fetchLog) return false;
        if (cvsLogFilePath != null ? !cvsLogFilePath.equals(cvsRepository.cvsLogFilePath) : cvsRepository.cvsLogFilePath != null) return false;
        if (cvsRoot != null ? !cvsRoot.equals(cvsRepository.cvsRoot) : cvsRepository.cvsRoot != null) return false;
        if (moduleName != null ? !moduleName.equals(cvsRepository.moduleName) : cvsRepository.moduleName != null) return false;
        if (password != null ? !password.equals(cvsRepository.password) : cvsRepository.password != null) return false;

        return true;
    }

    public int hashCode()
    {
        int result = super.hashCode();
        result = 29 * result + (cvsLogFilePath != null ? cvsLogFilePath.hashCode() : 0);
        result = 29 * result + (cvsRoot != null ? cvsRoot.hashCode() : 0);
        result = 29 * result + (moduleName != null ? moduleName.hashCode() : 0);
        result = 29 * result + (password != null ? password.hashCode() : 0);
        result = 29 * result + (fetchLog ? 1 : 0);
        return result;
    }

    /**
     * Copies the content of the given repository only if it is the same type (CvsRepository).
     *
     * @param repository repository to copy the content of
     * @since v3.10
     */
    public void copyContent(Repository repository)
    {
        if (repository instanceof CvsRepository)
        {
            this.content = ((CvsRepository) repository).content;
        }
    }

    /**
     * Returns the CVS content of this repository.
     *
     * @return the CVS content of this repository, can be null
     */
    protected CvsContent getCvsContent()
    {
        return content;
    }
}
