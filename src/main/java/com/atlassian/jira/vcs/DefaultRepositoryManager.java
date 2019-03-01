/*
 * Copyright (c) 2002-2004
 * All rights reserved.
 */

package com.atlassian.jira.vcs;

import com.atlassian.core.ofbiz.util.OFBizPropertyUtils;
import com.atlassian.core.util.map.EasyMap;
import com.atlassian.crowd.embedded.api.User;
import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.jira.association.NodeAssociationStore;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.entity.EntityUtils;
import com.atlassian.jira.event.ClearCacheEvent;
import com.atlassian.jira.exception.DataAccessException;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.changehistory.ChangeHistoryManager;
import com.atlassian.jira.ofbiz.OfBizDelegator;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectFactory;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.project.ProjectRelationConstants;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.security.Permissions;
import com.atlassian.jira.service.services.vcs.VcsService;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.JiraKeyUtils;
import com.atlassian.jira.util.LockException;
import com.atlassian.jira.util.ObjectUtils;
import com.atlassian.jira.vcs.cvsimpl.CVSCommit;
import com.atlassian.jira.vcs.cvsimpl.CvsRepository;
import com.atlassian.jira.vcs.cvsimpl.CvsRepositoryUtil;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import com.atlassian.sal.api.scheduling.PluginScheduler;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.opensymphony.module.propertyset.PropertySet;
import net.sf.statcvs.input.LogSyntaxException;
import net.sf.statcvs.model.Commit;
import org.apache.log4j.Logger;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.ofbiz.core.entity.EntityUtil;
import org.ofbiz.core.entity.GenericEntityException;
import org.ofbiz.core.entity.GenericValue;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.annotation.Nullable;


public class DefaultRepositoryManager implements RepositoryManager, InitializingBean, DisposableBean, LifecycleAware
{
    private static final Logger log = Logger.getLogger(DefaultRepositoryManager.class);
    private final NodeAssociationStore nodeAssociationStore;
    private final PluginScheduler pluginScheduler;
    private PermissionManager permissionManager;
    private final ChangeHistoryManager changeHistoryManager;
    private final ProjectManager projectManager;
    private final ProjectFactory projectFactory;
    private final OfBizDelegator ofBizDelegator;
    private final EventPublisher eventPublisher;
    private final CvsRepositoryUtil cvsRepositoryUtil;
    private final Map<Long, Repository> repositories;


    @Autowired
    public DefaultRepositoryManager(OfBizDelegator ofBizDelegator,
            PluginScheduler pluginScheduler, PermissionManager permissionManager,
            ChangeHistoryManager changeHistoryManager, ProjectManager projectManager,
            final ProjectFactory projectFactory, EventPublisher eventPublisher,@ComponentImport CvsRepositoryUtil cvsRepositoryUtil) throws GenericEntityException
    {
        this.projectFactory = projectFactory;
        this.cvsRepositoryUtil = cvsRepositoryUtil;
        this.nodeAssociationStore = ComponentAccessor.getComponent(NodeAssociationStore.class);
        this.ofBizDelegator = ofBizDelegator;
        this.pluginScheduler = pluginScheduler;
        this.permissionManager = permissionManager;
        this.changeHistoryManager = changeHistoryManager;
        this.projectManager = projectManager;
        this.eventPublisher = eventPublisher;

        // Initialize cache
        this.repositories = new HashMap<Long, Repository>();

        // Load the cache with all the repositories
        refresh();
    }


    @SuppressWarnings ({ "UnusedDeclaration" })
    @EventListener
    public void onClearCache(final ClearCacheEvent event)
    {
        try
        {
            refresh();
        }
        catch (GenericEntityException e)
        {
            throw new DataAccessException(e);
        }
    }

    private void loadRepositories()
    {
        @SuppressWarnings ({ "unchecked" }) List<GenericValue> vcsRepositories = ofBizDelegator.findAll("VersionControl");

        if (vcsRepositories == null)
        {
            return;
        }

        for (final GenericValue vcsRepository : vcsRepositories)
        {
            if (vcsRepository != null)
            {
                getRepository(vcsRepository.getLong("id"));
            }
        }
    }

    public Collection<Repository> getRepositoriesForProject(GenericValue project) throws GenericEntityException
    {
        if (project == null)
        {
            throw new IllegalArgumentException("Tried to get repository for null project");
        }

        if (!"Project".equals(project.getEntityName()))
            throw new IllegalArgumentException("getProviderForProject called with an entity of type '" + project.getEntityName() + "' - which is not a project");

        List<GenericValue> vcsRepositories = nodeAssociationStore.getSinksFromSource(project, "VersionControl", ProjectRelationConstants.PROJECT_VERSIONCONTROL);

        if (vcsRepositories == null)
        {
            return Collections.emptyList();
        }
        else if (vcsRepositories.size() >= 1)
        {
            List<Repository> repositories = new ArrayList<Repository>();
            for (GenericValue vcsRepository : vcsRepositories)
            {
                Repository repository = getRepository(vcsRepository.getLong("id"));
                repositories.add(repository);
            }

            return repositories;
        }
        else
        {
            log.debug("No repository defined for project '" + project.getString("name") + "'");
            return Collections.emptyList();
        }
    }

    public Collection<Project> getProjectsForRepository(Repository repository) throws GenericEntityException
    {
        if (repository == null)
        {
            throw new IllegalArgumentException("Tried to get projects for null repository");
        }

        // Retrieve the generic value for the repository
        final GenericValue repositoryGV = getRepositoryGV(repository.getId());

        @SuppressWarnings ({ "unchecked" }) List<GenericValue> projectGVs = nodeAssociationStore.getSourcesFromSink(repositoryGV, "Project", ProjectRelationConstants.PROJECT_VERSIONCONTROL);

        if (projectGVs == null || projectGVs.isEmpty())
        {
            log.debug("No projects defined for repository '" + repository.getName() + "'.");
            return Collections.emptyList();
        }

        return Collections2.transform(projectGVs, new Function<GenericValue, Project>()
        {
            @Override
            public Project apply(@Nullable final GenericValue projectGV)
            {
                return projectFactory.getProject(projectGV);
            }
        });
    }

    /**
     * Retrieves the repository by name
     *
     * @param name name of the repository
     * @return the repository with the given name if one is found, null otherwise
     */
    public Repository getRepository(String name)
    {
        for (Repository repository : getRepositories())
        {
            if (name == null)
            {
                if (repository.getName() == null)
                {
                    return repository;
                }
            }
            else if (name.equals(repository.getName()))
            {
                return repository;
            }
        }

        return null;
    }

    public Repository getRepository(Long id)
    {
        // Check the cache
        if (repositories.containsKey(id))
        {
            // If we have the repository cached return the cached one
            return repositories.get(id);
        }

        // The repository is not cached, retrieve it from the database
        final GenericValue versionControlGV = getRepositoryGV(id);
        final Repository repository = getRepository(versionControlGV);

        // Cache the repository
        repositories.put(id, repository);
        return repository;
    }

    private GenericValue getRepositoryGV(Long id)
    {
        return EntityUtil.getOnly(ofBizDelegator.findByAnd("VersionControl", EasyMap.build("id", id)));
    }

    /**
     * Retrieve a Repository for the given VersionControl gv
     *
     * @param versionControlGV version control generic value
     * @return new repository object created from the values of the given generic value
     */
    private Repository getRepository(GenericValue versionControlGV)
    {
        if (CVS_TYPE.equals(versionControlGV.getString("type")))
        {
            PropertySet cvsPropertySet = getPropertySet(versionControlGV);
            CvsRepository cvsRepo = new CvsRepository(cvsPropertySet, cvsRepositoryUtil);
            cvsRepo.setId(versionControlGV.getLong("id"));
            cvsRepo.setName(versionControlGV.getString("name"));
            cvsRepo.setDescription(versionControlGV.getString("description"));

            return cvsRepo;
        }
        else
        {
            throw new IllegalArgumentException("Unknown repository type '" + versionControlGV.getString("type") + "'");
        }
    }

    public PropertySet getPropertySet(GenericValue versionControlGV)
    {
        return OFBizPropertyUtils.getPropertySet(versionControlGV);
    }

    public boolean isValidType(String type)
    {
        return VCS_TYPES.contains(type);
    }

    public Map<Long, Set<CVSCommit>> getCommits(Issue issue, ApplicationUser applicationUser)
    {
        if (issue == null)
        {
            throw new IllegalArgumentException("Issue cannot be null.");
        }

        if (!permissionManager.hasPermission(Permissions.VIEW_VERSION_CONTROL, issue,  applicationUser))
        {
            // If the user does not have the required permission, do not return any information.
            return Collections.emptyMap();
        }

        return getAllCommitsInAllIssueRepositories(issue);
    }

    /**
     * Adds all commits for the given issue.  This means going through previous issue keys and getting all the
     * commits for those issues, even for repositories that the prvs issue's projects may have been linked to.
     *
     * Note that if the previousIssueKeys returns an empty list (which will be the case most of the times), no
     * additional work is done.
     *
     * @param issue the issue to get commits for
     * @return Returns a map of (repository id -> Set of {@link com.atlassian.jira.vcs.cvsimpl.CVSCommit}) with all
     * the passed issue's associated commits.
     */
    private Map<Long, Set<CVSCommit>> getAllCommitsInAllIssueRepositories(Issue issue)
    {
        Collection<String> previousIssueKeys = changeHistoryManager.getPreviousIssueKeys(issue.getId());
        Set<Repository> repositories = getAllRepositories(issue, previousIssueKeys);

        Collection<String> allIssueKeys = new LinkedHashSet<String>();
        allIssueKeys.addAll(previousIssueKeys);
        allIssueKeys.add(issue.getKey());

        Map<Long, Set<CVSCommit>> repositoryCommits = new HashMap<Long, Set<CVSCommit>>();
        //for each repository, check for commits for each issue key.
        for (Repository repository : repositories)
        {
            for (String issueKey : allIssueKeys)
            {
                mapCommitsToRepository(repository, issueKey, repositoryCommits);
            }
        }

        return repositoryCommits;
    }

    private Set<Repository> getAllRepositories(Issue issue, Collection<String> previousIssueKeys)
    {
        Set<Repository> repositories = new HashSet<Repository>();
        try
        {
            repositories.addAll(getRepositoriesForProject(issue.getProject()));

            //lookup the repositories for each previous issuekey
            for (String issueKey : previousIssueKeys)
            {
                String projectKey = JiraKeyUtils.getProjectKeyFromIssueKey(issueKey);
                Project project = projectManager.getProjectObjByKey(projectKey);
                //the project may no longer exist.
                if (project != null)
                {
                    Collection<Repository> repos = getRepositoriesForProject(project.getGenericValue());
                    if (repos != null)
                    {
                        repositories.addAll(repos);
                    }
                }
            }
        }
        catch (GenericEntityException e)
        {
            log.error("Error retrieving project repositories",e);
        }
        return repositories;
    }

    private void mapCommitsToRepository(Repository repository, String issueKey, Map<Long, Set<CVSCommit>> repositoryCommits)
    {
        try
        {
            List<Commit> coms = repository.getCommitsForIssue(issueKey);
            mergeCommitsForRepository(repositoryCommits, repository, coms);
        }
        catch (RepositoryException e)
        {
            log.error("Error while retrieving commits from the repository", e);
        }
    }

    private void mergeCommitsForRepository(Map<Long, Set<CVSCommit>> repositoryCommits, Repository repository, List<Commit> coms)
    {
        final Long repositoryId = repository.getId();
        //there's no mapping yet for this repository.
        if(!repositoryCommits.containsKey(repositoryId) || repositoryCommits.get(repositoryId) == null)
        {
            repositoryCommits.put(repositoryId, transformCommits(repository, coms));
        }
        else
        {
            final Collection<CVSCommit> commits = repositoryCommits.get(repositoryId);
            final Set<CVSCommit> cvsCommits = transformCommits(repository, coms);
            if(cvsCommits != null)
            {
                commits.addAll(cvsCommits);
            }
        }
    }

    private Set<CVSCommit> transformCommits(Repository repository, List<Commit> coms)
    {
        if(coms == null)
        {
            //the commit log has not yet been parsed for this repository
            return null;
        }

        //actually a set doesn't really help here, because the underlying Commit in CVSCommit doesn't provide equals().
        // Using a set anyway just in the hope that Commit will implement this at some stage in the future.
        final Set<CVSCommit> commits = new HashSet<CVSCommit>();
        for (Commit com : coms)
        {
            commits.add(new CVSCommit(com, repository));
        }
        return commits;
    }

    public Repository createRepository(String type, String name, String description, Properties properties) throws Exception
    {
        if (CVS_TYPE.equals(type))
        {
            HashMap<String, Object> fields = new HashMap<String, Object>();
            fields.put("type", type);
            fields.put("name", name);
            fields.put("description", description);

            GenericValue repositoryRecord = EntityUtils.createValue("VersionControl", fields);

            PropertySet propertySet = OFBizPropertyUtils.getPropertySet(repositoryRecord);

            persistProperties(properties, propertySet);

            // Ensure that we do not modify the repositries map by more than
            // one thread at a time
            synchronized (repositories)
            {
                // If this is the first repository create the VCS Update Service
                if (repositories.isEmpty())
                {
                    createRepositoryUpdateService();
                }

                // Load the repository into the cache
                return getRepository(repositoryRecord.getLong("id"));
            }
        }
        else
        {
            throw new IllegalArgumentException("Unhandled VCS provider type " + type);
        }
    }

    private void persistProperties(Properties properties, PropertySet propertySet)
    {
        for (final Object o : properties.keySet())
        {
            String key = (String) o;
            propertySet.setString(key, properties.getProperty(key));
        }
    }

    private void createRepositoryUpdateService()
    {
        final RepositoryManager repositoryManager = (RepositoryManager) this;
        pluginScheduler.scheduleJob(VCS_SERVICE_NAME, VcsService.class, new HashMap<String, Object>() {{
            put(VcsService.REPOSITORY_MANAGER_KEY, repositoryManager); }}, new Date(), VCS_SERVICE_DELAY);
    }

    private void removeRepositoryUpdateService()
    {
        try {
            pluginScheduler.unscheduleJob(VCS_SERVICE_NAME);
        } catch (IllegalArgumentException e) {
            // Swallow illegal argument exception in case this service has already been removed.
        }
    }

    /**
     * Updates the repository with the given id. The values are updated based on the properties
     *
     * @param id          repository id
     * @param type        VCS type, this method only supports {@link #CVS_TYPE}
     * @param name        VCS name
     * @param description VCS description
     * @param properties  new properties to store
     */
    public void updateRepository(Long id, String type, String name, String description, Properties properties) throws GenericEntityException
    {
        // Check if we can handle this repository type
        if (!CVS_TYPE.equals(type))
        {
            throw new IllegalArgumentException("Unhandled VCS provider type " + type);
        }

        // Get the version control record
        final GenericValue versionControlGV = getVersionControlGV(id);
        final PropertySet cvsPropertySet = OFBizPropertyUtils.getPropertySet(versionControlGV);

        // Check if the properties that mean different CVS repository are set
        final boolean isDiffrentRepository = isDifferentRepository(cvsPropertySet, properties);

        final Repository oldRepository = getRepository(id);

        // Check if the repository is in cache (actually it should be at all times - but just to be safe)
        if (repositories.containsKey(id))
        {
            // Remove the repository from cache
            repositories.remove(id);
        }

        // Update the record attributes
        versionControlGV.set("type", type);
        versionControlGV.set("name", name);
        versionControlGV.set("description", description);
        versionControlGV.store();

        // Set the variable attributes
        // Save the new properties
        persistProperties(properties, cvsPropertySet);

        // Get existing KeySet - create new collection as we will modify it
        final Collection<String> keys = new LinkedList<String>(cvsPropertySet.getKeys());

        // Workout the difference between the sets
        keys.removeAll(keys);

        // Remove all the old keys that are not present any more
        for (String key : keys)
        {
            cvsPropertySet.remove(key);
        }

        // Load the repository into the cache
        final Repository repository = getRepository(id);

        if (!isDiffrentRepository)
        {
            // update this repository with old content
            repository.copyContent(oldRepository);
        }
        if (isDiffrentRepository)
        {
            markVcsServiceToRun();
        }
    }

    /**
     * Returns version control generic value
     *
     * @param id version control id
     * @return version control generic value, never null
     * @throws GenericEntityException if cannot get version control, or null
     */
    private GenericValue getVersionControlGV(Long id) throws GenericEntityException
    {
        GenericValue versionControlGV = getRepositoryGV(id);
        if (versionControlGV == null)
        {
            throw new GenericEntityException("Could not find VersionControl with id '" + id + "'.");
        }
        return versionControlGV;
    }

    /**
     * Checks if any of the new properties values will require a new repository to be created.
     * Returns true if the values differ in {@link CvsRepository#KEY_MODULE_NAME},
     * {@link CvsRepository#KEY_PASSWORD}, {@link CvsRepository#KEY_CVS_ROOT} or
     * {@link CvsRepository#KEY_FETCH_LOG} values.
     *
     * @param oldPropertySet old properties
     * @param newProperties  new properties
     * @return true if requires new repository based on the difference in property values, false otherwise
     */
    protected boolean isDifferentRepository(PropertySet oldPropertySet, Properties newProperties)
    {
        return !equals(CvsRepository.KEY_MODULE_NAME, oldPropertySet, newProperties)
                || !equals(CvsRepository.KEY_PASSWORD, oldPropertySet, newProperties)
                || !equals(CvsRepository.KEY_CVS_ROOT, oldPropertySet, newProperties)
                || !equals(CvsRepository.KEY_FETCH_LOG, oldPropertySet, newProperties);
    }

    /**
     * Gets and compares the values of given propety between old and new properties
     *
     * @param propertyName   property name
     * @param oldPropertySet old properties
     * @param newProperties  new properties
     * @return true if they are equal or both null
     */
    protected static boolean equals(String propertyName, PropertySet oldPropertySet, Properties newProperties)
    {
        return ObjectUtils.equalsNullSafe(oldPropertySet.getString(propertyName), newProperties.getProperty(propertyName));
    }

    /**
     * Adds VCS service to the schedule skipper of the service manager.
     * The service is only added if is usable.
     */
    protected void markVcsServiceToRun()
    {
        removeRepositoryUpdateService();
        createRepositoryUpdateService();
    }

    public void removeRepository(Long id) throws Exception
    {
        final GenericValue versionControlGV = getRepositoryGV(id);

        // Remove all the properties for this entry
        OFBizPropertyUtils.removePropertySet(versionControlGV);

        // Remove the record
        versionControlGV.remove();

        // Remove the repository from cache
        repositories.remove(id);

        // Check if this is the last repository, If so remove the VCS Update Service
        if (repositories.isEmpty())
        {
            removeRepositoryUpdateService();
        }
    }

    /**
     * Given a project and a collection of repository ids, associates the project with the repositories that have the given ids
     * NOTE: the old associations are removed
     *
     * @param project       project generic value
     * @param repositoryIds collection of repository ids &lt;Long&gt;
     * @throws GenericEntityException if at least one repository with the given id does not exist
     */
    public void setProjectRepositories(final GenericValue project, final Collection<Long> repositoryIds)
            throws GenericEntityException
    {
        List<GenericValue> newRepositories = new ArrayList<GenericValue>();

        // Iterate over the list of given ids and see if they all exist
        for (Long repositoryId : repositoryIds)
        {
            GenericValue vcEntityList = getRepositoryGV(repositoryId);
            if (vcEntityList == null)
            {
                // If the id is not present, throw an exception
                throw new GenericEntityException("Could not set project's repository; no VersionControl record with id '" + repositoryId + "'");
            }

            newRepositories.add(vcEntityList);
        }

        setProjectRepositories(project, newRepositories);
    }

    /**
     * Given a project and a collection of repositories, associates the project with the given repositories
     * NOTE: the old associations are removed
     *
     * @param project          project generic value
     * @param newRepositoryGVs list of repository generic values
     * @throws GenericEntityException if error occurs
     */
    private void setProjectRepositories(final GenericValue project, List<GenericValue> newRepositoryGVs)
            throws GenericEntityException
    {
        // Remove existing project to repository associations
        // That is, de-associate the project from the repositories it is currently associated with
        @SuppressWarnings ({ "unchecked" })
        List<GenericValue> oldAssociations = nodeAssociationStore.getSinksFromSource(project, "VersionControl", ProjectRelationConstants.PROJECT_VERSIONCONTROL);
        for (GenericValue oldAssociation : oldAssociations)
        {
            nodeAssociationStore.removeAssociation(project, oldAssociation, ProjectRelationConstants.PROJECT_VERSIONCONTROL);
        }

        // Create new project to repository associations
        for (GenericValue repoEntity : newRepositoryGVs)
        {
            nodeAssociationStore.createAssociation(project, repoEntity, ProjectRelationConstants.PROJECT_VERSIONCONTROL);
        }
    }

    /**
     * @return A List of all {@link Repository} objects in the system.
     */
    public Collection<Repository> getRepositories()
    {
        return repositories.values();
    }

    public boolean updateRepositories() throws GenericEntityException
    {
        boolean exception = true;

        // Get all the repositories and update them
        final Collection<Repository> repositories = getRepositories();
        for (Repository repository : repositories)
        {
            try
            {
                updateRepository(repository);
            }
            catch (CommandException e)
            {
                log.error("Error occurred while updating repository '" + repository.getName() + "': " + e.getMessage(), e);
                exception = false;
            }
            catch (AuthenticationException e)
            {
                log.error("Error occurred while updating repository '" + repository.getName() + "': " + e.getMessage(), e);
                Throwable cause = e.getUnderlyingThrowable();
                if (cause != null)
                {
                    log.error("Caused by: " + cause.getMessage(), cause);
                }
                exception = false;
            }
            catch (IOException e)
            {
                log.error("Error occurred while updating repository '" + repository.getName() + "': " + e.getMessage(), e);
                exception = false;
            }
            catch (LogSyntaxException e)
            {
                log.error("Error occurred while updating repository '" + repository.getName() + "': " + e.getMessage(), e);
                exception = false;
            }
            catch (LockException e)
            {
                log.error("Error occurred while updating repository '" + repository.getName() + "': " + e.getMessage(), e);
                exception = false;
            }
        }

        return exception;
    }

    protected boolean updateRepository(Repository repository) throws CommandException, AuthenticationException, IOException, LogSyntaxException, LockException
    {
        // update if this is a cvs repository
        if (RepositoryManager.CVS_TYPE.equals(repository.getType()))
        {
            // Check if this repository is associated with at least one project
            try
            {
                if (!getProjectsForRepository(repository).isEmpty())
                {
                    log.debug("Updating repository '" + repository.getName() + "'...");
                    CvsRepository cvsRepository = (CvsRepository) repository;
                    cvsRepository.updateRepository();
                    log.debug("Finished updating repository '" + repository.getName() + "'.");
                    return true;
                }
                else
                {
                    log.debug("No projects are associated with repository '" + repository.getName() + "' - not updating.");
                }
            }
            catch (GenericEntityException e)
            {
                log.error("Error occurred while retrieving projects for repository '" + repository.getName() + "' - not updating.");
            }
        }
        else
        {
            log.debug("Repository '" + repository.getName() + "' is not CVS repository - not updating.");
        }

        return false;
    }

    public void refresh() throws GenericEntityException
    {
        repositories.clear();
        loadRepositories();
    }

    @Override
    public void afterPropertiesSet() throws Exception
    {
        eventPublisher.register(this);
    }

    @Override
    public void destroy() throws Exception
    {
        eventPublisher.unregister(this);
        removeRepositoryUpdateService();
    }

    @Override
    public void onStart()
    {
        // We'll reschedule the plugin job if necessary.
        try
        {
            refresh();
        }
        catch (GenericEntityException e)
        {
        }
        if (!repositories.isEmpty())
        {
            markVcsServiceToRun();
        }
    }

}
