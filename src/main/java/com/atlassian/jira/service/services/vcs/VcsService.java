/*
 * Copyright (c) 2002-2004
 * All rights reserved.
 */

package com.atlassian.jira.service.services.vcs;

import com.atlassian.configurable.ObjectConfiguration;
import com.atlassian.configurable.ObjectConfigurationException;
import com.atlassian.configurable.ObjectConfigurationImpl;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.properties.SystemPropertyKeys;
import com.atlassian.jira.service.AbstractService;
import com.atlassian.jira.vcs.RepositoryManager;
import com.atlassian.sal.api.scheduling.PluginJob;
import com.opensymphony.module.propertyset.PropertySet;
import org.apache.log4j.Logger;
import org.ofbiz.core.entity.GenericEntityException;

import java.util.Map;

public class VcsService implements PluginJob
{
    private static final Logger log = Logger.getLogger(VcsService.class);
    public static final String REPOSITORY_MANAGER_KEY = "repositoryManager";

    @Override
    public void execute(final Map<String, Object> stringObjectMap)
    {
        RepositoryManager repositoryManager = (RepositoryManager) stringObjectMap.get(VcsService.REPOSITORY_MANAGER_KEY);

        if (Boolean.getBoolean(SystemPropertyKeys.DISABLE_VCS_POLLING_SYSTEM_PROPERTY))
        {
            log.info("Version control polling (VcsService) disabled by '"+ SystemPropertyKeys.DISABLE_VCS_POLLING_SYSTEM_PROPERTY+"' property.");
            return;
        }
        log.debug("VcsService service running...");
        try
        {
            // Update all the repositories
            repositoryManager.updateRepositories();
        }
        catch (GenericEntityException e)
        {
            log.error("Error occurred while running VcsService.", e);
        }
        catch (OutOfMemoryError e)
        {
            log.error("OutOfMemoryError while updating the repositories. Start the app server with more memory (see '-Xmx' parameter of the 'java' command.)", e);
        }

        log.debug("VcsService service finished.");

    }
}
