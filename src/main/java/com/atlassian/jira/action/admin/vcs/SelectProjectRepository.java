/*
 * Copyright (c) 2002-2004
 * All rights reserved.
 */

package com.atlassian.jira.action.admin.vcs;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.xsrf.RequiresXsrfCheck;
import com.atlassian.jira.vcs.Repository;
import com.atlassian.jira.vcs.RepositoryManager;
import com.atlassian.jira.web.action.ActionViewData;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.sal.api.websudo.WebSudoRequired;
import com.google.common.base.Function;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
@WebSudoRequired
public class SelectProjectRepository extends JiraWebActionSupport
{
    private Long pid;
    private String[] repoIds;
    private List ids;
    private List<Map<String, Object>> repositories;
    private final RepositoryManager repositoryManager;

    public SelectProjectRepository(RepositoryManager repositoryManager)
    {
        this.repositoryManager = repositoryManager;
    }

    public String doDefault() throws Exception
    {
        // Initialize the current repositories
        final Collection repositories = getRepositoryManager().getRepositoriesForProject(getProject().getGenericValue());
        if (repositories != null)
        {
            repoIds = new String[repositories.size()];

            int i = 0;
            for (Iterator iterator = repositories.iterator(); iterator.hasNext(); i++)
            {
                Repository repository = (Repository) iterator.next();
                repoIds[i] = repository.getId().toString();
            }
        }

        return super.doDefault();
    }

    protected void doValidation()
    {
        // Must have a valid project
        if (null == getProject())
        {
            addErrorMessage(getText("admin.errors.repositories.must.specify.project"));
        }

        if (repoIds == null || repoIds.length == 0)
        {
            addError("repositoryIds", getText("admin.errors.repositories.please.select.repository"));
        }
        else
        {
            convertRepositoryIds();

            // Check that if 'None' was selected no other item was selected at the same time
            for (int i = 0; i < ids.size(); i++)
            {
                Long id = (Long) ids.get(i);

                // 'None' has id < 0
                if (id.longValue() < 0L)
                {
                    if (ids.size() > 1)
                    {
                        addError(getRepositoryIdsControlName(), getText("admin.errors.repositories.cannot.select.none"));
                        break;
                    }
                    else
                    {
                        ids = Collections.EMPTY_LIST;
                    }
                }
            }
        }
    }

    @RequiresXsrfCheck
    protected String doExecute() throws Exception
    {
        getRepositoryManager().setProjectRepositories(getProject().getGenericValue(), ids);
        return getRedirect("/plugins/servlet/project-config/" + getProject().getKey() + "/summary");
    }

    @ActionViewData
    public Project getProject()
    {
        // TODO Inject project manager instead of calling to the ManagerFactory
        //return ManagerFactory.getProjectManager().getProjectObj(getProjectId());
        Project projectManager;
        return  projectManager = ComponentAccessor.getProjectManager().getProjectObj(getProjectId());
    }

    @ActionViewData
    public String getBaseURL()
    {
        return getHttpRequest().getContextPath();
    }

    public Long getProjectId()
    {
        return pid;
    }

    public void setProjectId(Long pid)
    {
        this.pid = pid;
    }

    public void setRepositoryIds(String prids)
    {
        setRepoIds(new String[]{prids});
    }

    public String getRepositoryIds()
    {
        if (repoIds != null && repoIds.length > 0)
            return repoIds[0];
        else
            return null;
    }

    protected void convertRepositoryIds()
    {
        if (ids == null)
        {
            ids = new ArrayList();

            // Convert the ids to Longs
            for (String prid : repoIds)
            {
                ids.add(new Long(prid));
            }
        }
    }

    @ActionViewData
    public List<Map<String, Object>> getRepositories()
    {
        if (repositories == null)
        {
            repositories = new ArrayList(Lists.transform(new ArrayList<Repository>(getRepositoryManager().getRepositories()), new Function<Repository, Map<String, Object>>()
            {
                @Override
                public Map<String, Object> apply(@Nullable final Repository repository)
                {
                    HashMap<String, Object> repositoryOption = new HashMap<String, Object>();
                    repositoryOption.put("text", repository.getName());
                    repositoryOption.put("value", repository.getId().toString());
                    return repositoryOption;
                }
            }));

            // TODO i18n, lazy addition through Lists.concat or similar.
            HashMap<String, Object> noneOption = new HashMap<String, Object>();
            noneOption.put("text", "None");
            noneOption.put("value", "-1");

            repositories.add(0, noneOption);
        }

        return repositories;
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


    protected String[] getRepoIds()
    {
        return repoIds;
    }

    protected void setRepoIds(String[] repoIds)
    {
        this.repoIds = repoIds;
    }

    protected String getRepositoryIdsControlName()
    {
        return "repositoryIds";
    }

    protected RepositoryManager getRepositoryManager()
    {
        return repositoryManager;
    }
}
