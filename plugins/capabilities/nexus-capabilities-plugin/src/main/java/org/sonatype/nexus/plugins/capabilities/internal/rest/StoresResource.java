/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */

package org.sonatype.nexus.plugins.capabilities.internal.rest;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import org.sonatype.nexus.capabilities.model.SelectableEntryXO;
import org.sonatype.nexus.capability.support.CapabilitiesPlugin;
import org.sonatype.nexus.formfields.RepositoryCombobox;
import org.sonatype.nexus.proxy.access.NexusItemAuthorizer;
import org.sonatype.nexus.proxy.registry.RepositoryRegistry;
import org.sonatype.nexus.proxy.repository.Repository;
import org.sonatype.sisu.goodies.common.ComponentSupport;
import org.sonatype.sisu.siesta.common.Resource;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.codehaus.plexus.util.StringUtils;

import static com.google.common.base.Preconditions.checkNotNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;

/**
 * Stores REST resource.
 *
 * @since 2.7
 */
@Named
@Singleton
@Path(StoresResource.RESOURCE_URI)
public class StoresResource
    extends ComponentSupport
    implements Resource
{

  public static final String RESOURCE_URI = CapabilitiesPlugin.REST_PREFIX + "/stores";

  private final RepositoryRegistry repositoryRegistry;

  private final NexusItemAuthorizer nexusItemAuthorizer;

  @Inject
  public StoresResource(final RepositoryRegistry repositoryRegistry,
                        final NexusItemAuthorizer nexusItemAuthorizer)
  {
    this.repositoryRegistry = checkNotNull(repositoryRegistry);
    this.nexusItemAuthorizer = checkNotNull(nexusItemAuthorizer);
  }

  /**
   * Returns repositories filtered based on query parameters.
   */
  @GET
  @Path("/repositories")
  @Produces({APPLICATION_XML, APPLICATION_JSON})
  @RequiresPermissions("nexus:repositories:read")
  public List<SelectableEntryXO> getRepositories(
      final @QueryParam(RepositoryCombobox.REGARDLESS_VIEW_PERMISSIONS) Boolean regardlessViewPermissions,
      final @QueryParam(RepositoryCombobox.FACET) List<String> facets,
      final @QueryParam(RepositoryCombobox.CONTENT_CLASS) List<String> contentClasses)
  {
    final Predicate<Repository> predicate = Predicates.and(removeNulls(
        hasRightsToView(regardlessViewPermissions),
        anyOfFacts(facets),
        anyOfContentClasses(contentClasses)
    ));

    return Lists.transform(
        Lists.newArrayList(Iterables.filter(
            repositoryRegistry.getRepositories(),
            new Predicate<Repository>()
            {
              @Override
              public boolean apply(@Nullable final Repository input) {
                return input != null && predicate.apply(input);
              }
            }
        )),
        new Function<Repository, SelectableEntryXO>()
        {
          @Override
          public SelectableEntryXO apply(final Repository input) {
            return new SelectableEntryXO().withId(input.getId()).withName(input.getName());
          }
        });
  }

  private Predicate<Repository> hasRightsToView(final Boolean skipPermissions) {
    if (skipPermissions == null || !skipPermissions) {
      return new Predicate<Repository>()
      {
        @Override
        public boolean apply(@Nullable final Repository input) {
          return input != null && nexusItemAuthorizer.isViewable(
              NexusItemAuthorizer.VIEW_REPOSITORY_KEY, input.getId()
          );
        }
      };
    }
    return null;
  }

  private Predicate<Repository> anyOfFacts(@Nullable final List<String> facets) {
    if (facets != null && !facets.isEmpty()) {
      List<Predicate<Repository>> predicates = Lists.newArrayList();
      for (String facet : facets) {
        if (StringUtils.isNotEmpty(facet)) {
          try {
            final Class<?> facetClass = Class.forName(facet);
            predicates.add(new Predicate<Repository>()
            {
              @Override
              public boolean apply(@Nullable final Repository input) {
                return input != null && input.getRepositoryKind().isFacetAvailable(facetClass);
              }
            });
          }
          catch (ClassNotFoundException e) {
            log.warn(
                "Repositories with facet {} will not be available for selection as facet class could not be loaded",
                facet
            );
          }
        }
      }
      if (!predicates.isEmpty()) {
        return Predicates.or(predicates);
      }
    }
    return null;
  }

  private Predicate<Repository> anyOfContentClasses(final List<String> contentClasses) {
    if (contentClasses != null && !contentClasses.isEmpty()) {
      List<Predicate<Repository>> predicates = Lists.newArrayList();
      for (final String contentClass : contentClasses) {
        if (StringUtils.isNotEmpty(contentClass)) {
          predicates.add(new Predicate<Repository>()
          {
            @Override
            public boolean apply(@Nullable final Repository input) {
              return input != null && input.getRepositoryContentClass().getId().equals(contentClass);
            }
          });
        }
      }
      if (!predicates.isEmpty()) {
        return Predicates.or(predicates);
      }
    }
    return null;
  }

  private static <T> Iterable<T> removeNulls(final T... values) {
    return removeNulls(Arrays.asList(values));
  }

  private static <T> Iterable<T> removeNulls(final Iterable<T> values) {
    return Iterables.filter(values, Predicates.notNull());
  }

}
