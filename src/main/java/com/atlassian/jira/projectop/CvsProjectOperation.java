package com.atlassian.jira.projectop;

import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.plugin.projectoperation.AbstractPluggableProjectOperation;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.security.Permissions;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.collect.MapBuilder;
import com.atlassian.jira.vcs.Repository;
import com.atlassian.jira.vcs.RepositoryManager;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import org.apache.log4j.Logger;
import org.ofbiz.core.entity.GenericEntityException;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Project Operation for CVS
 *
 * @since v6.0
 */

public class CvsProjectOperation extends AbstractPluggableProjectOperation
{
    private static final String CONTEXT_REPOS = "repos";
    private static final String CONTEXT_ERROR = "error";

    private static final Logger log = Logger.getLogger(CvsProjectOperation.class);

    private final RepositoryManager repositoryManager;
    private final JiraAuthenticationContext authContext;
    private final PermissionManager permissionManager;

    public CvsProjectOperation(RepositoryManager repositoryManager, JiraAuthenticationContext authContext,
                               PermissionManager permissionManager)
    {
        this.repositoryManager = repositoryManager;
        this.authContext = authContext;
        this.permissionManager = permissionManager;
    }

    @Override
    public String getHtml(final Project project, ApplicationUser applicationUser)
    {
        final MapBuilder<String, Object> contextBuilder = MapBuilder.newBuilder();
        contextBuilder.addAll(descriptor.getParams());

        try
        {
            List<Repository> collection = new ArrayList<Repository>(repositoryManager.getRepositoriesForProject(project.getGenericValue()));
            final Collator stringComparator = Collator.getInstance(authContext.getLocale());
            Collections.sort(collection, new Comparator<Repository>()
            {
                public int compare(Repository o1, Repository o2)
                {
                    return stringComparator.compare(o1.getName(), o2.getName());
                }
            });

            contextBuilder.add(CONTEXT_REPOS, collection);
        }
        catch (GenericEntityException e)
        {
            log.error("An error occured while getting repositories from the database.", e);
            contextBuilder.add(CONTEXT_ERROR, true);
        }
        contextBuilder.add("isAdmin", permissionManager.hasPermission(Permissions.PROJECT_ADMIN, project, applicationUser));
        contextBuilder.add("project", project);

        return descriptor.getHtml("view", contextBuilder.toMap());
    }

    @Override
    public boolean showOperation(final Project project, ApplicationUser applicationUser)
    {
        return true;
    }


}
