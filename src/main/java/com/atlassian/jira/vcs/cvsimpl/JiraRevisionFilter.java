package com.atlassian.jira.vcs.cvsimpl;

import com.atlassian.jira.util.JiraKeyUtils;
import net.sf.statcvs.input.RevisionData;
import net.sf.statcvs.input.RevisionFilter;

public class JiraRevisionFilter implements RevisionFilter
{
    public boolean isValid(RevisionData revisionData) {
       return JiraKeyUtils.isKeyInString(revisionData.getComment());
    }
}
