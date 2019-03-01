/*
 * Copyright (c) 2002-2004
 * All rights reserved.
 */

package com.atlassian.jira.vcs;

import com.atlassian.jira.JiraException;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;

/**
 * An exception thrown when an error occurs while communicating with a
 * {@link Repository}
 */
public class RepositoryException extends JiraException
{
    public RepositoryException()
    {
    }

    public RepositoryException(String s)
    {
        super(s);
    }

    public RepositoryException(Throwable throwable)
    {
        super(throwable);
    }

    public RepositoryException(String s, Throwable throwable)
    {
        super(s, throwable);
    }

    public String toString() {
        if (getCause() != null && getCause() instanceof AuthenticationException)
        {
            return super.toString()+"\n Caused by: "+((AuthenticationException)getCause()).getUnderlyingThrowable().toString();
        }
        else
        {
            return super.toString();

        }
    }
}
