// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.features;

import com.android.tools.r8.DataResourceConsumer;
import com.android.tools.r8.DataResourceProvider;
import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.Reporter;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FeatureSplitConfiguration {

  private final List<FeatureSplit> featureSplits;

  // TODO(141451259): Consider doing the mapping from DexType to Feature (with support in mapper).
  private final Map<String, FeatureSplit> javaTypeToFeatureSplitMapping = new HashMap<>();

  public FeatureSplitConfiguration(List<FeatureSplit> featureSplits, Reporter reporter) {
    this.featureSplits = featureSplits;
    for (FeatureSplit featureSplit : featureSplits) {
      for (ProgramResourceProvider programResourceProvider :
          featureSplit.getProgramResourceProviders()) {
        try {
          for (ProgramResource programResource : programResourceProvider.getProgramResources()) {
            for (String classDescriptor : programResource.getClassDescriptors()) {
              javaTypeToFeatureSplitMapping.put(
                  DescriptorUtils.descriptorToJavaType(classDescriptor), featureSplit);
            }
          }
        } catch (ResourceException e) {
          throw reporter.fatalError(e.getMessage());
        }
      }
    }
  }

  public Map<FeatureSplit, Set<DexProgramClass>> getFeatureSplitClasses(
      Set<DexProgramClass> classes, ClassNameMapper mapper) {
    Map<FeatureSplit, Set<DexProgramClass>> result = new IdentityHashMap<>();

    for (DexProgramClass programClass : classes) {
      String originalClassName =
          DescriptorUtils.descriptorToJavaType(programClass.type.toDescriptorString(), mapper);
      FeatureSplit featureSplit = javaTypeToFeatureSplitMapping.get(originalClassName);
      if (featureSplit != null) {
        result.computeIfAbsent(featureSplit, f -> Sets.newIdentityHashSet()).add(programClass);
      }
    }
    return result;
  }

  public static class DataResourceProvidersAndConsumer {
    private final Set<DataResourceProvider> providers;
    private final DataResourceConsumer consumer;

    public DataResourceProvidersAndConsumer(
        Set<DataResourceProvider> providers, DataResourceConsumer consumer) {
      this.providers = providers;
      this.consumer = consumer;
    }

    public Set<DataResourceProvider> getProviders() {
      return providers;
    }

    public DataResourceConsumer getConsumer() {
      return consumer;
    }
  }

  public Collection<DataResourceProvidersAndConsumer> getDataResourceProvidersAndConsumers() {
    List<DataResourceProvidersAndConsumer> result = new ArrayList<>();
    for (FeatureSplit featureSplit : featureSplits) {
      DataResourceConsumer dataResourceConsumer =
          featureSplit.getProgramConsumer().getDataResourceConsumer();
      if (dataResourceConsumer != null) {
        Set<DataResourceProvider> dataResourceProviders = new HashSet<>();
        for (ProgramResourceProvider programResourceProvider :
            featureSplit.getProgramResourceProviders()) {
          DataResourceProvider dataResourceProvider =
              programResourceProvider.getDataResourceProvider();
          if (dataResourceProvider != null) {
            dataResourceProviders.add(dataResourceProvider);
          }
        }
        if (!dataResourceProviders.isEmpty()) {
          result.add(
              new DataResourceProvidersAndConsumer(dataResourceProviders, dataResourceConsumer));
        }
      }
    }
    return result;
  }

  public boolean inBaseOrSameFeatureAs(DexProgramClass clazz, DexProgramClass context) {
    FeatureSplit split = getFeatureSplit(clazz);
    return split.isBase() || split == getFeatureSplit(context);
  }

  public boolean isInFeature(DexProgramClass clazz) {
    return !isInBase(clazz);
  }

  public boolean isInBase(DexProgramClass clazz) {
    return getFeatureSplit(clazz).isBase();
  }

  public boolean inSameFeatureOrBothInBase(ProgramMethod a, ProgramMethod b) {
    return inSameFeatureOrBothInBase(a.getHolder(), b.getHolder());
  }

  public boolean inSameFeatureOrBothInBase(DexProgramClass a, DexProgramClass b) {
    return getFeatureSplit(a) == getFeatureSplit(b);
  }

  public List<FeatureSplit> getFeatureSplits() {
    return featureSplits;
  }

  public FeatureSplit getFeatureSplitFromClassDescriptor(String classDescriptor) {
    return javaTypeToFeatureSplitMapping.get(DescriptorUtils.descriptorToJavaType(classDescriptor));
  }

  public FeatureSplit getFeatureSplit(DexProgramClass clazz) {
    return javaTypeToFeatureSplitMapping.getOrDefault(
        clazz.type.toSourceString(), FeatureSplit.BASE);
  }
}
