// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.Add;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlock.ThrowingInfo;
import com.android.tools.r8.ir.code.Binop;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.Div;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.Mul;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Rem;
import com.android.tools.r8.ir.code.Sub;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.code.ValueTypeConstraint;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.SourceCode;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.SynthesizedOrigin;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.OutlineOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.StringUtils.BraceType;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import org.objectweb.asm.Opcodes;

/**
 * Support class for implementing outlining (i.e. extracting common code patterns as methods).
 *
 * <p>Outlining happens in three steps.
 *
 * <ul>
 *   <li>First, all methods are converted to IR and passed to {@link
 *       Outliner#identifyCandidateMethods()} to identify outlining candidates and the methods
 *       containing each candidate. IR is converted to the output format (DEX or CF) and thrown away
 *       along with the outlining candidates; only a list of lists of methods is kept, where each
 *       list of methods corresponds to methods containing an outlining candidate.
 *   <li>Second, {@link Outliner#selectMethodsForOutlining()} is called to retain the lists of
 *       methods found in the first step that are large enough (see {@link InternalOptions#outline}
 *       {@link OutlineOptions#threshold}), and the methods to be further analyzed for outlining is
 *       returned by {@link Outliner#getMethodsSelectedForOutlining}. Each selected method is then
 *       converted back to IR and passed to {@link Outliner#identifyOutlineSites(IRCode,
 *       DexEncodedMethod)}, which then stores concrete outlining candidates in {@link
 *       Outliner#outlineSites}.
 *   <li>Third, {@link Outliner#buildOutlinerClass(DexType)} is called to construct the <em>outline
 *       support class</em> containing a static helper method for each outline candidate that occurs
 *       frequently enough. Each selected method is then converted to IR, passed to {@link
 *       Outliner#applyOutliningCandidate(IRCode, DexEncodedMethod)} to perform the outlining, and
 *       converted back to the output format (DEX or CF).
 * </ul>
 */
public class Outliner {

  private final InternalOptions options;
  /** Result of first step (see {@link Outliner#identifyCandidateMethods()}. */
  private final List<List<DexEncodedMethod>> candidateMethodLists = new ArrayList<>();
  /** Result of second step (see {@link Outliner#selectMethodsForOutlining()}. */
  private final Set<DexEncodedMethod> methodsSelectedForOutlining = Sets.newIdentityHashSet();
  /** Result of second step (see {@link Outliner#selectMethodsForOutlining()}. */
  private final Map<Outline, List<DexEncodedMethod>> outlineSites = new HashMap<>();
  /** Result of third step (see {@link Outliner#buildOutlinerClass(DexType)}. */
  private final Map<Outline, DexMethod> generatedOutlines = new HashMap<>();

  static final int MAX_IN_SIZE = 5;  // Avoid using ranged calls for outlined code.

  final private AppInfoWithLiveness appInfo;
  final private DexItemFactory dexItemFactory;
  final private IRConverter converter;
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
  }

  private static class BinOpOutlineInstruction extends OutlineInstruction {

    private final NumericType numericType;

    private BinOpOutlineInstruction(
        OutlineInstructionType type,
        NumericType numericType) {
      super(type);
      this.numericType = numericType;
    }

    static BinOpOutlineInstruction fromInstruction(Binop instruction) {
      return new BinOpOutlineInstruction(
          OutlineInstructionType.fromInstruction(instruction),
          instruction.getNumericType());
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
      TypeLatticeElement latticeElement = TypeLatticeElement.fromNumericType(numericType);
      Value outValue =
          builder.writeRegister(outline.argumentCount(), latticeElement, ThrowingInfo.CAN_THROW);
      Instruction newInstruction = null;
      switch (type) {
        case ADD:
          newInstruction = new Add(numericType, outValue, inValues.get(0), inValues.get(1));
          break;
        case MUL:
          newInstruction = new Mul(numericType, outValue, inValues.get(0), inValues.get(1));
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
      return clazz.slowCompareTo(o.clazz);
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
      TypeLatticeElement latticeElement =
          TypeLatticeElement.fromDexType(clazz, false, builder.getAppInfo());
      Value outValue =
          builder.writeRegister(outline.argumentCount(), latticeElement, ThrowingInfo.CAN_THROW);
      Instruction newInstruction = new NewInstance(clazz, outValue);
      builder.add(newInstruction);
      return argumentMapIndex;
    }
  }

  private static class InvokeOutlineInstruction extends OutlineInstruction {
    private final DexMethod method;
    private final Invoke.Type invokeType;
    private final boolean hasOutValue;
    private final DexProto proto;
    private final boolean hasReceiver;

    private InvokeOutlineInstruction(
        DexMethod method,
        Type type,
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
      int result = method.slowCompareTo(o.method);
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
        result = proto.slowCompareTo(o.proto);
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
        TypeLatticeElement latticeElement =
            TypeLatticeElement.fromDexType(method.proto.returnType, true, builder.getAppInfo());
        outValue =
            builder.writeRegister(outline.argumentCount(), latticeElement, ThrowingInfo.CAN_THROW);
      }

      Instruction newInstruction = Invoke.create(invokeType, method, proto, outValue, inValues);
      builder.add(newInstruction);
      return argumentMapIndex;
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
    final public DexType returnType;

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
        DexType[] argumentTypesArray = argumentTypes.toArray(new DexType[argumentTypes.size()]);
        proto = dexItemFactory.createProto(returnType, argumentTypesArray);
      }
      return proto;
    }

    // Build the DexMethod for this outline.
    DexMethod buildMethod(DexType clazz, DexString name) {
      return dexItemFactory.createMethod(clazz, buildProto(), name);
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Outline)) {
        return false;
      }
      List<OutlineInstruction> instructions0 = this.templateInstructions;
      List<OutlineInstruction> instructions1 = ((Outline) other).templateInstructions;
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
      return argumentMap.equals(((Outline) other).argumentMap)
          && returnType == ((Outline) other).returnType;
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
      result = buildProto().slowCompareTo(other.buildProto());
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
  abstract private class OutlineSpotter {

    final DexEncodedMethod method;
    final BasicBlock block;
    // instructionArrayCache is block.getInstructions() copied to an ArrayList.
    private List<Instruction> instructionArrayCache = null;

    int start;
    int index;
    int actualInstructions;
    List<Value> arguments;
    List<DexType> argumentTypes;
    List<Integer> argumentsMap;
    int argumentRegisters;
    DexType returnType;
    Value returnValue;
    int returnValueUsersLeft;
    int pendingNewInstanceIndex = -1;

    OutlineSpotter(DexEncodedMethod method, BasicBlock block) {
      this.method = method;
      this.block = block;
      reset(0);
    }

    protected List<Instruction> getInstructionArray() {
      if (instructionArrayCache == null) {
        instructionArrayCache = new ArrayList<>(block.getInstructions());
      }
      return instructionArrayCache;
    }

    // Call this before modifying block.getInstructions().
    protected void invalidateInstructionArray() {
      instructionArrayCache = null;
    }

    protected void process() {
      List<Instruction> instructions;
      for (;;) {
        instructions = getInstructionArray(); // ProcessInstruction may have invalidated it.
        if (index >= instructions.size()) {
          break;
        }
        processInstruction(instructions.get(index));
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
      boolean include = false;
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
      } else {
        include = canIncludeInstruction(instruction);
      }

      if (include) {
        actualInstructions += instructionIncrement;

        // Add this instruction.
        includeInstruction(instruction);
        // Check if this instruction ends the outline.
        if (actualInstructions >= options.outline.maxSize) {
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
      int returnValueUsersLeftIfIncluded = returnValueUsersLeft;
      if (returnValue != null) {
        for (Value value : instruction.inValues()) {
          if (value == returnValue) {
            returnValueUsersLeftIfIncluded--;
          }
        }
      }

      // If this instruction has an out-value, but the previous one is still active end the
      // outline.
      if (instruction.outValue() != null && returnValueUsersLeftIfIncluded > 0) {
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
      ConstraintWithTarget constraint =
          invoke.inliningConstraint(inliningConstraints, method.method.holder);
      if (constraint != ConstraintWithTarget.ALWAYS) {
        return false;
      }
      // Find the number of in-going arguments, if adding this instruction.
      int newArgumentRegisters = argumentRegisters;
      if (instruction.inValues().size() > 0) {
        List<Value> inValues = orderedInValues(instruction, returnValue);
        for (int i = 0; i < inValues.size(); i++) {
          Value value = inValues.get(i);
          if (value == returnValue) {
            continue;
          }
          if (invoke.isInvokeStatic()) {
            newArgumentRegisters += value.requiredRegisters();
          } else {
            // For virtual calls only re-use the receiver argument.
            if (i > 0 || !arguments.contains(value)) {
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
        List<Instruction> instructions = getInstructionArray();
        do {
          offset++;
          previous = instructions.get(index - offset);
        } while (previous.isConstInstruction());
        if (!previous.isNewInstance() || previous.outValue() != returnValue) {
          return false;
        }
        // Clear pending new instance flag as the last thing, now the matching constructor is known
        // to be included.
        pendingNewInstanceIndex = -1;
      }
      return true;
    }

    private DexType argumentTypeFromInvoke(InvokeMethod invoke, int index) {
      assert invoke.isInvokeMethodWithReceiver() || invoke.isInvokePolymorphic();
      if (index == 0) {
        return invoke.getInvokedMethod().getHolder();
      }
      DexProto methodProto;
      if (invoke.isInvokePolymorphic()) {
        // Type of argument of a polymorphic call must be taken from the call site.
        methodProto = invoke.asInvokePolymorphic().getProto();
      } else {
        methodProto = invoke.getInvokedMethod().proto;
      }
      // -1 due to receiver.
      return methodProto.parameters.values[index - 1];
    }

    // Add the current instruction to the outline.
    private void includeInstruction(Instruction instruction) {
      List<Value> inValues = orderedInValues(instruction, returnValue);

      Value prevReturnValue = returnValue;
      if (returnValue != null) {
        for (Value value : inValues) {
          if (value == returnValue) {
            assert returnValueUsersLeft > 0;
            returnValueUsersLeft--;
          }
          if (returnValueUsersLeft == 0) {
            returnValue = null;
            returnType = dexItemFactory.voidType;
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
          Value value = inValues.get(i);
          if (value == prevReturnValue) {
            argumentsMap.add(OutlineInstruction.OUTLINE_TEMP);
            continue;
          }
          if (instruction.isInvokeMethodWithReceiver() || instruction.isInvokePolymorphic()) {
            InvokeMethod invoke = instruction.asInvokeMethod();
            int argumentIndex = arguments.indexOf(value);
            // For virtual calls only re-use the receiver argument.
            if (i == 0 && argumentIndex != -1) {
              DexType receiverType = argumentTypeFromInvoke(invoke, i);
              // Ensure that the outline argument type is specific enough.
              if (receiverType.isClassType()) {
                if (receiverType.isSubtypeOf(argumentTypes.get(argumentIndex), appInfo)) {
                  argumentTypes.set(argumentIndex, receiverType);
                }
              }
              argumentsMap.add(argumentIndex);
            } else {
              arguments.add(value);
              argumentRegisters += value.requiredRegisters();
              argumentTypes.add(argumentTypeFromInvoke(invoke, i));
              argumentsMap.add(argumentTypes.size() - 1);
            }
          } else {
            arguments.add(value);
            if (instruction.isInvokeMethod()) {
              argumentTypes
                  .add(instruction.asInvokeMethod().getInvokedMethod().proto.parameters.values[i]);
            } else {
              argumentTypes.add(instruction.asBinop().getNumericType().dexTypeFor(dexItemFactory));
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
              instruction.asBinop().getNumericType().dexTypeFor(dexItemFactory));
        }
      }
    }

    private void updateReturnValueState(Value newReturnValue, DexType newReturnType) {
      returnValueUsersLeft = newReturnValue.numberOfAllUsers();
      // If the return value is not used don't track it.
      if (returnValueUsersLeft == 0) {
        returnValue = null;
        returnType = dexItemFactory.voidType;
      } else {
        returnValue = newReturnValue;
        returnType = newReturnType;
      }
    }


    protected abstract void handle(int start, int end, Outline outline);

    private void candidate(int start, int index) {
      List<Instruction> instructions = getInstructionArray();
      assert !instructions.get(start).isConstInstruction();

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
      while (instructions.get(end - 1).isConstInstruction()) {
        end--;
      }

      // Check if the candidate qualifies.
      int nonConstInstructions = 0;
      for (int i = start; i < end; i++) {
        if (!instructions.get(i).isConstInstruction()) {
          nonConstInstructions++;
        }
      }
      if (nonConstInstructions < options.outline.minSize) {
        reset(start + 1);
        return;
      }

      Outline outline =
          new Outline(instructions, argumentTypes, argumentsMap, returnType, start, end);
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
      returnValueUsersLeft = 0;
      pendingNewInstanceIndex = -1;
    }
  }

  // Collect outlining candidates with the methods that can use them.
  // TODO(sgjesse): This does not take several usages in the same method into account.
  private class OutlineMethodIdentifier extends OutlineSpotter {

    private final Map<Outline, List<DexEncodedMethod>> candidateMap;

    OutlineMethodIdentifier(
        DexEncodedMethod method,
        BasicBlock block,
        Map<Outline, List<DexEncodedMethod>> candidateMap) {
      super(method, block);
      this.candidateMap = candidateMap;
    }

    @Override
    protected void handle(int start, int end, Outline outline) {
      synchronized (candidateMap) {
        candidateMap.computeIfAbsent(outline, this::addOutlineMethodList).add(method);
      }
    }

    private List<DexEncodedMethod> addOutlineMethodList(Outline outline) {
      List<DexEncodedMethod> result = new ArrayList<>();
      candidateMethodLists.add(result);
      return result;
    }
  }

  private class OutlineSiteIdentifier extends OutlineSpotter {

    OutlineSiteIdentifier(DexEncodedMethod method, BasicBlock block) {
      super(method, block);
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
    private final ListIterator<BasicBlock> blocksIterator;
    private final List<Integer> toRemove;
    int argumentsMapIndex;

    OutlineRewriter(
        DexEncodedMethod method, IRCode code,
        ListIterator<BasicBlock> blocksIterator, BasicBlock block, List<Integer> toRemove) {
      super(method, block);
      this.code = code;
      this.blocksIterator = blocksIterator;
      this.toRemove = toRemove;
    }

    @Override
    protected void handle(int start, int end, Outline outline) {
      DexMethod m = generatedOutlines.get(outline);
      if (m != null) {
        assert removeMethodFromOutlineList(outline);
        List<Value> in = new ArrayList<>();
        Value returnValue = null;
        argumentsMapIndex = 0;
        Position position = Position.none();
        { // Scope for 'instructions'.
          List<Instruction> instructions = getInstructionArray();
          for (int i = start; i < end; i++) {
            Instruction current = instructions.get(i);
            if (current.isConstInstruction()) {
              // Leave any const instructions.
              continue;
            }
            if (position.isNone()) {
              position = current.getPosition();
            }
            // Prepare to remove the instruction.
            List<Value> inValues = orderedInValues(current, returnValue);
            for (int j = 0; j < inValues.size(); j++) {
              Value value = inValues.get(j);
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
              toRemove.add(i);
            }
          }
        }
        assert m.proto.shorty.toString().length() - 1 == in.size();
        if (returnValue != null && !returnValue.isUsed()) {
          returnValue = null;
        }
        Invoke outlineInvoke = new InvokeStatic(m, returnValue, in);
        outlineInvoke.setBlock(block);
        outlineInvoke.setPosition(position);
        if (position.isNone() && code.doAllThrowingInstructionsHavePositions()) {
          // We have introduced a static invoke, but non of the outlines instructions could throw
          // and none had a position. The code no longer has the previous property.
          code.setAllThrowingInstructionsHavePositions(false);
        }
        InstructionListIterator endIterator = block.listIterator(end - 1);
        Instruction instructionBeforeEnd = endIterator.next();
        invalidateInstructionArray(); // Because we're about to modify the original linked list.
        instructionBeforeEnd.clearBlock();
        endIterator.set(outlineInvoke); // Replaces instructionBeforeEnd.
        if (block.hasCatchHandlers()) {
          // If the inserted invoke is inserted in a block with handlers, split the block after
          // the inserted invoke.
          endIterator.split(code, blocksIterator);
        }
      }
    }

    /** When assertions are enabled, remove method from the outline's list. */
    private boolean removeMethodFromOutlineList(Outline outline) {
      synchronized (outlineSites) {
        assert outlineSites.get(outline).remove(method);
      }
      return true;
    }
  }

  public Outliner(AppInfoWithLiveness appInfo, InternalOptions options, IRConverter converter) {
    this.appInfo = appInfo;
    this.dexItemFactory = appInfo.dexItemFactory;
    this.converter = converter;
    this.inliningConstraints = new InliningConstraints(appInfo);
    this.options = options;
  }

  public BiConsumer<IRCode, DexEncodedMethod> identifyCandidateMethods() {
    // Since optimizations may change the map identity of Outline objects (e.g. by setting the
    // out-value of invokes to null), this map must not be used except for identifying methods
    // potentially relevant to outlining. OutlineMethodIdentifier will add method lists to
    // candidateMethodLists whenever it adds an entry to candidateMap.
    Map<Outline, List<DexEncodedMethod>> candidateMap = new HashMap<>();
    assert candidateMethodLists.isEmpty();
    return (code, method) -> {
      assert !(method.getCode() instanceof OutlineCode);
      for (BasicBlock block : code.blocks) {
        new OutlineMethodIdentifier(method, block, candidateMap).process();
      }
    };
  }

  public void identifyOutlineSites(IRCode code, DexEncodedMethod method) {
    assert !(method.getCode() instanceof OutlineCode);
    for (BasicBlock block : code.blocks) {
      new OutlineSiteIdentifier(method, block).process();
    }
  }

  public boolean selectMethodsForOutlining() {
    assert methodsSelectedForOutlining.size() == 0;
    assert outlineSites.size() == 0;
    for (List<DexEncodedMethod> outlineMethods : candidateMethodLists) {
      if (outlineMethods.size() >= options.outline.threshold) {
        for (DexEncodedMethod outlineMethod : outlineMethods) {
          methodsSelectedForOutlining.add(
              converter.graphLense().mapDexEncodedMethod(appInfo, outlineMethod));
        }
      }
    }
    candidateMethodLists.clear();
    return methodsSelectedForOutlining.size() > 0;
  }

  public Set<DexEncodedMethod> getMethodsSelectedForOutlining() {
    return methodsSelectedForOutlining;
  }

  public DexProgramClass buildOutlinerClass(DexType type) {
    // Build the outlined methods.
    // By now the candidates are the actual selected outlines. Name the generated methods in a
    // consistent order, to provide deterministic output.
    List<Outline> outlines = selectOutlines();
    outlines.sort(Comparator.naturalOrder());
    DexEncodedMethod[] direct = new DexEncodedMethod[outlines.size()];
    int count = 0;
    for (Outline outline : outlines) {
      MethodAccessFlags methodAccess =
          MethodAccessFlags.fromSharedAccessFlags(
              Constants.ACC_PUBLIC | Constants.ACC_STATIC, false);
      DexString methodName = dexItemFactory.createString(OutlineOptions.METHOD_PREFIX + count);
      DexMethod method = outline.buildMethod(type, methodName);
      List<DexEncodedMethod> sites = outlineSites.get(outline);
      assert !sites.isEmpty();
      direct[count] =
          new DexEncodedMethod(
              method,
              methodAccess,
              DexAnnotationSet.empty(),
              ParameterAnnotationsList.empty(),
              new OutlineCode(outline));
      if (options.isGeneratingClassFiles()) {
        direct[count].upgradeClassFileVersion(sites.get(0).getClassFileVersion());
      }
      generatedOutlines.put(outline, method);
      count++;
    }
    // No need to sort the direct methods as they are generated in sorted order.

    // Build the outliner class.
    DexType superType = dexItemFactory.createType("Ljava/lang/Object;");
    DexTypeList interfaces = DexTypeList.empty();
    DexString sourceFile = dexItemFactory.createString("outline");
    ClassAccessFlags accessFlags = ClassAccessFlags.fromSharedAccessFlags(Constants.ACC_PUBLIC);
    DexProgramClass clazz =
        new DexProgramClass(
            type,
            null,
            new SynthesizedOrigin("outlining", getClass()),
            accessFlags,
            superType,
            interfaces,
            sourceFile,
            null,
            Collections.emptyList(),
            // TODO: Build dex annotations structure.
            DexAnnotationSet.empty(),
            DexEncodedField.EMPTY_ARRAY, // Static fields.
            DexEncodedField.EMPTY_ARRAY, // Instance fields.
            direct,
            DexEncodedMethod.EMPTY_ARRAY, // Virtual methods.
            options.itemFactory.getSkipNameValidationForTesting());
    if (options.isGeneratingClassFiles()) {
      // All program classes must have a class-file version. Use Java 6.
      clazz.setClassFileVersion(Opcodes.V1_6);
    }
    return clazz;
  }

  private List<Outline> selectOutlines() {
    assert outlineSites.size() > 0;
    assert candidateMethodLists.isEmpty();
    List<Outline> result = new ArrayList<>();
    for (Entry<Outline, List<DexEncodedMethod>> entry : outlineSites.entrySet()) {
      if (entry.getValue().size() >= options.outline.threshold) {
        result.add(entry.getKey());
      }
    }
    return result;
  }

  public void applyOutliningCandidate(IRCode code, DexEncodedMethod method) {
    assert !(method.getCode() instanceof OutlineCode);
    ListIterator<BasicBlock> blocksIterator = code.blocks.listIterator();
    while (blocksIterator.hasNext()) {
      BasicBlock block = blocksIterator.next();
      List<Integer> toRemove = new ArrayList<>();
      new OutlineRewriter(method, code, blocksIterator, block, toRemove).process();
      block.removeInstructions(toRemove);
    }
  }

  public boolean checkAllOutlineSitesFoundAgain() {
    for (Outline outline : generatedOutlines.keySet()) {
      assert outlineSites.get(outline).isEmpty() : outlineSites.get(outline);
    }
    return true;
  }

  static public void noProcessing(IRCode code, DexEncodedMethod method) {
    // No operation.
  }

  private class OutlineSourceCode implements SourceCode {

    final private Outline outline;
    private final Position position;
    private int argumentMapIndex = 0;

    OutlineSourceCode(Outline outline, DexMethod method) {
      this.outline = outline;
      this.position = Position.synthetic(0, method, null);
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
    public void setUp() {
    }

    @Override
    public void clear() {
    }

    @Override
    public void buildPrelude(IRBuilder builder) {
      // Fill in the Argument instructions in the argument block.
      for (int i = 0; i < outline.argumentTypes.size(); i++) {
        TypeLatticeElement typeLattice =
            TypeLatticeElement.fromDexType(outline.argumentTypes.get(i), true, appInfo);
        builder.addNonThisArgument(i, typeLattice);
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
      // Build IR from the template.
      argumentMapIndex =
          outline
              .templateInstructions
              .get(instructionIndex)
              .createInstruction(builder, outline, argumentMapIndex);
    }

    @Override
    public void resolveAndBuildSwitch(int value, int fallthroughOffset, int payloadOffset,
        IRBuilder builder) {
      throw new Unreachable("Unexpected call to resolveAndBuildSwitch");
    }

    @Override
    public void resolveAndBuildNewArrayFilledData(int arrayRef, int payloadOffset,
        IRBuilder builder) {
      throw new Unreachable("Unexpected call to resolveAndBuildNewArrayFilledData");
    }

    @Override
    public CatchHandlers<Integer> getCurrentCatchHandlers() {
      return null;
    }

    @Override
    public int getMoveExceptionRegister(int instructionIndex) {
      throw new Unreachable();
    }

    @Override
    public Position getCanonicalDebugPositionAtOffset(int offset) {
      throw new Unreachable();
    }

    @Override
    public Position getCurrentPosition() {
      return position;
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
    public OutlineCode asOutlineCode() {
      return this;
    }

    @Override
    public boolean isEmptyVoidMethod() {
      return false;
    }

    @Override
    public IRCode buildIR(
        DexEncodedMethod encodedMethod,
        AppInfo appInfo,
        GraphLense graphLense,
        InternalOptions options,
        Origin origin) {
      assert getOwner() == encodedMethod;
      OutlineSourceCode source = new OutlineSourceCode(outline, encodedMethod.method);
      return new IRBuilder(encodedMethod, appInfo, source, options, origin).build(encodedMethod);
    }

    @Override
    public String toString() {
      return outline.toString();
    }

    @Override
    public void registerCodeReferences(UseRegistry registry) {
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
    public String toString(DexEncodedMethod method, ClassNameMapper naming) {
      return null;
    }
  }
}
