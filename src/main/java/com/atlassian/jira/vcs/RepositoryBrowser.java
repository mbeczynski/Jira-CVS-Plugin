/*
 * Copyright (c) 2002-2004
 * All rights reserved.
 */

package com.atlassian.jira.vcs;

public interface RepositoryBrowser
{
    public static final String VIEW_CVS_TYPE = "VIEW_CVS";

    /**
     * Returns the link to the main screen of the file
     *
     */
    public String getFileLink(String filePath);

    /**
     * Returns the link to the actual (given) revison of the file
     *
     */
    public String getRevisionLink(String filePath, String revision);

    /**
     * Returns the link to the diff between the current (given) revision of the file
     * and the previous one.
     *
     * @param filePath
     * @param currentRevision
     */
    public String getDiffLink(String filePath, String currentRevision);

    public String getType();
}
