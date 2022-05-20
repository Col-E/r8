// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import static org.objectweb.asm.Opcodes.F_NEW;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.cf.code.frame.InitializedFrameType;
import com.android.tools.r8.cf.code.frame.PreciseFrameType;
import com.android.tools.r8.cf.code.frame.PrimitiveFrameType;
import com.android.tools.r8.cf.code.frame.SingleFrameType;
import com.android.tools.r8.cf.code.frame.WideFrameType;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.CfCompareHelper;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.InitClassLens;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.optimize.interfaces.analysis.CfAnalysisConfig;
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
  public static final Deque<PreciseFrameType> EMPTY_STACK = ImmutableDeque.of();

  private abstract static class BaseFrameType implements FrameType {

    @Override
    public boolean isBoolean() {
      return false;
    }

    @Override
    public boolean isByte() {
      return false;
    }

    @Override
    public boolean isChar() {
      return false;
    }

    @Override
    public boolean isDouble() {
      return false;
    }

    @Override
    public boolean isFloat() {
      return false;
    }

    @Override
    public boolean isInt() {
      return false;
    }

    @Override
    public boolean isLong() {
      return false;
    }

    @Override
    public boolean isShort() {
      return false;
    }

    @Override
    public boolean isNullType() {
      return false;
    }

    @Override
    public boolean isObject() {
      return false;
    }

    @Override
    public DexType getObjectType(DexType context) {
      assert false : "Unexpected use of getObjectType() for non-object FrameType";
      return null;
    }

    @Override
    public boolean isPrecise() {
      assert isOneWord() || isTwoWord();
      return false;
    }

    @Override
    public PreciseFrameType asPrecise() {
      assert isOneWord() || isTwoWord();
      return null;
    }

    @Override
    public boolean isPrimitive() {
      return false;
    }

    @Override
    public PrimitiveFrameType asPrimitive() {
      return null;
    }

    @Override
    public final boolean isSingle() {
      return !isWide();
    }

    @Override
    public SingleFrameType asSingle() {
      return null;
    }

    @Override
    public SinglePrimitiveFrameType asSinglePrimitive() {
      return null;
    }

    @Override
    public InitializedReferenceFrameType asInitializedReferenceType() {
      return null;
    }

    @Override
    public boolean isWide() {
      return false;
    }

    @Override
    public WideFrameType asWide() {
      return null;
    }

    @Override
    public int getWidth() {
      assert isSingle();
      return 1;
    }

    @Override
    public boolean isUninitializedNew() {
      return false;
    }

    @Override
    public UninitializedNew asUninitializedNew() {
      return null;
    }

    @Override
    public boolean isUninitialized() {
      return false;
    }

    @Override
    public UninitializedFrameType asUninitialized() {
      return null;
    }

    @Override
    public CfLabel getUninitializedLabel() {
      return null;
    }

    @Override
    public boolean isUninitializedThis() {
      return false;
    }

    @Override
    public UninitializedThis asUninitializedThis() {
      return null;
    }

    @Override
    public boolean isInitialized() {
      return false;
    }

    @Override
    public DexType getInitializedType(DexItemFactory dexItemFactory) {
      return null;
    }

    @Override
    public DexType getUninitializedNewType() {
      return null;
    }

    @Override
    public boolean isOneWord() {
      return false;
    }

    @Override
    public boolean isTwoWord() {
      return false;
    }

    private BaseFrameType() {}

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int hashCode();

  }

  public abstract static class SingletonFrameType extends BaseFrameType {

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

  public abstract static class SinglePrimitiveFrameType extends SingletonFrameType
      implements PrimitiveFrameType, SingleFrameType {

    public boolean hasIntVerificationType() {
      return false;
    }

    @Override
    public final boolean isInitialized() {
      return true;
    }

    @Override
    public final boolean isPrecise() {
      return true;
    }

    @Override
    public PreciseFrameType asPrecise() {
      return this;
    }

    @Override
    public final boolean isPrimitive() {
      return true;
    }

    @Override
    public PrimitiveFrameType asPrimitive() {
      return this;
    }

    @Override
    public final SingleFrameType asSingle() {
      return this;
    }

    @Override
    public final SinglePrimitiveFrameType asSinglePrimitive() {
      return this;
    }

    @Override
    public final SingleFrameType join(SingleFrameType frameType) {
      if (this == frameType) {
        return this;
      }
      if (hasIntVerificationType()
          && frameType.isPrimitive()
          && frameType.asSinglePrimitive().hasIntVerificationType()) {
        return FrameType.intType();
      }
      return FrameType.oneWord();
    }

    @Override
    public final String toString() {
      return getTypeName();
    }
  }

  public static class BooleanFrameType extends SinglePrimitiveFrameType {

    static final BooleanFrameType SINGLETON = new BooleanFrameType();

    private BooleanFrameType() {}

    @Override
    public DexType getInitializedType(DexItemFactory dexItemFactory) {
      return dexItemFactory.booleanType;
    }

    @Override
    public String getTypeName() {
      return "boolean";
    }

    @Override
    public Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
      throw new Unreachable("Unexpected value type: " + this);
    }

    @Override
    public boolean hasIntVerificationType() {
      return true;
    }

    @Override
    public boolean isBoolean() {
      return true;
    }
  }

  public static class ByteFrameType extends SinglePrimitiveFrameType {

    static final ByteFrameType SINGLETON = new ByteFrameType();

    private ByteFrameType() {}

    @Override
    public DexType getInitializedType(DexItemFactory dexItemFactory) {
      return dexItemFactory.byteType;
    }

    @Override
    public String getTypeName() {
      return "byte";
    }

    @Override
    public Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
      throw new Unreachable("Unexpected value type: " + this);
    }

    @Override
    public boolean hasIntVerificationType() {
      return true;
    }

    @Override
    public boolean isByte() {
      return true;
    }
  }

  public static class CharFrameType extends SinglePrimitiveFrameType {

    static final CharFrameType SINGLETON = new CharFrameType();

    private CharFrameType() {}

    @Override
    public DexType getInitializedType(DexItemFactory dexItemFactory) {
      return dexItemFactory.charType;
    }

    @Override
    public String getTypeName() {
      return "char";
    }

    @Override
    public Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
      throw new Unreachable("Unexpected value type: " + this);
    }

    @Override
    public boolean hasIntVerificationType() {
      return true;
    }

    @Override
    public boolean isChar() {
      return true;
    }
  }

  public static class FloatFrameType extends SinglePrimitiveFrameType {

    static final FloatFrameType SINGLETON = new FloatFrameType();

    private FloatFrameType() {}

    @Override
    public DexType getInitializedType(DexItemFactory dexItemFactory) {
      return dexItemFactory.floatType;
    }

    @Override
    public String getTypeName() {
      return "float";
    }

    @Override
    public Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
      return Opcodes.FLOAT;
    }

    @Override
    public boolean isFloat() {
      return true;
    }
  }

  public static class IntFrameType extends SinglePrimitiveFrameType {

    static final IntFrameType SINGLETON = new IntFrameType();

    private IntFrameType() {}

    @Override
    public DexType getInitializedType(DexItemFactory dexItemFactory) {
      return dexItemFactory.intType;
    }

    @Override
    public String getTypeName() {
      return "int";
    }

    @Override
    public Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
      return Opcodes.INTEGER;
    }

    @Override
    public boolean hasIntVerificationType() {
      return true;
    }

    @Override
    public boolean isInt() {
      return true;
    }
  }

  public static class ShortFrameType extends SinglePrimitiveFrameType {

    static final ShortFrameType SINGLETON = new ShortFrameType();

    private ShortFrameType() {}

    @Override
    public DexType getInitializedType(DexItemFactory dexItemFactory) {
      return dexItemFactory.shortType;
    }

    @Override
    public String getTypeName() {
      return "short";
    }

    @Override
    public Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
      throw new Unreachable("Unexpected value type: " + this);
    }

    @Override
    public boolean hasIntVerificationType() {
      return true;
    }

    @Override
    public boolean isShort() {
      return true;
    }
  }

  public static class InitializedReferenceFrameType extends BaseFrameType
      implements InitializedFrameType, SingleFrameType {

    private final DexType type;

    InitializedReferenceFrameType(DexType type) {
      assert type != null;
      assert type.isReferenceType();
      this.type = type;
    }

    @Override
    public boolean isPrecise() {
      return true;
    }

    @Override
    public PreciseFrameType asPrecise() {
      return this;
    }

    @Override
    public InitializedReferenceFrameType asInitializedReferenceType() {
      return this;
    }

    @Override
    public SingleFrameType join(SingleFrameType frameType) {
      if (equals(frameType)) {
        return this;
      }
      if (frameType.isOneWord() || frameType.isPrimitive() || frameType.isUninitialized()) {
        return FrameType.oneWord();
      }
      DexType otherType = frameType.asInitializedReferenceType().getInitializedType();
      assert type != otherType;
      assert type.isReferenceType();
      if (isNullType()) {
        return otherType.isReferenceType() ? frameType : FrameType.oneWord();
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
      InitializedReferenceFrameType initializedType = (InitializedReferenceFrameType) obj;
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
    public Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
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
    public DexType getObjectType(DexType context) {
      assert isObject() : "Unexpected use of getObjectType() for non-object FrameType";
      return type;
    }
  }

  public abstract static class WidePrimitiveFrameType extends SingletonFrameType
      implements PrimitiveFrameType, WideFrameType {

    @Override
    public boolean isInitialized() {
      return true;
    }

    @Override
    public boolean isPrecise() {
      return true;
    }

    @Override
    public PreciseFrameType asPrecise() {
      return this;
    }

    @Override
    public boolean isPrimitive() {
      return true;
    }

    @Override
    public PrimitiveFrameType asPrimitive() {
      return this;
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
    public int getWidth() {
      return 2;
    }

    @Override
    public WideFrameType join(WideFrameType frameType) {
      return this == frameType ? this : FrameType.twoWord();
    }

    @Override
    public final String toString() {
      return getTypeName();
    }
  }

  public static class DoubleFrameType extends WidePrimitiveFrameType {

    static final DoubleFrameType SINGLETON = new DoubleFrameType();

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
    public String getTypeName() {
      return "double";
    }

    @Override
    public Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
      return Opcodes.DOUBLE;
    }
  }

  public static class LongFrameType extends WidePrimitiveFrameType {

    static final LongFrameType SINGLETON = new LongFrameType();

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
    public String getTypeName() {
      return "long";
    }

    @Override
    public Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
      return Opcodes.LONG;
    }
  }

  public abstract static class UninitializedFrameType extends BaseFrameType
      implements PreciseFrameType, SingleFrameType {

    @Override
    public boolean isObject() {
      return true;
    }

    @Override
    public boolean isPrecise() {
      return true;
    }

    @Override
    public PreciseFrameType asPrecise() {
      return this;
    }

    @Override
    public SingleFrameType asSingle() {
      return this;
    }

    @Override
    public boolean isUninitialized() {
      return true;
    }

    @Override
    public UninitializedFrameType asUninitialized() {
      return this;
    }
  }

  public static class UninitializedNew extends UninitializedFrameType {

    private final CfLabel label;
    private final DexType type;

    UninitializedNew(CfLabel label, DexType type) {
      this.label = label;
      this.type = type;
    }

    @Override
    public DexType getObjectType(DexType context) {
      return type;
    }

    @Override
    public Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
      return label.getLabel();
    }

    @Override
    public CfLabel getUninitializedLabel() {
      return label;
    }

    @Override
    public DexType getUninitializedNewType() {
      return type;
    }

    @Override
    public boolean isUninitializedNew() {
      return true;
    }

    @Override
    public UninitializedNew asUninitializedNew() {
      return this;
    }

    @Override
    public SingleFrameType join(SingleFrameType frameType) {
      return equals(frameType) ? this : FrameType.oneWord();
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
  }

  public static class UninitializedThis extends UninitializedFrameType {

    static final UninitializedThis SINGLETON = new UninitializedThis();

    private UninitializedThis() {}

    @Override
    public DexType getObjectType(DexType context) {
      return context;
    }

    @Override
    public Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
      return Opcodes.UNINITIALIZED_THIS;
    }

    @Override
    public boolean isUninitializedThis() {
      return true;
    }

    @Override
    public UninitializedThis asUninitializedThis() {
      return this;
    }

    @Override
    public SingleFrameType join(SingleFrameType frameType) {
      if (this == frameType) {
        return this;
      }
      return FrameType.oneWord();
    }

    @Override
    public boolean equals(Object obj) {
      return this == obj;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }

    @Override
    public String toString() {
      return "uninitialized this";
    }
  }

  public static class OneWord extends SingletonFrameType implements SingleFrameType {

    static final OneWord SINGLETON = new OneWord();

    private OneWord() {}

    @Override
    public boolean isOneWord() {
      return true;
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
    public Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
      return Opcodes.TOP;
    }

    @Override
    public String toString() {
      return "oneword";
    }
  }

  static class TwoWord extends SingletonFrameType implements WideFrameType {

    static final TwoWord SINGLETON = new TwoWord();

    private TwoWord() {}

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
    public int getWidth() {
      return 2;
    }

    @Override
    public WideFrameType join(WideFrameType frameType) {
      // The join of wide with one of {double, long, wide} is wide.
      return this;
    }

    @Override
    public Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
      throw new Unreachable("Should only be used for verification");
    }

    @Override
    public String toString() {
      return "twoword";
    }
  }

  private final Int2ObjectSortedMap<FrameType> locals;
  private final Deque<PreciseFrameType> stack;

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
  public CfFrame(Deque<PreciseFrameType> stack) {
    this(EMPTY_LOCALS, stack);
    assert !stack.isEmpty() || stack == EMPTY_STACK : "Should use EMPTY_STACK instead";
  }

  // Constructor used by CfCodePrinter.
  public CfFrame(Int2ObjectAVLTreeMap<FrameType> locals, Deque<PreciseFrameType> stack) {
    this((Int2ObjectSortedMap<FrameType>) locals, stack);
    assert !locals.isEmpty() || locals == EMPTY_LOCALS : "Should use EMPTY_LOCALS instead";
    assert !stack.isEmpty() || stack == EMPTY_STACK : "Should use EMPTY_STACK instead";
  }

  // Internal constructor that does not require locals to be of the type Int2ObjectAVLTreeMap.
  private CfFrame(Int2ObjectSortedMap<FrameType> locals, Deque<PreciseFrameType> stack) {
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

  public Int2ObjectAVLTreeMap<FrameType> getMutableLocals() {
    assert locals instanceof Int2ObjectAVLTreeMap<?>;
    return (Int2ObjectAVLTreeMap<FrameType>) locals;
  }

  public Deque<PreciseFrameType> getStack() {
    return stack;
  }

  public ArrayDeque<PreciseFrameType> getMutableStack() {
    assert stack instanceof ArrayDeque<?>;
    return (ArrayDeque<PreciseFrameType>) stack;
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
    for (PreciseFrameType frameType : stack) {
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
    for (PreciseFrameType frameType : stack) {
      size += frameType.getWidth();
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
    for (PreciseFrameType frameType : stack) {
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
  public CfFrameState evaluate(
      CfFrameState frame,
      AppView<?> appView,
      CfAnalysisConfig config,
      DexItemFactory dexItemFactory) {
    return frame.check(appView, this);
  }

  public static PreciseFrameType getInitializedFrameType(
      UninitializedFrameType unInit, UninitializedFrameType other, DexType newType) {
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
      FrameType originalType = locals.get(var);
      FrameType mappedType = originalType.map(func);
      mapped = originalType != mappedType;
      if (mapped) {
        break;
      }
    }
    if (!mapped) {
      for (PreciseFrameType frameType : stack) {
        PreciseFrameType mappedType = frameType.map(func);
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
    for (PreciseFrameType frameType : stack) {
      builder.push(frameType.map(func));
    }
    return builder.build();
  }

  public static class Builder {

    private Int2ObjectSortedMap<FrameType> locals = EMPTY_LOCALS;
    private Deque<PreciseFrameType> stack = EMPTY_STACK;

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

    public Builder push(PreciseFrameType frameType) {
      ensureMutableStack();
      stack.addLast(frameType);
      return this;
    }

    public Builder setLocals(Int2ObjectSortedMap<FrameType> locals) {
      this.locals = locals;
      return this;
    }

    public Builder setStack(Deque<PreciseFrameType> stack) {
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
