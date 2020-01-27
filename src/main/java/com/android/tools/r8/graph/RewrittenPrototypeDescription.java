// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.ir.code.ConstInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.IteratorUtils;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Consumer;

public class RewrittenPrototypeDescription {

  public static class RemovedArgumentInfo {

    public static class Builder {

      private int argumentIndex = -1;
      private boolean isAlwaysNull = false;
      private DexType type = null;

      public Builder setArgumentIndex(int argumentIndex) {
        this.argumentIndex = argumentIndex;
        return this;
      }

      public Builder setIsAlwaysNull() {
        this.isAlwaysNull = true;
        return this;
      }

      public Builder setType(DexType type) {
        this.type = type;
        return this;
      }

      public RemovedArgumentInfo build() {
        assert argumentIndex >= 0;
        assert type != null;
        return new RemovedArgumentInfo(argumentIndex, isAlwaysNull, type);
      }
    }

    private final int argumentIndex;
    private final boolean isAlwaysNull;
    private final DexType type;

    private RemovedArgumentInfo(int argumentIndex, boolean isAlwaysNull, DexType type) {
      this.argumentIndex = argumentIndex;
      this.isAlwaysNull = isAlwaysNull;
      this.type = type;
    }

    public static Builder builder() {
      return new Builder();
    }

    public int getArgumentIndex() {
      return argumentIndex;
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

    RemovedArgumentInfo withArgumentIndex(int argumentIndex) {
      return this.argumentIndex != argumentIndex
          ? new RemovedArgumentInfo(argumentIndex, isAlwaysNull, type)
          : this;
    }
  }

  public static class RemovedArgumentsInfo {

    private static final RemovedArgumentsInfo empty = new RemovedArgumentsInfo(null);

    private final List<RemovedArgumentInfo> removedArguments;

    public RemovedArgumentsInfo(List<RemovedArgumentInfo> removedArguments) {
      assert verifyRemovedArguments(removedArguments);
      this.removedArguments = removedArguments;
    }

    private static boolean verifyRemovedArguments(List<RemovedArgumentInfo> removedArguments) {
      if (removedArguments != null && !removedArguments.isEmpty()) {
        // Check that list is sorted by argument indices.
        int lastArgumentIndex = removedArguments.get(0).getArgumentIndex();
        for (int i = 1; i < removedArguments.size(); ++i) {
          int currentArgumentIndex = removedArguments.get(i).getArgumentIndex();
          assert lastArgumentIndex < currentArgumentIndex;
          lastArgumentIndex = currentArgumentIndex;
        }
      }
      return true;
    }

    public static RemovedArgumentsInfo empty() {
      return empty;
    }

    public ListIterator<RemovedArgumentInfo> iterator() {
      return removedArguments == null
          ? Collections.emptyListIterator()
          : removedArguments.listIterator();
    }

    public boolean hasRemovedArguments() {
      return removedArguments != null && !removedArguments.isEmpty();
    }

    public boolean isArgumentRemoved(int argumentIndex) {
      if (removedArguments != null) {
        for (RemovedArgumentInfo info : removedArguments) {
          if (info.getArgumentIndex() == argumentIndex) {
            return true;
          }
        }
      }
      return false;
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

    public RemovedArgumentsInfo combine(RemovedArgumentsInfo info) {
      assert info != null;
      if (hasRemovedArguments()) {
        if (!info.hasRemovedArguments()) {
          return this;
        }
      } else {
        return info;
      }

      List<RemovedArgumentInfo> newRemovedArguments = new LinkedList<>(removedArguments);
      ListIterator<RemovedArgumentInfo> iterator = newRemovedArguments.listIterator();
      int offset = 0;
      for (RemovedArgumentInfo pending : info.removedArguments) {
        RemovedArgumentInfo next = IteratorUtils.peekNext(iterator);
        while (next != null && next.getArgumentIndex() <= pending.getArgumentIndex() + offset) {
          iterator.next();
          next = IteratorUtils.peekNext(iterator);
          offset++;
        }
        iterator.add(pending.withArgumentIndex(pending.getArgumentIndex() + offset));
      }
      return new RemovedArgumentsInfo(newRemovedArguments);
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
  private final RemovedArgumentsInfo removedArgumentsInfo;

  private RewrittenPrototypeDescription() {
    this(false, false, RemovedArgumentsInfo.empty());
  }

  private RewrittenPrototypeDescription(
      boolean hasBeenChangedToReturnVoid,
      boolean extraNullParameter,
      RemovedArgumentsInfo removedArgumentsInfo) {
    assert removedArgumentsInfo != null;
    this.extraNullParameter = extraNullParameter;
    this.hasBeenChangedToReturnVoid = hasBeenChangedToReturnVoid;
    this.removedArgumentsInfo = removedArgumentsInfo;
  }

  public static RewrittenPrototypeDescription createForUninstantiatedTypes(
      boolean hasBeenChangedToReturnVoid, RemovedArgumentsInfo removedArgumentsInfo) {
    return new RewrittenPrototypeDescription(
        hasBeenChangedToReturnVoid, false, removedArgumentsInfo);
  }

  public static RewrittenPrototypeDescription none() {
    return none;
  }

  public boolean isEmpty() {
    return !extraNullParameter
        && !hasBeenChangedToReturnVoid
        && !getRemovedArgumentsInfo().hasRemovedArguments();
  }

  public boolean hasExtraNullParameter() {
    return extraNullParameter;
  }

  public boolean hasBeenChangedToReturnVoid() {
    return hasBeenChangedToReturnVoid;
  }

  public RemovedArgumentsInfo getRemovedArgumentsInfo() {
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

  public RewrittenPrototypeDescription withRemovedArguments(RemovedArgumentsInfo other) {
    return new RewrittenPrototypeDescription(
        hasBeenChangedToReturnVoid, extraNullParameter, removedArgumentsInfo.combine(other));
  }

  public RewrittenPrototypeDescription withExtraNullParameter() {
    return !extraNullParameter
        ? new RewrittenPrototypeDescription(hasBeenChangedToReturnVoid, true, removedArgumentsInfo)
        : this;
  }
}
