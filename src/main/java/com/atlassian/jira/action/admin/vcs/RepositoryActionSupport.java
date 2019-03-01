/*
 * Copyright (c) 2002-2004
 * All rights reserved.
 */

package com.atlassian.jira.action.admin.vcs;

import com.atlassian.jira.util.LockException;
import com.atlassian.jira.vcs.Repository;
import com.atlassian.jira.vcs.RepositoryBrowser;
import com.atlassian.jira.vcs.RepositoryManager;
import com.atlassian.jira.vcs.cvsimpl.CvsRepository;
import com.atlassian.jira.vcs.cvsimpl.CvsRepositoryUtil;
import com.atlassian.jira.vcs.cvsimpl.ValidationException;
import com.atlassian.jira.vcs.viewcvs.ViewCvsBrowser;
import com.atlassian.jira.web.action.ActionViewData;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.opensymphony.util.TextUtils;
import net.sf.statcvs.input.LogSyntaxException;
import org.apache.commons.lang.StringUtils;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class RepositoryActionSupport extends JiraWebActionSupport
{
    protected Long id;
    private String type;
    private String name = null;
    private String description;
    private String logFilePath;
    private String cvsRoot;
    private String moduleName;
    private String password;
    private boolean fetchLog;
    private String timeout;
    private long timeoutMS = CvsRepository.CVS_OPERATION_TIMEOUT_DEFAULT;

    private String repositoryBrowserURL;
    private final RepositoryManager repositoryManager;
    private final CvsRepositoryUtil cvsRepositoryUtil;
    private String repositoryBrowserRootParam;

    public RepositoryActionSupport(RepositoryManager repositoryManager, CvsRepositoryUtil cvsRepositoryUtil)
    {
        this.repositoryManager = repositoryManager;
        this.cvsRepositoryUtil = cvsRepositoryUtil;
        this.timeout = String.valueOf(TimeUnit.MILLISECONDS.toSeconds(CvsRepository.CVS_OPERATION_TIMEOUT_DEFAULT));
    }

    public String doView()
    {
        return SUCCESS;
    }

    public String doDefault() throws Exception
    {
        if (id == null || id.longValue() <= 0)
        {
            return getRedirect("ViewRepositories.jspa");
        }

        // Retrieve the cvs repository
        final Repository repository = getRepositoryManager().getRepository(id);
        if (!(repository instanceof CvsRepository))
        {
            addErrorMessage(getText("admin.errors.repository.with.id.not.cvs.repo", id.toString()));
            return ERROR;
        }

        CvsRepository cvsRepository = (CvsRepository) repository;

        // Initialize the values
        setType(RepositoryManager.CVS_TYPE);
        setName(cvsRepository.getName());
        setDescription(cvsRepository.getDescription());
        setLogFilePath(cvsRepository.getCvsLogFilePath());
        setCvsRoot(cvsRepository.getCvsRoot());
        setModuleName(cvsRepository.getModuleName());
        setPassword(cvsRepository.getPassword());
        setFetchLog(cvsRepository.fetchLog());
        setTimeoutMillis(cvsRepository.getCvsTimeout());

        // If the repository has the repository browser, set the base url
        if (cvsRepository.getRepositoryBrowser() != null)
        {
            if (RepositoryBrowser.VIEW_CVS_TYPE.equals(cvsRepository.getRepositoryBrowser().getType()))
            {
                final ViewCvsBrowser viewCvsBrowser = (ViewCvsBrowser) cvsRepository.getRepositoryBrowser();
                setRepositoryBrowserURL(viewCvsBrowser.getBaseURL());
                setRepositoryBrowserRootParam(viewCvsBrowser.getRootParameter());
            }
        }

        return INPUT;
    }

    @ActionViewData
    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    @ActionViewData
    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    @ActionViewData
    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    @ActionViewData
    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        if (getRepositoryManager().isValidType(type))
        {
            this.type = type;
        }
        else
        {
            this.type = null;
        }
    }

    public Collection<String> getTypes()
    {
        return RepositoryManager.VCS_TYPES;
    }

    protected String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    @ActionViewData
    public String getLogFilePath()
    {
        return logFilePath;
    }

    public void setLogFilePath(String logFilePath)
    {
        this.logFilePath = logFilePath;
    }

    @ActionViewData
    public String getCvsRoot()
    {
        return cvsRoot;
    }

    public void setCvsRoot(String cvsRoot)
    {
        this.cvsRoot = cvsRoot;
    }

    @ActionViewData
    public String getModuleName()
    {
        return moduleName;
    }

    public void setModuleName(String moduleName)
    {
        this.moduleName = moduleName;
    }

    public boolean isFetchLog()
    {
        return fetchLog;
    }

    public void setFetchLog(boolean fetchLog)
    {
        this.fetchLog = fetchLog;
    }

    @ActionViewData
    public String getRepositoryBrowserURL()
    {
        return repositoryBrowserURL;
    }

    public void setRepositoryBrowserURL(String repositoryBrowserURL)
    {
        this.repositoryBrowserURL = repositoryBrowserURL;
    }

    @ActionViewData
    public String getRepositoryBrowserRootParam()
    {
        return repositoryBrowserRootParam;
    }

    public void setRepositoryBrowserRootParam(String repositoryBrowserRootParam)
    {
        this.repositoryBrowserRootParam = repositoryBrowserRootParam;
    }

    protected RepositoryManager getRepositoryManager()
    {
        return repositoryManager;
    }

    @ActionViewData
    public String getTimeout()
    {
        return timeout;
    }

    public long getTimeoutMillis()
    {
        return timeoutMS;
    }

    private void setTimeoutMillis(long timeoutMs)
    {
        if (timeoutMs <= 0)
        {
            this.timeoutMS = CvsRepository.CVS_OPERATION_TIMEOUT_DEFAULT;
        }
        else
        {
            this.timeoutMS = timeoutMs;
        }

        this.timeout = String.valueOf(TimeUnit.MILLISECONDS.toSeconds(this.timeoutMS));
    }

    public void setTimeout(String timeout)
    {
        this.timeout = timeout;
        if (StringUtils.isBlank(timeout))
        {
            timeoutMS = CvsRepository.CVS_OPERATION_TIMEOUT_DEFAULT;
        }
        else
        {
            try
            {
                final long ms = Long.parseLong(timeout);
                timeoutMS = ms > 0 ? TimeUnit.SECONDS.toMillis(ms) : CvsRepository.CVS_OPERATION_TIMEOUT_DEFAULT;
            }
            catch (NumberFormatException e)
            {
                timeoutMS = CvsRepository.CVS_OPERATION_TIMEOUT_DEFAULT;
            }
        }
    }

    protected String checkRepository(String repositoryName, String logFilePath, String cvsRoot, String moduleName, String password, long cvsTimeout, boolean fetchLog)
    {
        try
        {
            testRepository(repositoryName, logFilePath, cvsRoot, moduleName, password, cvsTimeout, fetchLog);
        }
        catch (AuthenticationException e)
        {
            final String message = "Error authenticating with the repository: " + e.getLocalizedMessage();
            log.error(message, e);
            Throwable cause = e.getUnderlyingThrowable();
            if (cause != null)
            {
                log.error("Caused by: " + cause.getMessage(), cause);
            }
            return getText("admin.errors.authentication.repository", e.getLocalizedMessage());
        }
        catch (LockException e)
        {
            final String message = "Error obtaining lock: " + e.getMessage();
            log.error(message, e);
            return getText("admin.errors.cvs.obtaining.lock", e.getMessage());
        }
        catch (IOException e)
        {
            final String message = e.getMessage();
            log.error(message, e);
            return message;
        }
        catch (CommandException e)
        {
            log.error("Error occured while retrieving cvs log.", e);
            return getText("admin.errors.cvs.retrieving.log", e.getMessage());
        }
        catch (LogSyntaxException e)
        {
            log.error("Error occured while parsing cvs log.", e);
            return getText("admin.errors.cvs.parsing.log", e.getMessage());
        }
        catch (Exception unexpected)
        {
            log.error("Error occurred while obtaining cvs log or parsing the cvs log.", unexpected);
            return getText("admin.errors.cvs.parsing.or.obtaining.log");
        }

        return null;
    }

    protected void testRepository(String repositoryName, String logFilePath, String cvsRoot, String moduleName, String password, long cvsTimeout, boolean fetchLog)
            throws AuthenticationException, IOException, CommandException, LogSyntaxException, LockException
    {
        File logFile = new File(logFilePath);
        // Only check if we can get the log if the user wants us to do it
        if (fetchLog)
        {
            // Check that we can get the cvs log
            cvsRepositoryUtil.updateCvs(logFile, cvsRoot, moduleName, password, cvsTimeout);
        }
        else
        {
            if (!logFile.exists())
            {
                addErrorMessage(getText("admin.errors.cvs.log.file.not.found", logFile.getPath()));
                return;
            }
        }

        // Check if we can parse the log (if we got here no exception was thrown and hence updating the logs went ok)
        cvsRepositoryUtil.parseCvsLogs(logFile, moduleName, getCvsRepositoryUtil().parseCvsRoot(cvsRoot).getRepository(), repositoryName);
    }

    protected CvsRepositoryUtil getCvsRepositoryUtil()
    {
        return cvsRepositoryUtil;
    }

    // This is public for testing purposes
    public void validateRepositoryParameters()
    {
        if (!TextUtils.stringSet(getName()))
        {
            addError("name", getText("admin.errors.you.must.specify.a.name.for.the.repository"));
        }
        if (!TextUtils.stringSet(getCvsRoot()))
        {
            addError("cvsRoot", getText("admin.errors.you.must.specify.the.cvs.root.of.the.module"));
        }
        if (!TextUtils.stringSet(getModuleName()))
        {
            addError("moduleName", getText("admin.errors.you.must.specify.the.name.of.the.cvs.module"));
        }
        try
        {
            if (timeout == null || Long.parseLong(timeout) < 1)
            {
                addError("timeout", getText("admin.errors.cvs.invalid.timeout"));
            }
        }
        catch (NumberFormatException e)
        {
            addError("timeout", getText("admin.errors.cvs.invalid.timeout"));
        }

        if (!TextUtils.stringSet(getLogFilePath()))
        {
            addError("logFilePath", getText("admin.errors.you.must.specify.the.full.path.to.the.cvs.log.file"));
        }
        else
        {
            checkPathsAndRepository();
        }

        // If the ViewCVS URL is specified, check that it is in correct format
        if (TextUtils.stringSet(getRepositoryBrowserURL()))
        {
            try
            {
                new ViewCvsBrowser(getRepositoryBrowserURL(), Collections.EMPTY_MAP);
            }
            catch (MalformedURLException e)
            {
                addError("repositoryBrowserURL", getText("admin.errors.invalid.url.format"));
            }
        }
    }

    private void checkPathsAndRepository()
    {
        // Check LogFilePath validity
        checkLogFile();
        // Check CvsRoot validity
        checkCvsRoot();

        // Only go ahead with testing the repository if no problems were found
        if (!invalidInput())
        {
            final String message = checkRepository(getName(), getLogFilePath(), getCvsRoot(), getModuleName(), getPassword(), getTimeoutMillis(), isFetchLog());
            if (message != null)
            {
                addErrorMessage(message);
            }
        }
    }

    private void checkCvsRoot()
    {
        try
        {
            getCvsRepositoryUtil().checkCvsRoot(getCvsRoot());
        }
        catch (ValidationException e)
        {
            addError("cvsRoot", e.getMessage());
        }
    }

    private void checkLogFile()
    {
        try
        {
            File file = new File(getLogFilePath());
            getCvsRepositoryUtil().checkLogFilePath(file, isFetchLog());
        }
        catch (ValidationException e)
        {
            addError("logFilePath", e.getMessage());
        }
    }
}
