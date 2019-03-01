/*
 * Copyright (c) 2002-2004
 * All rights reserved.
 */

package com.atlassian.jira.action.admin.vcs.enterprise;

import com.atlassian.jira.vcs.RepositoryManager;
import com.atlassian.jira.action.admin.vcs.SelectProjectRepository;
import com.atlassian.sal.api.websudo.WebSudoRequired;

@WebSudoRequired
public class EnterpriseSelectProjectRepository extends SelectProjectRepository
{
    public EnterpriseSelectProjectRepository(RepositoryManager repositoryManager)
    {
        super(repositoryManager);
    }

    public void setMultipleRepositoryIds(String[] prids)
    {
        setRepoIds(prids);
    }

    public String[] getMultipleRepositoryIds()
    {
        return getRepoIds();
    }

    protected String getRepositoryIdsControlName()
    {
        return "multipleRepositoryIds";
    }
}
