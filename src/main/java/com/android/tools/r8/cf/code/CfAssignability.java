// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.cf.code.frame.PreciseFrameType;
import com.android.tools.r8.cf.code.frame.SingleFrameType;
import com.android.tools.r8.cf.code.frame.WideFrameType;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.utils.MapUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;

public class CfAssignability {

  final AppView<?> appView;
  final DexItemFactory dexItemFactory;

  public CfAssignability(AppView<?> appView) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
  }

  public boolean isFrameTypeAssignable(FrameType source, FrameType target) {
    if (source.isSingle() != target.isSingle()) {
      return false;
    }
    return source.isSingle()
        ? isFrameTypeAssignable(source.asSingle(), target.asSingle())
        : isFrameTypeAssignable(source.asWide(), target.asWide());
  }

  @SuppressWarnings("ReferenceEquality")
  // Based on https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.10.1.2.
  public boolean isFrameTypeAssignable(SingleFrameType source, SingleFrameType target) {
    if (source.equals(target) || target.isOneWord()) {
      return true;
    }
    if (source.isOneWord()) {
      return false;
    }
    if (source.isUninitializedNew() && target.isUninitializedNew()) {
      // TODO(b/168190134): Allow for picking the offset from the target if not set.
      DexType uninitializedNewTypeSource = source.getUninitializedNewType();
      DexType uninitializedNewTypeTarget = target.getUninitializedNewType();
      return uninitializedNewTypeSource == null
          || uninitializedNewTypeTarget == null
          || uninitializedNewTypeSource == uninitializedNewTypeTarget;
    }
    // TODO(b/168190267): Clean-up the lattice.
    if (target.isPrimitive()) {
      return source.isPrimitive()
          && source.asSinglePrimitive().hasIntVerificationType()
          && target.asSinglePrimitive().hasIntVerificationType();
    }
    if (source.isPrimitive()) {
      return false;
    }
    if (target.isInitialized()) {
      if (source.isInitialized()) {
        // Both are instantiated types and we resort to primitive type/java type hierarchy checking.
        return isAssignable(
            source.asInitializedReferenceType().getInitializedType(dexItemFactory),
            target.asInitializedReferenceType().getInitializedType(dexItemFactory));
      }
      return target.asInitializedReferenceType().getInitializedType(dexItemFactory)
          == dexItemFactory.objectType;
    }
    return false;
  }

  public boolean isFrameTypeAssignable(WideFrameType source, WideFrameType target) {
    assert !source.isTwoWord();
    return source.lessThanOrEqualTo(target);
  }

  @SuppressWarnings("ReferenceEquality")
  // Rules found at https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.10.1.2
  public boolean isAssignable(DexType source, DexType target) {
    assert !target.isNullValueType();
    source = byteCharShortOrBooleanToInt(source, dexItemFactory);
    target = byteCharShortOrBooleanToInt(target, dexItemFactory);
    if (source == target) {
      return true;
    }
    if (source.isPrimitiveType() || target.isPrimitiveType()) {
      return false;
    }
    // Both are now references - everything is assignable to object.
    assert source.isReferenceType();
    assert target.isReferenceType();
    if (target == dexItemFactory.objectType) {
      return true;
    }
    // isAssignable(null, class(_, _)).
    // isAssignable(null, arrayOf(_)).
    if (source.isNullValueType()) {
      return true;
    }
    if (target.isArrayType()) {
      return source.isArrayType()
          && isAssignable(
              source.toArrayElementType(dexItemFactory), target.toArrayElementType(dexItemFactory));
    }
    assert target.isClassType();
    if (source.isArrayType()) {
      // Array types are assignable to the class types Object, Cloneable and Serializable.
      // Object is handled above, so we only need to check the other two.
      return target == dexItemFactory.cloneableType || target == dexItemFactory.serializableType;
    }
    assert source.isClassType();
    return internalIsClassTypeAssignableToClassType(source, target);
  }

  boolean internalIsClassTypeAssignableToClassType(DexType source, DexType target) {
    return true;
  }

  public boolean isAssignable(DexType source, ValueType target) {
    return isAssignable(source, target.toDexType(dexItemFactory));
  }

  private static DexType byteCharShortOrBooleanToInt(DexType type, DexItemFactory factory) {
    // byte, char, short and boolean has verification type int.
    return hasIntVerificationType(type) ? factory.intType : type;
  }

  public static boolean hasIntVerificationType(DexType type) {
    return type.isBooleanType()
        || type.isByteType()
        || type.isCharType()
        || type.isIntType()
        || type.isShortType();
  }

  public AssignabilityResult isFrameAssignable(CfFrame source, CfFrame target) {
    AssignabilityResult result = isLocalsAssignable(source.getLocals(), target.getLocals());
    return result.isSuccessful() ? isStackAssignable(source.getStack(), target.getStack()) : result;
  }

  public AssignabilityResult isLocalsAssignable(
      Int2ObjectSortedMap<FrameType> sourceLocals, Int2ObjectSortedMap<FrameType> targetLocals) {
    int localsLastKey = sourceLocals.isEmpty() ? -1 : sourceLocals.lastIntKey();
    int otherLocalsLastKey = targetLocals.isEmpty() ? -1 : targetLocals.lastIntKey();
    int maxKey = Math.max(localsLastKey, otherLocalsLastKey);
    for (int i = 0; i <= maxKey; i++) {
      FrameType sourceType =
          sourceLocals.containsKey(i) ? sourceLocals.get(i) : FrameType.oneWord();
      FrameType destinationType =
          targetLocals.containsKey(i) ? targetLocals.get(i) : FrameType.oneWord();
      if (sourceType.isWide() && destinationType.isOneWord()) {
        destinationType = FrameType.twoWord();
      }
      if (!isFrameTypeAssignable(sourceType, destinationType)) {
        return reportFailedAssignabilityResult(
            sourceLocals, targetLocals, sourceType, destinationType, i);
      }
    }
    return new SuccessfulAssignabilityResult();
  }

  private FailedAssignabilityResult reportFailedAssignabilityResult(
      Int2ObjectSortedMap<FrameType> sourceLocals,
      Int2ObjectSortedMap<FrameType> targetLocals,
      FrameType sourceType,
      FrameType destinationType,
      int index) {
    return new FailedAssignabilityResult(
        "Could not assign '"
            + MapUtils.toString(sourceLocals)
            + "' to '"
            + MapUtils.toString(targetLocals)
            + "'. The local at index "
            + index
            + " with '"
            + sourceType
            + "' not being assignable to '"
            + destinationType
            + "'");
  }

  public AssignabilityResult isStackAssignable(
      Deque<PreciseFrameType> sourceStack, Deque<PreciseFrameType> targetStack) {
    if (sourceStack.size() != targetStack.size()) {
      return new FailedAssignabilityResult(
          "Source stack "
              + Arrays.toString(sourceStack.toArray())
              + " and destination stack "
              + Arrays.toString(targetStack.toArray())
              + " is not the same size");
    }
    Iterator<PreciseFrameType> otherIterator = targetStack.iterator();
    int stackIndex = 0;
    for (PreciseFrameType sourceType : sourceStack) {
      PreciseFrameType destinationType = otherIterator.next();
      if (!isFrameTypeAssignable(sourceType, destinationType)) {
        return new FailedAssignabilityResult(
            "Could not assign '"
                + Arrays.toString(sourceStack.toArray())
                + "' to '"
                + Arrays.toString(targetStack.toArray())
                + "'. The stack value at index "
                + stackIndex
                + " (from top) with '"
                + sourceType
                + "' not being assignable to '"
                + destinationType
                + "'");
      }
      stackIndex++;
    }
    return new SuccessfulAssignabilityResult();
  }

  public abstract static class AssignabilityResult {

    public boolean isSuccessful() {
      return false;
    }

    public boolean isFailed() {
      return false;
    }

    public FailedAssignabilityResult asFailed() {
      return null;
    }
  }

  public static class SuccessfulAssignabilityResult extends AssignabilityResult {

    @Override
    public boolean isSuccessful() {
      return true;
    }
  }

  public static class FailedAssignabilityResult extends AssignabilityResult {

    private final String message;

    FailedAssignabilityResult(String message) {
      this.message = message;
    }

    public String getMessage() {
      return message;
    }

    @Override
    public boolean isFailed() {
      return true;
    }

    @Override
    public FailedAssignabilityResult asFailed() {
      return this;
    }
  }
}
