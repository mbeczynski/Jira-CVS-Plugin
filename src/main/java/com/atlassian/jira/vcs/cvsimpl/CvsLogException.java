package com.atlassian.jira.vcs.cvsimpl;


public class CvsLogException extends RuntimeException
{
    public CvsLogException(Throwable throwable)
    {
        super(throwable);
    }

    public CvsLogException(String s, Throwable throwable)
    {
        super(s, throwable);
    }

}
