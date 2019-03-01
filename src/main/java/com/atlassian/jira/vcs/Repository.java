/*
 * Copyright (c) 2002-2004
 * All rights reserved.
 */

package com.atlassian.jira.vcs;

import net.sf.statcvs.model.Commit;

import java.util.List;

public interface Repository extends Comparable<Repository>
{
    public static final String KEY_REPOSITTORY_BROWSER_TYPE = "repositorybrowsertype";

    /**
     * Get a list of commits for a particular issue
     *
     * @param issueKey issue key
     * @return List a list of {@link net.sf.statcvs.model.Commit} objects
     * @throws RepositoryException if cannot retrieve commits for given issue
     */
    public List<Commit> getCommitsForIssue(String issueKey) throws RepositoryException;

    public Long getId();

    public String getName();

    public String getDescription();

    public String getType();

    public void setRepositoryBrowser(RepositoryBrowser repositoryBrowser);

    public RepositoryBrowser getRepositoryBrowser();

    /**
     * Copies the content of the given repository only if it is the same type.
     *
     * @param repository repository to copy the content of
     * @since v3.10
     */
    public void copyContent(Repository repository);
}
