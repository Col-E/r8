// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.ConstInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.BooleanUtils;
import it.unimi.dsi.fastutil.ints.Int2ReferenceLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import it.unimi.dsi.fastutil.ints.IntBidirectionalIterator;
import java.util.function.Consumer;

public class RewrittenPrototypeDescription {

  public static class RemovedArgumentInfo {

    public static class Builder {

      private boolean isAlwaysNull = false;
      private DexType type = null;

      public Builder setIsAlwaysNull() {
        this.isAlwaysNull = true;
        return this;
      }

      public Builder setType(DexType type) {
        this.type = type;
        return this;
      }

      public RemovedArgumentInfo build() {
        assert type != null;
        return new RemovedArgumentInfo(isAlwaysNull, type);
      }
    }

    private final boolean isAlwaysNull;
    private final DexType type;

    private RemovedArgumentInfo(boolean isAlwaysNull, DexType type) {
      this.isAlwaysNull = isAlwaysNull;
      this.type = type;
    }

    public static Builder builder() {
      return new Builder();
    }

    public DexType getType() {
      return type;
    }

    public boolean isAlwaysNull() {
      return isAlwaysNull;
    }

    public boolean isNeverUsed() {
      return !isAlwaysNull;
    }
  }

  public static class RemovedArgumentInfoCollection {

    private static final RemovedArgumentInfoCollection EMPTY = new RemovedArgumentInfoCollection();

    private final Int2ReferenceSortedMap<RemovedArgumentInfo> removedArguments;

    // Specific constructor for empty.
    private RemovedArgumentInfoCollection() {
      this.removedArguments = new Int2ReferenceLinkedOpenHashMap<>();
    }

    private RemovedArgumentInfoCollection(
        Int2ReferenceSortedMap<RemovedArgumentInfo> removedArguments) {
      assert removedArguments != null : "should use empty.";
      assert !removedArguments.isEmpty() : "should use empty.";
      this.removedArguments = removedArguments;
    }

    public static RemovedArgumentInfoCollection empty() {
      return EMPTY;
    }

    public RemovedArgumentInfo getArgumentInfo(int argIndex) {
      return removedArguments.get(argIndex);
    }

    public boolean hasRemovedArguments() {
      return !removedArguments.isEmpty();
    }

    public boolean isArgumentRemoved(int argumentIndex) {
      return removedArguments.containsKey(argumentIndex);
    }

    public DexType[] rewriteParameters(DexEncodedMethod encodedMethod) {
      // Currently not allowed to remove the receiver of an instance method. This would involve
      // changing invoke-direct/invoke-virtual into invoke-static.
      assert encodedMethod.isStatic() || !isArgumentRemoved(0);
      DexType[] params = encodedMethod.method.proto.parameters.values;
      if (!hasRemovedArguments()) {
        return params;
      }
      DexType[] newParams = new DexType[params.length - numberOfRemovedArguments()];
      int offset = encodedMethod.isStatic() ? 0 : 1;
      int newParamIndex = 0;
      for (int oldParamIndex = 0; oldParamIndex < params.length; ++oldParamIndex) {
        if (!isArgumentRemoved(oldParamIndex + offset)) {
          newParams[newParamIndex++] = params[oldParamIndex];
        }
      }
      return newParams;
    }

    public int numberOfRemovedArguments() {
      return removedArguments != null ? removedArguments.size() : 0;
    }

    public RemovedArgumentInfoCollection combine(RemovedArgumentInfoCollection info) {
      if (hasRemovedArguments()) {
        if (!info.hasRemovedArguments()) {
          return this;
        }
      } else {
        return info;
      }

      Int2ReferenceSortedMap<RemovedArgumentInfo> newRemovedArguments =
          new Int2ReferenceLinkedOpenHashMap<>();
      newRemovedArguments.putAll(removedArguments);
      IntBidirectionalIterator iterator = removedArguments.keySet().iterator();
      int offset = 0;
      for (int pendingArgIndex : info.removedArguments.keySet()) {
        int nextArgindex = peekNextOrMax(iterator);
        while (nextArgindex <= pendingArgIndex + offset) {
          iterator.nextInt();
          nextArgindex = peekNextOrMax(iterator);
          offset++;
        }
        assert !newRemovedArguments.containsKey(pendingArgIndex + offset);
        newRemovedArguments.put(
            pendingArgIndex + offset, info.removedArguments.get(pendingArgIndex));
      }
      return new RemovedArgumentInfoCollection(newRemovedArguments);
    }

    static int peekNextOrMax(IntBidirectionalIterator iterator) {
      if (iterator.hasNext()) {
        int i = iterator.nextInt();
        iterator.previousInt();
        return i;
      }
      return Integer.MAX_VALUE;
    }

    public Consumer<DexEncodedMethod.Builder> createParameterAnnotationsRemover(
        DexEncodedMethod method) {
      if (numberOfRemovedArguments() > 0 && !method.parameterAnnotationsList.isEmpty()) {
        return builder -> {
          int firstArgumentIndex = BooleanUtils.intValue(!method.isStatic());
          builder.removeParameterAnnotations(
              oldIndex -> isArgumentRemoved(oldIndex + firstArgumentIndex));
        };
      }
      return null;
    }

    public static Builder builder() {
      return new Builder();
    }

    public static class Builder {
      private Int2ReferenceSortedMap<RemovedArgumentInfo> removedArguments;

      public Builder addRemovedArgument(int argIndex, RemovedArgumentInfo argInfo) {
        if (removedArguments == null) {
          removedArguments = new Int2ReferenceLinkedOpenHashMap<>();
        }
        assert !removedArguments.containsKey(argIndex);
        removedArguments.put(argIndex, argInfo);
        return this;
      }

      public RemovedArgumentInfoCollection build() {
        if (removedArguments == null || removedArguments.isEmpty()) {
          return EMPTY;
        }
        return new RemovedArgumentInfoCollection(removedArguments);
      }
    }
  }

  public static class RewrittenTypeInfo {

    private final DexType oldType;
    private final DexType newType;

    static RewrittenTypeInfo toVoid(DexType oldReturnType, AppView<?> appView) {
      return new RewrittenTypeInfo(oldReturnType, appView.dexItemFactory().voidType);
    }

    public RewrittenTypeInfo(DexType oldType, DexType newType) {
      this.oldType = oldType;
      this.newType = newType;
    }

    public DexType getNewType() {
      return newType;
    }

    public DexType getOldType() {
      return oldType;
    }

    boolean hasBeenChangedToReturnVoid(AppView<?> appView) {
      return newType == appView.dexItemFactory().voidType;
    }

    public boolean defaultValueHasChanged() {
      if (newType.isPrimitiveType()) {
        if (oldType.isPrimitiveType()) {
          return ValueType.fromDexType(newType) != ValueType.fromDexType(oldType);
        }
        return true;
      } else if (oldType.isPrimitiveType()) {
        return true;
      }
      // All reference types uses null as default value.
      assert newType.isReferenceType();
      assert oldType.isReferenceType();
      return false;
    }

    public TypeLatticeElement defaultValueLatticeElement(AppView<?> appView) {
      if (newType.isPrimitiveType()) {
        return TypeLatticeElement.fromDexType(newType, null, appView);
      }
      return TypeLatticeElement.getNull();
    }
  }

  public static class RewrittenTypeArgumentInfoCollection {

    private static final RewrittenTypeArgumentInfoCollection EMPTY =
        new RewrittenTypeArgumentInfoCollection();
    private final Int2ReferenceMap<RewrittenTypeInfo> rewrittenArgumentsInfo;

    private RewrittenTypeArgumentInfoCollection() {
      this.rewrittenArgumentsInfo = new Int2ReferenceOpenHashMap<>(0);
    }

    private RewrittenTypeArgumentInfoCollection(
        Int2ReferenceMap<RewrittenTypeInfo> rewrittenArgumentsInfo) {
      this.rewrittenArgumentsInfo = rewrittenArgumentsInfo;
    }

    public static RewrittenTypeArgumentInfoCollection empty() {
      return EMPTY;
    }

    public boolean isEmpty() {
      return rewrittenArgumentsInfo.isEmpty();
    }

    public RewrittenTypeInfo getArgumentRewrittenTypeInfo(int argIndex) {
      return rewrittenArgumentsInfo.get(argIndex);
    }

    public boolean isArgumentRewrittenTypeInfo(int argIndex) {
      return rewrittenArgumentsInfo.containsKey(argIndex);
    }

    public DexType[] rewriteParameters(DexEncodedMethod encodedMethod) {
      DexType[] params = encodedMethod.method.proto.parameters.values;
      if (isEmpty()) {
        return params;
      }
      DexType[] newParams = new DexType[params.length];
      int offset = encodedMethod.isStatic() ? 0 : 1;
      for (int index = 0; index < params.length; index++) {
        RewrittenTypeInfo argInfo = getArgumentRewrittenTypeInfo(index + offset);
        if (argInfo != null) {
          assert params[index] == argInfo.oldType;
          newParams[index] = argInfo.newType;
        } else {
          newParams[index] = params[index];
        }
      }
      return newParams;
    }

    public static Builder builder() {
      return new Builder();
    }

    public static class Builder {

      private Int2ReferenceMap<RewrittenTypeInfo> rewrittenArgumentsInfo;

      public Builder rewriteArgument(int argIndex, DexType oldType, DexType newType) {
        if (rewrittenArgumentsInfo == null) {
          rewrittenArgumentsInfo = new Int2ReferenceOpenHashMap<>();
        }
        rewrittenArgumentsInfo.put(argIndex, new RewrittenTypeInfo(oldType, newType));
        return this;
      }

      public RewrittenTypeArgumentInfoCollection build() {
        if (rewrittenArgumentsInfo == null) {
          return EMPTY;
        }
        assert !rewrittenArgumentsInfo.isEmpty();
        return new RewrittenTypeArgumentInfoCollection(rewrittenArgumentsInfo);
      }
    }
  }

  private static final RewrittenPrototypeDescription none = new RewrittenPrototypeDescription();

  // TODO(b/149681096): Unify RewrittenPrototypeDescription.
  private final boolean extraNullParameter;
  private final RemovedArgumentInfoCollection removedArgumentInfoCollection;
  private final RewrittenTypeInfo rewrittenReturnInfo;
  private final RewrittenTypeArgumentInfoCollection rewrittenTypeArgumentInfoCollection;

  private RewrittenPrototypeDescription() {
    this(
        false,
        RemovedArgumentInfoCollection.empty(),
        null,
        RewrittenTypeArgumentInfoCollection.empty());
  }

  private RewrittenPrototypeDescription(
      boolean extraNullParameter,
      RemovedArgumentInfoCollection removedArgumentsInfo,
      RewrittenTypeInfo rewrittenReturnInfo,
      RewrittenTypeArgumentInfoCollection rewrittenArgumentsInfo) {
    assert removedArgumentsInfo != null;
    this.extraNullParameter = extraNullParameter;
    this.removedArgumentInfoCollection = removedArgumentsInfo;
    this.rewrittenReturnInfo = rewrittenReturnInfo;
    this.rewrittenTypeArgumentInfoCollection = rewrittenArgumentsInfo;
  }

  public static RewrittenPrototypeDescription createForUninstantiatedTypes(
      DexMethod method,
      AppView<AppInfoWithLiveness> appView,
      RemovedArgumentInfoCollection removedArgumentsInfo) {
    DexType returnType = method.proto.returnType;
    RewrittenTypeInfo returnInfo =
        returnType.isAlwaysNull(appView) ? RewrittenTypeInfo.toVoid(returnType, appView) : null;
    return new RewrittenPrototypeDescription(
        false, removedArgumentsInfo, returnInfo, RewrittenTypeArgumentInfoCollection.empty());
  }

  public static RewrittenPrototypeDescription createForRewrittenTypes(
      RewrittenTypeInfo returnInfo, RewrittenTypeArgumentInfoCollection rewrittenArgumentsInfo) {
    return new RewrittenPrototypeDescription(
        false, RemovedArgumentInfoCollection.empty(), returnInfo, rewrittenArgumentsInfo);
  }

  public static RewrittenPrototypeDescription none() {
    return none;
  }

  public boolean isEmpty() {
    return !extraNullParameter
        && !removedArgumentInfoCollection.hasRemovedArguments()
        && rewrittenReturnInfo == null
        && rewrittenTypeArgumentInfoCollection.isEmpty();
  }

  public boolean hasExtraNullParameter() {
    return extraNullParameter;
  }

  public boolean hasBeenChangedToReturnVoid(AppView<?> appView) {
    return rewrittenReturnInfo != null && rewrittenReturnInfo.hasBeenChangedToReturnVoid(appView);
  }

  public RemovedArgumentInfoCollection getRemovedArgumentInfoCollection() {
    return removedArgumentInfoCollection;
  }

  public RewrittenTypeArgumentInfoCollection getRewrittenTypeArgumentInfoCollection() {
    return rewrittenTypeArgumentInfoCollection;
  }

  public boolean hasRewrittenReturnInfo() {
    return rewrittenReturnInfo != null;
  }

  public RewrittenTypeInfo getRewrittenReturnInfo() {
    return rewrittenReturnInfo;
  }

  /**
   * Returns the {@link ConstInstruction} that should be used to materialize the result of
   * invocations to the method represented by this {@link RewrittenPrototypeDescription}.
   *
   * <p>This method should only be used for methods that return a constant value and whose return
   * type has been changed to void.
   *
   * <p>Note that the current implementation always returns null at this point.
   */
  public ConstInstruction getConstantReturn(IRCode code, Position position) {
    ConstInstruction instruction = code.createConstNull();
    instruction.setPosition(position);
    return instruction;
  }

  @SuppressWarnings("ConstantConditions")
  public DexProto rewriteProto(DexEncodedMethod encodedMethod, DexItemFactory dexItemFactory) {
    if (isEmpty()) {
      return encodedMethod.method.proto;
    }
    DexType newReturnType =
        rewrittenReturnInfo != null
            ? rewrittenReturnInfo.newType
            : encodedMethod.method.proto.returnType;
    // TODO(b/149681096): Unify RewrittenPrototypeDescription, have a single variable for return.
    if (rewrittenReturnInfo != null || !rewrittenTypeArgumentInfoCollection.isEmpty()) {
      assert !removedArgumentInfoCollection.hasRemovedArguments();
      DexType[] newParameters =
          rewrittenTypeArgumentInfoCollection.rewriteParameters(encodedMethod);
      return dexItemFactory.createProto(newReturnType, newParameters);
    } else {
      assert rewrittenReturnInfo == null;
      assert rewrittenTypeArgumentInfoCollection.isEmpty();
      DexType[] newParameters = removedArgumentInfoCollection.rewriteParameters(encodedMethod);
      return dexItemFactory.createProto(newReturnType, newParameters);
    }
  }

  public RewrittenPrototypeDescription withConstantReturn(
      DexType oldReturnType, AppView<?> appView) {
    assert rewrittenReturnInfo == null;
    return !hasBeenChangedToReturnVoid(appView)
        ? new RewrittenPrototypeDescription(
            extraNullParameter,
            removedArgumentInfoCollection,
            RewrittenTypeInfo.toVoid(oldReturnType, appView),
            rewrittenTypeArgumentInfoCollection)
        : this;
  }

  public RewrittenPrototypeDescription withRemovedArguments(RemovedArgumentInfoCollection other) {
    return new RewrittenPrototypeDescription(
        extraNullParameter,
        removedArgumentInfoCollection.combine(other),
        rewrittenReturnInfo,
        rewrittenTypeArgumentInfoCollection);
  }

  public RewrittenPrototypeDescription withExtraNullParameter() {
    return !extraNullParameter
        ? new RewrittenPrototypeDescription(
            true,
            removedArgumentInfoCollection,
            rewrittenReturnInfo,
            rewrittenTypeArgumentInfoCollection)
        : this;
  }
}
