/*
 * Copyright (c) 2002-2004
 * All rights reserved.
 */

package com.atlassian.jira.vcs.cvsimpl;

import com.atlassian.jira.util.LockException;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import net.sf.statcvs.input.LogSyntaxException;
import net.sf.statcvs.model.CvsContent;
import org.netbeans.lib.cvsclient.CVSRoot;
import org.netbeans.lib.cvsclient.command.CommandAbortedException;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.Connection;

import java.io.File;
import java.io.IOException;
public interface CvsRepositoryUtil
{
    public CvsContent parseCvsLogs(File logFile, String moduleName, String repositoryPath, String repositoryName) throws IOException, LogSyntaxException, LockException;

    public void updateCvs(File logFile, String cvsRoot, String moduleName, String password, long cvsTimeout) throws AuthenticationException, CommandException, IOException, LockException;

    /**
     * Creates a connection to a CVS Repository given a CVS root
     *
     * @param cvsRoot the string representing a CVS root
     * @return an unopened connection to CVS repository
     */
    public Connection openConnectionToRepository(String cvsRoot, String password) throws CommandAbortedException, AuthenticationException;

    public CVSRoot parseCvsRoot(String cvsRoot);

    public void checkLogFilePath(File logFile, boolean fetchLog) throws ValidationException;

    public void checkCvsRoot(String cvsRoot) throws ValidationException;
}
