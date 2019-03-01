/*
 * Copyright (c) 2002-2004
 * All rights reserved.
 */

package com.atlassian.jira.vcs.cvsimpl;

import com.atlassian.jira.vcs.Repository;
import net.sf.statcvs.model.Commit;

import java.util.Collection;
import java.util.Date;

public class CVSCommit
{
    private final Commit commit;
    private Repository repository;

    public CVSCommit(Commit commit, Repository repository)
    {
        this.commit = commit;
        this.repository = repository;
    }

    public Date getTimePerformed()
    {
        return commit.getDate();
    }

    public String getUsername()
    {
       return commit.getAuthor().getName();
    }

    public String getComment()
    {
        return commit.getComment();
    }

    public String getRepositoryName()
    {
        return repository.getName();
    }

    public String getBranchName()
    {
        return commit.getMainBranch().getName();
    }

    public Collection getRevisions()
    {
        return commit.getRevisions();
    }

    public boolean hasRepositoryViewer()
    {
        return repository != null && repository.getRepositoryBrowser() != null;
    }

    public String getFileLink(String filePath)
    {
        if (hasRepositoryViewer())
        {
            return repository.getRepositoryBrowser().getFileLink(filePath);
        }
        else
        {
            throw new IllegalStateException("Cannot return file link as repositoryBrowser is not set.");
        }
    }

    public String getRevisionLink(String filePath, String revision)
    {
        if (hasRepositoryViewer())
        {
            return repository.getRepositoryBrowser().getRevisionLink(filePath, revision);
        }
        else
        {
            throw new IllegalStateException("Cannot return revision link as repositoryBrowser is not set.");
        }
    }

    public String getDiffLink(String filePath, String revision)
    {
        if (hasRepositoryViewer())
        {
            return repository.getRepositoryBrowser().getDiffLink(filePath, revision);
        }
        else
        {
            throw new IllegalStateException("Cannot return diff link as repositoryBrowser is not set.");
        }
    }
}
