// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import static org.objectweb.asm.Opcodes.F_NEW;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
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
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import java.util.List;
import java.util.Objects;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfFrame extends CfInstruction {

  public abstract static class FrameType {

    public static FrameType initialized(DexType type) {
      return new InitializedType(type);
    }

    public static FrameType uninitializedNew(CfLabel label) {
      return new UninitializedNew(label);
    }

    public static FrameType uninitializedThis() {
      return new UninitializedThis();
    }

    public static FrameType top() {
      return Top.SINGLETON;
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

    public boolean isTop() {
      return false;
    }

    private FrameType() {}
  }

  @Override
  public boolean isFrame() {
    return true;
  }

  @Override
  public CfFrame asFrame() {
    return this;
  }

  private static class InitializedType extends FrameType {

    private final DexType type;

    private InitializedType(DexType type) {
      assert type != null;
      this.type = type;
    }

    @Override
    public String toString() {
      return type.toString();
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

    private UninitializedNew(CfLabel label) {
      this.label = label;
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

  private final Int2ReferenceSortedMap<FrameType> locals;
  private final List<FrameType> stack;

  public CfFrame(Int2ReferenceSortedMap<FrameType> locals, List<FrameType> stack) {
    assert locals.values().stream().allMatch(Objects::nonNull);
    assert stack.stream().allMatch(Objects::nonNull);
    this.locals = locals;
    this.stack = stack;
  }

  public Int2ReferenceSortedMap<FrameType> getLocals() {
    return locals;
  }

  public List<FrameType> getStack() {
    return stack;
  }

  @Override
  public void write(
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
    for (int i = 0; i < stackCount; i++) {
      stackTypes[i] = stack.get(i).getTypeOpcode(graphLens, namingLens);
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
    // TODO(mathiasr): Verify stack map frames before building IR.
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
}
