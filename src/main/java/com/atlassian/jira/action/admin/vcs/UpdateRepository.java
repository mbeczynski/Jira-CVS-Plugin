/*
 * Copyright (c) 2002-2004
 * All rights reserved.
 */

package com.atlassian.jira.action.admin.vcs;

import com.atlassian.jira.security.xsrf.RequiresXsrfCheck;
import com.atlassian.jira.vcs.Repository;
import com.atlassian.jira.vcs.RepositoryBrowser;
import com.atlassian.jira.vcs.RepositoryManager;
import com.atlassian.jira.vcs.cvsimpl.CvsRepository;
import com.atlassian.jira.vcs.cvsimpl.CvsRepositoryUtil;
import com.atlassian.jira.vcs.viewcvs.ViewCvsBrowser;
import com.atlassian.jira.web.action.ActionViewData;
import com.atlassian.sal.api.websudo.WebSudoRequired;
import com.opensymphony.util.TextUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@WebSudoRequired
public class UpdateRepository extends RepositoryActionSupport
{
    public UpdateRepository(RepositoryManager repositoryManager, CvsRepositoryUtil cvsRepositoryUtil)
    {
        super(repositoryManager, cvsRepositoryUtil);
    }

    protected void doValidation()
    {
        if (id == null || id.longValue() <= 0)
        {
            addErrorMessage(getText("admin.errors.repositories.specify.repository.to.update"));
            return;
        }

        if (!TextUtils.stringSet(getName()))
        {
            addError("name", getText("admin.errors.repositories.specify.name"));
        }
        else
        {
            final Repository repository = getRepositoryManager().getRepository(getName());
            // If a different repository with the same name exists add an error message
            if (repository != null && !id.equals(repository.getId()))
            {
                addError("name", getText("admin.errors.repositories.duplicate.name"));
            }
        }


        validateRepositoryParameters();

        super.doValidation();
    }

    @RequiresXsrfCheck
    protected String doExecute() throws Exception
    {
        final Repository repository = getRepositoryManager().getRepository(getId());
        if (repository == null)
        {
            addErrorMessage(getText("admin.errors.repositories.error.retrieving"));
            return ERROR;
        }
        else
        {
            Properties properties = new Properties();
            properties.setProperty(CvsRepository.KEY_LOG_FILE_PATH, getLogFilePath());
            properties.setProperty(CvsRepository.KEY_CVS_ROOT, getCvsRoot());
            properties.setProperty(CvsRepository.KEY_MODULE_NAME, getModuleName());
            properties.setProperty(CvsRepository.KEY_CVS_TIMEOUT, String.valueOf(getTimeoutMillis()));
            if (getPassword() != null)
            {
                properties.setProperty(CvsRepository.KEY_PASSWORD, getPassword());
            }
            properties.setProperty(CvsRepository.KEY_FETCH_LOG, String.valueOf(isFetchLog()));

            if (TextUtils.stringSet(getRepositoryBrowserURL()))
            {
                properties.setProperty(Repository.KEY_REPOSITTORY_BROWSER_TYPE, RepositoryBrowser.VIEW_CVS_TYPE);
                properties.setProperty(ViewCvsBrowser.KEY_BASE_URL, getRepositoryBrowserURL());
                properties.setProperty(ViewCvsBrowser.ROOT_PARAMETER, getRepositoryBrowserRootParam());
            }
            else
            {
                properties.setProperty(Repository.KEY_REPOSITTORY_BROWSER_TYPE, "");
                properties.setProperty(ViewCvsBrowser.KEY_BASE_URL, "");
                properties.setProperty(ViewCvsBrowser.ROOT_PARAMETER, "");
            }

            getRepositoryManager().updateRepository(id, getType(), getName(), getDescription(), properties);
            return getRedirect("ViewRepositories.jspa");
        }
    }

    @ActionViewData
    public boolean getFetchLog()
    {
        return super.isFetchLog();
    }

    @Override
    @ActionViewData
    public String getXsrfToken()
    {
        return super.getXsrfToken();
    }

    @Override
    @ActionViewData
    public Map getErrors()
    {
        // This is done as the ActionViewData thing will smoosh Maps into the top level Soy context
        HashMap<String, Object> errorsMap = new HashMap<String, Object>();
        errorsMap.put("errors", super.getErrors());
        return errorsMap;
    }
}
