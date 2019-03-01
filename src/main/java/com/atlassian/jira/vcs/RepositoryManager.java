/*
 * Copyright (c) 2002-2004
 * All rights reserved.
 */

package com.atlassian.jira.vcs;

import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.collect.CollectionBuilder;
import com.atlassian.jira.vcs.cvsimpl.CVSCommit;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.opensymphony.module.propertyset.PropertySet;
import org.ofbiz.core.entity.GenericEntityException;
import org.ofbiz.core.entity.GenericValue;

import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * The RepositoryManager is used to interface with JIRA's CVS integration. Through this manager you can create,
 * retrieve, update, and delete CVS repository references. You can also associate/unassociate repositories with
 * JIRA projects.
 * <p/>
 *
 * <strong>NOTE:</strong> JIRA also integrates with Perforce and Subversion repositories but this integration
 * is done via JIRA's plugin framework and therefore does not utilize this manager.
 */
public interface RepositoryManager
{
    @ComponentImport
    public static final String CVS_TYPE = "cvs";
    public static final Collection<String> VCS_TYPES = CollectionBuilder.newBuilder(CVS_TYPE).asSet();
    public static final String VCS_SERVICE_NAME = "CVS Update Job";
    public static final long VCS_SERVICE_DELAY = 60 * 60 * 1000L;

    /**
     * This method will return a list of {@link Repository}s that are associated with the provided project.
     *
     * @param project a GenericValue representing the project associated with the repositories you would like to
     * retrieve.
     * @return collection of {@link Repository}'s.
     *
     * @throws GenericEntityException if there is a problem accessing the data store.
     */
    public Collection<Repository> getRepositoriesForProject(GenericValue project) throws GenericEntityException;

    /**
     * This method will associate the provided repositories with the provided project. The provided repository
     * id's must reference valid existing repository instances. Associating a repository with a project causes
     * JIRA to show relevant commit messages on the projects CVS issue tab panel.
     *
     * @param project a GenericValue representing the project to associate the repositories to.
     * @param repositoryIds a list of {@link Long}'s representing the {@link com.atlassian.jira.vcs.Repository#getId()}
     * of the repositories you would like to associate with the project.
     *
     * @throws GenericEntityException thrown if a repository id can not be resolved.
     */
    public void setProjectRepositories(GenericValue project, Collection<Long> repositoryIds) throws GenericEntityException;

    /**
     * This method allows you to find the projects that have been associated with a given {@link Repository}.
     *
     * @param repository represents the repository associated with the collection of projects returned.
     * @return a collection of {@link GenericValue}s that represent the projects the provided repository are
     * associated with.
     *
     * @throws GenericEntityException thrown if there is trouble accessing the datastore.
     */
    public Collection<Project> getProjectsForRepository(Repository repository) throws GenericEntityException;

    /**
     * This method will return all {@link Repository}'s that exist within JIRA.
     *
     * @return a collection of {@link Repository} objects.
     */
    public Collection<Repository> getRepositories();

    /**
     * Retrieves the {@link Repository} by its {@link com.atlassian.jira.vcs.Repository#getId()}. If the repository
     * is not found in the datastore a RuntimeException will be generated.
     *
     * @param id represents the repositories id.
     * @return a {@link Repository} object as found by id.
     *
     * @throws GenericEntityException thrown if there is trouble accessing the datastore.
     */
    public Repository getRepository(Long id) throws GenericEntityException;

    /**
     * Retrieves the {@link Repository} by its {@link com.atlassian.jira.vcs.Repository#getName()}. If the repository
     * is not found then this will return null.
     *
     * @param name represents the repositories name.
     * @return the {@link Repository} with the given name if one is found, null otherwise.
     */
    public Repository getRepository(String name);

    /**
     * Validates if a {@link com.atlassian.jira.vcs.Repository#getType()} is valid for this RepositoryManager.
     *
     * @param type is a repository type (e.g. {@link RepositoryManager#CVS_TYPE})
     * @return true if the passed type is in the {@link RepositoryManager#VCS_TYPES}, false otherwise.
     */
    public boolean isValidType(String type);

    /**
     * This method will remove the {@link Repository} specified by id from the datastore and it will remove all
     * associations that may exist from the repository to projects.
     *
     * @param id represents the repositories id.
     *
     * @throws Exception thrown if there is trouble removing the values from the datastore.
     */
    void removeRepository(Long id) throws Exception;

    /**
     * Updates the properties and attributes of the {@link Repository} with the given id. All passed in values
     * will replace any previously existing values. For example, if you would like to change the description, but
     * not the name you should pass in all the existing attributes and properties as well as the new description.
     *
     * @param id represents the repositories id.
     * @param type represents the type to update the repository to.
     * @param name represents the name to update the repository to.
     * @param description represents the description to update the repository to.
     * @param properties defines the properties to update on the repository.
     *
     * @throws GenericEntityException thrown if the {@link Repository} can not be found by id or if there is trouble
     * accessing the datastore.
     */
    void updateRepository(Long id, String type, String name, String description, Properties properties) throws GenericEntityException;

    /**
     * Tries to update (e.g. cvs up) all the repositories in the system.
     *
     * @return true if <strong>ALL</strong> repositories were updated without a problem, false if an exception occurred.
     *
     * @throws GenericEntityException throw if there is trouble accessing the datastore.
     */
    public boolean updateRepositories() throws GenericEntityException;

    /**
     * Creates a new {@link Repository} with the provided attributes and properties and creates a
     * {@link com.atlassian.jira.service.services.vcs.VcsService} if one does not yet exist.
     *
     * @param type the type of the Repository, this should be {@link RepositoryManager#CVS_TYPE}.
     * @param name the name of the Repository.
     * @param description the description of the Repository.
     * @param properties are properties that are essential for the repository to work, such as
     * {@link com.atlassian.jira.vcs.viewcvs.ViewCvsBrowser#ROOT_PARAMETER} and
     * {@link com.atlassian.jira.vcs.viewcvs.ViewCvsBrowser#KEY_BASE_URL}.
     * @return the {@link Repository} object representation of the newly created repository.
     *
     * @throws Exception thrown if there is a problem creating the repository, this could be caused by trouble accessing
     * the datastore, or trouble creating the {@link com.atlassian.jira.service.services.vcs.VcsService}.
     */
    public Repository createRepository(String type, String name, String description, Properties properties) throws Exception;

    /**
     * This will clear the repository cache and load all {@link Repository}'s from the datastore.
     *
     * @throws GenericEntityException thrown if there is trouble accessing the datastore.
     */
    public void refresh() throws GenericEntityException;

    /**
     * Retrieves a {@link PropertySet} for a repository provided the repositories {@link GenericValue}. The property
     * set stores values such as {@link com.atlassian.jira.vcs.viewcvs.ViewCvsBrowser#ROOT_PARAMETER} and
     * {@link com.atlassian.jira.vcs.viewcvs.ViewCvsBrowser#KEY_BASE_URL}.
     *
     * @param versionControlGV the generic value representation of the repository.
     * @return the {@link PropertySet} for the specified repository.
     */
    public PropertySet getPropertySet(GenericValue versionControlGV);

    /**
     * Retrieves all of the commits for this {@link Issue} from <strong>ALL</strong> of the repositories associated
     * with the issue's project.  It will only show commits if the user has the
     * {@link com.atlassian.jira.security.Permissions#VIEW_VERSION_CONTROL} permission.
     * <p>
     * Returns a map of (repository id -> Set of {@link com.atlassian.jira.vcs.cvsimpl.CVSCommit}):
     * <p>
     * If the map is empty, there are no associated repositories for the issue and user.
     * If the List of {@link com.atlassian.jira.vcs.cvsimpl.CVSCommit} is null,
     * then the vcs log has not yet been parsed for that repository.
     *
     * @param issue is the issue which identifies the project which should be used to find {@link Repository}'s.
     * @param remoteUser is the user who's permissions will determine which commits are visible. This is the user
     * who is making the browse request.
     * @return map of (repository id -> List of {@link com.atlassian.jira.vcs.cvsimpl.CVSCommit})
     */
    public Map<Long, Set<CVSCommit>> getCommits(Issue issue, ApplicationUser remoteUser);
}
