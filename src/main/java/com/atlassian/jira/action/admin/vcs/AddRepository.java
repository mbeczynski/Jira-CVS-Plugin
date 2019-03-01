/*
 * Copyright (c) 2002-2004
 * All rights reserved.
 */

package com.atlassian.jira.action.admin.vcs;

import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.xsrf.RequiresXsrfCheck;
import com.atlassian.jira.util.collect.MapBuilder;
import com.atlassian.jira.vcs.Repository;
import com.atlassian.jira.vcs.RepositoryBrowser;
import com.atlassian.jira.vcs.RepositoryManager;
import com.atlassian.jira.vcs.cvsimpl.CvsRepository;
import com.atlassian.jira.vcs.cvsimpl.CvsRepositoryUtil;
import com.atlassian.jira.vcs.viewcvs.ViewCvsBrowser;
import com.atlassian.jira.web.action.ActionViewData;
import com.atlassian.sal.api.websudo.WebSudoRequired;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.opensymphony.util.TextUtils;
import org.ofbiz.core.entity.GenericEntityException;
import org.ofbiz.core.entity.GenericValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.annotation.Nullable;

@WebSudoRequired
public class AddRepository extends RepositoryActionSupport
{
    private Map<Long, Collection<Project>> projects;

    public AddRepository(RepositoryManager repositoryManager, CvsRepositoryUtil cvsRepositoryUtil)
    {
        super(repositoryManager, cvsRepositoryUtil);
        projects = new HashMap();
    }

    public String doDefault() throws Exception
    {
        setFetchLog(true);
        // Do not use the super's implementation.
        if (isSystemAdministrator())
        {
            return INPUT;
        }
        // Don't show the input page if the user does not have permission
        else
        {
            return ERROR;
        }
    }

    protected void doValidation()
    {
        // Only sys admins can add cvs modules
        if (!isSystemAdministrator())
        {
            addErrorMessage(getText("admin.errors.no.perm.when.creating"));
            return;
        }
        if (!TextUtils.stringSet(getName()))
        {
            addError("name", getText("admin.errors.you.must.specify.a.name.for.the.repository"));
        }
        else
        {
            if (getRepositoryManager().getRepository(getName()) != null)
            {
                addError("name", getText("admin.errors.another.repository.with.this.name.already.exists"));
            }
        }

        validateRepositoryParameters();
    }

    @RequiresXsrfCheck
    protected String doExecute() throws Exception
    {
        Properties cvsProps = new Properties();

        // Add all properties required by the CVS repository
        cvsProps.setProperty(CvsRepository.KEY_LOG_FILE_PATH, getLogFilePath());
        cvsProps.setProperty(CvsRepository.KEY_CVS_ROOT, getCvsRoot());
        cvsProps.setProperty(CvsRepository.KEY_MODULE_NAME, getModuleName());
        if (getPassword() != null)
        {
            cvsProps.setProperty(CvsRepository.KEY_PASSWORD, getPassword());
        }
        cvsProps.setProperty(CvsRepository.KEY_FETCH_LOG, String.valueOf(isFetchLog()));

        cvsProps.setProperty(CvsRepository.KEY_CVS_TIMEOUT, String.valueOf(getTimeoutMillis()));
        try
        {
            if (TextUtils.stringSet(getRepositoryBrowserURL()))
            {
                cvsProps.setProperty(Repository.KEY_REPOSITTORY_BROWSER_TYPE, RepositoryBrowser.VIEW_CVS_TYPE);
                cvsProps.setProperty(ViewCvsBrowser.KEY_BASE_URL, getRepositoryBrowserURL());
                cvsProps.setProperty(ViewCvsBrowser.ROOT_PARAMETER, getRepositoryBrowserRootParam());
            }

            // Create the repository
            getRepositoryManager().createRepository(RepositoryManager.CVS_TYPE, getName(), getDescription(), cvsProps);
        }
        catch (Exception e)
        {
            log.error("Error occurred while creating the repository.", e);
            addErrorMessage(getText("admin.errors.occured.when.creating"));
            return getResult();
        }

        // Redirect to the view screen
        return getRedirect("ViewRepositories.jspa");
    }

    private Collection<Project> getProjects(Repository repository)
    {
        if (!projects.containsKey(repository.getId()))
        {
            try
            {
                projects.put(repository.getId(), getRepositoryManager().getProjectsForRepository(repository));
            }
            catch (GenericEntityException e)
            {
                log.error("Error while retrieving projects for repository '" + repository + "'.", e);
                addErrorMessage(getText("admin.errors.occured.when.retrieving", repository));
                projects.put(repository.getId(), Collections.EMPTY_LIST);
            }
        }

        return projects.get(repository.getId());
    }

    private String getViewCVSBaseUrl(Repository repository)
    {
        final RepositoryBrowser repositoryBrowser = repository.getRepositoryBrowser();
        if (repositoryBrowser != null && RepositoryBrowser.VIEW_CVS_TYPE.equals(repositoryBrowser.getType()))
        {
            final ViewCvsBrowser viewCvsBrowser = (ViewCvsBrowser) repositoryBrowser;
            return viewCvsBrowser.getBaseURL();
        }
        else
        {
            return "";
        }
    }

    private String getViewCVSRootParameter(Repository repository)
    {
        final RepositoryBrowser repositoryBrowser = repository.getRepositoryBrowser();
        if (repositoryBrowser != null && RepositoryBrowser.VIEW_CVS_TYPE.equals(repositoryBrowser.getType()))
        {
            final ViewCvsBrowser viewCvsBrowser = (ViewCvsBrowser) repositoryBrowser;
            return viewCvsBrowser.getRootParameter();
        }
        else
        {
            return "";
        }
    }

    // TODO These are slightly ugly hacks to pre-compute Repository -> view cvs data mappings for soy conversion.


    @ActionViewData
    public Map<String, Map<String, String>> getViewCVSRootParameterMappings()
    {
        try
        {
            // TODO Ugly hack to get a Map of repo id -> base url
            List<CvsRepository> repositories = getRepositories();

            final Map<String, Repository> repositoryMap = Maps.newHashMap();

            for (Repository repository : repositories)
            {
                repositoryMap.put(repository.getId().toString(), repository);
            }

            return MapBuilder.newBuilder("viewCVSRootParameterMappings", newMapFrom(Lists.newArrayList(repositoryMap.keySet()), new Function<String, String>()
            {
                @Override
                public String apply(@Nullable final String repositoryId)
                {
                    return getViewCVSRootParameter(repositoryMap.get(repositoryId));
                }
            })).toMap();
        }
        catch (GenericEntityException e)
        {
            return Maps.newHashMap();
        }
    }

    @ActionViewData
    public Map<String, Map<String, String>> getViewCVSBaseUrlMappings()
    {
        try
        {
            // TODO Ugly hack to get a Map of repo id -> base url
            List<CvsRepository> repositories = getRepositories();

            final Map<String, Repository> repositoryMap = Maps.newHashMap();

            for (Repository repository : repositories)
            {
                repositoryMap.put(repository.getId().toString(), repository);
            }

            return MapBuilder.newBuilder("viewCVSBaseUrlMappings", newMapFrom(Lists.newArrayList(repositoryMap.keySet()), new Function<String, String>()
            {
                @Override
                public String apply(@Nullable final String repositoryId)
                {
                    return getViewCVSBaseUrl(repositoryMap.get(repositoryId));
                }
            })).toMap();
        }
        catch (GenericEntityException e)
        {
            return Maps.newHashMap();
        }
    }

    @ActionViewData
    public Map<String, Map<String, Collection<Project>>> getRepositoryProjectMappings()
    {
        try
        {
            // TODO Ugly hack to get a Map of repo id -> base url
            List<CvsRepository> repositories = getRepositories();

            final Map<String, Repository> repositoryMap = Maps.newHashMap();

            for (Repository repository : repositories)
            {
                repositoryMap.put(repository.getId().toString(), repository);
            }

            return MapBuilder.newBuilder("repositoryProjectMappings", newMapFrom(Lists.newArrayList(repositoryMap.keySet()), new Function<String, Collection<Project>>()
            {
                @Override
                public Collection<Project> apply(@Nullable final String repositoryId)
                {
                    return getProjects(repositoryMap.get(repositoryId));
                }
            })).toMap();
        }
        catch (GenericEntityException e)
        {
            return Maps.newHashMap();
        }
    }

    @ActionViewData
    public Map<String, Map<String, Boolean>> getRepositoryDeletableMappings()
    {
        try
        {
            // TODO Ugly hack to get a Map of repo id -> base url
            List<CvsRepository> repositories = getRepositories();

            final Map<String, Repository> repositoryMap = Maps.newHashMap();

            for (Repository repository : repositories)
            {
                repositoryMap.put(repository.getId().toString(), repository);
            }

            return MapBuilder.newBuilder("repositoryDeletableMappings", newMapFrom(Lists.newArrayList(repositoryMap.keySet()), new Function<String, Boolean>()
            {
                @Override
                public Boolean apply(@Nullable final String repositoryId)
                {
                    return isDeletable(repositoryMap.get(repositoryId));
                }
            })).toMap();
        }
        catch (GenericEntityException e)
        {
            return Maps.newHashMap();
        }
    }

    public static <K,V> Map<K,V> newMapFrom(List<K> keys, Function<? super K,V> f) {
        Map<K,V> map = Maps.newHashMap();
        for (K k : keys) {
            map.put(k, f.apply(k));
        }
        return map;
    }

    @ActionViewData
    public List<CvsRepository> getRepositories() throws GenericEntityException
    {
        final List repositories = new ArrayList(getRepositoryManager().getRepositories());
        Collections.sort(repositories);
        return repositories;
    }

    @ActionViewData
    @Override
    public boolean isSystemAdministrator() {
        return super.isSystemAdministrator();
    }

    @Override
    @ActionViewData
    public String getXsrfToken()
    {
        return super.getXsrfToken();
    }

    @ActionViewData
    public boolean getFetchLog()
    {
        return super.isFetchLog();
    }

    private boolean isDeletable(Repository repository)
    {
        try
        {
            return getRepositoryManager().getProjectsForRepository(repository).isEmpty();
        }
        catch (Exception e)
        {
            log.error("Error occurred while retrieving projects for repository '" + repository + "'.", e);
            addErrorMessage(getText("admin.errors.occured.when.retrieving", repository));
            return false;
        }
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
