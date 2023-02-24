// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static com.android.tools.r8.utils.StringUtils.join;

import com.android.tools.r8.cf.code.CfArithmeticBinop;
import com.android.tools.r8.cf.code.CfArrayLength;
import com.android.tools.r8.cf.code.CfArrayLoad;
import com.android.tools.r8.cf.code.CfArrayStore;
import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfCmp;
import com.android.tools.r8.cf.code.CfConstClass;
import com.android.tools.r8.cf.code.CfConstDynamic;
import com.android.tools.r8.cf.code.CfConstMethodHandle;
import com.android.tools.r8.cf.code.CfConstMethodType;
import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfDexItemBasedConstString;
import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfGoto;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfIfCmp;
import com.android.tools.r8.cf.code.CfIinc;
import com.android.tools.r8.cf.code.CfInstanceFieldRead;
import com.android.tools.r8.cf.code.CfInstanceFieldWrite;
import com.android.tools.r8.cf.code.CfInstanceOf;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfInvokeDynamic;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfLogicalBinop;
import com.android.tools.r8.cf.code.CfMonitor;
import com.android.tools.r8.cf.code.CfMultiANewArray;
import com.android.tools.r8.cf.code.CfNeg;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfNewArray;
import com.android.tools.r8.cf.code.CfNop;
import com.android.tools.r8.cf.code.CfNumberConversion;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStaticFieldRead;
import com.android.tools.r8.cf.code.CfStaticFieldWrite;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.cf.code.CfSwitch;
import com.android.tools.r8.cf.code.CfThrow;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.MonitorType;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.StringUtils.BraceType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.objectweb.asm.Opcodes;

/** Rudimentary printer to print the source representation for creating CfCode object. */
public class CfCodePrinter extends CfPrinter {

  private Set<String> imports = new HashSet<>();
  private List<String> methods = new ArrayList<>();
  private Set<String> methodNames = new HashSet<>();
  private Set<String> synthesizedTypes = new HashSet<>();

  // Per method structures.

  // Sorted list of labels.
  private List<CfLabel> sortedLabels = null;
  // Map from label to its sorted-order index.
  private Reference2IntMap<CfLabel> labelToIndex = null;
  private boolean pendingComma = false;
  private StringBuilder builder = null;

  public CfCodePrinter() {}

  public Set<String> getImports() {
    return imports;
  }

  public List<String> getImportsSorted() {
    ArrayList<String> sorted = new ArrayList<>(imports);
    sorted.sort(String::compareTo);
    return sorted;
  }

  public List<String> getMethods() {
    return methods;
  }

  public void visitMethod(String methodName, CfCode code) {
    if (!methodNames.add(methodName)) {
      throw new IllegalStateException(
          "Invalid attempt to visit the same method twice: " + methodName);
    }
    labelToIndex = new Reference2IntOpenHashMap<>();
    sortedLabels = new ArrayList<>();
    pendingComma = false;
    builder =
        new StringBuilder()
            .append("public static ")
            .append(r8Type("CfCode", "graph"))
            .append(" ")
            .append(methodName)
            .append("(")
            .append(dexItemFactoryType())
            .append(" factory, ")
            .append(r8Type("DexMethod", "graph"))
            .append(" method) {");

    for (CfInstruction instruction : code.getInstructions()) {
      if (instruction instanceof CfLabel) {
        CfLabel label = (CfLabel) instruction;
        labelToIndex.put(label, sortedLabels.size());
        sortedLabels.add(label);
        builder
            .append("CfLabel ")
            .append(labelName(label))
            .append(" = new " + cfType("CfLabel") + "();");
      }
    }

    builder
        .append("return new " + r8Type("CfCode", "graph") + "(")
        .append("method.holder,")
        .append(code.getMaxStack())
        .append(",")
        .append(code.getMaxLocals())
        .append(",")
        .append(immutableListType())
        .append(".of(");

    for (CfInstruction instruction : code.getInstructions()) {
      instruction.print(this);
    }

    builder.append("),").append(immutableListType()).append(".of(");
    pendingComma = false;
    for (CfTryCatch tryCatchRange : code.getTryCatchRanges()) {
      String guards =
          tryCatchRange.guards.stream().map(this::dexType).collect(Collectors.joining(", "));
      String targets =
          tryCatchRange.targets.stream().map(this::labelName).collect(Collectors.joining(", "));
      printNewInstruction(
          "CfTryCatch",
          labelName(tryCatchRange.start),
          labelName(tryCatchRange.end),
          immutableListType() + ".of(" + guards + ")",
          immutableListType() + ".of(" + targets + ")");
    }

    builder.append("),").append(immutableListType()).append(".of());").append("}");

    methods.add(builder.toString());
  }

  private String quote(String string) {
    return "\"" + string + "\"";
  }

  private String longValue(long value) {
    return (value < Integer.MIN_VALUE || Integer.MAX_VALUE < value) ? (value + "L") : ("" + value);
  }

  // Ensure a type import for a given type.
  // Note that the package should be given as individual parts to avoid the repackage "fixing" it...
  private String type(String name, List<String> pkg) {
    assert !name.contains(".");
    assert pkg.stream().noneMatch(p -> p.contains("."));
    imports.add(String.join(".", pkg) + "." + name);
    return name;
  }

  private String immutableListType() {
    return type("ImmutableList", ImmutableList.of("com", "google", "common", "collect"));
  }

  private String int2ObjectAVLTreeMapType() {
    return type("Int2ObjectAVLTreeMap", ImmutableList.of("it", "unimi", "dsi", "fastutil", "ints"));
  }

  private String frameTypeType() {
    return r8Type("FrameType", ImmutableList.of("cf", "code", "frame"));
  }

  private String dexItemFactoryType() {
    return r8Type("DexItemFactory", "graph");
  }

  private String arrayDequeType() {
    return type("ArrayDeque", ImmutableList.of("java", "util"));
  }

  private String arraysType() {
    return type("Arrays", ImmutableList.of("java", "util"));
  }

  private String r8Type(String name, String pkg) {
    return r8Type(name, Collections.singletonList(pkg));
  }

  private String r8Type(String name, List<String> pkg) {
    return type(name, ImmutableList.<String>builder()
        .addAll(ImmutableList.of("com", "android", "tools", "r8"))
        .addAll(pkg).build());
  }

  private String irType(String name) {
    return r8Type(name, ImmutableList.of("ir", "code"));
  }

  private String cfType(String name) {
    return r8Type(name, ImmutableList.of("cf", "code"));
  }

  private String cfFrameType() {
    return cfType("CfFrame");
  }

  private String labelName(CfLabel label) {
    return "label" + labelToIndex.getInt(label);
  }

  private String valueType(ValueType type) {
    return irType("ValueType") + "." + type.name();
  }

  private String numericType(NumericType type) {
    return irType("NumericType") + "." + type.name();
  }

  private String memberType(MemberType type) {
    return irType("MemberType") + "." + type.name();
  }

  private String ifTypeKind(IfType kind) {
    return irType("IfType") + "." + kind.name();
  }

  private String monitorTypeKind(MonitorType kind) {
    return irType("MonitorType") + "." + kind.name();
  }

  private String dexString(DexString string) {
    return "factory.createString(" + quote(string.toString()) + ")";
  }

  private final Map<String, String> knownTypeFields =
      ImmutableMap.<String, String>builder()
          .put("Z", "booleanType")
          .put("B", "byteType")
          .put("C", "charType")
          .put("D", "doubleType")
          .put("F", "floatType")
          .put("I", "intType")
          .put("J", "longType")
          .put("S", "shortType")
          .put("V", "voidType")
          .put("[Z", "booleanArrayType")
          .put("[B", "byteArrayType")
          .put("[C", "charArrayType")
          .put("[D", "doubleArrayType")
          .put("[F", "floatArrayType")
          .put("[I", "intArrayType")
          .put("[J", "longArrayType")
          .put("[S", "shortArrayType")
          .put("Ljava/lang/Object;", "objectType")
          .put("Ljava/lang/Class;", "classType")
          .put("Ljava/lang/Throwable;", "throwableType")
          .put("Ljava/lang/String;", "stringType")
          .put("Ljava/lang/Character;", "boxedCharType")
          .put("Ljava/lang/CharSequence;", "charSequenceType")
          .put("Ljava/lang/StringBuilder;", "stringBuilderType")
          .put("Ljava/lang/AutoCloseable;", "autoCloseableType")
          .build();

  private String dexType(DexType type) {
    String descriptor = type.toDescriptorString();
    String field = knownTypeFields.get(descriptor);
    if (field != null) {
      return "factory." + field;
    }
    synthesizedTypes.add(descriptor);
    return "factory.createType(" + quote(descriptor) + ")";
  }

  private String dexProto(DexProto proto) {
    StringBuilder builder =
        new StringBuilder().append("factory.createProto(").append(dexType(proto.returnType));
    for (DexType param : proto.parameters.values) {
      builder.append(", ").append(dexType(param));
    }
    return builder.append(")").toString();
  }

  private String dexMethod(DexMethod method) {
    return "factory.createMethod("
        + dexType(method.holder)
        + ", "
        + dexProto(method.proto)
        + ", "
        + dexString(method.name)
        + ")";
  }

  private String dexField(DexField field) {
    return "factory.createField("
        + dexType(field.holder)
        + ", "
        + dexType(field.type)
        + ", "
        + dexString(field.name)
        + ")";
  }

  private void ensureComma() {
    if (pendingComma) {
      builder.append(",");
    }
    pendingComma = true;
  }

  private void printNewInstruction(String name, String... args) {
    ensureComma();
    builder.append("new ").append(cfType(name));
    StringUtils.append(builder, Arrays.asList(args), ", ", BraceType.PARENS);
  }

  private void printNewVarInstruction(String name, ValueType type, int index) {
    printNewInstruction(name, valueType(type), "" + index);
  }

  private void printNewJumpInstruction(String name, IfType kind, ValueType type, CfLabel target) {
    printNewInstruction(name, ifTypeKind(kind), valueType(type), labelName(target));
  }

  @Override
  public void print(CfNop nop) {
    // Since locals and lines are not printed, no need to print nops.
  }

  @Override
  public void print(CfStackInstruction instruction) {
    printNewInstruction(
        "CfStackInstruction",
        cfType("CfStackInstruction") + ".Opcode." + instruction.getOpcode().name());
  }

  @Override
  public void print(CfThrow insn) {
    printNewInstruction("CfThrow");
  }

  @Override
  public void print(CfConstNull constNull) {
    printNewInstruction("CfConstNull");
  }

  @Override
  public void print(CfConstNumber constNumber) {
    printNewInstruction(
        "CfConstNumber", longValue(constNumber.getRawValue()), valueType(constNumber.getType()));
  }

  @Override
  public void print(CfConstClass constClass) {
    printNewInstruction("CfConstClass", dexType(constClass.getType()));
  }

  @Override
  public void print(CfConstDynamic constDynamic) {
    // TODO(b/198143561): Support CfConstDynamic.
    throw new Unimplemented(constDynamic.getClass().getSimpleName());
  }

  @Override
  public void print(CfReturnVoid ret) {
    printNewInstruction("CfReturnVoid");
  }

  @Override
  public void print(CfReturn ret) {
    printNewInstruction("CfReturn", valueType(ret.getType()));
  }

  @Override
  public void print(CfMonitor monitor) {
    printNewInstruction("CfMonitor", monitorTypeKind(monitor.getType()));
  }

  @Override
  public void print(CfArithmeticBinop arithmeticBinop) {
    printNewInstruction(
        "CfArithmeticBinop",
        cfType("CfArithmeticBinop") + ".Opcode." + arithmeticBinop.getOpcode().name(),
        numericType(arithmeticBinop.getType()));
  }

  @Override
  public void print(CfCmp cmp) {
    printNewInstruction(
        "CfCmp", irType("Cmp") + ".Bias." + cmp.getBias().name(), numericType(cmp.getType()));
  }

  @Override
  public void print(CfLogicalBinop logicalBinop) {
    printNewInstruction(
        "CfLogicalBinop",
        cfType("CfLogicalBinop") + ".Opcode." + logicalBinop.getOpcode().name(),
        numericType(logicalBinop.getType()));
  }

  @Override
  public void print(CfNeg neg) {
    printNewInstruction("CfNeg", numericType(neg.getType()));
  }

  @Override
  public void print(CfNumberConversion numberConversion) {
    printNewInstruction(
        "CfNumberConversion",
        numericType(numberConversion.getFromType()),
        numericType(numberConversion.getToType()));
  }

  @Override
  public void print(CfConstString constString) {
    printNewInstruction("CfConstString", dexString(constString.getString()));
  }

  @Override
  public void print(CfDexItemBasedConstString constString) {
    throw new Unimplemented(constString.getClass().getSimpleName());
  }

  @Override
  public void print(CfArrayLoad arrayLoad) {
    printNewInstruction("CfArrayLoad", memberType(arrayLoad.getType()));
  }

  @Override
  public void print(CfArrayStore arrayStore) {
    printNewInstruction("CfArrayStore", memberType(arrayStore.getType()));
  }

  @Override
  public void print(CfInvoke invoke) {
    printNewInstruction(
        "CfInvoke",
        Integer.toString(invoke.getOpcode()),
        dexMethod(invoke.getMethod()),
        Boolean.toString(invoke.isInterface()));
  }

  @Override
  public void print(CfInvokeDynamic invoke) {
    throw new Unimplemented(invoke.getClass().getSimpleName());
  }

  @Override
  public void print(CfFrame frame) {
    if (frame.getLocals().isEmpty()) {
      if (frame.getStack().isEmpty()) {
        printNewInstruction(cfFrameType());
      } else {
        printNewInstruction(cfFrameType(), getCfFrameStack(frame));
      }
    } else {
      if (frame.getStack().isEmpty()) {
        printNewInstruction(cfFrameType(), getCfFrameLocals(frame));
      } else {
        printNewInstruction(cfFrameType(), getCfFrameLocals(frame), getCfFrameStack(frame));
      }
    }
  }

  private String getCfFrameLocals(CfFrame frame) {
    String localsKeys = join(",", frame.getLocals().keySet());
    String localsElements = join(",", frame.getLocals().values(), this::frameTypeType);
    return "new "
        + int2ObjectAVLTreeMapType()
        + "<>("
        + "new int[] {"
        + localsKeys
        + "},"
        + "new "
        + frameTypeType()
        + "[] { "
        + localsElements
        + " })";
  }

  private String getCfFrameStack(CfFrame frame) {
    String stackElements = join(",", frame.getStack(), this::frameTypeType);
    return "new " + arrayDequeType() + "<>(" + arraysType() + ".asList(" + stackElements + "))";
  }

  private String frameTypeType(FrameType frameType) {
    if (frameType.isOneWord()) {
      return frameTypeType() + ".oneWord()";
    } else if (frameType.isTwoWord()) {
      return frameTypeType() + ".twoWord()";
    } else if (frameType.isUninitializedThis()) {
      return frameTypeType() + ".uninitializedThis()";
    } else if (frameType.isUninitializedNew()) {
      return frameTypeType() + ".uninitializedNew(new " + cfType("CfLabel") + "())";
    } else if (frameType.isPrimitive()) {
      if (frameType.isWidePrimitiveHigh()) {
        return frameTypeType()
            + "."
            + frameType.asWidePrimitive().getLowType().getTypeName()
            + "HighType()";
      } else {
        return frameTypeType() + "." + frameType.asPrimitive().getTypeName() + "Type()";
      }
    } else {
      assert frameType.isInitializedReferenceType();
      assert !frameType.isInitializedNonNullReferenceTypeWithInterfaces()
          : "Unexpected InitializedNonNullReferenceTypeWithInterfaces in CfFrame";
      if (frameType.isNullType()) {
        return frameTypeType() + ".nullType()";
      } else {
        assert frameType.isInitializedNonNullReferenceTypeWithoutInterfaces();
        return frameTypeType()
            + ".initializedNonNullReference("
            + dexType(
                frameType.asInitializedNonNullReferenceTypeWithoutInterfaces().getInitializedType())
            + ")";
      }
    }
  }

  @Override
  public void print(CfInstanceOf insn) {
    printNewInstruction("CfInstanceOf", dexType(insn.getType()));
  }

  @Override
  public void print(CfCheckCast insn) {
    printNewInstruction("CfCheckCast", dexType(insn.getType()));
  }

  @Override
  public void print(CfInstanceFieldRead insn) {
    printNewInstruction("CfInstanceFieldRead", dexField(insn.getField()));
  }

  @Override
  public void print(CfInstanceFieldWrite insn) {
    printNewInstruction("CfInstanceFieldWrite", dexField(insn.getField()));
  }

  @Override
  public void print(CfStaticFieldRead insn) {
    printNewInstruction("CfStaticFieldRead", dexField(insn.getField()));
  }

  @Override
  public void print(CfStaticFieldWrite insn) {
    printNewInstruction("CfStaticFieldWrite", dexField(insn.getField()));
  }

  @Override
  public void print(CfFieldInstruction insn) {
    switch (insn.getOpcode()) {
      case Opcodes.GETFIELD:
        printNewInstruction("CfInstanceFieldRead", dexField(insn.getField()));
        break;
      case Opcodes.PUTFIELD:
        printNewInstruction("CfInstanceFieldWrite", dexField(insn.getField()));
        break;
      case Opcodes.GETSTATIC:
        printNewInstruction("CfStaticFieldRead", dexField(insn.getField()));
        break;
      case Opcodes.PUTSTATIC:
        printNewInstruction("CfStaticFieldWrite", dexField(insn.getField()));
        break;
      default:
        throw new Unreachable();
    }
  }

  @Override
  public void print(CfNew newInstance) {
    printNewInstruction("CfNew", dexType(newInstance.getType()));
  }

  @Override
  public void print(CfNewArray newArray) {
    printNewInstruction("CfNewArray", dexType(newArray.getType()));
  }

  @Override
  public void print(CfMultiANewArray multiANewArray) {
    throw new Unimplemented(multiANewArray.getClass().getSimpleName());
  }

  @Override
  public void print(CfArrayLength arrayLength) {
    printNewInstruction("CfArrayLength");
  }

  @Override
  public void print(CfLabel label) {
    ensureComma();
    builder.append(labelName(label));
  }

  @Override
  public void print(CfPosition instruction) {
    // Ignoring positions.
  }

  @Override
  public void print(CfGoto jump) {
    printNewInstruction("CfGoto", labelName(jump.getTarget()));
  }

  @Override
  public void print(CfIf conditional) {
    printNewJumpInstruction(
        "CfIf", conditional.getKind(), conditional.getType(), conditional.getTarget());
  }

  @Override
  public void print(CfIfCmp conditional) {
    printNewJumpInstruction(
        "CfIfCmp", conditional.getKind(), conditional.getType(), conditional.getTarget());
  }

  @Override
  public void print(CfSwitch cfSwitch) {
    throw new Unimplemented(cfSwitch.getClass().getSimpleName());
  }

  @Override
  public void print(CfLoad load) {
    printNewVarInstruction("CfLoad", load.getType(), load.getLocalIndex());
  }

  @Override
  public void print(CfStore store) {
    printNewVarInstruction("CfStore", store.getType(), store.getLocalIndex());
  }

  @Override
  public void print(CfIinc instruction) {
    printNewInstruction(
        "CfIinc",
        Integer.toString(instruction.getLocalIndex()),
        Integer.toString(instruction.getIncrement()));
  }

  @Override
  public void print(CfConstMethodHandle handle) {
    throw new Unimplemented(handle.getClass().getSimpleName());
  }

  @Override
  public void print(CfConstMethodType type) {
    throw new Unimplemented(type.getClass().getSimpleName());
  }

  public Set<String> getSynthesizedTypes() {
    return synthesizedTypes;
  }
}
