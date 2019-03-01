package com.atlassian.jira.vcs.cvsimpl;


public class ValidationException extends Exception
{
    public ValidationException(String string)
    {
        super(string);
    }

    public ValidationException(String string, Throwable throwable)
    {
        super(string, throwable);
    }
}
