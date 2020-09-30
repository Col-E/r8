// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import static org.objectweb.asm.Opcodes.F_NEW;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCodeStackMapValidatingException;
import com.android.tools.r8.graph.CfCompareHelper;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
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
import it.unimi.dsi.fastutil.ints.Int2ReferenceAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfFrame extends CfInstruction {

  public abstract static class FrameType {

    public static FrameType initialized(DexType type) {
      return new InitializedType(type);
    }

    public static FrameType uninitializedNew(CfLabel label, DexType typeToInitialize) {
      return new UninitializedNew(label, typeToInitialize);
    }

    public static FrameType uninitializedThis() {
      return new UninitializedThis();
    }

    public static FrameType top() {
      return Top.SINGLETON;
    }

    public static FrameType oneWord() {
      return OneWord.SINGLETON;
    }

    public static FrameType twoWord() {
      return TwoWord.SINGLETON;
    }

    abstract Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens);

    public boolean isWide() {
      return false;
    }

    public boolean isUninitializedNew() {
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

    public DexType getInitializedType() {
      return null;
    }

    public DexType getUninitializedNewType() {
      return null;
    }

    public boolean isTop() {
      return false;
    }

    public boolean isOneWord() {
      return false;
    }

    public boolean isTwoWord() {
      return false;
    }

    private FrameType() {}

    public static FrameType fromMemberType(MemberType memberType, DexItemFactory factory) {
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
          return FrameType.initialized(factory.longType);
        case DOUBLE:
          return FrameType.initialized(factory.doubleType);
        case INT_OR_FLOAT:
          return FrameType.oneWord();
        case LONG_OR_DOUBLE:
          return FrameType.twoWord();
        default:
          throw new Unreachable("Unexpected MemberType: " + memberType);
      }
    }

    public static FrameType fromNumericType(NumericType numericType, DexItemFactory factory) {
      return FrameType.initialized(numericType.dexTypeFor(factory));
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
  public int internalCompareTo(CfInstruction other, CfCompareHelper helper) {
    // The frame should be determined by the code so it should for equal iff the code is equal.
    // Thus we just require the frame to be in place.
    return CfCompareHelper.compareIdUniquelyDeterminesEquality(this, other);
  }

  private static class InitializedType extends FrameType {

    private final DexType type;

    private InitializedType(DexType type) {
      assert type != null;
      this.type = type;
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
    public boolean isWide() {
      return type.isPrimitiveType() && (type.toShorty() == 'J' || type.toShorty() == 'D');
    }

    @Override
    public boolean isInitialized() {
      return true;
    }

    @Override
    public DexType getInitializedType() {
      return type;
    }
  }

  private static class Top extends FrameType {

    private static final Top SINGLETON = new Top();

    @Override
    public String toString() {
      return "top";
    }

    @Override
    Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
      return Opcodes.TOP;
    }

    @Override
    public boolean isTop() {
      return true;
    }
  }

  private static class UninitializedNew extends FrameType {
    private final CfLabel label;
    private final DexType type;

    private UninitializedNew(CfLabel label, DexType type) {
      this.label = label;
      this.type = type;
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
    public boolean isUninitializedNew() {
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

  private static class UninitializedThis extends FrameType {

    private UninitializedThis() {}

    @Override
    Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
      return Opcodes.UNINITIALIZED_THIS;
    }

    @Override
    public String toString() {
      return "uninitialized this";
    }

    @Override
    public boolean isUninitializedThis() {
      return true;
    }
  }

  private static class OneWord extends FrameType {

    private static final OneWord SINGLETON = new OneWord();

    @Override
    Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
      throw new Unreachable("Should only be used for verification");
    }

    @Override
    public boolean isOneWord() {
      return true;
    }

    @Override
    public String toString() {
      return "oneword";
    }
  }

  private static class TwoWord extends FrameType {

    private static final TwoWord SINGLETON = new TwoWord();

    @Override
    Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
      throw new Unreachable("Should only be used for verification");
    }

    @Override
    public boolean isWide() {
      return true;
    }

    @Override
    public boolean isTwoWord() {
      return true;
    }

    @Override
    public String toString() {
      return "twoword";
    }
  }

  private final Int2ReferenceSortedMap<FrameType> locals;
  private final Deque<FrameType> stack;

  public CfFrame(Int2ReferenceSortedMap<FrameType> locals, Deque<FrameType> stack) {
    assert locals.values().stream().allMatch(Objects::nonNull);
    assert stack.stream().allMatch(Objects::nonNull);
    this.locals = locals;
    this.stack = stack;
  }

  public Int2ReferenceSortedMap<FrameType> getLocals() {
    return locals;
  }

  public Deque<FrameType> getStack() {
    return stack;
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

  private int computeStackCount() {
    return stack.size();
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
      InliningConstraints inliningConstraints, DexProgramClass context) {
    return ConstraintWithTarget.ALWAYS;
  }

  @Override
  public void evaluate(
      CfFrameVerificationHelper frameBuilder,
      DexType context,
      DexType returnType,
      DexItemFactory factory,
      InitClassLens initClassLens) {
    frameBuilder.verifyFrameAndSet(this);
  }

  public CfFrame markInstantiated(FrameType uninitializedType, DexType initType) {
    if (uninitializedType.isInitialized()) {
      throw CfCodeStackMapValidatingException.error(
          "Cannot instantiate already instantiated type " + uninitializedType);
    }
    Int2ReferenceSortedMap<FrameType> newLocals = new Int2ReferenceAVLTreeMap<>();
    for (int var : locals.keySet()) {
      newLocals.put(var, getInitializedFrameType(uninitializedType, locals.get(var), initType));
    }
    Deque<FrameType> newStack = new ArrayDeque<>();
    for (FrameType frameType : stack) {
      newStack.addLast(getInitializedFrameType(uninitializedType, frameType, initType));
    }
    return new CfFrame(newLocals, newStack);
  }

  private FrameType getInitializedFrameType(FrameType unInit, FrameType other, DexType newType) {
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
}
