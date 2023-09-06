// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.outliner;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;

import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.contexts.CompilationContext.ProcessorContext;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.features.ClassToFeatureSplitMap;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClasspathMethod;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.analysis.type.ArrayTypeElement;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.PrimitiveTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Add;
import com.android.tools.r8.ir.code.Assume;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlock.ThrowingInfo;
import com.android.tools.r8.ir.code.Binop;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.Div;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.code.LinearFlowInstructionListIterator;
import com.android.tools.r8.ir.code.Mul;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.OutlineCallerPosition;
import com.android.tools.r8.ir.code.Position.OutlineCallerPosition.OutlineCallerPositionBuilder;
import com.android.tools.r8.ir.code.Position.OutlinePosition;
import com.android.tools.r8.ir.code.Rem;
import com.android.tools.r8.ir.code.Sub;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.code.ValueTypeConstraint;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.conversion.MethodConversionOptions.MutableMethodConversionOptions;
import com.android.tools.r8.ir.conversion.MethodProcessorEventConsumer;
import com.android.tools.r8.ir.conversion.SourceCode;
import com.android.tools.r8.ir.conversion.passes.MoveResultRewriter;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.ir.optimize.CodeRewriter;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackDelayed;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackIgnore;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.OutlineOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.StringUtils.BraceType;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Support class for implementing outlining (i.e. extracting common code patterns as methods).
 *
 * <p>Outlining happens in three steps.
 *
 * <ul>
 *   <li>First, all methods are converted to IR and passed to {@link
 *       OutlinerImpl#collectOutlineSites} to identify outlining candidates and the methods
 *       containing each candidate. IR is converted to the output format (DEX or CF) and thrown away
 *       along with the outlining candidates; only a list of lists of methods is kept, where each
 *       list of methods corresponds to methods containing an outlining candidate.
 *   <li>Second, {@link OutlinerImpl#selectMethodsForOutlining()} is called to retain the lists of
 *       methods found in the first step that are large enough (see {@link InternalOptions#outline}
 *       {@link OutlineOptions#threshold}). Each selected method is then converted back to IR and
 *       passed to {@link OutlinerImpl#identifyOutlineSites(IRCode)}, which then stores concrete
 *       outlining candidates in {@link OutlinerImpl#outlineSites}.
 *   <li>Third, {@link OutlinerImpl#buildOutlineMethods()} is called to construct the <em>outline
 *       support classes</em> containing a static helper method for each outline candidate that
 *       occurs frequently enough. Each selected method is then converted to IR, passed to {@link
 *       OutlinerImpl#applyOutliningCandidate(IRCode)} to perform the outlining, and converted back
 *       to the output format (DEX or CF).
 * </ul>
 */
public class OutlinerImpl extends Outliner {

  /**
   * Result of first step (see {@link OutlinerImpl#prepareForPrimaryOptimizationPass(GraphLens)}
   * ()}.
   */
  private OutlineCollection outlineCollection;

  /** Result of second step (see {@link OutlinerImpl#selectMethodsForOutlining()}. */
  private final Map<Outline, List<ProgramMethod>> outlineSites = new HashMap<>();

  /** Result of third step (see {@link OutlinerImpl#buildOutlineMethods()}. */
  private final Map<Outline, DexMethod> generatedOutlines = new HashMap<>();

  static final int MAX_IN_SIZE = 5; // Avoid using ranged calls for outlined code.

  private final AppView<AppInfoWithLiveness> appView;
  private final DexItemFactory dexItemFactory;
  private final InliningConstraints inliningConstraints;

  private abstract static class OutlineInstruction {

    // Value signaling that this is the one allowed temporary register for an outline.
    private static final int OUTLINE_TEMP = -1;

    public enum OutlineInstructionType {
      ADD,
      SUB,
      MUL,
      DIV,
      REM,
      INVOKE,
      NEW;

      static OutlineInstructionType fromInstruction(Instruction instruction) {
        if (instruction.isAdd()) {
          return ADD;
        }
        if (instruction.isSub()) {
          return SUB;
        }
        if (instruction.isMul()) {
          return MUL;
        }
        if (instruction.isDiv()) {
          return DIV;
        }
        if (instruction.isRem()) {
          return REM;
        }
        if (instruction.isInvokeMethod()) {
          return INVOKE;
        }
        if (instruction.isNewInstance()) {
          return NEW;
        }
        throw new Unreachable();
      }
    }

    protected final OutlineInstructionType type;

    protected OutlineInstruction(OutlineInstructionType type) {
      this.type = type;
    }

    static OutlineInstruction fromInstruction(Instruction instruction) {
      if (instruction.isBinop()) {
        return BinOpOutlineInstruction.fromInstruction(instruction.asBinop());
      }
      if (instruction.isNewInstance()) {
        return new NewInstanceOutlineInstruction(instruction.asNewInstance().clazz);
      }
      assert instruction.isInvokeMethod();
      return InvokeOutlineInstruction.fromInstruction(instruction.asInvokeMethod());
    }

    @Override
    public int hashCode() {
      return type.ordinal();
    }

    public int compareTo(OutlineInstruction other) {
      return type.compareTo(other.type);
    }

    @Override
    public abstract boolean equals(Object other);

    public abstract String getDetailsString();

    public abstract String getInstructionName();

    public abstract boolean hasOutValue();

    public abstract int numberOfInputs();

    public abstract int createInstruction(IRBuilder builder, Outline outline, int argumentMapIndex);

    public abstract boolean needsLensRewriting(GraphLens currentGraphLens);
  }

  private static class BinOpOutlineInstruction extends OutlineInstruction {

    private final NumericType numericType;

    private BinOpOutlineInstruction(OutlineInstructionType type, NumericType numericType) {
      super(type);
      this.numericType = numericType;
    }

    static BinOpOutlineInstruction fromInstruction(Binop instruction) {
      return new BinOpOutlineInstruction(
          OutlineInstructionType.fromInstruction(instruction), instruction.getNumericType());
    }

    @Override
    public int hashCode() {
      return super.hashCode() * 7 + numericType.ordinal();
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof BinOpOutlineInstruction)) {
        return false;
      }
      BinOpOutlineInstruction o = (BinOpOutlineInstruction) other;
      return o.type.equals(type) && o.numericType.equals(numericType);
    }

    @Override
    public int compareTo(OutlineInstruction other) {
      if (!(other instanceof BinOpOutlineInstruction)) {
        return super.compareTo(other);
      }
      BinOpOutlineInstruction o = (BinOpOutlineInstruction) other;
      int result = type.compareTo(o.type);
      if (result != 0) {
        return result;
      }
      return numericType.compareTo(o.numericType);
    }

    @Override
    public String getDetailsString() {
      return "";
    }

    @Override
    public String getInstructionName() {
      return type.name() + "-" + numericType.name();
    }

    @Override
    public boolean hasOutValue() {
      return true;
    }

    @Override
    public int numberOfInputs() {
      return 2;
    }

    @Override
    public int createInstruction(IRBuilder builder, Outline outline, int argumentMapIndex) {
      List<Value> inValues = new ArrayList<>(numberOfInputs());
      // The template in-values are not re-ordered for commutative binary operations, as it does
      // not matter.
      for (int i = 0; i < numberOfInputs(); i++) {
        int register = outline.argumentMap.get(argumentMapIndex++);
        if (register == OutlineInstruction.OUTLINE_TEMP) {
          register = outline.argumentCount();
        }
        inValues.add(
            builder.readRegister(register, ValueTypeConstraint.fromNumericType(numericType)));
      }
      TypeElement latticeElement = PrimitiveTypeElement.fromNumericType(numericType);
      Value outValue =
          builder.writeRegister(outline.argumentCount(), latticeElement, ThrowingInfo.CAN_THROW);
      Instruction newInstruction = null;
      switch (type) {
        case ADD:
          newInstruction = Add.create(numericType, outValue, inValues.get(0), inValues.get(1));
          break;
        case MUL:
          newInstruction = Mul.create(numericType, outValue, inValues.get(0), inValues.get(1));
          break;
        case SUB:
          newInstruction = new Sub(numericType, outValue, inValues.get(0), inValues.get(1));
          break;
        case DIV:
          newInstruction = new Div(numericType, outValue, inValues.get(0), inValues.get(1));
          break;
        case REM:
          newInstruction = new Rem(numericType, outValue, inValues.get(0), inValues.get(1));
          break;
        default:
          throw new Unreachable("Invalid binary operation type: " + type);
      }
      builder.add(newInstruction);
      return argumentMapIndex;
    }

    @Override
    public boolean needsLensRewriting(GraphLens currentGraphLens) {
      return false;
    }
  }

  private static class NewInstanceOutlineInstruction extends OutlineInstruction {
    private final DexType clazz;

    NewInstanceOutlineInstruction(DexType clazz) {
      super(OutlineInstructionType.NEW);
      this.clazz = clazz;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof NewInstanceOutlineInstruction)) {
        return false;
      }
      NewInstanceOutlineInstruction o = (NewInstanceOutlineInstruction) other;
      boolean result = clazz == o.clazz;
      return result;
    }

    @Override
    public int hashCode() {
      return super.hashCode() * 7 + clazz.hashCode();
    }

    @Override
    public int compareTo(OutlineInstruction other) {
      if (!(other instanceof NewInstanceOutlineInstruction)) {
        return super.compareTo(other);
      }
      NewInstanceOutlineInstruction o = (NewInstanceOutlineInstruction) other;
      return clazz.compareTo(o.clazz);
    }

    @Override
    public String getDetailsString() {
      return clazz.toSourceString();
    }

    @Override
    public String getInstructionName() {
      return type.name();
    }

    @Override
    public boolean hasOutValue() {
      return true;
    }

    @Override
    public int numberOfInputs() {
      return 0;
    }

    @Override
    public int createInstruction(IRBuilder builder, Outline outline, int argumentMapIndex) {
      TypeElement latticeElement =
          TypeElement.fromDexType(clazz, definitelyNotNull(), builder.appView);
      Value outValue =
          builder.writeRegister(outline.argumentCount(), latticeElement, ThrowingInfo.CAN_THROW);
      Instruction newInstruction = new NewInstance(clazz, outValue);
      builder.add(newInstruction);
      return argumentMapIndex;
    }

    @Override
    public boolean needsLensRewriting(GraphLens currentGraphLens) {
      return currentGraphLens.lookupType(clazz) != clazz;
    }
  }

  private static class InvokeOutlineInstruction extends OutlineInstruction {
    private final DexMethod method;
    private final InvokeType invokeType;
    private final boolean hasOutValue;
    private final DexProto proto;
    private final boolean hasReceiver;

    private InvokeOutlineInstruction(
        DexMethod method,
        InvokeType type,
        boolean hasOutValue,
        ValueType[] inputTypes,
        DexProto proto) {
      super(OutlineInstructionType.INVOKE);
      hasReceiver = inputTypes.length != method.proto.parameters.values.length;
      assert !hasReceiver || inputTypes[0].isObject();
      this.method = method;
      this.invokeType = type;
      this.hasOutValue = hasOutValue;
      this.proto = proto;
    }

    static InvokeOutlineInstruction fromInstruction(InvokeMethod invoke) {
      ValueType[] inputTypes = new ValueType[invoke.inValues().size()];
      int i = 0;
      for (Value value : invoke.inValues()) {
        inputTypes[i++] = value.outType();
      }
      return new InvokeOutlineInstruction(
          invoke.getInvokedMethod(),
          invoke.getType(),
          invoke.outValue() != null,
          inputTypes,
          invoke.isInvokePolymorphic() ? invoke.asInvokePolymorphic().getProto() : null);
    }

    @Override
    public int hashCode() {
      return super.hashCode() * 7
          + method.hashCode() * 13
          + invokeType.hashCode()
          + Boolean.hashCode(hasOutValue)
          + Objects.hashCode(proto);
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof InvokeOutlineInstruction)) {
        return false;
      }
      InvokeOutlineInstruction o = (InvokeOutlineInstruction) other;
      return method == o.method
          && invokeType == o.invokeType
          && hasOutValue == o.hasOutValue
          && Objects.equals(proto, o.proto);
    }

    @Override
    public int compareTo(OutlineInstruction other) {
      if (!(other instanceof InvokeOutlineInstruction)) {
        return super.compareTo(other);
      }
      InvokeOutlineInstruction o = (InvokeOutlineInstruction) other;
      int result = method.compareTo(o.method);
      if (result != 0) {
        return result;
      }
      result = invokeType.compareTo(o.invokeType);
      if (result != 0) {
        return result;
      }
      result = Boolean.compare(hasOutValue, o.hasOutValue);
      if (result != 0) {
        return result;
      }
      if (proto != null) {
        result = proto.compareTo(o.proto);
        if (result != 0) {
          return result;
        }
      }
      assert this.equals(other);
      return 0;
    }

    @Override
    public String getDetailsString() {
      return "; method: " + method.toSourceString();
    }

    @Override
    public String getInstructionName() {
      return type.name() + "-" + invokeType.name();
    }

    @Override
    public boolean hasOutValue() {
      return hasOutValue;
    }

    @Override
    public int numberOfInputs() {
      return (hasReceiver ? 1 : 0) + method.proto.parameters.values.length;
    }

    private ValueTypeConstraint getArgumentConstraint(int index) {
      if (hasReceiver) {
        return index == 0
            ? ValueTypeConstraint.OBJECT
            : ValueTypeConstraint.fromDexType(method.proto.parameters.values[index - 1]);
      }
      return ValueTypeConstraint.fromDexType(method.proto.parameters.values[index]);
    }

    @Override
    public int createInstruction(IRBuilder builder, Outline outline, int argumentMapIndex) {
      List<Value> inValues = new ArrayList<>(numberOfInputs());
      for (int i = 0; i < numberOfInputs(); i++) {
        int register = outline.argumentMap.get(argumentMapIndex++);
        if (register == OutlineInstruction.OUTLINE_TEMP) {
          register = outline.argumentCount();
        }
        inValues.add(builder.readRegister(register, getArgumentConstraint(i)));
      }
      Value outValue = null;
      if (hasOutValue) {
        TypeElement latticeElement =
            TypeElement.fromDexType(method.proto.returnType, maybeNull(), builder.appView);
        outValue =
            builder.writeRegister(outline.argumentCount(), latticeElement, ThrowingInfo.CAN_THROW);
      }

      Instruction newInstruction = Invoke.create(invokeType, method, proto, outValue, inValues);
      builder.add(newInstruction);
      return argumentMapIndex;
    }

    @Override
    public boolean needsLensRewriting(GraphLens currentGraphLens) {
      return currentGraphLens.getRenamedMethodSignature(method) != method;
    }
  }

  // Representation of an outline.
  // This includes the instructions in the outline, and a map from the arguments of this outline
  // to the values in the instructions.
  //
  // E.g. an outline of two StringBuilder.append(String) calls could look like this:
  //
  //  InvokeVirtual       { v5 v6 } Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
  //  InvokeVirtual       { v5 v9 } Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
  //  ReturnVoid
  //
  // It takes three arguments
  //
  //   v5, v6, v9
  //
  // and the argument map is a list mapping the arguments to the in-values of all instructions in
  // the order they are encountered, in this case:
  //
  //   [0, 1, 0, 2]
  //
  // The actual value numbers (in this example v5, v6, v9 are "arbitrary", as the instruction in
  // the outline are taken from the block where they are collected as candidates. The comparison
  // of two outlines rely on the instructions and the argument mapping *not* the concrete values.
  public class Outline implements Comparable<Outline> {

    final List<DexType> argumentTypes;
    final List<Integer> argumentMap;
    final List<OutlineInstruction> templateInstructions = new ArrayList<>();
    public final DexType returnType;

    private DexProto proto;

    // Build an outline over the instructions [start, end[.
    // The arguments are the arguments to pass to an outline of these instructions.
    Outline(
        List<Instruction> instructions,
        List<DexType> argumentTypes,
        List<Integer> argumentMap,
        DexType returnType,
        int start,
        int end) {
      this.argumentTypes = argumentTypes;
      this.argumentMap = argumentMap;
      this.returnType = returnType;

      for (int i = start; i < end; i++) {
        Instruction current = instructions.get(i);
        if (current.isInvoke() || current.isNewInstance() || current.isArithmeticBinop()) {
          templateInstructions.add(OutlineInstruction.fromInstruction(current));
        } else if (current.isConstInstruction()) {
          // Don't include const instructions in the template.
        } else if (current.isAssume()) {
          // Don't include assume instructions in the template.
        } else {
          assert false : "Unexpected type of instruction in outlining template.";
        }
      }
    }

    int argumentCount() {
      return argumentTypes.size();
    }

    DexProto buildProto() {
      if (proto == null) {
        DexType[] argumentTypesArray = argumentTypes.toArray(DexType.EMPTY_ARRAY);
        proto = dexItemFactory.createProto(returnType, argumentTypesArray);
      }
      return proto;
    }

    public Outline rewrittenWithLens(GraphLens currentGraphLens) {
      if (needsLensRewriting(currentGraphLens)) {
        // Discard this outline.
        return null;
      }
      return this;
    }

    private boolean needsLensRewriting(GraphLens currentGraphLens) {
      for (DexType argumentType : argumentTypes) {
        if (currentGraphLens.lookupType(argumentType) != argumentType) {
          return true;
        }
      }
      if (currentGraphLens.lookupType(returnType) != returnType) {
        return true;
      }
      return Iterables.any(
          templateInstructions,
          templateInstruction -> templateInstruction.needsLensRewriting(currentGraphLens));
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Outline)) {
        return false;
      }
      Outline otherOutline = (Outline) other;
      List<OutlineInstruction> instructions0 = this.templateInstructions;
      List<OutlineInstruction> instructions1 = otherOutline.templateInstructions;
      if (instructions0.size() != instructions1.size()) {
        return false;
      }
      for (int i = 0; i < instructions0.size(); i++) {
        OutlineInstruction i0 = instructions0.get(i);
        OutlineInstruction i1 = instructions1.get(i);
        if (!i0.equals(i1)) {
          return false;
        }
      }
      return argumentTypes.equals(otherOutline.argumentTypes)
          && argumentMap.equals(otherOutline.argumentMap)
          && returnType == otherOutline.returnType;
    }

    @Override
    public int hashCode() {
      final int MAX_HASH_INSTRUCTIONS = 5;

      int hash = templateInstructions.size();
      int hashPart = 0;
      for (int i = 0; i < templateInstructions.size() && i < MAX_HASH_INSTRUCTIONS; i++) {
        OutlineInstruction instruction = templateInstructions.get(i);
        hashPart = hashPart << 4;
        hashPart += instruction.hashCode();
        hash = hash * 3 + hashPart;
      }
      return hash;
    }

    @Override
    public int compareTo(Outline other) {
      if (this == other) {
        return 0;
      }
      // First compare the proto.
      int result;
      result = buildProto().compareTo(other.buildProto());
      if (result != 0) {
        assert !equals(other);
        return result;
      }
      assert argumentCount() == other.argumentCount();
      // Then compare the instructions (non value part).
      List<OutlineInstruction> instructions0 = templateInstructions;
      List<OutlineInstruction> instructions1 = other.templateInstructions;
      result = instructions0.size() - instructions1.size();
      if (result != 0) {
        assert !equals(other);
        return result;
      }
      for (int i = 0; i < instructions0.size(); i++) {
        OutlineInstruction i0 = instructions0.get(i);
        OutlineInstruction i1 = instructions1.get(i);
        result = i0.compareTo(i1);
        if (result != 0) {
          assert !i0.equals(i1);
          return result;
        }
      }
      // Finally compare the value part.
      result = argumentMap.size() - other.argumentMap.size();
      if (result != 0) {
        assert !equals(other);
        return result;
      }
      for (int i = 0; i < argumentMap.size(); i++) {
        result = argumentMap.get(i) - other.argumentMap.get(i);
        if (result != 0) {
          assert !equals(other);
          return result;
        }
      }
      assert equals(other);
      return 0;
    }

    @Override
    public String toString() {
      // The printing of the code for an outline maps the value numbers to the arguments numbers.
      int outRegisterNumber = argumentTypes.size();
      StringBuilder builder = new StringBuilder();
      builder.append(returnType);
      builder.append(" anOutline");
      StringUtils.append(builder, argumentTypes, ", ", BraceType.PARENS);
      builder.append("\n");
      int argumentMapIndex = 0;
      for (OutlineInstruction instruction : templateInstructions) {
        builder.append(instruction.toString());
        String name = instruction.getInstructionName();
        StringUtils.appendRightPadded(builder, name, 20);
        if (instruction.hasOutValue()) {
          builder.append("v" + outRegisterNumber);
          builder.append(" <- ");
        }
        for (int i = 0; i < instruction.numberOfInputs(); i++) {
          builder.append(i > 0 ? ", " : "");
          builder.append("v");
          int index = argumentMap.get(argumentMapIndex++);
          if (index >= 0) {
            builder.append(index);
          } else {
            builder.append(outRegisterNumber);
          }
        }
        builder.append(instruction.getDetailsString());
        builder.append("\n");
      }
      if (returnType == dexItemFactory.voidType) {
        builder.append("Return-Void");
      } else {
        StringUtils.appendRightPadded(builder, "Return", 20);
        builder.append("v" + (outRegisterNumber));
      }
      builder.append("\n");
      builder.append(argumentMap);
      return builder.toString();
    }
  }

  // Spot the outline opportunities in a basic block.
  // This is the superclass for both collection candidates and actually replacing code.
  // TODO(sgjesse): Collect more information in the candidate collection and reuse that for
  // replacing.
  private abstract class OutlineSpotter {

    final ProgramMethod method;
    final IRCode irCode;
    final List<Instruction> currentCandidateInstructions;

    int start;
    int index;
    int actualInstructions;
    List<Value> arguments;
    List<DexType> argumentTypes;
    List<Integer> argumentsMap;
    int argumentRegisters;
    DexType returnType;
    Value returnValue;
    int returnValueUniqueUsersLeft;
    int pendingNewInstanceIndex = -1;

    OutlineSpotter(
        ProgramMethod method, IRCode irCode, List<Instruction> currentCandidateInstructions) {
      this.method = method;
      this.irCode = irCode;
      this.currentCandidateInstructions = currentCandidateInstructions;
      reset(0);
    }

    protected void process() {
      while (index < currentCandidateInstructions.size()) {
        processInstruction(currentCandidateInstructions.get(index));
      }
      if (actualInstructions > 0) {
        candidate(start, index);
      }
    }

    // Get int in-values for an instruction. For commutative binary operations using the current
    // return value (active out-value) make sure that that value is the left value.
    protected List<Value> orderedInValues(Instruction instruction, Value returnValue) {
      List<Value> inValues = instruction.inValues();
      if (instruction.isBinop() && instruction.asBinop().isCommutative()) {
        if (inValues.get(1) == returnValue) {
          Value tmp = inValues.get(0);
          inValues.set(0, inValues.get(1));
          inValues.set(1, tmp);
        }
      }
      return inValues;
    }

    // Process instruction. Returns true if an outline candidate was found.
    private void processInstruction(Instruction instruction) {
      // Figure out whether to include the instruction.
      boolean include;
      int instructionIncrement = 1;
      if (instruction.isConstInstruction()) {
        if (index == start) {
          // Restart for first const instruction.
          reset(index + 1);
          return;
        } else {
          // Otherwise include const instructions.
          include = true;
          instructionIncrement = 0;
        }
      } else if (instruction.isAssume()) {
        // Technically, assume instructions will be removed, and thus it should not be included.
        // However, we should keep searching, so here we pretend to include it with 0 increment.
        // When adding instruction into the outline candidate, we filter out assume instructions.
        include = true;
        instructionIncrement = 0;
      } else {
        include = canIncludeInstruction(instruction);
      }

      if (include) {
        actualInstructions += instructionIncrement;

        // Add this instruction.
        includeInstruction(instruction);
        // Check if this instruction ends the outline.
        if (actualInstructions >= appView.options().outline.maxSize) {
          candidate(start, index + 1);
        } else {
          index++;
        }
      } else if (index > start) {
        // Do not add this instruction, candidate ends with previous instruction.
        candidate(start, index);
      } else {
        // Restart search from next instruction.
        reset(index + 1);
      }
    }

    // Check if the current instruction can be included in the outline.
    private boolean canIncludeInstruction(Instruction instruction) {
      // Find the users of the active out-value (potential return value).
      int returnValueUniqueUsersLeftIfIncluded = returnValueUniqueUsersLeft;
      if (returnValue != null
          && Iterables.any(
              instruction.inValues(), value -> value.getAliasedValue() == returnValue)) {
        returnValueUniqueUsersLeftIfIncluded--;
      }

      // If this instruction has an out-value, but the previous one is still active end the
      // outline.
      if (instruction.outValue() != null && returnValueUniqueUsersLeftIfIncluded > 0) {
        return false;
      }

      // Allow all new-instance instructions in a outline.
      if (instruction.isNewInstance()) {
        if (instruction.outValue().isUsed()) {
          // Track the new-instance value to make sure the <init> call is part of the outline.
          pendingNewInstanceIndex = index;
        }
        return true;
      }

      if (instruction.isArithmeticBinop()) {
        return true;
      }

      // Otherwise we only allow invoke.
      if (!instruction.isInvokeMethod()) {
        return false;
      }
      InvokeMethod invoke = instruction.asInvokeMethod();
      boolean constructor = dexItemFactory.isConstructor(invoke.getInvokedMethod());

      // See whether we could move this invoke somewhere else. We reuse the logic from inlining
      // here, as the constraints are the same.
      ConstraintWithTarget constraint = invoke.inliningConstraint(inliningConstraints, method);
      if (constraint != ConstraintWithTarget.ALWAYS) {
        return false;
      }
      // Find the number of in-going arguments, if adding this instruction.
      int newArgumentRegisters = argumentRegisters;
      if (invoke.hasArguments()) {
        for (int i = 0; i < invoke.arguments().size(); i++) {
          Value value = invoke.getArgument(i).getAliasedValue();
          if (value == returnValue) {
            continue;
          }
          if (!supportedArgumentType(value)) {
            return false;
          }
          if (invoke.isInvokeStatic()) {
            newArgumentRegisters += value.requiredRegisters();
          } else {
            // For virtual calls only re-use the receiver argument.
            Value receiver = invoke.asInvokeMethodWithReceiver().getReceiver().getAliasedValue();
            if (value != receiver || !arguments.contains(value)) {
              newArgumentRegisters += value.requiredRegisters();
            }
          }
        }
      }
      if (newArgumentRegisters > MAX_IN_SIZE) {
        return false;
      }

      // Only include constructors if the previous instruction is the corresponding new-instance.
      if (constructor) {
        if (start == index) {
          return false;
        }
        assert index > 0;
        int offset = 0;
        Instruction previous;
        do {
          offset++;
          previous = currentCandidateInstructions.get(index - offset);
        } while (previous.isConstInstruction());
        if (!previous.isNewInstance()
            || invoke != previous.asNewInstance().getUniqueConstructorInvoke(dexItemFactory)) {
          return false;
        }
        if (returnValue == null || returnValue != previous.outValue()) {
          assert false;
          return false;
        }
        // Clear pending new instance flag as the last thing, now the matching constructor is known
        // to be included.
        pendingNewInstanceIndex = -1;
      }
      return true;
    }

    private DexType argumentTypeFromInvoke(InvokeMethod invoke, int index) {
      boolean withReceiver = invoke.isInvokeMethodWithReceiver() || invoke.isInvokePolymorphic();
      if (withReceiver && index == 0) {
        return invoke.getInvokedMethod().holder;
      }
      DexProto methodProto;
      if (invoke.isInvokePolymorphic()) {
        // Type of argument of a polymorphic call must be taken from the call site.
        methodProto = invoke.asInvokePolymorphic().getProto();
      } else {
        methodProto = invoke.getInvokedMethod().proto;
      }
      // Skip receiver if present.
      return methodProto.parameters.values[index - (withReceiver ? 1 : 0)];
    }

    private boolean supportedArgumentType(Value value) {
      // All non array types are supported.
      if (!value.getType().isArrayType()) {
        return true;
      }
      // Avoid array type elements which have interfaces, as Art does not have the same semantics
      // for array types as the Java VM, see b/132420510.
      if (appView.options().testing.allowOutlinerInterfaceArrayArguments
          && appView.options().isGeneratingClassFiles()) {
        return true;
      }
      ArrayTypeElement arrayType = value.getType().asArrayType();
      TypeElement arrayBaseType = arrayType.getBaseType();
      if (arrayBaseType.isPrimitiveType()) {
        return true;
      }
      if (arrayBaseType.isClassType()) {
        return arrayBaseType.asClassType().getInterfaces().isEmpty();
      }
      return false;
    }

    private DexType argumentTypeFromValue(Value value, InvokeMethod invoke, int argumentIndex) {
      assert supportedArgumentType(value);
      DexItemFactory itemFactory = appView.options().itemFactory;
      DexType objectType = itemFactory.objectType;
      TypeElement valueType = value.getType();
      if (valueType.isClassType()) {
        ClassTypeElement valueClassType = value.getType().asClassType();
        // For values of lattice type java.lang.Object and only one interface use the interface as
        // the type of the outline argument. If there are several interfaces these interfaces don't
        // have a common super interface nor are they implemented by a common superclass so the
        // argument type of the outline will be java.lang.Object.
        if (valueClassType.getClassType() == objectType
            && valueClassType.getInterfaces().hasSingleKnownInterface()) {
          return valueClassType.getInterfaces().getSingleKnownInterface();
        } else {
          return valueClassType.getClassType();
        }
      } else if (valueType.isArrayType()) {
        return value.getType().asArrayType().toDexType(itemFactory);
      } else if (valueType.isNullType()) {
        // For values which are always null use the actual type at the call site.
        return argumentTypeFromInvoke(invoke, argumentIndex);
      } else {
        assert valueType.isPrimitiveType();
        assert valueType.asPrimitiveType().hasDexType();
        DexType type = valueType.asPrimitiveType().toDexType(itemFactory);
        if (valueType.isInt()) {
          // In the type lattice boolean, byte, short and char are all int. However, as the
          // outline argument type use the actual primitive type at the call site.
          assert type == itemFactory.intType;
          type = argumentTypeFromInvoke(invoke, argumentIndex);
        } else {
          assert type == argumentTypeFromInvoke(invoke, argumentIndex);
        }
        return type;
      }
    }

    // Add the current instruction to the outline.
    private void includeInstruction(Instruction instruction) {
      if (instruction.isAssume()) {
        Assume assume = instruction.asAssume();
        if (returnValue != null && assume.src().getAliasedValue() == returnValue) {
          adjustReturnValueUsersLeft(assume.outValue().numberOfAllUsers() - 1);
        }
        return;
      }

      List<Value> inValues = orderedInValues(instruction, returnValue);

      Value prevReturnValue = returnValue;
      if (returnValue != null) {
        for (Value value : inValues) {
          if (value.getAliasedValue() == returnValue) {
            adjustReturnValueUsersLeft(-1);
          }
        }
      }

      if (instruction.isNewInstance()) {
        assert returnValue == null;
        updateReturnValueState(instruction.outValue(), instruction.asNewInstance().clazz);
        return;
      }

      assert instruction.isInvoke()
          || instruction.isConstInstruction()
          || instruction.isArithmeticBinop();
      if (inValues.size() > 0) {
        for (int i = 0; i < inValues.size(); i++) {
          Value value = inValues.get(i).getAliasedValue();
          if (value == prevReturnValue) {
            argumentsMap.add(OutlineInstruction.OUTLINE_TEMP);
            continue;
          }
          if (instruction.isInvokeMethodWithReceiver() || instruction.isInvokePolymorphic()) {
            int argumentIndex = arguments.indexOf(value);
            // For virtual calls only re-use the receiver argument.
            if (i == 0 && argumentIndex != -1) {
              argumentsMap.add(argumentIndex);
            } else {
              arguments.add(value);
              argumentRegisters += value.requiredRegisters();
              argumentTypes.add(argumentTypeFromValue(value, instruction.asInvokeMethod(), i));
              argumentsMap.add(argumentTypes.size() - 1);
            }
          } else {
            arguments.add(value);
            argumentRegisters += value.requiredRegisters();
            if (instruction.isInvokeMethod()) {
              argumentTypes.add(argumentTypeFromValue(value, instruction.asInvokeMethod(), i));
            } else {
              argumentTypes.add(instruction.asBinop().getNumericType().toDexType(dexItemFactory));
            }
            argumentsMap.add(argumentTypes.size() - 1);
          }
        }
      }
      if (!instruction.isConstInstruction() && instruction.outValue() != null) {
        assert returnValue == null;
        if (instruction.isInvokeMethod()) {
          updateReturnValueState(
              instruction.outValue(),
              instruction.asInvokeMethod().getInvokedMethod().proto.returnType);
        } else {
          updateReturnValueState(
              instruction.outValue(),
              instruction.asBinop().getNumericType().toDexType(dexItemFactory));
        }
      }
    }

    private void updateReturnValueState(Value newReturnValue, DexType newReturnType) {
      returnValueUniqueUsersLeft = newReturnValue.numberOfAllUsers();
      // If the return value is not used don't track it.
      if (returnValueUniqueUsersLeft == 0) {
        returnValue = null;
        returnType = dexItemFactory.voidType;
      } else {
        returnValue = newReturnValue;
        returnType = newReturnType;
      }
    }

    private void adjustReturnValueUsersLeft(int change) {
      returnValueUniqueUsersLeft += change;
      assert returnValueUniqueUsersLeft >= 0;
      if (returnValueUniqueUsersLeft == 0) {
        returnValue = null;
        returnType = dexItemFactory.voidType;
      }
    }

    protected abstract void handle(int start, int end, Outline outline);

    private void candidate(int start, int index) {
      assert !currentCandidateInstructions.get(start).isConstInstruction();

      if (pendingNewInstanceIndex != -1) {
        if (pendingNewInstanceIndex == start) {
          reset(index);
        } else {
          reset(pendingNewInstanceIndex);
        }
        return;
      }

      // Back out of any const instructions ending this candidate.
      int end = index;
      while (currentCandidateInstructions.get(end - 1).isConstInstruction()) {
        end--;
      }

      // Check if the candidate qualifies.
      if (actualInstructions < appView.options().outline.minSize) {
        reset(start + 1);
        return;
      }

      Outline outline =
          new Outline(
              currentCandidateInstructions, argumentTypes, argumentsMap, returnType, start, end);
      handle(start, end, outline);

      // Start a new candidate search from the next instruction after this outline.
      reset(index);
    }

    // Restart the collection of outline candidate to the given instruction start index.
    private void reset(int startIndex) {
      start = startIndex;
      index = startIndex;
      actualInstructions = 0;
      arguments = new ArrayList<>(MAX_IN_SIZE);
      argumentTypes = new ArrayList<>(MAX_IN_SIZE);
      argumentsMap = new ArrayList<>(MAX_IN_SIZE);
      argumentRegisters = 0;
      returnType = dexItemFactory.voidType;
      returnValue = null;
      returnValueUniqueUsersLeft = 0;
      pendingNewInstanceIndex = -1;
    }
  }

  // Collect outlining candidates with the methods that can use them.
  // TODO(sgjesse): This does not take several usages in the same method into account.
  private class OutlineMethodIdentifier extends OutlineSpotter {

    private final List<Outline> outlinesForMethod;

    OutlineMethodIdentifier(
        ProgramMethod method,
        IRCode irCode,
        List<Instruction> currentCandidateInstructions,
        List<Outline> outlinesForMethod) {
      super(method, irCode, currentCandidateInstructions);
      this.outlinesForMethod = outlinesForMethod;
    }

    @Override
    protected void handle(int start, int end, Outline outline) {
      outlinesForMethod.add(outline);
    }
  }

  private class OutlineSiteIdentifier extends OutlineSpotter {

    OutlineSiteIdentifier(
        ProgramMethod method, IRCode irCode, List<Instruction> currentCandidateInstructions) {
      super(method, irCode, currentCandidateInstructions);
    }

    @Override
    protected void handle(int start, int end, Outline outline) {
      synchronized (outlineSites) {
        outlineSites.computeIfAbsent(outline, k -> new ArrayList<>()).add(method);
      }
    }
  }

  // Replace instructions with a call to the outlined method.
  private class OutlineRewriter extends OutlineSpotter {

    private final IRCode code;
    private final Set<Instruction> toRemove;
    private final Set<Instruction> invokesToOutlineMethods;
    int argumentsMapIndex;

    OutlineRewriter(
        IRCode code,
        List<Instruction> currentCandidateInstructions,
        Set<Instruction> toRemove,
        Set<Instruction> invokesToOutlineMethods) {
      super(code.context(), code, currentCandidateInstructions);
      this.code = code;
      this.toRemove = toRemove;
      this.invokesToOutlineMethods = invokesToOutlineMethods;
    }

    @Override
    protected void handle(int start, int end, Outline outline) {
      DexMethod outlineMethod = generatedOutlines.get(outline);
      AffectedValues affectedValues = new AffectedValues();
      if (outlineMethod != null) {
        assert removeMethodFromOutlineList(outline);
        List<Value> in = new ArrayList<>();
        Value returnValue = null;
        argumentsMapIndex = 0;
        OutlineCallerPositionBuilder positionBuilder =
            OutlineCallerPosition.builder()
                .setMethod(appView.graphLens().getOriginalMethodSignature(method.getReference()))
                .setOutlineCallee(outlineMethod)
                // We set the line number to 0 here and rely on the LineNumberOptimizer to
                // set a new disjoint line.
                .setLine(0);
        Instruction lastInstruction = null;
        { // Scope for 'instructions'.
          int outlinePositionIndex = 0;
          for (int i = start; i < end; i++) {
            Instruction current = currentCandidateInstructions.get(i);
            if (current.isConstInstruction()) {
              // Leave any const instructions.
              continue;
            }
            int currentPositionIndex = outlinePositionIndex++;
            if (current.getPosition() != null
                && current.instructionInstanceCanThrow(appView, method)) {
              positionBuilder.addOutlinePosition(currentPositionIndex, current.getPosition());
            }

            // Prepare to remove the instruction.
            List<Value> inValues = orderedInValues(current, returnValue);
            for (Value value : inValues) {
              value.removeUser(current);
              int argumentIndex = outline.argumentMap.get(argumentsMapIndex++);
              if (argumentIndex >= in.size()) {
                assert argumentIndex == in.size();
                in.add(value);
              }
            }
            if (current.outValue() != null) {
              returnValue = current.outValue();
            }
            // The invoke of the outline method will be placed at the last instruction index,
            // so don't mark that for removal.
            if (i < end - 1) {
              toRemove.add(current);
            }
            lastInstruction = current;
          }
        }
        assert lastInstruction != null;
        assert outlineMethod.proto.createShortyString().length() - 1 == in.size();
        if (returnValue != null && !returnValue.isUsed()) {
          returnValue = null;
        }
        Invoke outlineInvoke = new InvokeStatic(outlineMethod, returnValue, in);
        outlineInvoke.setBlock(lastInstruction.getBlock());
        outlineInvoke.setPosition(
            positionBuilder.hasOutlinePositions()
                ? positionBuilder.build()
                : Position.syntheticNone());
        InstructionListIterator endIterator =
            lastInstruction.getBlock().listIterator(code, lastInstruction);
        Instruction instructionBeforeEnd = endIterator.previous();
        assert instructionBeforeEnd == lastInstruction;
        endIterator.set(outlineInvoke);
        if (outlineInvoke.hasOutValue()
            && returnValue.getType().isReferenceType()
            && returnValue.getType().nullability().isDefinitelyNotNull()) {
          Value nullableReturnValue =
              code.createValue(
                  returnValue
                      .getType()
                      .asReferenceType()
                      .getOrCreateVariant(Nullability.maybeNull()),
                  returnValue.getLocalInfo());
          outlineInvoke.outValue().replaceUsers(nullableReturnValue, affectedValues);
          outlineInvoke.setOutValue(nullableReturnValue);
        }
        invokesToOutlineMethods.add(outlineInvoke);
      }
      affectedValues.widening(appView, code);
    }

    /** When assertions are enabled, remove method from the outline's list. */
    private boolean removeMethodFromOutlineList(Outline outline) {
      synchronized (outlineSites) {
        assert ListUtils.removeFirstMatch(
                outlineSites.get(outline),
                element -> element.getDefinition() == method.getDefinition())
            .isPresent();
      }
      return true;
    }
  }

  public OutlinerImpl(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
    this.inliningConstraints = new InliningConstraints(appView, GraphLens.getIdentityLens());
  }

  @Override
  public void onMethodPruned(ProgramMethod method) {
    onMethodCodePruned(method);
  }

  @Override
  public void onMethodCodePruned(ProgramMethod method) {
    outlineCollection.remove(appView, method);
  }

  @Override
  public void prepareForPrimaryOptimizationPass(GraphLens graphLensForPrimaryOptimizationPass) {
    assert appView.graphLens() == graphLensForPrimaryOptimizationPass;
    assert outlineCollection == null;
    outlineCollection = new OutlineCollection(graphLensForPrimaryOptimizationPass);
  }

  @Override
  public void performOutlining(
      IRConverter converter,
      OptimizationFeedbackDelayed feedback,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    assert feedback.noUpdatesLeft();
    converter.printPhase("Outlining");
    timing.begin("IR conversion phase 3");
    ProgramMethodSet methodsSelectedForOutlining = selectMethodsForOutlining();
    if (!methodsSelectedForOutlining.isEmpty()) {
      OutlineOptimizationEventConsumer eventConsumer =
          OutlineOptimizationEventConsumer.create(appView);
      forEachSelectedOutliningMethod(
          converter,
          methodsSelectedForOutlining,
          code -> {
            converter.printMethod(code, "IR before outlining (SSA)", null);
            identifyOutlineSites(code);
          },
          executorService);
      List<ProgramMethod> outlineMethods = buildOutlineMethods(eventConsumer);
      MethodProcessorEventConsumer methodProcessorEventConsumer =
          MethodProcessorEventConsumer.empty();
      converter.optimizeSynthesizedMethods(
          outlineMethods,
          methodProcessorEventConsumer,
          MethodConversionOptions.forLirPhase(appView),
          executorService);
      feedback.updateVisibleOptimizationInfo();
      forEachSelectedOutliningMethod(
          converter,
          methodsSelectedForOutlining,
          code -> {
            applyOutliningCandidate(code);
            converter.printMethod(code, "IR after outlining (SSA)", null);
            converter.memberValuePropagation.run(code);
            new MoveResultRewriter(appView).run(code, Timing.empty());
            converter.removeDeadCodeAndFinalizeIR(
                code, OptimizationFeedbackIgnore.getInstance(), Timing.empty());
          },
          executorService);
      feedback.updateVisibleOptimizationInfo();
      assert checkAllOutlineSitesFoundAgain();
      outlineMethods.forEach(m -> m.getDefinition().markNotProcessed());
      eventConsumer.finished(appView);
    }
    appView.notifyOptimizationFinishedForTesting();
    timing.end();
  }

  private void forEachSelectedOutliningMethod(
      IRConverter converter,
      ProgramMethodSet methodsSelectedForOutlining,
      Consumer<IRCode> consumer,
      ExecutorService executorService)
      throws ExecutionException {
    assert !appView.options().skipIR;
    ThreadUtils.processItems(
        methodsSelectedForOutlining,
        method -> {
          IRCode code = method.buildIR(appView);
          assert code != null;
          assert !method.getDefinition().getCode().isOutlineCode();
          // Instead of repeating all the optimizations of rewriteCode(), only run the
          // optimizations needed for outlining: rewriteMoveResult() to remove out-values on
          // StringBuilder/StringBuffer method invocations, and removeDeadCode() to remove
          // unused out-values.
          new MoveResultRewriter(appView).run(code, Timing.empty());
          converter.deadCodeRemover.run(code, Timing.empty());
          CodeRewriter.removeAssumeInstructions(appView, code);
          consumer.accept(code);
        },
        executorService);
  }

  @Override
  public void rewriteWithLens() {
    // Rewrite the outline collection with the graph lens, such that the reprocessing of methods
    // will correctly delete/rewrite entries in the outline collection.
    outlineCollection.rewriteWithLens(appView.graphLens());
  }

  @Override
  public void collectOutlineSites(IRCode code, Timing timing) {
    if (outlineCollection == null) {
      return;
    }

    ProgramMethod context = code.context();
    assert !context.getDefinition().getCode().isOutlineCode();

    if (ClassToFeatureSplitMap.isInFeature(context.getHolder(), appView)) {
      return;
    }

    timing.begin("Collect outlines");
    List<Outline> outlinesForMethod = new ArrayList<>();
    getInstructions(
        appView,
        code,
        instructions ->
            new OutlineMethodIdentifier(context, code, instructions, outlinesForMethod).process());
    outlineCollection.set(appView, context, outlinesForMethod);
    timing.end();
  }

  public static void getInstructions(
      AppView<?> appView, IRCode code, Consumer<List<Instruction>> consumer) {
    int maxNumberOfInstructionsToBeConsidered =
        appView.options().outline.maxNumberOfInstructionsToBeConsidered;
    int minSize = appView.options().outline.minSize;
    Set<BasicBlock> seenBlocks = Sets.newIdentityHashSet();
    for (BasicBlock block : code.blocks) {
      if (seenBlocks.add(block)) {
        ImmutableList.Builder<Instruction> builder = ImmutableList.builder();
        LinearFlowInstructionListIterator instructionIterator =
            new LinearFlowInstructionListIterator(code, block);
        // Maintaining the last seen block ensure that we always consider all instructions in a
        // block before adding it to the seen set.
        BasicBlock lastSeenBlock = block;
        int counter = 0;
        boolean sawLinearFlowWithCatchHandlers = false;
        while (instructionIterator.hasNext()) {
          Instruction instruction = instructionIterator.next();
          if (instruction.getBlock() != block) {
            // Disregard linear flow when there are catch handlers
            if (block.hasCatchHandlers() || instruction.getBlock().hasCatchHandlers()) {
              lastSeenBlock = instruction.getBlock();
              sawLinearFlowWithCatchHandlers = true;
              break;
            }
            // Disregard revisiting already processed blocks.
            if (seenBlocks.contains(instruction.getBlock())) {
              break;
            }
          }
          builder.add(instruction);
          counter++;
          if (counter > maxNumberOfInstructionsToBeConsidered
              && instruction.getBlock() != lastSeenBlock) {
            // Ensure we only break on whole blocks.
            break;
          }
          lastSeenBlock = instruction.getBlock();
        }
        // Add all seen blocks including trivial goto blocks skipped by the linear iterator.
        seenBlocks.addAll(instructionIterator.getSeenBlocks());
        if (sawLinearFlowWithCatchHandlers) {
          assert lastSeenBlock != block;
          // Remove the last seen block since we just visited the first instruction in that block
          // and terminated without adding it.
          seenBlocks.remove(lastSeenBlock);
        }
        if (counter >= minSize) {
          consumer.accept(builder.build());
        }
      }
    }
  }

  public void identifyOutlineSites(IRCode code) {
    ProgramMethod context = code.context();
    assert !context.getDefinition().getCode().isOutlineCode();
    assert !ClassToFeatureSplitMap.isInFeature(context.getHolder(), appView);
    getInstructions(
        appView,
        code,
        instructions -> new OutlineSiteIdentifier(context, code, instructions).process());
  }

  public ProgramMethodSet selectMethodsForOutlining() {
    ProgramMethodSet result = outlineCollection.computeMethodsSubjectToOutlining(appView);
    outlineCollection = null;
    return result;
  }

  public List<ProgramMethod> buildOutlineMethods(OutlineOptimizationEventConsumer eventConsumer) {
    ProcessorContext outlineProcessorContext = appView.createProcessorContext();
    Map<DexMethod, MethodProcessingContext> methodProcessingContexts = new IdentityHashMap<>();
    List<ProgramMethod> outlineMethods = new ArrayList<>();
    // By now the candidates are the actual selected outlines. Iterate the outlines in a
    // consistent order, to provide deterministic naming of the internal-synthetics.
    // The choice of 'representative' will ensure deterministic naming of the external names.
    List<Outline> outlines = selectOutlines();
    outlines.sort(Comparator.naturalOrder());
    for (Outline outline : outlines) {
      List<ProgramMethod> sites = outlineSites.get(outline);
      assert !sites.isEmpty();
      // The representative might be shared among multiple outlines.
      ProgramMethod representative = findDeterministicRepresentative(sites);
      MethodProcessingContext methodProcessingContext =
          methodProcessingContexts.computeIfAbsent(
              representative.getReference(),
              key -> outlineProcessorContext.createMethodProcessingContext(representative));
      ProgramMethod outlineMethod =
          appView
              .getSyntheticItems()
              .createMethod(
                  kinds -> kinds.OUTLINE,
                  methodProcessingContext.createUniqueContext(),
                  appView,
                  builder -> {
                    builder
                        .setAccessFlags(
                            MethodAccessFlags.fromSharedAccessFlags(
                                Constants.ACC_PUBLIC | Constants.ACC_STATIC, false))
                        .setProto(outline.buildProto())
                        // It is OK to set the api level to UNKNOWN since we are not interested in
                        // inlining the outlines anyway.
                        .setApiLevelForDefinition(ComputedApiLevel.unknown())
                        .setApiLevelForCode(ComputedApiLevel.unknown())
                        .setCode(m -> new OutlineCode(outline));
                    if (appView.options().isGeneratingClassFiles()) {
                      builder.setClassFileVersion(
                          representative.getDefinition().getClassFileVersion());
                    }
                  });
      assert outlineMethod.getReference().getArity() <= Constants.U8BIT_MAX;
      eventConsumer.acceptOutlineMethod(outlineMethod, sites);
      generatedOutlines.put(outline, outlineMethod.getReference());
      outlineMethods.add(outlineMethod);
    }
    return outlineMethods;
  }

  private List<Outline> selectOutlines() {
    assert !outlineSites.isEmpty();
    List<Outline> result = new ArrayList<>();
    for (Entry<Outline, List<ProgramMethod>> entry : outlineSites.entrySet()) {
      if (entry.getValue().size() >= appView.options().outline.threshold) {
        result.add(entry.getKey());
      }
    }
    return result;
  }

  private static ProgramMethod findDeterministicRepresentative(List<ProgramMethod> members) {
    // Pick a deterministic member as representative.
    ProgramMethod smallest = members.get(0);
    for (int i = 1; i < members.size(); i++) {
      ProgramMethod next = members.get(i);
      if (next.getReference().compareTo(smallest.getReference()) < 0) {
        smallest = next;
      }
    }
    return smallest;
  }

  public void applyOutliningCandidate(IRCode code) {
    assert !code.context().getDefinition().getCode().isOutlineCode();
    Set<Instruction> toRemove = Sets.newIdentityHashSet();
    Set<Instruction> invokesToOutlineMethods = Sets.newIdentityHashSet();
    getInstructions(
        appView,
        code,
        instructions ->
            new OutlineRewriter(code, instructions, toRemove, invokesToOutlineMethods).process());
    if (!toRemove.isEmpty()) {
      assert !invokesToOutlineMethods.isEmpty();
      // Scan over the entire code to remove outline instructions.
      ListIterator<BasicBlock> blocksIterator = code.listIterator();
      while (blocksIterator.hasNext()) {
        BasicBlock block = blocksIterator.next();
        InstructionListIterator instructionListIterator = block.listIterator(code);
        instructionListIterator.forEachRemaining(
            instruction -> {
              if (toRemove.contains(instruction)) {
                instructionListIterator.removeInstructionIgnoreOutValue();
              } else if (invokesToOutlineMethods.contains(instruction)
                  && block.hasCatchHandlers()) {
                // If the inserted invoke is inserted in a block with handlers, split the block
                // after the inserted invoke.
                instructionListIterator.split(code, blocksIterator);
              }
            });
      }
      code.removeRedundantBlocks();
    }
    code.removeRedundantBlocks();
    assert code.isConsistentSSA(appView);
  }

  public boolean checkAllOutlineSitesFoundAgain() {
    for (Outline outline : generatedOutlines.keySet()) {
      assert outlineSites.get(outline).isEmpty() : outlineSites.get(outline);
    }
    return true;
  }

  private class OutlineSourceCode implements SourceCode {

    private final Outline outline;
    private final DexMethod method;
    private int position;
    private int argumentMapIndex = 0;

    OutlineSourceCode(Outline outline, DexMethod method) {
      this.outline = outline;
      this.method = method;
    }

    @Override
    public int instructionCount() {
      return outline.templateInstructions.size() + 1;
    }

    @Override
    public int instructionIndex(int instructionOffset) {
      return instructionOffset;
    }

    @Override
    public int instructionOffset(int instructionIndex) {
      return instructionIndex;
    }

    @Override
    public DebugLocalInfo getIncomingLocalAtBlock(int register, int blockOffset) {
      return null;
    }

    @Override
    public DebugLocalInfo getIncomingLocal(int register) {
      return null;
    }

    @Override
    public DebugLocalInfo getOutgoingLocal(int register) {
      return null;
    }

    @Override
    public int traceInstruction(int instructionIndex, IRBuilder builder) {
      // There is just one block, and after the last instruction it is closed.
      return instructionIndex == outline.templateInstructions.size() ? instructionIndex : -1;
    }

    @Override
    public void setUp() {}

    @Override
    public void clear() {}

    @Override
    public void buildPrelude(IRBuilder builder) {
      // If the assertion fails, check DexSourceCode#buildArgumentsWithUnusedArgumentStubs.
      assert builder.getPrototypeChanges().isEmpty();
      // Fill in the Argument instructions in the argument block.
      for (int i = 0; i < outline.argumentTypes.size(); i++) {
        if (outline.argumentTypes.get(i).isBooleanType()) {
          builder.addBooleanNonThisArgument(i);
        } else {
          TypeElement typeLattice =
              TypeElement.fromDexType(outline.argumentTypes.get(i), maybeNull(), appView);
          builder.addNonThisArgument(i, typeLattice);
        }
      }
    }

    @Override
    public void buildBlockTransfer(
        IRBuilder builder, int predecessorOffset, int successorOffset, boolean isExceptional) {
      throw new Unreachable("Outliner does not support control flow");
    }

    @Override
    public void buildPostlude(IRBuilder builder) {
      // Intentionally left empty. (Needed for Java-bytecode-frontend synchronization support.)
    }

    @Override
    public void buildInstruction(
        IRBuilder builder, int instructionIndex, boolean firstBlockInstruction) {
      if (instructionIndex == outline.templateInstructions.size()) {
        if (outline.returnType == dexItemFactory.voidType) {
          builder.addReturn();
        } else {
          builder.addReturn(outline.argumentCount());
        }
        return;
      }
      position = instructionIndex;
      // Build IR from the template.
      argumentMapIndex =
          outline
              .templateInstructions
              .get(instructionIndex)
              .createInstruction(builder, outline, argumentMapIndex);
    }

    @Override
    public void resolveAndBuildSwitch(
        int value, int fallthroughOffset, int payloadOffset, IRBuilder builder) {
      throw new Unreachable("Unexpected call to resolveAndBuildSwitch");
    }

    @Override
    public void resolveAndBuildNewArrayFilledData(
        int arrayRef, int payloadOffset, IRBuilder builder) {
      throw new Unreachable("Unexpected call to resolveAndBuildNewArrayFilledData");
    }

    @Override
    public CatchHandlers<Integer> getCurrentCatchHandlers(IRBuilder builder) {
      return null;
    }

    @Override
    public int getMoveExceptionRegister(int instructionIndex) {
      throw new Unreachable();
    }

    @Override
    public Position getCanonicalDebugPositionAtOffset(int offset) {
      return Position.syntheticNone();
    }

    @Override
    public Position getCurrentPosition() {
      // Always build positions for outlinee - each callsite will only build a position map for
      // instructions that are actually throwing.
      return OutlinePosition.builder().setLine(position).setMethod(method).build();
    }

    @Override
    public boolean verifyCurrentInstructionCanThrow() {
      // TODO(sgjesse): Check more here?
      return true;
    }

    @Override
    public boolean verifyLocalInScope(DebugLocalInfo local) {
      return true;
    }

    @Override
    public boolean verifyRegister(int register) {
      return true;
    }
  }

  public class OutlineCode extends Code {

    private final Outline outline;

    OutlineCode(Outline outline) {
      this.outline = outline;
    }

    @Override
    public boolean isOutlineCode() {
      return true;
    }

    @Override
    public int estimatedSizeForInlining() {
      // We just onlined this, so do not inline it again.
      return Integer.MAX_VALUE;
    }

    @Override
    public int estimatedDexCodeSizeUpperBoundInBytes() {
      return Integer.MAX_VALUE;
    }

    @Override
    public boolean isEmptyVoidMethod() {
      return false;
    }

    @Nonnull
    @Override
    public Code copySubtype() {
      return this;
    }

    @Override
    public IRCode buildIR(
        ProgramMethod method,
        AppView<?> appView,
        Origin origin,
        MutableMethodConversionOptions conversionOptions) {
      OutlineSourceCode source = new OutlineSourceCode(outline, method.getReference());
      return IRBuilder.create(method, appView, source, origin).build(method, conversionOptions);
    }

    @Override
    public String toString() {
      return outline.toString();
    }

    @Override
    public void registerCodeReferences(ProgramMethod method, UseRegistry registry) {
      throw new Unreachable();
    }

    @Override
    public void registerCodeReferencesForDesugaring(ClasspathMethod method, UseRegistry registry) {
      throw new Unreachable();
    }

    @Override
    protected int computeHashCode() {
      return outline.hashCode();
    }

    @Override
    protected boolean computeEquals(Object other) {
      return outline.equals(other);
    }

    @Override
    public String toString(DexEncodedMethod method, RetracerForCodePrinting retracer) {
      return null;
    }
  }
}
