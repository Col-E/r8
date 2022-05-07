// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import static org.objectweb.asm.Opcodes.F_NEW;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.cf.code.frame.SingleFrameType;
import com.android.tools.r8.cf.code.frame.WideFrameType;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.CfCodeStackMapValidatingException;
import com.android.tools.r8.graph.CfCompareHelper;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.InitClassLens;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.optimize.interfaces.analysis.CfFrameState;
import com.android.tools.r8.utils.IntObjConsumer;
import com.android.tools.r8.utils.collections.ImmutableDeque;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMaps;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Consumer;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfFrame extends CfInstruction implements Cloneable {

  public static final Int2ObjectSortedMap<FrameType> EMPTY_LOCALS = Int2ObjectSortedMaps.emptyMap();
  public static final Deque<FrameType> EMPTY_STACK = ImmutableDeque.of();

  public abstract static class FrameType {

    public static DoubleFrameType doubleType() {
      return DoubleFrameType.SINGLETON;
    }

    public static IntFrameType intType() {
      return IntFrameType.SINGLETON;
    }

    public static LongFrameType longType() {
      return LongFrameType.SINGLETON;
    }

    public static FrameType initialized(DexType type) {
      if (type.isPrimitiveType()) {
        if (type.isWideType()) {
          return type.isDoubleType() ? doubleType() : longType();
        } else if (type.isIntType()) {
          return intType();
        }
      }
      return new SingleInitializedType(type);
    }

    public static FrameType uninitializedNew(CfLabel label, DexType typeToInitialize) {
      return new UninitializedNew(label, typeToInitialize);
    }

    public static FrameType uninitializedThis() {
      return UninitializedThis.SINGLETON;
    }

    public static OneWord oneWord() {
      return OneWord.SINGLETON;
    }

    public static TwoWord twoWord() {
      return TwoWord.SINGLETON;
    }

    public FrameType asFrameType() {
      return this;
    }

    abstract Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens);

    public boolean isDouble() {
      return false;
    }

    public boolean isInt() {
      return false;
    }

    public boolean isLong() {
      return false;
    }

    public boolean isNullType() {
      return false;
    }

    public boolean isObject() {
      return false;
    }

    public DexType getObjectType(ProgramMethod context) {
      assert false : "Unexpected use of getObjectType() for non-object FrameType";
      return null;
    }

    public boolean isPrimitive() {
      return false;
    }

    public final boolean isSingle() {
      return !isWide();
    }

    public SingleFrameType asSingle() {
      return null;
    }

    public SingleInitializedType asSingleInitializedType() {
      return null;
    }

    public boolean isWide() {
      return false;
    }

    public WideFrameType asWide() {
      return null;
    }

    public boolean isUninitializedNew() {
      return false;
    }

    public boolean isUninitializedObject() {
      return false;
    }

    public CfLabel getUninitializedLabel() {
      return null;
    }

    public boolean isUninitializedThis() {
      return false;
    }

    public boolean isInitialized() {
      return false;
    }

    public boolean isSingleInitialized() {
      return false;
    }

    public DexType getInitializedType(DexItemFactory dexItemFactory) {
      return null;
    }

    public DexType getUninitializedNewType() {
      return null;
    }

    public boolean isOneWord() {
      return false;
    }

    public boolean isSpecific() {
      return true;
    }

    public boolean isTwoWord() {
      return false;
    }

    FrameType map(java.util.function.Function<DexType, DexType> func) {
      if (isObject()) {
        if (isInitialized()) {
          DexType type = asSingleInitializedType().getInitializedType();
          DexType newType = func.apply(type);
          if (type != newType) {
            return initialized(newType);
          }
        }
        if (isUninitializedNew()) {
          DexType type = getUninitializedNewType();
          DexType newType = func.apply(type);
          if (type != newType) {
            return uninitializedNew(getUninitializedLabel(), newType);
          }
        }
      }
      return this;
    }

    private FrameType() {}

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int hashCode();

    public static FrameType fromPreciseMemberType(MemberType memberType, DexItemFactory factory) {
      assert memberType.isPrecise();
      switch (memberType) {
        case OBJECT:
          return FrameType.initialized(factory.objectType);
        case BOOLEAN_OR_BYTE:
          return FrameType.initialized(factory.intType);
        case CHAR:
          return FrameType.initialized(factory.intType);
        case SHORT:
          return FrameType.initialized(factory.intType);
        case INT:
          return FrameType.initialized(factory.intType);
        case FLOAT:
          return FrameType.initialized(factory.floatType);
        case LONG:
          return FrameType.longType();
        case DOUBLE:
          return FrameType.doubleType();
        default:
          throw new Unreachable("Unexpected MemberType: " + memberType);
      }
    }

    public static FrameType fromNumericType(NumericType numericType, DexItemFactory factory) {
      return FrameType.initialized(numericType.toDexType(factory));
    }
  }

  public abstract static class SingletonFrameType extends FrameType {

    @Override
    public final boolean equals(Object obj) {
      return this == obj;
    }

    @Override
    public final int hashCode() {
      return System.identityHashCode(this);
    }
  }

  @Override
  public boolean isFrame() {
    return true;
  }

  @Override
  public CfFrame asFrame() {
    return this;
  }

  @Override
  public int getCompareToId() {
    return CfCompareHelper.FRAME_COMPARE_ID;
  }

  @Override
  public int internalAcceptCompareTo(
      CfInstruction other, CompareToVisitor visitor, CfCompareHelper helper) {
    // The frame should be determined by the code so it should for equal iff the code is equal.
    // Thus we just require the frame to be in place.
    return CfCompareHelper.compareIdUniquelyDeterminesEquality(this, other);
  }

  public abstract static class SinglePrimitiveType extends SingletonFrameType
      implements SingleFrameType {

    @Override
    public boolean isInitialized() {
      return true;
    }

    @Override
    public boolean isPrimitive() {
      return true;
    }

    @Override
    public SingleFrameType asSingle() {
      return this;
    }
  }

  public static class IntFrameType extends SinglePrimitiveType {

    private static final IntFrameType SINGLETON = new IntFrameType();

    private IntFrameType() {}

    @Override
    public DexType getInitializedType(DexItemFactory dexItemFactory) {
      return dexItemFactory.intType;
    }

    @Override
    public boolean isInt() {
      return true;
    }

    @Override
    Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
      return Opcodes.INTEGER;
    }

    @Override
    public SingleFrameType join(SingleFrameType frameType) {
      if (this == frameType) {
        return this;
      }
      if (frameType.isOneWord() || frameType.isUninitializedObject()) {
        return oneWord();
      }
      assert frameType.isInitialized();
      return CfAssignability.hasIntVerificationType(
              frameType.asSingleInitializedType().getInitializedType())
          ? this
          : oneWord();
    }

    @Override
    public String toString() {
      return "int";
    }
  }

  public static class SingleInitializedType extends FrameType implements SingleFrameType {

    private final DexType type;

    private SingleInitializedType(DexType type) {
      assert type != null;
      assert !type.isIntType();
      this.type = type;
    }

    @Override
    public SingleInitializedType asSingleInitializedType() {
      return this;
    }

    @Override
    public SingleFrameType join(SingleFrameType frameType) {
      if (equals(frameType)) {
        return this;
      }
      if (frameType.isOneWord() || frameType.isUninitializedObject()) {
        return oneWord();
      }
      assert frameType.isInitialized();
      if (frameType.isPrimitive()) {
        if (frameType.isInt()) {
          return CfAssignability.hasIntVerificationType(type) ? frameType : oneWord();
        }
        // The rest of the primitives are still represented using SingleInitializedType.
        DexType otherType = frameType.asSingleInitializedType().getInitializedType();
        return CfAssignability.hasIntVerificationType(type)
                && CfAssignability.hasIntVerificationType(otherType)
            ? intType()
            : oneWord();
      }
      DexType otherType = frameType.asSingleInitializedType().getInitializedType();
      assert type != otherType;
      if (type.isPrimitiveType()) {
        return oneWord();
      }
      assert type.isReferenceType();
      if (isNullType()) {
        return otherType.isReferenceType() ? frameType : oneWord();
      }
      if (frameType.isNullType()) {
        return this;
      }
      assert type.isArrayType() || type.isClassType();
      assert otherType.isArrayType() || otherType.isClassType();
      // TODO(b/214496607): Implement join of different reference types using class hierarchy.
      throw new Unimplemented();
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      SingleInitializedType initializedType = (SingleInitializedType) obj;
      return type == initializedType.type;
    }

    @Override
    public int hashCode() {
      return type.hashCode();
    }

    @Override
    public String toString() {
      return "Initialized(" + type.toString() + ")";
    }

    @Override
    Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
      DexType rewrittenType = graphLens.lookupType(type);
      if (rewrittenType == DexItemFactory.nullValueType) {
        return Opcodes.NULL;
      }
      switch (rewrittenType.toShorty()) {
        case 'L':
          return namingLens.lookupInternalName(rewrittenType);
        case 'I':
          return Opcodes.INTEGER;
        case 'F':
          return Opcodes.FLOAT;
        case 'J':
          return Opcodes.LONG;
        case 'D':
          return Opcodes.DOUBLE;
        default:
          throw new Unreachable("Unexpected value type: " + rewrittenType);
      }
    }

    @Override
    public SingleFrameType asSingle() {
      return this;
    }

    @Override
    public boolean isWide() {
      return false;
    }

    @Override
    public boolean isInitialized() {
      return true;
    }

    @Override
    public boolean isPrimitive() {
      return type.isPrimitiveType();
    }

    @Override
    public boolean isSingleInitialized() {
      return true;
    }

    public DexType getInitializedType() {
      return type;
    }

    @Override
    public DexType getInitializedType(DexItemFactory dexItemFactory) {
      return getInitializedType();
    }

    @Override
    public boolean isNullType() {
      return type.isNullValueType();
    }

    @Override
    public boolean isObject() {
      return type.isReferenceType();
    }

    @Override
    public DexType getObjectType(ProgramMethod context) {
      assert isObject() : "Unexpected use of getObjectType() for non-object FrameType";
      return type;
    }
  }

  public abstract static class WideInitializedType extends SingletonFrameType
      implements WideFrameType {

    @Override
    public boolean isInitialized() {
      return true;
    }

    @Override
    public boolean isPrimitive() {
      return true;
    }

    @Override
    public boolean isWide() {
      return true;
    }

    @Override
    public WideFrameType asWide() {
      return this;
    }

    @Override
    public WideFrameType join(WideFrameType frameType) {
      return this == frameType ? this : twoWord();
    }
  }

  private static class DoubleFrameType extends WideInitializedType {

    private static final DoubleFrameType SINGLETON = new DoubleFrameType();

    private DoubleFrameType() {}

    @Override
    public boolean isDouble() {
      return true;
    }

    @Override
    public DexType getInitializedType(DexItemFactory dexItemFactory) {
      return dexItemFactory.doubleType;
    }

    @Override
    Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
      return Opcodes.DOUBLE;
    }

    @Override
    public String toString() {
      return "double";
    }
  }

  public static class LongFrameType extends WideInitializedType {

    private static final LongFrameType SINGLETON = new LongFrameType();

    private LongFrameType() {}

    @Override
    public boolean isLong() {
      return true;
    }

    @Override
    public DexType getInitializedType(DexItemFactory dexItemFactory) {
      return dexItemFactory.longType;
    }

    @Override
    Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
      return Opcodes.LONG;
    }

    @Override
    public String toString() {
      return "long";
    }
  }

  private static class UninitializedNew extends FrameType implements SingleFrameType {

    private final CfLabel label;
    private final DexType type;

    private UninitializedNew(CfLabel label, DexType type) {
      this.label = label;
      this.type = type;
    }

    @Override
    public SingleFrameType asSingle() {
      return this;
    }

    @Override
    public SingleFrameType join(SingleFrameType frameType) {
      return equals(frameType) ? this : oneWord();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      UninitializedNew uninitializedNew = (UninitializedNew) o;
      return label == uninitializedNew.label && type == uninitializedNew.type;
    }

    @Override
    public int hashCode() {
      return Objects.hash(label, type);
    }

    @Override
    public String toString() {
      return "uninitialized new";
    }

    @Override
    Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
      return label.getLabel();
    }

    @Override
    public boolean isObject() {
      return true;
    }

    @Override
    public DexType getObjectType(ProgramMethod context) {
      return type;
    }

    @Override
    public boolean isUninitializedNew() {
      return true;
    }

    @Override
    public boolean isUninitializedObject() {
      return true;
    }

    @Override
    public CfLabel getUninitializedLabel() {
      return label;
    }

    @Override
    public DexType getUninitializedNewType() {
      return type;
    }
  }

  private static class UninitializedThis extends SingletonFrameType implements SingleFrameType {

    private static final UninitializedThis SINGLETON = new UninitializedThis();

    private UninitializedThis() {}

    @Override
    public SingleFrameType asSingle() {
      return this;
    }

    @Override
    public SingleFrameType join(SingleFrameType frameType) {
      if (this == frameType) {
        return this;
      }
      return oneWord();
    }

    @Override
    Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
      return Opcodes.UNINITIALIZED_THIS;
    }

    @Override
    public String toString() {
      return "uninitialized this";
    }

    @Override
    public boolean isObject() {
      return true;
    }

    @Override
    public DexType getObjectType(ProgramMethod context) {
      return context.getHolderType();
    }

    @Override
    public boolean isUninitializedObject() {
      return true;
    }

    @Override
    public boolean isUninitializedThis() {
      return true;
    }
  }

  private static class OneWord extends SingletonFrameType implements SingleFrameType {

    private static final OneWord SINGLETON = new OneWord();

    private OneWord() {}

    @Override
    public boolean isOneWord() {
      return true;
    }

    @Override
    public boolean isSpecific() {
      return false;
    }

    @Override
    public SingleFrameType asSingle() {
      return this;
    }

    @Override
    public SingleFrameType join(SingleFrameType frameType) {
      return this;
    }

    @Override
    Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
      return Opcodes.TOP;
    }

    @Override
    public String toString() {
      return "oneword";
    }
  }

  private static class TwoWord extends SingletonFrameType implements WideFrameType {

    private static final TwoWord SINGLETON = new TwoWord();

    private TwoWord() {}

    @Override
    public boolean isSpecific() {
      return false;
    }

    @Override
    public boolean isTwoWord() {
      return true;
    }

    @Override
    public boolean isWide() {
      return true;
    }

    @Override
    public WideFrameType asWide() {
      return this;
    }

    @Override
    public WideFrameType join(WideFrameType frameType) {
      // The join of wide with one of {double, long, wide} is wide.
      return this;
    }

    @Override
    Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
      throw new Unreachable("Should only be used for verification");
    }

    @Override
    public String toString() {
      return "twoword";
    }
  }

  private final Int2ObjectSortedMap<FrameType> locals;
  private final Deque<FrameType> stack;

  // Constructor used by CfCodePrinter.
  public CfFrame() {
    this(EMPTY_LOCALS, EMPTY_STACK);
  }

  // Constructor used by CfCodePrinter.
  public CfFrame(Int2ObjectAVLTreeMap<FrameType> locals) {
    this((Int2ObjectSortedMap<FrameType>) locals, EMPTY_STACK);
    assert !locals.isEmpty() || locals == EMPTY_LOCALS : "Should use EMPTY_LOCALS instead";
  }

  // Constructor used by CfCodePrinter.
  public CfFrame(Deque<FrameType> stack) {
    this(EMPTY_LOCALS, stack);
    assert !stack.isEmpty() || stack == EMPTY_STACK : "Should use EMPTY_STACK instead";
  }

  // Constructor used by CfCodePrinter.
  public CfFrame(Int2ObjectAVLTreeMap<FrameType> locals, Deque<FrameType> stack) {
    this((Int2ObjectSortedMap<FrameType>) locals, stack);
    assert !locals.isEmpty() || locals == EMPTY_LOCALS : "Should use EMPTY_LOCALS instead";
    assert !stack.isEmpty() || stack == EMPTY_STACK : "Should use EMPTY_STACK instead";
  }

  // Internal constructor that does not require locals to be of the type Int2ObjectAVLTreeMap.
  private CfFrame(Int2ObjectSortedMap<FrameType> locals, Deque<FrameType> stack) {
    assert locals.values().stream().allMatch(Objects::nonNull);
    assert stack.stream().allMatch(Objects::nonNull);
    this.locals = locals;
    this.stack = stack;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public CfFrame clone() {
    return new CfFrame(locals, stack);
  }

  public CfFrame mutableCopy() {
    return new CfFrame(
        (Int2ObjectSortedMap<FrameType>) new Int2ObjectAVLTreeMap<>(locals),
        new ArrayDeque<>(stack));
  }

  public void forEachLocal(IntObjConsumer<FrameType> consumer) {
    for (Int2ObjectMap.Entry<FrameType> entry : locals.int2ObjectEntrySet()) {
      consumer.accept(entry.getIntKey(), entry.getValue());
    }
  }

  public Int2ObjectSortedMap<FrameType> getLocals() {
    return locals;
  }

  public Deque<FrameType> getStack() {
    return stack;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    CfFrame frame = (CfFrame) obj;
    return locals.equals(frame.locals) && Iterables.elementsEqual(stack, frame.stack);
  }

  @Override
  public int hashCode() {
    // Generates a hash that is identical to Objects.hash(locals, stack[0], ..., stack[n]).
    int result = 31 + locals.hashCode();
    for (FrameType frameType : stack) {
      result = 31 * result + frameType.hashCode();
    }
    return result;
  }

  @Override
  public void write(
      AppView<?> appView,
      ProgramMethod context,
      DexItemFactory dexItemFactory,
      GraphLens graphLens,
      InitClassLens initClassLens,
      NamingLens namingLens,
      LensCodeRewriterUtils rewriter,
      MethodVisitor visitor) {
    int stackCount = computeStackCount();
    Object[] stackTypes = computeStackTypes(stackCount, graphLens, namingLens);
    int localsCount = computeLocalsCount();
    Object[] localsTypes = computeLocalsTypes(localsCount, graphLens, namingLens);
    visitor.visitFrame(F_NEW, localsCount, localsTypes, stackCount, stackTypes);
  }

  @Override
  public int bytecodeSizeUpperBound() {
    return 0;
  }

  private int computeStackCount() {
    return stack.size();
  }

  public int computeStackSize() {
    int size = 0;
    for (FrameType frameType : stack) {
      size += frameType.isWide() ? 2 : 1;
    }
    return size;
  }

  private Object[] computeStackTypes(int stackCount, GraphLens graphLens, NamingLens namingLens) {
    assert stackCount == stack.size();
    if (stackCount == 0) {
      return null;
    }
    Object[] stackTypes = new Object[stackCount];
    int index = 0;
    for (FrameType frameType : stack) {
      stackTypes[index++] = frameType.getTypeOpcode(graphLens, namingLens);
    }
    return stackTypes;
  }

  private int computeLocalsCount() {
    if (locals.isEmpty()) {
      return 0;
    }
    // Compute the size of locals. Absent indexes are denoted by a single-width element (ie, TOP).
    int maxRegister = locals.lastIntKey();
    int localsCount = 0;
    for (int i = 0; i <= maxRegister; i++) {
      localsCount++;
      FrameType type = locals.get(i);
      if (type != null && type.isWide()) {
        i++;
      }
    }
    return localsCount;
  }

  private Object[] computeLocalsTypes(int localsCount, GraphLens graphLens, NamingLens namingLens) {
    if (localsCount == 0) {
      return null;
    }
    int maxRegister = locals.lastIntKey();
    Object[] localsTypes = new Object[localsCount];
    int localIndex = 0;
    for (int i = 0; i <= maxRegister; i++) {
      FrameType type = locals.get(i);
      localsTypes[localIndex++] =
          type == null ? Opcodes.TOP : type.getTypeOpcode(graphLens, namingLens);
      if (type != null && type.isWide()) {
        i++;
      }
    }
    return localsTypes;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    code.setStateFromFrame(this);
  }

  @Override
  public boolean emitsIR() {
    return false;
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, CfCode code, ProgramMethod context) {
    return ConstraintWithTarget.ALWAYS;
  }

  @Override
  public void evaluate(
      CfFrameVerificationHelper frameBuilder,
      DexMethod context,
      AppView<?> appView,
      DexItemFactory dexItemFactory) {
    frameBuilder.checkFrameAndSet(this);
  }

  @Override
  public CfFrameState evaluate(
      CfFrameState frame,
      ProgramMethod context,
      AppView<?> appView,
      DexItemFactory dexItemFactory) {
    return frame.check(appView, this);
  }

  public CfFrame markInstantiated(FrameType uninitializedType, DexType initType) {
    if (uninitializedType.isInitialized()) {
      throw CfCodeStackMapValidatingException.error(
          "Cannot instantiate already instantiated type " + uninitializedType);
    }
    CfFrame.Builder builder = CfFrame.builder().allocateStack(stack.size());
    forEachLocal(
        (localIndex, frameType) ->
            builder.store(
                localIndex, getInitializedFrameType(uninitializedType, frameType, initType)));
    for (FrameType frameType : stack) {
      builder.push(getInitializedFrameType(uninitializedType, frameType, initType));
    }
    return builder.build();
  }

  public static FrameType getInitializedFrameType(
      FrameType unInit, FrameType other, DexType newType) {
    assert !unInit.isInitialized();
    if (other.isInitialized()) {
      return other;
    }
    if (unInit.isUninitializedThis() && other.isUninitializedThis()) {
      return FrameType.initialized(newType);
    }
    if (unInit.isUninitializedNew()
        && other.isUninitializedNew()
        && unInit.getUninitializedLabel() == other.getUninitializedLabel()) {
      return FrameType.initialized(newType);
    }
    return other;
  }

  public CfFrame map(java.util.function.Function<DexType, DexType> func) {
    boolean mapped = false;
    for (int var : locals.keySet()) {
      CfFrame.FrameType originalType = locals.get(var);
      CfFrame.FrameType mappedType = originalType.map(func);
      mapped = originalType != mappedType;
      if (mapped) {
        break;
      }
    }
    if (!mapped) {
      for (FrameType frameType : stack) {
        CfFrame.FrameType mappedType = frameType.map(func);
        mapped = frameType != mappedType;
        if (mapped) {
          break;
        }
      }
    }
    if (!mapped) {
      return this;
    }
    Builder builder = builder();
    for (Int2ObjectMap.Entry<FrameType> entry : locals.int2ObjectEntrySet()) {
      builder.store(entry.getIntKey(), entry.getValue().map(func));
    }
    for (FrameType frameType : stack) {
      builder.push(frameType.map(func));
    }
    return builder.build();
  }

  public static class Builder {

    private Int2ObjectSortedMap<FrameType> locals = EMPTY_LOCALS;
    private Deque<FrameType> stack = EMPTY_STACK;

    private boolean hasIncompleteUninitializedNew = false;
    private boolean seenStore = false;

    public Builder allocateStack(int size) {
      assert stack == EMPTY_STACK;
      if (size > 0) {
        stack = new ArrayDeque<>(size);
      }
      return this;
    }

    public Builder appendLocal(FrameType frameType) {
      // Mixing appendLocal() and store() is somewhat error prone. Catch it if we ever do it.
      assert !seenStore;
      int localIndex = locals.size();
      return internalStore(localIndex, frameType);
    }

    public Builder apply(Consumer<Builder> consumer) {
      consumer.accept(this);
      return this;
    }

    public boolean hasIncompleteUninitializedNew() {
      return hasIncompleteUninitializedNew;
    }

    public Builder setHasIncompleteUninitializedNew() {
      hasIncompleteUninitializedNew = true;
      return this;
    }

    public boolean hasLocal(int localIndex) {
      return locals.containsKey(localIndex);
    }

    public FrameType getLocal(int localIndex) {
      assert hasLocal(localIndex);
      return locals.get(localIndex);
    }

    public Builder push(FrameType frameType) {
      ensureMutableStack();
      stack.addLast(frameType);
      return this;
    }

    public Builder setLocals(Int2ObjectSortedMap<FrameType> locals) {
      this.locals = locals;
      return this;
    }

    public Builder setStack(Deque<FrameType> stack) {
      this.stack = stack;
      return this;
    }

    public Builder store(int localIndex, FrameType frameType) {
      seenStore = true;
      return internalStore(localIndex, frameType);
    }

    private Builder internalStore(int localIndex, FrameType frameType) {
      ensureMutableLocals();
      locals.put(localIndex, frameType);
      if (frameType.isWide()) {
        locals.put(localIndex + 1, frameType);
      }
      return this;
    }

    public CfFrame build() {
      return new CfFrame(locals, stack);
    }

    public CfFrame buildMutable() {
      ensureMutableLocals();
      ensureMutableStack();
      return build();
    }

    private void ensureMutableLocals() {
      if (locals == EMPTY_LOCALS) {
        locals = new Int2ObjectAVLTreeMap<>();
      }
    }

    private void ensureMutableStack() {
      if (stack == EMPTY_STACK) {
        stack = new ArrayDeque<>();
      }
    }
  }
}
