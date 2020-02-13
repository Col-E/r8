// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.ir.code.ConstInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.utils.BooleanUtils;
import it.unimi.dsi.fastutil.ints.Int2ReferenceLinkedOpenHashMap;
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

    public static RemovedArgumentInfoCollection create(
        Int2ReferenceSortedMap<RemovedArgumentInfo> removedArguments) {
      if (removedArguments == null || removedArguments.isEmpty()) {
        return EMPTY;
      }
      return new RemovedArgumentInfoCollection(removedArguments);
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
  }

  private static final RewrittenPrototypeDescription none = new RewrittenPrototypeDescription();

  private final boolean hasBeenChangedToReturnVoid;
  private final boolean extraNullParameter;
  private final RemovedArgumentInfoCollection removedArgumentsInfo;

  private RewrittenPrototypeDescription() {
    this(false, false, RemovedArgumentInfoCollection.empty());
  }

  private RewrittenPrototypeDescription(
      boolean hasBeenChangedToReturnVoid,
      boolean extraNullParameter,
      RemovedArgumentInfoCollection removedArgumentsInfo) {
    assert removedArgumentsInfo != null;
    this.extraNullParameter = extraNullParameter;
    this.hasBeenChangedToReturnVoid = hasBeenChangedToReturnVoid;
    this.removedArgumentsInfo = removedArgumentsInfo;
  }

  public static RewrittenPrototypeDescription createForUninstantiatedTypes(
      boolean hasBeenChangedToReturnVoid, RemovedArgumentInfoCollection removedArgumentsInfo) {
    return new RewrittenPrototypeDescription(
        hasBeenChangedToReturnVoid, false, removedArgumentsInfo);
  }

  public static RewrittenPrototypeDescription none() {
    return none;
  }

  public boolean isEmpty() {
    return !extraNullParameter
        && !hasBeenChangedToReturnVoid
        && !getRemovedArgumentInfoCollection().hasRemovedArguments();
  }

  public boolean hasExtraNullParameter() {
    return extraNullParameter;
  }

  public boolean hasBeenChangedToReturnVoid() {
    return hasBeenChangedToReturnVoid;
  }

  public RemovedArgumentInfoCollection getRemovedArgumentInfoCollection() {
    return removedArgumentsInfo;
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
    assert hasBeenChangedToReturnVoid;
    ConstInstruction instruction = code.createConstNull();
    instruction.setPosition(position);
    return instruction;
  }

  public DexProto rewriteProto(DexEncodedMethod encodedMethod, DexItemFactory dexItemFactory) {
    if (isEmpty()) {
      return encodedMethod.method.proto;
    }
    DexType newReturnType =
        hasBeenChangedToReturnVoid
            ? dexItemFactory.voidType
            : encodedMethod.method.proto.returnType;
    DexType[] newParameters = removedArgumentsInfo.rewriteParameters(encodedMethod);
    return dexItemFactory.createProto(newReturnType, newParameters);
  }

  public RewrittenPrototypeDescription withConstantReturn() {
    return !hasBeenChangedToReturnVoid
        ? new RewrittenPrototypeDescription(true, extraNullParameter, removedArgumentsInfo)
        : this;
  }

  public RewrittenPrototypeDescription withRemovedArguments(RemovedArgumentInfoCollection other) {
    return new RewrittenPrototypeDescription(
        hasBeenChangedToReturnVoid, extraNullParameter, removedArgumentsInfo.combine(other));
  }

  public RewrittenPrototypeDescription withExtraNullParameter() {
    return !extraNullParameter
        ? new RewrittenPrototypeDescription(hasBeenChangedToReturnVoid, true, removedArgumentsInfo)
        : this;
  }
}
