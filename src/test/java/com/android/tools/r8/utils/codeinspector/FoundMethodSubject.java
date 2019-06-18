// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.CfCode.LocalVariableInfo;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexDebugEvent;
import com.android.tools.r8.graph.DexDebugInfo;
import com.android.tools.r8.graph.DexDebugPositionState;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.JarCode;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.naming.MemberNaming;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.signature.GenericSignatureParser;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.codeinspector.LocalVariableTable.LocalVariableTableEntry;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Predicate;

public class FoundMethodSubject extends MethodSubject {

  private final CodeInspector codeInspector;
  private final FoundClassSubject clazz;
  private final DexEncodedMethod dexMethod;

  public FoundMethodSubject(
      CodeInspector codeInspector, DexEncodedMethod encoded, FoundClassSubject clazz) {
    this.codeInspector = codeInspector;
    this.clazz = clazz;
    this.dexMethod = encoded;
  }

  @Override
  public IRCode buildIR(DexItemFactory dexItemFactory) {
    InternalOptions options = new InternalOptions(dexItemFactory, new Reporter());
    options.programConsumer = DexIndexedConsumer.emptyConsumer();

    DexEncodedMethod method = getMethod();
    return method
        .getCode()
        .buildIR(
            method,
            AppView.createForD8(new AppInfo(codeInspector.application), options),
            Origin.unknown());
  }

  @Override
  public boolean isPresent() {
    return true;
  }

  @Override
  public boolean isRenamed() {
    return clazz.naming != null && !getFinalSignature().name.equals(getOriginalSignature().name);
  }

  @Override
  public boolean isPublic() {
    return dexMethod.accessFlags.isPublic();
  }

  @Override
  public boolean isProtected() {
    return dexMethod.accessFlags.isProtected();
  }

  @Override
  public boolean isPrivate() {
    return dexMethod.accessFlags.isPrivate();
  }

  @Override
  public boolean isPackagePrivate() {
    return !isPublic() && !isProtected() && !isPrivate();
  }

  @Override
  public boolean isStatic() {
    return dexMethod.accessFlags.isStatic();
  }

  @Override
  public boolean isSynthetic() {
    return dexMethod.accessFlags.isSynthetic();
  }

  @Override
  public boolean isFinal() {
    return dexMethod.accessFlags.isFinal();
  }

  @Override
  public boolean isAbstract() {
    return dexMethod.accessFlags.isAbstract();
  }

  @Override
  public boolean isBridge() {
    return dexMethod.accessFlags.isBridge();
  }

  @Override
  public boolean isInstanceInitializer() {
    return dexMethod.isInstanceInitializer();
  }

  @Override
  public boolean isClassInitializer() {
    return dexMethod.isClassInitializer();
  }

  @Override
  public boolean isVirtual() {
    return dexMethod.isVirtualMethod();
  }

  @Override
  public DexEncodedMethod getMethod() {
    return dexMethod;
  }

  @Override
  public MethodSignature getOriginalSignature() {
    MethodSignature signature = getFinalSignature();
    if (clazz.naming == null) {
      return signature;
    }

    // Map the parameters and return type to original names. This is needed as the in the
    // Proguard map the names on the left side are the original names. E.g.
    //
    //   X -> a
    //     X method(X) -> a
    //
    // whereas the final signature is for X.a is "a (a)"
    String[] originalParameters = new String[signature.parameters.length];
    for (int i = 0; i < originalParameters.length; i++) {
      originalParameters[i] = codeInspector.getOriginalTypeName(signature.parameters[i]);
    }
    String returnType = codeInspector.getOriginalTypeName(signature.type);

    MethodSignature lookupSignature =
        new MethodSignature(signature.name, returnType, originalParameters);

    MemberNaming memberNaming = clazz.naming.lookup(lookupSignature);
    return memberNaming != null ? (MethodSignature) memberNaming.getOriginalSignature() : signature;
  }

  @Override
  public MethodSignature getFinalSignature() {
    return MethodSignature.fromDexMethod(dexMethod.method);
  }

  @Override
  public String getOriginalSignatureAttribute() {
    return codeInspector.getOriginalSignatureAttribute(
        dexMethod.annotations, GenericSignatureParser::parseMethodSignature);
  }

  @Override
  public String getFinalSignatureAttribute() {
    return codeInspector.getFinalSignatureAttribute(dexMethod.annotations);
  }

  @Override
  public Iterator<InstructionSubject> iterateInstructions() {
    return codeInspector.createInstructionIterator(this);
  }

  @Override
  public <T extends InstructionSubject> Iterator<T> iterateInstructions(
      Predicate<InstructionSubject> filter) {
    return new FilteredInstructionIterator<>(codeInspector, this, filter);
  }

  @Override
  public Iterator<TryCatchSubject> iterateTryCatches() {
    return codeInspector.createTryCatchIterator(this);
  }

  @Override
  public <T extends TryCatchSubject> Iterator<T> iterateTryCatches(
      Predicate<TryCatchSubject> filter) {
    return new FilteredTryCatchIterator<>(codeInspector, this, filter);
  }

  @Override
  public boolean hasLocalVariableTable() {
    Code code = getMethod().getCode();
    if (code.isDexCode()) {
      DexCode dexCode = code.asDexCode();
      if (dexCode.getDebugInfo() != null) {
        for (DexString parameter : dexCode.getDebugInfo().parameters) {
          if (parameter != null) {
            return true;
          }
        }
        for (DexDebugEvent event : dexCode.getDebugInfo().events) {
          if (event instanceof DexDebugEvent.StartLocal) {
            return true;
          }
        }
      }
      return false;
    }
    if (code.isCfCode()) {
      return !code.asCfCode().getLocalVariables().isEmpty();
    }
    if (code.isJarCode()) {
      return code.asJarCode().hasLocalVariableTable();
    }
    throw new Unreachable("Unexpected code type: " + code.getClass().getSimpleName());
  }

  @Override
  public LineNumberTable getLineNumberTable() {
    Code code = getMethod().getCode();
    if (code.isDexCode()) {
      return getDexLineNumberTable(code.asDexCode());
    }
    if (code.isCfCode()) {
      return getCfLineNumberTable(code.asCfCode());
    }
    if (code.isJarCode()) {
      return getJarLineNumberTable(code.asJarCode());
    }
    throw new Unreachable("Unexpected code type: " + code.getClass().getSimpleName());
  }

  private LineNumberTable getJarLineNumberTable(JarCode code) {
    throw new Unimplemented("No support for inspecting the line number table for JarCode");
  }

  private LineNumberTable getCfLineNumberTable(CfCode code) {
    int currentLine = -1;
    Reference2IntMap<InstructionSubject> lineNumberTable =
        new Reference2IntOpenHashMap<>(code.getInstructions().size());
    for (CfInstruction insn : code.getInstructions()) {
      if (insn instanceof CfPosition) {
        currentLine = ((CfPosition) insn).getPosition().line;
      }
      if (currentLine != -1) {
        lineNumberTable.put(new CfInstructionSubject(insn), currentLine);
      }
    }
    return currentLine == -1 ? null : new LineNumberTable(lineNumberTable);
  }

  private LineNumberTable getDexLineNumberTable(DexCode code) {
    DexDebugInfo debugInfo = code.getDebugInfo();
    if (debugInfo == null) {
      return null;
    }
    Reference2IntMap<InstructionSubject> lineNumberTable =
        new Reference2IntOpenHashMap<>(code.instructions.length);
    DexDebugPositionState state =
        new DexDebugPositionState(debugInfo.startLine, getMethod().method);
    Iterator<DexDebugEvent> iterator = Arrays.asList(debugInfo.events).iterator();
    for (Instruction insn : code.instructions) {
      int offset = insn.getOffset();
      while (state.getCurrentPc() < offset && iterator.hasNext()) {
        iterator.next().accept(state);
      }
      lineNumberTable.put(new DexInstructionSubject(insn), state.getCurrentLine());
    }
    return new LineNumberTable(lineNumberTable);
  }

  @Override
  public LocalVariableTable getLocalVariableTable() {
    Code code = getMethod().getCode();
    if (code.isDexCode()) {
      return getDexLocalVariableTable(code.asDexCode());
    }
    if (code.isCfCode()) {
      return getCfLocalVariableTable(code.asCfCode());
    }
    if (code.isJarCode()) {
      return getJarLocalVariableTable(code.asJarCode());
    }
    throw new Unreachable("Unexpected code type: " + code.getClass().getSimpleName());
  }

  private LocalVariableTable getJarLocalVariableTable(JarCode code) {
    throw new Unimplemented("No support for inspecting the line number table for JarCode");
  }

  private LocalVariableTable getCfLocalVariableTable(CfCode code) {
    ImmutableList.Builder<LocalVariableTableEntry> builder = ImmutableList.builder();
    for (LocalVariableInfo localVariable : code.getLocalVariables()) {
      builder.add(
          new LocalVariableTableEntry(
              localVariable.getIndex(),
              localVariable.getLocal().name.toString(),
              new TypeSubject(codeInspector, localVariable.getLocal().type),
              localVariable.getLocal().signature == null
                  ? null
                  : localVariable.getLocal().signature.toString(),
              new CfInstructionSubject(localVariable.getStart()),
              new CfInstructionSubject(localVariable.getEnd())));
    }
    return new LocalVariableTable(builder.build());
  }

  private LocalVariableTable getDexLocalVariableTable(DexCode code) {
    throw new Unimplemented("No support for inspecting the line number table for DexCode");
  }

  @Override
  public String toString() {
    return dexMethod.toSourceString();
  }

  @Override
  public AnnotationSubject annotation(String name) {
    DexAnnotation annotation = codeInspector.findAnnotation(name, dexMethod.annotations);
    return annotation == null
        ? new AbsentAnnotationSubject()
        : new FoundAnnotationSubject(annotation);
  }
}
