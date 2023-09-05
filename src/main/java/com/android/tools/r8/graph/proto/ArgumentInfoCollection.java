// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.proto;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfoFixer;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.IntObjConsumer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;

public class ArgumentInfoCollection {

  private static final Int2ObjectRBTreeMap<ArgumentInfo> EMPTY_MAP = new Int2ObjectRBTreeMap<>();
  private static final ArgumentInfoCollection EMPTY = new ArgumentInfoCollection();

  private final Int2ObjectSortedMap<ArgumentInfo> argumentInfos;
  private final int argumentInfosSize;
  private final ArgumentPermutation argumentPermutation;
  private final boolean isConvertedToStaticMethod;

  // Specific constructor for empty.
  private ArgumentInfoCollection() {
    this.argumentInfos = EMPTY_MAP;
    this.argumentInfosSize = -1;
    this.argumentPermutation = ArgumentPermutation.getDefault();
    this.isConvertedToStaticMethod = false;
  }

  private ArgumentInfoCollection(
      Int2ObjectSortedMap<ArgumentInfo> argumentInfos,
      int argumentInfosSize,
      ArgumentPermutation argumentPermutation,
      boolean isConvertedToStaticMethod) {
    assert argumentInfos != null;
    assert argumentPermutation != null;
    assert !argumentInfos.isEmpty() || argumentInfos == EMPTY_MAP;
    assert !argumentInfos.isEmpty() || !argumentPermutation.isDefault() || isConvertedToStaticMethod
        : "should use empty.";
    assert argumentInfosSize >= 0;
    this.argumentInfos = argumentInfos;
    this.argumentInfosSize = argumentInfosSize;
    this.argumentPermutation = argumentPermutation;
    this.isConvertedToStaticMethod = isConvertedToStaticMethod;
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

  @SuppressWarnings("ReferenceEquality")
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
    return getNumberOfRemovedArgumentsBefore(Integer.MAX_VALUE, argumentInfos);
  }

  public int getNumberOfRemovedArgumentsBefore(int index) {
    return getNumberOfRemovedArgumentsBefore(index, argumentInfos);
  }

  private static int getNumberOfRemovedArgumentsBefore(
      int index, Int2ObjectSortedMap<ArgumentInfo> argumentInfos) {
    int removed = 0;
    for (Entry<ArgumentInfo> entry : argumentInfos.int2ObjectEntrySet()) {
      int argumentIndex = entry.getIntKey();
      ArgumentInfo argumentInfo = entry.getValue();
      if (argumentIndex >= index) {
        assert argumentIndex > index || !argumentInfo.isRemovedArgumentInfo();
        break;
      }
      if (argumentInfo.isRemovedArgumentInfo()) {
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

  public boolean hasArgumentPermutation() {
    return !argumentPermutation.isDefault();
  }

  public ArgumentInfo getArgumentInfo(int argumentIndex) {
    return argumentInfos.getOrDefault(argumentIndex, ArgumentInfo.NO_INFO);
  }

  public int getNewArgumentIndex(int argumentIndex) {
    return getNewArgumentIndex(argumentIndex, getNumberOfRemovedArgumentsBefore(argumentIndex));
  }

  public int getNewArgumentIndex(int argumentIndex, int numberOfRemovedArgumentsBefore) {
    int intermediateArgumentIndex = argumentIndex - numberOfRemovedArgumentsBefore;
    return argumentPermutation.getNewArgumentIndex(intermediateArgumentIndex);
  }

  public boolean isConvertedToStaticMethod() {
    return isConvertedToStaticMethod;
  }

  public int size() {
    assert !isEmpty();
    return argumentInfosSize;
  }

  @SuppressWarnings("ReferenceEquality")
  public ArgumentInfoCollection rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, GraphLens graphLens, GraphLens codeLens) {
    if (isEmpty()) {
      return this;
    }
    Builder builder = builder();
    forEach(
        (argumentIndex, argumentInfo) -> {
          ArgumentInfo rewrittenArgumentInfo =
              argumentInfo.rewrittenWithLens(appView, graphLens, codeLens);
          if (rewrittenArgumentInfo != argumentInfo) {
            builder.addArgumentInfo(argumentIndex, rewrittenArgumentInfo);
          }
        });
    if (!builder.isEmpty()) {
      forEach(
          (argumentIndex, argumentInfo) -> {
            if (!builder.hasArgumentInfo(argumentIndex)) {
              builder.addArgumentInfo(argumentIndex, argumentInfo);
            }
          });
      return builder
          .setArgumentInfosSize(argumentInfosSize)
          .setIsConvertedToStaticMethod(isConvertedToStaticMethod())
          .build();
    }
    return this;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    ArgumentInfoCollection other = (ArgumentInfoCollection) obj;
    return argumentInfos.equals(other.argumentInfos)
        && argumentPermutation.equals(other.argumentPermutation)
        && argumentInfosSize == other.argumentInfosSize
        && isConvertedToStaticMethod == other.isConvertedToStaticMethod;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        argumentInfos, argumentPermutation, argumentInfosSize, isConvertedToStaticMethod);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private Int2ObjectSortedMap<ArgumentInfo> argumentInfos = new Int2ObjectRBTreeMap<>();
    private int argumentInfosSize = -1;
    private ArgumentPermutation argumentPermutation = ArgumentPermutation.getDefault();
    private boolean isConvertedToStaticMethod;

    public Builder addArgumentInfo(int argumentIndex, ArgumentInfo argInfo) {
      argumentInfos.put(argumentIndex, argInfo);
      return this;
    }

    public Builder addArgumentInfos(ArgumentInfoCollection argumentInfoCollection) {
      argumentInfoCollection.forEach(this::addArgumentInfo);
      return this;
    }

    public int getNumberOfRemovedArgumentsBefore(int index) {
      return ArgumentInfoCollection.getNumberOfRemovedArgumentsBefore(index, argumentInfos);
    }

    public boolean hasArgumentInfo(int argumentIndex) {
      return argumentInfos.containsKey(argumentIndex);
    }

    public boolean isEmpty() {
      return argumentInfos.isEmpty()
          && argumentPermutation.isDefault()
          && !isConvertedToStaticMethod;
    }

    public Builder setArgumentInfosSize(int argumentInfosSize) {
      this.argumentInfosSize = argumentInfosSize;
      return this;
    }

    public Builder setArgumentPermutation(ArgumentPermutation argumentPermutation) {
      this.argumentPermutation = argumentPermutation;
      return this;
    }

    public Builder setIsConvertedToStaticMethod() {
      return setIsConvertedToStaticMethod(true);
    }

    public Builder setIsConvertedToStaticMethod(boolean isConvertedToStaticMethod) {
      this.isConvertedToStaticMethod = isConvertedToStaticMethod;
      return this;
    }

    public ArgumentInfoCollection build() {
      if (isEmpty()) {
        return empty();
      }
      Int2ObjectSortedMap<ArgumentInfo> argumentInfosOrEmpty =
          argumentInfos.isEmpty() ? EMPTY_MAP : argumentInfos;
      return new ArgumentInfoCollection(
          argumentInfosOrEmpty, argumentInfosSize, argumentPermutation, isConvertedToStaticMethod);
    }
  }

  public ArgumentInfoCollection combine(ArgumentInfoCollection other) {
    if (isEmpty()) {
      return other;
    }
    if (other.isEmpty()) {
      return this;
    }
    Builder builder = builder().addArgumentInfos(this);
    ObjectBidirectionalIterator<Entry<ArgumentInfo>> iterator =
        argumentInfos.int2ObjectEntrySet().iterator();
    int offset = 0;
    for (Entry<ArgumentInfo> entry : other.argumentInfos.int2ObjectEntrySet()) {
      int pendingArgumentIndex = entry.getIntKey();
      ArgumentInfo pendingArgumentInfo = entry.getValue();
      Entry<ArgumentInfo> nextCommittedEntry = peekNext(iterator);
      while (nextCommittedEntry != null
          && nextCommittedEntry.getIntKey() <= pendingArgumentIndex + offset) {
        Entry<ArgumentInfo> committedEntry = iterator.next();
        ArgumentInfo committedArgumentInfo = committedEntry.getValue();
        if (committedArgumentInfo.isRemovedArgumentInfo()) {
          offset++;
        }
        nextCommittedEntry = peekNext(iterator);
      }
      if (nextCommittedEntry != null
          && nextCommittedEntry.getIntKey() == pendingArgumentIndex + offset) {
        ArgumentInfo committedArgumentInfo = nextCommittedEntry.getValue();
        assert !committedArgumentInfo.isRemovedArgumentInfo();
        pendingArgumentInfo = committedArgumentInfo.combine(pendingArgumentInfo);
      }
      builder.addArgumentInfo(pendingArgumentIndex + offset, pendingArgumentInfo);
    }
    // TODO(b/195112263): Double-check the size of the permutation in presence of extra and removed
    //  arguments.
    ArgumentPermutation.Builder argumentPermutationBuilder =
        ArgumentPermutation.builder(argumentInfosSize);
    for (int argumentIndex = 0; argumentIndex < argumentInfosSize; argumentIndex++) {
      if (isArgumentRemoved(argumentIndex)) {
        continue;
      }
      int intermediateArgumentIndex = getNewArgumentIndex(argumentIndex);
      if (other.isArgumentRemoved(intermediateArgumentIndex)) {
        continue;
      }
      int newArgumentIndex = other.getNewArgumentIndex(intermediateArgumentIndex);
      int defaultNewArgumentIndex =
          argumentIndex - builder.getNumberOfRemovedArgumentsBefore(argumentIndex);
      if (newArgumentIndex != defaultNewArgumentIndex) {
        argumentPermutationBuilder.setNewArgumentIndex(argumentIndex, newArgumentIndex);
      }
    }
    assert BooleanUtils.intValue(isConvertedToStaticMethod())
            + BooleanUtils.intValue(other.isConvertedToStaticMethod())
        <= 1;
    return builder
        .setArgumentInfosSize(argumentInfosSize)
        .setArgumentPermutation(argumentPermutationBuilder.build())
        .setIsConvertedToStaticMethod(
            isConvertedToStaticMethod() || other.isConvertedToStaticMethod())
        .build();
  }

  private static Entry<ArgumentInfo> peekNext(
      ObjectBidirectionalIterator<Entry<ArgumentInfo>> iterator) {
    if (iterator.hasNext()) {
      Entry<ArgumentInfo> entry = iterator.next();
      iterator.previous();
      return entry;
    }
    return null;
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
    return builder -> builder.rewriteParameterAnnotations(method, this);
  }
}
