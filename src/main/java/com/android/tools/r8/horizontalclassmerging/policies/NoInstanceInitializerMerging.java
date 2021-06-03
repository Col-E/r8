// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger.Mode;
import com.android.tools.r8.horizontalclassmerging.MergeGroup;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.initializer.InstanceInitializerInfo;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Identifies when instance initializer merging is required and bails out. This is needed to ensure
 * that we don't need to append extra null arguments at constructor call sites, such that the result
 * of the final round of class merging can be described as a renaming only.
 */
public class NoInstanceInitializerMerging extends MultiClassPolicy {

  public NoInstanceInitializerMerging(Mode mode) {
    assert mode.isFinal();
  }

  @Override
  public Collection<MergeGroup> apply(MergeGroup group) {
    Map<MergeGroup, Map<DexMethodSignature, ProgramMethod>> newGroups = new LinkedHashMap<>();

    for (DexProgramClass clazz : group) {
      Map<DexMethodSignature, ProgramMethod> classSignatures = new HashMap<>();
      clazz.forEachProgramInstanceInitializer(
          method -> classSignatures.put(method.getMethodSignature(), method));

      MergeGroup newGroup = null;
      for (Entry<MergeGroup, Map<DexMethodSignature, ProgramMethod>> entry : newGroups.entrySet()) {
        Map<DexMethodSignature, ProgramMethod> groupSignatures = entry.getValue();
        if (canAddClassToGroup(classSignatures.values(), groupSignatures)) {
          newGroup = entry.getKey();
          groupSignatures.putAll(classSignatures);
          break;
        }
      }

      if (newGroup != null) {
        newGroup.add(clazz);
      } else {
        newGroups.put(new MergeGroup(clazz), classSignatures);
      }
    }

    return removeTrivialGroups(newGroups.keySet());
  }

  private boolean canAddClassToGroup(
      Iterable<ProgramMethod> classMethods,
      Map<DexMethodSignature, ProgramMethod> groupSignatures) {
    for (ProgramMethod classMethod : classMethods) {
      ProgramMethod groupMethod = groupSignatures.get(classMethod.getMethodSignature());
      if (groupMethod != null && !equivalent(classMethod, groupMethod)) {
        return false;
      }
    }
    return true;
  }

  // For now, only recognize constructors with 0 parameters that call the same parent constructor.
  private boolean equivalent(ProgramMethod method, ProgramMethod other) {
    if (!method.getProto().getParameters().isEmpty()) {
      return false;
    }

    MethodOptimizationInfo optimizationInfo = method.getDefinition().getOptimizationInfo();
    InstanceInitializerInfo instanceInitializerInfo =
        optimizationInfo.getContextInsensitiveInstanceInitializerInfo();
    if (instanceInitializerInfo.isDefaultInstanceInitializerInfo()) {
      return false;
    }

    InstanceInitializerInfo otherInstanceInitializerInfo =
        other.getDefinition().getOptimizationInfo().getContextInsensitiveInstanceInitializerInfo();
    assert otherInstanceInitializerInfo.isNonTrivialInstanceInitializerInfo();
    if (!instanceInitializerInfo.hasParent()
        || instanceInitializerInfo.getParent().getArity() > 0) {
      return false;
    }

    if (instanceInitializerInfo.getParent() != otherInstanceInitializerInfo.getParent()) {
      return false;
    }

    return !method.getDefinition().getOptimizationInfo().mayHaveSideEffects()
        && !other.getDefinition().getOptimizationInfo().mayHaveSideEffects();
  }

  @Override
  public String getName() {
    return "NoInstanceInitializerMerging";
  }
}
