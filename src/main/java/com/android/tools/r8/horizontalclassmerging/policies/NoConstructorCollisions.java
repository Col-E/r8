// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger.Mode;
import com.android.tools.r8.horizontalclassmerging.MergeGroup;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicyWithPreprocessing;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * In the final round, we're not allowed to resolve constructor collisions by appending null
 * arguments to constructor calls.
 *
 * <p>As an example, if a class in the program declares the constructors {@code <init>(A)} and
 * {@code <init>(B)}, the classes A and B must not be merged.
 *
 * <p>To avoid collisions of this kind, we run over all the classes in the program, and apply the
 * current set of merge groups to the constructor signatures of each class. Then, in case of a
 * collision, we extract all the mapped types from the constructor signatures, and prevent merging
 * of these types.
 */
public class NoConstructorCollisions extends MultiClassPolicyWithPreprocessing<Set<DexType>> {

  private final AppView<?> appView;
  private final DexItemFactory dexItemFactory;

  public NoConstructorCollisions(AppView<?> appView, Mode mode) {
    assert mode.isFinal();
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
  }

  /**
   * Removes the classes in {@param collisionResolution} from {@param group}, and returns the new
   * filtered group.
   */
  @Override
  public Collection<MergeGroup> apply(MergeGroup group, Set<DexType> collisionResolution) {
    MergeGroup newGroup =
        new MergeGroup(
            Iterables.filter(group, clazz -> !collisionResolution.contains(clazz.getType())));
    return newGroup.isTrivial() ? Collections.emptyList() : ListUtils.newLinkedList(newGroup);
  }

  /**
   * Computes the set of classes that must not be merged, because the merging of these classes could
   * lead to constructor collisions.
   */
  @Override
  public Set<DexType> preprocess(Collection<MergeGroup> groups, ExecutorService executorService) {
    // Build a mapping from types to groups.
    Map<DexType, MergeGroup> groupsByType = new IdentityHashMap<>();
    for (MergeGroup group : groups) {
      for (DexProgramClass clazz : group) {
        groupsByType.put(clazz.getType(), group);
      }
    }

    // Find the set of types that must not be merged, because they could lead to a constructor
    // collision.
    Set<DexType> collisionResolution = Sets.newIdentityHashSet();
    // Iterate over all the instance initializers of the current class. If the current class is in
    // a merge group, we must include all constructors of the entire merge group.
    WorkList.newIdentityWorkList(appView.appInfo().classes())
        .process(
            (current, workList) -> {
              Iterable<DexProgramClass> group =
                  groupsByType.containsKey(current.getType())
                      ? groupsByType.get(current.getType())
                      : IterableUtils.singleton(current);
              Set<DexMethod> seen = Sets.newIdentityHashSet();
              for (DexProgramClass clazz : group) {
                for (DexEncodedMethod method :
                    clazz.directMethods(DexEncodedMethod::isInstanceInitializer)) {
                  // Rewrite the constructor reference using the current merge groups.
                  DexMethod newReference = rewriteReference(method.getReference(), groupsByType);
                  if (!seen.add(newReference)) {
                    // Found a collision. Block all referenced types from being merged.
                    for (DexType type : method.getProto().getBaseTypes(dexItemFactory)) {
                      if (type.isClassType() && groupsByType.containsKey(type)) {
                        collisionResolution.add(type);
                      }
                    }
                  }
                }
              }
              workList.markAsSeen(group);
            });
    return collisionResolution;
  }

  private DexProto rewriteProto(DexProto proto, Map<DexType, MergeGroup> groups) {
    DexType[] parameters =
        ArrayUtils.map(
            proto.getParameters().values,
            parameter -> rewriteType(parameter, groups),
            DexType.EMPTY_ARRAY);
    return dexItemFactory.createProto(rewriteType(proto.getReturnType(), groups), parameters);
  }

  private DexMethod rewriteReference(DexMethod method, Map<DexType, MergeGroup> groups) {
    return dexItemFactory.createMethod(
        rewriteType(method.getHolderType(), groups),
        rewriteProto(method.getProto(), groups),
        method.getName());
  }

  @SuppressWarnings("ReferenceEquality")
  private DexType rewriteType(DexType type, Map<DexType, MergeGroup> groups) {
    if (type.isArrayType()) {
      DexType baseType = type.toBaseType(dexItemFactory);
      DexType rewrittenBaseType = rewriteType(baseType, groups);
      if (rewrittenBaseType == baseType) {
        return type;
      }
      return type.replaceBaseType(rewrittenBaseType, dexItemFactory);
    }
    if (type.isClassType()) {
      if (!groups.containsKey(type)) {
        return type;
      }
      return groups.get(type).getClasses().getFirst().getType();
    }
    assert type.isPrimitiveType() || type.isVoidType();
    return type;
  }

  @Override
  public String getName() {
    return "NoConstructorCollisions";
  }
}
