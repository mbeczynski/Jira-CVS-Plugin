/*
 * Copyright (c) 2002-2004
 * All rights reserved.
 */

package com.atlassian.jira.vcs.viewcvs;

import com.atlassian.jira.ComponentManager;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.properties.APKeys;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.vcs.RepositoryBrowser;
import com.opensymphony.util.UrlUtils;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

public class ViewCvsBrowser implements RepositoryBrowser
{
    public static final String KEY_BASE_URL = "viewcvsbaseurl";
    public static final String ROOT_PARAMETER = "viewcvsrootparameter";

    private String baseURL;
    private String rootParameter;

    public ViewCvsBrowser(String baseURL, Map<String, String> params) throws MalformedURLException
    {
        // See if the URL format is correct
        if (!UrlUtils.verifyHierachicalURI(baseURL))
        {
            throw new MalformedURLException("Invalid URL '" + baseURL + "'.");
        }

        if (baseURL.endsWith("/"))
        {
            this.baseURL = baseURL;
        }
        else
        {
            this.baseURL = baseURL + "/";
        }

        // Get the parameters
        if (params != null && params.containsKey(ROOT_PARAMETER))
        {
            rootParameter = params.get(ROOT_PARAMETER);
        }

        if (rootParameter == null)
        {
            rootParameter = "";
        }
    }

    public String getFileLink(String filePath)
    {
        /// Add the "root" parameter if necessary
        return applyParameters(getBaseURL() + filePath);
    }

    public String getRevisionLink(String filePath, String revision)
    {
        final String fileLink = applyParameters(getBaseURL() + filePath);
        return applyParameter(fileLink,  "rev", revision);
    }

    public String getDiffLink(String filePath, String currentRevision)
    {
        final String fileLink = applyParameters(getBaseURL() + filePath);
        final String fileLinkWithRevision = applyParameter(fileLink, "r1", getPreviousRevision(currentRevision));
        return applyParameter(fileLinkWithRevision, "r2", currentRevision);
    }

    public String getType()
    {
        return VIEW_CVS_TYPE;
    }

    public String getBaseURL()
    {
        return baseURL;
    }

    public String getRootParameter()
    {
        return rootParameter;
    }

    private String getPreviousRevision(String revision)
    {
        List<Integer> sections = stringToList(revision);
        int lastIdx = sections.size()-1;
        int lastVal = sections.get(lastIdx);
        if (lastVal != 1)
        {
            sections.set(lastIdx, --lastVal); // eg. 1.3 to 1.2
        }
        else
        {
            // eg. 1.2.2.1 to 1.2
            sections.remove(lastIdx);
            sections.remove(lastIdx-1);
        }

        return listToString(sections);
    }

    private String listToString(final List<Integer> list)
    {
        StringBuilder str = new StringBuilder(list.size()*5);
        for (int i = 0; i < list.size(); i++)
        {
            Integer sect = list.get(i);
            str.append(sect);
            if (i < list.size() -1)
            {
                str.append(".");
            }
        }
        return str.toString();
    }

    private List<Integer> stringToList(String str)
    {
        List<Integer> l = new ArrayList<Integer>(5);
        StringTokenizer tok = new StringTokenizer(str, ".");
        while (tok.hasMoreElements())
        {
            l.add(Integer.valueOf(tok.nextToken()));
        }
        return l;
    }

    /**
     * This method applies the configured parametes to the given url and returns the new one
     *
     * @param url the url to apply parameters to.
     * @return the new URL with the parameter applied.
     */
    private String applyParameters(String url)
    {
        if (rootParameter != null && !rootParameter.equals(""))
        {
            // Set the parameter to be the default backed application property
            ApplicationProperties applicationProperties = ComponentAccessor.getApplicationProperties();
            return applyParameter(url, applicationProperties.getDefaultBackedString(APKeys.VIEWCVS_ROOT_TYPE), rootParameter);
        }
        else
        {
            return url;
        }
    }

    private String applyParameter(String url, String parameterName, String parameterValue)
    {
        StringBuilder newUrl = new StringBuilder(url);
        if (url.indexOf("?") < 0)
        {
            newUrl.append("?");
        }
        else
        {
            newUrl.append("&");
        }

        return newUrl.append(parameterName).append("=").append(parameterValue).toString();
    }
}
