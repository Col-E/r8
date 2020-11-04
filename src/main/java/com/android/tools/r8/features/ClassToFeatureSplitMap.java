// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.features;

import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.Sets;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class ClassToFeatureSplitMap {

  private final Map<DexType, FeatureSplit> classToFeatureSplitMap = new IdentityHashMap<>();

  private ClassToFeatureSplitMap() {}

  public static ClassToFeatureSplitMap createEmptyClassToFeatureSplitMap() {
    return new ClassToFeatureSplitMap();
  }

  public static ClassToFeatureSplitMap createInitialClassToFeatureSplitMap(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return createInitialClassToFeatureSplitMap(appView.options());
  }

  public static ClassToFeatureSplitMap createInitialClassToFeatureSplitMap(
      InternalOptions options) {
    DexItemFactory dexItemFactory = options.dexItemFactory();
    FeatureSplitConfiguration featureSplitConfiguration = options.featureSplitConfiguration;

    ClassToFeatureSplitMap result = new ClassToFeatureSplitMap();
    if (featureSplitConfiguration == null) {
      return result;
    }

    for (FeatureSplit featureSplit : featureSplitConfiguration.getFeatureSplits()) {
      for (ProgramResourceProvider programResourceProvider :
          featureSplit.getProgramResourceProviders()) {
        try {
          for (ProgramResource programResource : programResourceProvider.getProgramResources()) {
            for (String classDescriptor : programResource.getClassDescriptors()) {
              DexType type = dexItemFactory.createType(classDescriptor);
              result.classToFeatureSplitMap.put(type, featureSplit);
            }
          }
        } catch (ResourceException e) {
          throw options.reporter.fatalError(e.getMessage());
        }
      }
    }
    return result;
  }

  public Map<FeatureSplit, Set<DexProgramClass>> getFeatureSplitClasses(
      Set<DexProgramClass> classes) {
    Map<FeatureSplit, Set<DexProgramClass>> result = new IdentityHashMap<>();
    for (DexProgramClass clazz : classes) {
      FeatureSplit featureSplit = getFeatureSplit(clazz);
      if (featureSplit != null && !featureSplit.isBase()) {
        result.computeIfAbsent(featureSplit, ignore -> Sets.newIdentityHashSet()).add(clazz);
      }
    }
    return result;
  }

  public FeatureSplit getFeatureSplit(ProgramDefinition clazz) {
    return getFeatureSplit(clazz.getContextType());
  }

  public FeatureSplit getFeatureSplit(DexType type) {
    return classToFeatureSplitMap.getOrDefault(type, FeatureSplit.BASE);
  }

  public boolean isEmpty() {
    return classToFeatureSplitMap.isEmpty();
  }

  public boolean isInBase(DexProgramClass clazz) {
    return getFeatureSplit(clazz).isBase();
  }

  public boolean isInBaseOrSameFeatureAs(DexProgramClass clazz, ProgramDefinition context) {
    FeatureSplit split = getFeatureSplit(clazz);
    return split.isBase() || split == getFeatureSplit(context);
  }

  public boolean isInFeature(DexProgramClass clazz) {
    return !isInBase(clazz);
  }

  public boolean isInSameFeatureOrBothInBase(ProgramMethod a, ProgramMethod b) {
    return isInSameFeatureOrBothInBase(a.getHolder(), b.getHolder());
  }

  public boolean isInSameFeatureOrBothInBase(DexProgramClass a, DexProgramClass b) {
    return getFeatureSplit(a) == getFeatureSplit(b);
  }

  public ClassToFeatureSplitMap rewrittenWithLens(GraphLens lens) {
    ClassToFeatureSplitMap rewrittenClassToFeatureSplitMap = new ClassToFeatureSplitMap();
    classToFeatureSplitMap.forEach(
        (type, featureSplit) -> {
          DexType rewrittenType = lens.lookupType(type);
          if (rewrittenType.isIntType()) {
            // The type was removed by enum unboxing.
            return;
          }
          FeatureSplit existing =
              rewrittenClassToFeatureSplitMap.classToFeatureSplitMap.put(
                  rewrittenType, featureSplit);
          // If we map two classes to the same class then they must be from the same feature split.
          assert existing == null || existing == featureSplit;
        });
    return rewrittenClassToFeatureSplitMap;
  }

  public ClassToFeatureSplitMap withoutPrunedClasses(Set<DexType> prunedClasses) {
    ClassToFeatureSplitMap classToFeatureSplitMapAfterPruning = new ClassToFeatureSplitMap();
    classToFeatureSplitMap.forEach(
        (type, featureSplit) -> {
          if (!prunedClasses.contains(type)) {
            classToFeatureSplitMapAfterPruning.classToFeatureSplitMap.put(type, featureSplit);
          }
        });
    return classToFeatureSplitMapAfterPruning;
  }
}
