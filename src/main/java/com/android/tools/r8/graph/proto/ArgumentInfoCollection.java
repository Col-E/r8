// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.proto;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfoFixer;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.ConsumerUtils;
import com.android.tools.r8.utils.IntObjConsumer;
import com.android.tools.r8.utils.IteratorUtils;
import com.google.common.collect.Ordering;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntBidirectionalIterator;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.function.Consumer;

public class ArgumentInfoCollection {

  private static final ArgumentInfoCollection EMPTY = new ArgumentInfoCollection();

  private final Int2ObjectSortedMap<ArgumentInfo> argumentInfos;

  // Specific constructor for empty.
  private ArgumentInfoCollection() {
    this.argumentInfos = new Int2ObjectRBTreeMap<>();
  }

  private ArgumentInfoCollection(Int2ObjectSortedMap<ArgumentInfo> argumentInfos) {
    assert argumentInfos != null : "should use empty.";
    assert !argumentInfos.isEmpty() : "should use empty.";
    this.argumentInfos = argumentInfos;
  }

  public static ArgumentInfoCollection empty() {
    return EMPTY;
  }

  public void forEach(IntObjConsumer<ArgumentInfo> consumer) {
    for (Entry<ArgumentInfo> entry : argumentInfos.int2ObjectEntrySet()) {
      consumer.accept(entry.getIntKey(), entry.getValue());
    }
  }

  public IntSortedSet getKeys() {
    return argumentInfos.keySet();
  }

  public IntCollection getRemovedParameterIndices() {
    int numberOfRemovedArguments = numberOfRemovedArguments();
    if (numberOfRemovedArguments == 0) {
      return IntLists.EMPTY_LIST;
    }
    if (numberOfRemovedArguments == argumentInfos.size()) {
      return getKeys();
    }
    IntList removedParameterIndices = new IntArrayList(numberOfRemovedArguments);
    Iterator<Entry<ArgumentInfo>> iterator = iterator();
    while (iterator.hasNext()) {
      Entry<ArgumentInfo> entry = iterator.next();
      if (entry.getValue().isRemovedArgumentInfo()) {
        removedParameterIndices.add(entry.getIntKey());
      }
    }
    return removedParameterIndices;
  }

  public boolean isArgumentRemoved(int argumentIndex) {
    return getArgumentInfo(argumentIndex).isRemovedArgumentInfo();
  }

  public boolean isEmpty() {
    return this == EMPTY;
  }

  public Iterator<Entry<ArgumentInfo>> iterator() {
    return argumentInfos.int2ObjectEntrySet().iterator();
  }

  public boolean hasRemovedArguments() {
    for (ArgumentInfo value : argumentInfos.values()) {
      if (value.isRemovedArgumentInfo()) {
        return true;
      }
    }
    return false;
  }

  public int numberOfRemovedArguments() {
    int removed = 0;
    for (ArgumentInfo value : argumentInfos.values()) {
      if (value.isRemovedArgumentInfo()) {
        removed++;
      }
    }
    return removed;
  }

  public int numberOfRemovedNonReceiverArguments(ProgramMethod method) {
    return numberOfRemovedArguments()
        - BooleanUtils.intValue(method.getDefinition().isInstance() && isArgumentRemoved(0));
  }

  public boolean hasArgumentInfo(int argumentIndex) {
    return argumentInfos.containsKey(argumentIndex);
  }

  public ArgumentInfo getArgumentInfo(int argumentIndex) {
    return argumentInfos.getOrDefault(argumentIndex, ArgumentInfo.NO_INFO);
  }

  public int size() {
    return argumentInfos.size();
  }

  public ArgumentInfoCollection rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, GraphLens graphLens, GraphLens codeLens) {
    Int2ObjectSortedMap<ArgumentInfo> rewrittenArgumentInfos = new Int2ObjectRBTreeMap<>();
    for (Entry<ArgumentInfo> entry : argumentInfos.int2ObjectEntrySet()) {
      ArgumentInfo argumentInfo = entry.getValue();
      ArgumentInfo rewrittenArgumentInfo =
          argumentInfo.rewrittenWithLens(appView, graphLens, codeLens);
      if (rewrittenArgumentInfo != argumentInfo) {
        rewrittenArgumentInfos.put(entry.getIntKey(), rewrittenArgumentInfo);
      }
    }
    if (!rewrittenArgumentInfos.isEmpty()) {
      for (Entry<ArgumentInfo> entry : argumentInfos.int2ObjectEntrySet()) {
        int key = entry.getIntKey();
        if (!rewrittenArgumentInfos.containsKey(key)) {
          rewrittenArgumentInfos.put(key, entry.getValue());
        }
      }
      return new ArgumentInfoCollection(rewrittenArgumentInfos);
    }
    return this;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    ArgumentInfoCollection other = (ArgumentInfoCollection) obj;
    return argumentInfos.equals(other.argumentInfos);
  }

  @Override
  public int hashCode() {
    return argumentInfos.hashCode();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private Int2ObjectSortedMap<ArgumentInfo> argumentInfos;

    public Builder addArgumentInfo(int argIndex, ArgumentInfo argInfo) {
      if (argumentInfos == null) {
        argumentInfos = new Int2ObjectRBTreeMap<>();
      }
      assert !argumentInfos.containsKey(argIndex);
      argumentInfos.put(argIndex, argInfo);
      return this;
    }

    public ArgumentInfoCollection build() {
      if (argumentInfos == null || argumentInfos.isEmpty()) {
        return EMPTY;
      }
      return new ArgumentInfoCollection(argumentInfos);
    }
  }

  public ArgumentInfoCollection combine(ArgumentInfoCollection info) {
    if (isEmpty()) {
      return info;
    } else {
      if (info.isEmpty()) {
        return this;
      }
    }

    Int2ObjectSortedMap<ArgumentInfo> newArgInfos = new Int2ObjectRBTreeMap<>();
    newArgInfos.putAll(argumentInfos);
    IntBidirectionalIterator iterator = argumentInfos.keySet().iterator();
    int offset = 0;
    int nextArgIndex;
    for (int pendingArgIndex : info.argumentInfos.keySet()) {
      nextArgIndex = peekNextOrMax(iterator);
      while (nextArgIndex <= pendingArgIndex + offset) {
        iterator.nextInt();
        ArgumentInfo argumentInfo = argumentInfos.get(nextArgIndex);
        nextArgIndex = peekNextOrMax(iterator);
        if (argumentInfo.isRemovedArgumentInfo()) {
          offset++;
        }
      }
      ArgumentInfo newArgInfo =
          nextArgIndex == pendingArgIndex + offset
              ? ArgumentInfo.combine(
                  argumentInfos.get(nextArgIndex), info.argumentInfos.get(pendingArgIndex))
              : info.argumentInfos.get(pendingArgIndex);
      newArgInfos.put(pendingArgIndex + offset, newArgInfo);
    }
    assert Ordering.natural().isOrdered(newArgInfos.keySet());
    return new ArgumentInfoCollection(newArgInfos);
  }

  static int peekNextOrMax(IntBidirectionalIterator iterator) {
    if (iterator.hasNext()) {
      int i = iterator.nextInt();
      iterator.previousInt();
      return i;
    }
    return Integer.MAX_VALUE;
  }

  public MethodOptimizationInfoFixer createMethodOptimizationInfoFixer() {
    RewrittenPrototypeDescription prototypeChanges =
        RewrittenPrototypeDescription.create(Collections.emptyList(), null, this);
    return prototypeChanges.createMethodOptimizationInfoFixer();
  }

  /**
   * Returns a function for rewriting the parameter annotations on a method info after prototype
   * changes were made.
   */
  public Consumer<DexEncodedMethod.Builder> createParameterAnnotationsRemover(
      DexEncodedMethod method) {
    if (numberOfRemovedArguments() > 0 && !method.parameterAnnotationsList.isEmpty()) {
      return builder -> {
        int firstArgumentIndex = method.getFirstNonReceiverArgumentIndex();
        builder.removeParameterAnnotations(
            oldIndex -> getArgumentInfo(oldIndex + firstArgumentIndex).isRemovedArgumentInfo());
      };
    }
    return ConsumerUtils.emptyConsumer();
  }

  public int getNewArgumentIndex(int argumentIndex) {
    int numberOfArgumentsRemovedBeforeArgument = 0;
    Iterator<Entry<ArgumentInfo>> iterator = iterator();
    while (iterator.hasNext()) {
      Entry<ArgumentInfo> entry = iterator.next();
      int argumentIndexForInfo = entry.getIntKey();
      if (argumentIndexForInfo >= argumentIndex) {
        break;
      }
      ArgumentInfo argumentInfo = entry.getValue();
      if (argumentInfo.isRemovedArgumentInfo()) {
        numberOfArgumentsRemovedBeforeArgument++;
      }
    }
    assert IteratorUtils.allRemainingMatchDestructive(
        iterator, entry -> entry.getIntKey() >= argumentIndex);
    return argumentIndex - numberOfArgumentsRemovedBeforeArgument;
  }
}
