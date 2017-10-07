/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2017 MicroBean.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.microbean.helm.chart;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.beans.SimpleBeanInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.zafarkhaja.semver.Parser;
import com.github.zafarkhaja.semver.Version;

import com.github.zafarkhaja.semver.expr.Expression;
import com.github.zafarkhaja.semver.expr.ExpressionParser;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;

import hapi.chart.ChartOuterClass.Chart;
import hapi.chart.ChartOuterClass.ChartOrBuilder;
import hapi.chart.ConfigOuterClass.Config;
import hapi.chart.ConfigOuterClass.ConfigOrBuilder;
import hapi.chart.MetadataOuterClass.Metadata;
import hapi.chart.MetadataOuterClass.MetadataOrBuilder;

import org.yaml.snakeyaml.Yaml;

import org.yaml.snakeyaml.constructor.Constructor;

import org.yaml.snakeyaml.introspector.PropertyUtils;

/*
 * TODO: tests tests tests
 *
 * If you call toBuilder() on a Chart, and then say, for example,
 * getDependenciesBuilderList(), do you get subchart Chart.Builders
 * for each subchart?
 */

public class Requirements {

  private static final Pattern commaSplitPattern = Pattern.compile("\\s*,\\s*");
  
  private static final Constructor requirementsConstructor = new Constructor(Requirements.class);
  
  private Map<String, Dependency> dependenciesByName;

  public Requirements() {
    super();
  }

  public final boolean isEmpty() {
    return this.dependenciesByName == null || this.dependenciesByName.isEmpty();
  }

  public Collection<Dependency> getDependencies() {
    final Collection<Dependency> returnValue;
    if (this.dependenciesByName == null) {
      returnValue = null;
    } else {
      returnValue = this.dependenciesByName.values();
    }
    return returnValue;
  }

  public void setDependencies(final Collection<Dependency> dependencies) {
    if (dependencies == null) {
      this.dependenciesByName = null;
    } else if (dependencies.isEmpty()) {
      this.dependenciesByName = Collections.emptyMap();
    } else {
      this.dependenciesByName = new HashMap<>();
      for (final Dependency dependency : dependencies) {
        if (dependency != null) {
          final String name = dependency.getName();
          if (name != null) {
            this.dependenciesByName.put(name, dependency);
          }
        }
      }
    }
  }

  public Dependency getDependency(final String name) {
    final Dependency dependency;
    if (this.dependenciesByName == null) {
      dependency = null;
    } else {
      dependency = this.dependenciesByName.get(name);
    }
    return dependency;
  }

  private final void enableAll() {
    final Collection<Dependency> dependencies = this.getDependencies();
    if (dependencies != null && !dependencies.isEmpty()) {
      for (final Dependency dependency : dependencies) {
        if (dependency != null) {
          dependency.setEnabled(true);
        }
      }
    }
  }

  static final ChartOrBuilder processImportValues(final ChartOrBuilder c) {
    Objects.requireNonNull(c);
    final List<? extends ChartOrBuilder> parentCharts = Charts.getParents(c, null);
    if (parentCharts != null && !parentCharts.isEmpty()) {
      Collections.reverse(parentCharts);
      for (final ChartOrBuilder parentChart : parentCharts) {
        if (parentChart != null) {
          //
          // TODO: resume work
          //
        }
      }
    }
    throw new UnsupportedOperationException("Not yet implemented");
  }
  
  // Ported from requirements.go processImportValues().  TODO: Wildly inefficient.
  private static final Chart processSingleChartImportValues(final Chart c) {
    Objects.requireNonNull(c);

    Chart returnValue = null;

    final Map<String, Object> canonicalValues = Configs.toDefaultValuesMap(c);
    
    Map<String, Object> b = new HashMap<>();
    final Requirements requirements = fromChartOrBuilder(c);
    if (requirements != null) {
      final Collection<Dependency> dependencies = requirements.getDependencies();
      if (dependencies != null && !dependencies.isEmpty()) {
        for (final Dependency dependency : dependencies) {
          if (dependency != null) {
            
            final String dependencyName = dependency.getName();
            if (dependencyName == null) {
              throw new IllegalStateException();
            }

            final Collection<?> importValues = dependency.getImportValues();
            if (importValues != null && !importValues.isEmpty()) {
              final Collection<Object> newImportValues = new ArrayList<>();
              for (final Object importValue : importValues) {
                final String s;
                
                if (importValue instanceof Map) {
                  @SuppressWarnings("unchecked")
                  final Map<String, String> importValueMap = (Map<String, String>)importValue;
                  
                  final String importValueChild = importValueMap.get("child");
                  final String importValueParent = importValueMap.get("parent");

                  final Map<String, String> newMap = new HashMap<>();
                  newMap.put("child", importValueChild);
                  newMap.put("parent", importValueParent);
                  
                  newImportValues.add(newMap);

                  final Map<String, Object> vv =
                    MapTree.newMapChain(importValueParent,
                                        getMap(canonicalValues,
                                               dependencyName + "." + importValueChild));
                  b = Values.coalesceMaps(vv, canonicalValues);
                  // OK
                  
                } else if (importValue instanceof String) {
                  final String importValueString = (String)importValue;
                  
                  final String importValueChild = "exports." + importValueString;
                  
                  final Map<String, String> newMap = new HashMap<>();
                  newMap.put("child", importValueChild);
                  newMap.put("parent", ".");
                  
                  newImportValues.add(newMap);
                  
                  b = Values.coalesceMaps(getMap(canonicalValues, dependencyName + "." + importValueChild), b);
                  // OK
                  
                }
              }
              dependency.setImportValues(newImportValues);            
            }
          }
        }
      }
    }
    b = Values.coalesceMaps(canonicalValues, b);
    final String yaml = new Yaml().dump(b);
    assert yaml != null;
    final Chart.Builder chartBuilder = c.toBuilder();
    assert chartBuilder != null;
    final Config.Builder configBuilder = chartBuilder.getValuesBuilder();
    assert configBuilder != null;
    configBuilder.setRaw(yaml);
    returnValue = chartBuilder.build();
    assert returnValue != null;
    return returnValue;
  }
  
  // Ported slavishly from ProcessRequirementsTags
  final void processTags(final Map<String, Object> values) {
    final Collection<Dependency> dependencies = this.getDependencies();
    if (dependencies != null && !dependencies.isEmpty()) {
      for (final Dependency dependency : dependencies) {
        if (dependency != null) {
          dependency.processTags(values);
        }
      }
    }
    
  }

  // Ported from ProcessRequirementsConditions
  final void processConditions(final Map<String, Object> values) {
    final Collection<Dependency> dependencies = this.getDependencies();
    if (dependencies != null && !dependencies.isEmpty()) {
      for (final Dependency dependency : dependencies) {
        if (dependency != null) {
          dependency.processConditions(values);
        }
      }
    }
  }

  // Ported from PathValue() in chartutil/values.go; bears a striking resemblance to Table()
  private static final Object pathValue(final Map<String, Object> map, final String dotSeparatedPath) {
    Objects.requireNonNull(dotSeparatedPath);
    final Object returnValue;
    if (map == null || map.isEmpty()) {
      returnValue = null;
    } else {
      returnValue = new MapTree(map).get(dotSeparatedPath, Object.class);
    }
    return returnValue;
  }

  // ported from Table() in chartutil/values.go
  private static final Map<String, Object> getMap(Map<String, Object> map, final String dotSeparatedPath) {
    final Map<String, Object> returnValue;
    if (map == null || dotSeparatedPath == null || dotSeparatedPath.isEmpty() || map.isEmpty()) {
      returnValue = null;
    } else {
      returnValue = new MapTree(map).getMap(dotSeparatedPath);
    }
    return returnValue;
  }

  // ported from tableLookup() in chartutil/values.go
  private static final Map<String, Object> getMap(final Map<String, Object> map, final Object key) {
    Map<String, Object> returnValue = null;
    if (map != null && !map.isEmpty()) {
      final Object valueObject = map.get(key);
      if (valueObject instanceof Map) {
        @SuppressWarnings("unchecked")
        final Map<String, Object> temp = (Map<String, Object>)valueObject;
        returnValue = temp;
      }
    }
    return returnValue;
  }

  // Ported from LoadRequirements() in chartutil/requirements.go
  public static final Requirements fromChartOrBuilder(final ChartOrBuilder chart) {
    Objects.requireNonNull(chart);
    Requirements returnValue = null;
    final Collection<? extends Any> files = chart.getFilesList();
    if (files != null && !files.isEmpty()) {
      final Yaml yaml = new Yaml(requirementsConstructor);
      for (final Any file : files) {
        if (file != null && "requirements.yaml".equals(file.getTypeUrl())) {
          final ByteString fileContents = file.getValue();
          if (fileContents != null) {
            final String yamlString = fileContents.toStringUtf8();
            if (yamlString != null) {
              returnValue = (Requirements)yaml.load(yamlString);
            }
          }
        }
      }
    }
    return returnValue;
  }

  /**
   * Based on ProcessRequirementsEnabled in requirements.go.  This
   * code is a slavish port of the equivalent Go code and can be
   * compressed and cleaned up extensively.
   */
  public static final Chart.Builder apply(Chart.Builder chartBuilder, ConfigOrBuilder userSuppliedValues) {
    Objects.requireNonNull(chartBuilder);
    Chart.Builder returnValue = chartBuilder;

    final Requirements requirements = fromChartOrBuilder(chartBuilder);
    if (requirements != null && !requirements.isEmpty()) {
      
      final Collection<? extends Dependency> requirementsDependencies = requirements.getDependencies();
      if (requirementsDependencies != null && !requirementsDependencies.isEmpty()) {
        
        final Collection<? extends Chart.Builder> existingSubcharts = chartBuilder.getDependenciesBuilderList();
        if (existingSubcharts != null && !existingSubcharts.isEmpty()) { 

          for (final Chart.Builder subchart : existingSubcharts) {
            if (subchart != null) {
              for (final Dependency dependency : requirementsDependencies) {
                if (dependency != null) {
                  dependency.adjustName(subchart);
                  dependency.setNameToAlias();
                  dependency.setEnabled(true);
                }
              }
            }
          }
        }
        
        // Next, combine the user-supplied values with the incoming
        // Chart's values according to precedence rules, to yield a
        // new canonical Config that will eventually be sent to
        // Tiller.
        //
        // The problem here is that on the first call of this apply()
        // method, the userSuppliedValues will truly be user-supplied
        // values...so we have to go through this madness to get them
        // "into" the chart.  But as you will see several lines below,
        // there is a recursive call made to this method, but this
        // time with the actual chartBuilder's associated
        // Config.Builder.  So from that point forward, 
        
        final Map<String, Object> chartValuesMap = Configs.toValuesMap(chartBuilder, userSuppliedValues);
        assert chartValuesMap != null;
        final String userSuppliedValuesYaml = Configs.toYAML(chartValuesMap); // madness
        assert userSuppliedValuesYaml != null;
        final Config.Builder configBuilder = chartBuilder.getValuesBuilder();
        assert configBuilder != null;  
        configBuilder.setRaw(userSuppliedValuesYaml);

        // Now disable certain Dependencies, that we just enabled.
        // This might be because the canonical value set contains tags
        // designating them for disablement.  We couldn't disable them
        // earlier because we didn't have values.
        requirements.processTags(chartValuesMap);

        // Do the same thing, but work with conditions instead of tags.
        requirements.processConditions(chartValuesMap);

        // Now our Dependencies' enablements have been possibly altered further.
        
        // OK, now we've semantically stated that certain Charts, that
        // we've already "added back" to the incoming Chart, really
        // shouldn't have been added in the first place--i.e. they're
        // disabled.  By definition, these will be drawn from the set
        // of those mentioned in requirements.yaml, not from the set
        // of those not mentioned in requirements.yaml.  So now we
        // need to go back through the list and remove those we
        // shouldn't have added in the first place.
        
        final Set<String> subchartNamesToRemove = new HashSet<>();
        
        for (final Dependency requirement : requirementsDependencies) {
          if (requirement != null && !requirement.isEnabled()) {
            // Remember that the requirement's name may have been
            // changed (see above) to the value of its alias.
            subchartNamesToRemove.add(requirement.getName());
          }
        }
        
        // Now we have a set of Dependency names representing
        // subcharts that are to be removed.
        
        // Clear the pre-existing list.  (So why did we add to the list earlier?!)
        // In the Go code, this is represented by the following code:
        //
        //   cd := []*chart.Chart{}
        //   copy(cd, c.Dependencies[:0]
        //
        // This makes cd be a brand new slice of an empty array.  Then
        // later the code uses cd as the full contents of the
        // dependencies.  So this is equivalent to clearing out the
        // dependencies and then re-adding them.
        chartBuilder.clearDependencies();
        
        // Add only enabled ones.

        // TODO: wait, if we cleared the dependencies, then this will
        // return nothing!
        Iterable<? extends Chart> chartDependencies = chartBuilder.getDependenciesList();
        if (chartDependencies != null) {
          for (final Chart subchart : chartDependencies) {
            if (subchart != null) {
              if (subchart.hasMetadata()) {
                final Metadata metadata = subchart.getMetadata();
                assert metadata != null;
                final String subchartName = metadata.getName();
                if (subchartNamesToRemove.contains(subchartName)) {
                  continue;
                }
              }
              chartBuilder.addDependencies(subchart);
            }
          }
        }

        // TODO: may be premature?
        // chart = chartBuilder.build();          
        // assert chart != null;

        // Now we have a chart with the proper subcharts in it (only
        // enabled ones are included).
        
        final Collection<Chart> newSubcharts = new ArrayList<>();
        chartDependencies = chartBuilder.getDependenciesList();
        if (chartDependencies != null) {
          for (Chart subchart : chartDependencies) {
            if (subchart != null) {
              // Recursively apply all of this logic to the (by
              // definition enabled) subchart with the canonical value
              // set.  configBuilder here is basically the parent's
              // effective values.
              final Chart.Builder subchartBuilder = apply(subchart.toBuilder(), configBuilder); // <-- RECURSIVE CALL
              assert subchartBuilder != null;
              subchart = subchartBuilder.build();
              if (subchart != null) {
                newSubcharts.add(subchart);
              }
            }
          }
          // TODO: not needed if we're working with chartBuilders everywhere
          // chartBuilder = chart.toBuilder();
          // assert chartBuilder != null;

          // Wipe out the list *again* and replace it with the new,
          // canonical copy.

          // TODO: not needed if we're working with chartBuilders
          // chartBuilder.clearDependencies();
          // chartBuilder.addAllDependencies(newSubcharts);
          // TODO: remove
          // final Chart chart = chartBuilder.build();
        }
        returnValue = chartBuilder;
        
      }
    }
    return returnValue;
  }

  
  // ported relatively slavishly from getAliasDependency()
  /**
   * @deprecated This method is not used and is slated for removla.
   * Please see {@link
   * Requirements.Dependency#getFirstIdentifiedSubchart(Collection)} instead.
   */
  @Deprecated // Not used.
  private static final Chart getAliasSubchart(final Collection<? extends Chart> subcharts, final Dependency aliasChart) {
    Chart returnValue = null;
    if (subcharts != null && !subcharts.isEmpty()) {
      final String aliasChartName = aliasChart.getName();
      if (aliasChartName != null) {
        final String aliasChartVersion = aliasChart.getVersion();
        for (Chart subchart : subcharts) {
          if (subchart != null && subchart.hasMetadata()) {
            Metadata subchartMetadata = subchart.getMetadata();
            assert subchartMetadata != null;
            final String subchartName = subchartMetadata.getName();
            if (aliasChartName.equals(subchartName)) {
              final String subchartVersion = subchartMetadata.getVersion();
              if (aliasChartVersion.equals(subchartVersion)) {
                
                // Find the first subchart that matches both both the
                // name and version of the supplied aliasChart.  If it
                // does, we're going to return it no matter what.  If
                // the aliasChart also has an alias, then rename the chart.

                final String alias = aliasChart.getAlias();
                if (alias != null && !alias.isEmpty()) {

                  final Metadata.Builder subchartMetadataBuilder;
                  subchartMetadataBuilder = subchartMetadata.toBuilder();
                  assert subchartMetadataBuilder != null;
                  subchartMetadataBuilder.setName(alias);
                  subchartMetadata = subchartMetadataBuilder.build();
                
                  final Chart.Builder subchartBuilder = subchart.toBuilder();
                  assert subchartBuilder != null;
                  subchartBuilder.setMetadata(subchartMetadata);
                  subchart = subchartBuilder.build();
                  assert subchart != null;
                }
                
                returnValue = subchart;
                break;
              }
            }
          }
        }
      }
    }
    return returnValue;
  }

  /*
   * Inner and nested classes.
   */

  public static final class DependencyBeanInfo extends SimpleBeanInfo {

    private final Collection<? extends PropertyDescriptor> propertyDescriptors;
    
    public DependencyBeanInfo() throws IntrospectionException {
      super();
      final Collection<PropertyDescriptor> propertyDescriptors = new ArrayList<>();
      propertyDescriptors.add(new PropertyDescriptor("name", Dependency.class));
      propertyDescriptors.add(new PropertyDescriptor("version", Dependency.class));
      propertyDescriptors.add(new PropertyDescriptor("repository", Dependency.class));
      propertyDescriptors.add(new PropertyDescriptor("condition", Dependency.class));
      propertyDescriptors.add(new PropertyDescriptor("tags", Dependency.class));
      propertyDescriptors.add(new PropertyDescriptor("import-values", Dependency.class, "getImportValues", "setImportValues"));
      propertyDescriptors.add(new PropertyDescriptor("alias", Dependency.class));
      this.propertyDescriptors = propertyDescriptors;
    }

    @Override
    public final PropertyDescriptor[] getPropertyDescriptors() {
      return this.propertyDescriptors.toArray(new PropertyDescriptor[this.propertyDescriptors.size()]);
    }
    
  }
  
  public static final class Dependency {

    private String name;

    private String version;

    private String repository; // apending "index.yaml" to this should result in a URL that can be used to fetch the repository index

    /**
     * A YAML path that resolves to a boolean value, used for enabling
     * or disabling subcharts.
     */
    private String condition;

    private Collection<String> tags;

    private boolean enabled;

    private Collection<Object> importValues;
    
    private String alias;
    
    public Dependency() {
      super();
      this.setEnabled(true);
    }

    public String getName() {
      return this.name;
    }

    public void setName(final String name) {
      this.name = name;
    }

    public String getVersion() {
      return this.version;
    }

    public void setVersion(final String version) {
      this.version = version;
    }

    public String getRepository() {
      return this.repository;
    }

    public void setRepository(final String repository) {
      this.repository = repository;
    }

    public String getCondition() {
      return this.condition;
    }

    public void setCondition(final String condition) {
      this.condition = condition;
    }

    public Collection<String> getTags() {
      return this.tags;
    }

    public void setTags(final Collection<String> tags) {
      this.tags = tags;
    }

    public boolean isEnabled() {
      return this.enabled;
    }

    public void setEnabled(final boolean enabled) {
      this.enabled = enabled;
    }

    public Collection<Object> getImportValues() {
      return this.importValues;
    }

    public void setImportValues(final Collection<Object> importValues) {
      this.importValues = importValues;
    }
    
    public String getAlias() {
      return this.alias;
    }

    public void setAlias(final String alias) {
      this.alias = alias;
    }

    public boolean selects(final ChartOrBuilder chart) {
      if (chart == null) {
        return false;
      }

      return this.selects(chart.getMetadata());
    }
    
    public final boolean selects(final MetadataOrBuilder metadata) {
      final boolean returnValue;
      if (metadata == null) {
        returnValue = this.selects(null, null);
      } else {
        returnValue = this.selects(metadata.getName(), metadata.getVersion());
      }
      return returnValue;
    }

    public final boolean selects(final String name, final String versionString) {
      final Object myName = this.getName();
      if (myName == null) {
        if (name != null) {
          return false;
        }
      } else if (!myName.equals(name)) {
        return false;
      }

      final String myVersion = this.getVersion();
      if (myVersion == null) {
        if (versionString != null) {
          return false;
        }
      } else {
        final Version version = Version.valueOf(myVersion);
        assert version != null;
        final Parser<Expression> parser = ExpressionParser.newInstance();
        assert parser != null;
        final Expression semVerConstraint = parser.parse(versionString);
        assert semVerConstraint != null;
        if (!version.satisfies(semVerConstraint)) {
          return false;
        }
      }
      return true;
    }

    /**
     * Checks the supplied {@link Collection} of {@link Chart}s to see
     * if there is a {@link Chart} in it that is {@linkplain
     * #selects(MetadataOrBuilder) identified} by this {@link
     * Dependency}, and, if so, if this {@link Dependency} {@linkplain
     * #getAlias() has an alias}, additionally renames the {@link
     * Chart} in question before returning it.
     *
     * <p>This method may return {@code null}.</p>
     *
     * <p>If this {@link Dependency} does not {@linkplain #getAlias()
     * have an alias} then the {@linkplain
     * #selects(MetadataOrBuilder) identified} {@link Chart} is
     * simply returned.</p>
     *
     * @param subcharts a {@link Collection} of {@link Chart}s; may be
     * {@code null} in which case {@code null} will be returned
     *
     * @return a {@link Chart} that this {@link Dependency}
     * {@linkplain #selects(MetadataOrBuilder) selects},
     * possibly renamed with this {@link Dependency}'s {@linkplain
     * #getAlias() alias}, or {@code null}
     *
     * @see #selects(MetadataOrBuilder)
     *
     * @see #getAlias()
     *
     * @deprecated Please use {@link
     * #getFirstIdentifiedSubchart(Collection)} instead.  This method is
     * slated for removal.
     */
    @Deprecated
    public Chart.Builder getAliasSubchart(final Collection<? extends Chart.Builder> subcharts) {
      return this.getFirstIdentifiedSubchart(subcharts);
    }

    /**
     * @deprecated Slated for removal.
     */
    @Deprecated
    public final Chart.Builder getFirstIdentifiedSubchart(final Collection<? extends Chart.Builder> subcharts) {
      Chart.Builder returnValue = null;
      if (subcharts != null && !subcharts.isEmpty()) {
        for (Chart.Builder subchart : subcharts) {
          if (this.selects(subchart)) {
            assert subchart != null;
            adjustName(subchart);
            returnValue = subchart;
            break;
          }
        }
      }
      return returnValue;
    }

    public final void adjustName(final Chart.Builder subchart) {
      if (subchart != null && this.selects(subchart)) {
        final String alias = this.getAlias();
        if (alias != null && !alias.isEmpty() && subchart.hasMetadata()) {
          final Metadata.Builder subchartMetadataBuilder = subchart.getMetadataBuilder();
          assert subchartMetadataBuilder != null;
          // Rename the chart to have our alias as its new name.
          subchartMetadataBuilder.setName(alias);
        }
      }
    }

    final void setNameToAlias() {
      final String alias = this.getAlias();
      if (alias != null && !alias.isEmpty()) {
        this.setName(alias);
      }
    }

    // Ported from ProcessRequirementsTags
    final void processTags(final Map<String, Object> values) {
      if (values != null) {
        final Object tagsObject = values.get("tags");
        if (tagsObject instanceof Map) {
          final Map<?, ?> tags = (Map<?, ?>)tagsObject;
          final Collection<? extends String> myTags = this.getTags();
          if (myTags != null && !myTags.isEmpty()) {
            boolean explicitlyTrue = false;
            boolean explicitlyFalse = false;
            for (final String myTag : myTags) {
              final Object tagValue = tags.get(myTag);
              if (Boolean.TRUE.equals(tagValue)) {
                explicitlyTrue = true;
              } else if (Boolean.FALSE.equals(tagValue)) {
                explicitlyFalse = true;
              } else {
                // Not a Boolean at all; just skip it
              }
            }
            
            // Note that this block looks different from the analogous
            // block in processConditions() below.  It is this way in the
            // Go code as well.
            if (explicitlyFalse) {
              if (!explicitlyTrue) {
                this.setEnabled(false);
              }
            } else {
              this.setEnabled(explicitlyTrue);
            }
          }
        }
      }
    }
    
    final void processConditions(final Map<String, Object> values) {
      boolean hasTrue = false;
      boolean hasFalse = false;
      String conditionString = this.getCondition();
      if (conditionString != null) {
        conditionString = conditionString.trim();
        final String[] conditions = commaSplitPattern.split(conditionString);
        if (conditions != null && conditions.length > 0) {
          for (final String condition : conditions) {
            if (condition != null && !condition.isEmpty()) {
              final Object conditionValue = pathValue(values, condition);
              if (Boolean.TRUE.equals(conditionValue)) {
                hasTrue = true;
              } else if (Boolean.FALSE.equals(conditionValue)) {
                hasFalse = false;
              } else if (conditionValue != null) {
                break;
              }
            }
          }
        }
      }
      
      // Note that this block looks different from the analogous block
      // in processTags() above.  It is this way in the Go code as
      // well.
      if (hasFalse) {
        if (!hasTrue) {
          this.setEnabled(false);
        }
      } else if (hasTrue) {
        this.setEnabled(true);
      }
    }

  }
  
}
