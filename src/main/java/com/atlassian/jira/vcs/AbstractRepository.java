/*
 * Copyright (c) 2002-2004
 * All rights reserved.
 */

package com.atlassian.jira.vcs;

import com.opensymphony.module.propertyset.PropertySet;

public abstract class AbstractRepository implements Repository
{
    protected Long id;
    protected String name;
    protected String description;
    protected String updateDelay;
    private RepositoryBrowser repositoryBrowser;
    public static final String KEY_NAME = "name";
    public static final String KEY_DESCRIPTION = "description";

    public AbstractRepository(final PropertySet propertySet)
    {
        this.name = propertySet.getString(KEY_NAME);
        this.description = propertySet.getString(KEY_DESCRIPTION);
    }

    public AbstractRepository()
    {
    }

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getUpdateDelay()
    {
        return updateDelay;
    }

    public void setUpdateDelay(String updateDelay)
    {
        this.updateDelay = updateDelay;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getDescription()
    {
        return description;
    }

    public void setRepositoryBrowser(RepositoryBrowser repositoryBrowser)
    {
        this.repositoryBrowser = repositoryBrowser;
    }

    public RepositoryBrowser getRepositoryBrowser()
    {
        return repositoryBrowser;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public String toString()
    {
        return "id=" + getId() + ",name=" + getName() + ",description=" + getDescription() + ",delay=" + getUpdateDelay();
    }

    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (!(o instanceof AbstractRepository))
        {
            return false;
        }

        final AbstractRepository abstractRepository = (AbstractRepository) o;

        if (description != null ? !description.equals(abstractRepository.description) : abstractRepository.description != null)
        {
            return false;
        }
        if (!id.equals(abstractRepository.id))
        {
            return false;
        }
        if (!name.equals(abstractRepository.name))
        {
            return false;
        }
        if (repositoryBrowser != null ? !repositoryBrowser.equals(abstractRepository.repositoryBrowser) : abstractRepository.repositoryBrowser != null)
        {
            return false;
        }
        if (updateDelay != null ? !updateDelay.equals(abstractRepository.updateDelay) : abstractRepository.updateDelay != null)
        {
            return false;
        }

        return true;
    }

    public int hashCode()
    {
        int result;
        result = id.hashCode();
        result = 29 * result + name.hashCode();
        result = 29 * result + (description != null ? description.hashCode() : 0);
        result = 29 * result + (updateDelay != null ? updateDelay.hashCode() : 0);
        result = 29 * result + (repositoryBrowser != null ? repositoryBrowser.hashCode() : 0);
        return result;
    }

    public int compareTo(Repository repository)
    {
        if (repository == null)
        {
            return 1;
        }
        if (getName() == null)
        {
            if (repository.getName() == null)
            {
                return 0;
            }
            else
            {
                return -1;
            }
        }
        else
        {
            if (repository.getName() == null)
            {
                return 1;
            }
            else
            {
                return getName().compareTo(repository.getName());
            }
        }
    }
}
