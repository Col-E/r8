// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import static com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringForTesting.getDefaultMethodPrefix;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.dex.code.DexInstruction;
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
import com.android.tools.r8.graph.DexDebugInfo.EventBasedDebugInfo;
import com.android.tools.r8.graph.DexDebugPositionState;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.naming.MemberNaming;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.signature.GenericSignatureParser;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.LocalVariableTable.LocalVariableTableEntry;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
  public IRCode buildIR() {
    return buildIR(
        AppView.createForD8(
            AppInfo.createInitialAppInfo(
                codeInspector.application, GlobalSyntheticsStrategy.forNonSynthesizing())));
  }

  @Override
  public IRCode buildIR(AppView<?> appView) {
    assert codeInspector.application.options.programConsumer != null;
    return getProgramMethod().buildIR(appView, MethodConversionOptions.forD8(appView));
  }

  @Override
  public boolean isPresent() {
    return true;
  }

  @Override
  public boolean isRenamed() {
    return clazz.getNaming() != null
        && !getFinalSignature().name.equals(getOriginalSignature().name);
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
  public boolean isSynchronized() {
    return dexMethod.accessFlags.isSynchronized();
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
    return dexMethod.isNonPrivateVirtualMethod();
  }

  @Override
  public boolean isNative() {
    return dexMethod.getAccessFlags().isNative();
  }

  @Override
  public MethodAccessFlags getAccessFlags() {
    return dexMethod.getAccessFlags();
  }

  @Override
  public DexEncodedMethod getMethod() {
    return dexMethod;
  }

  @Override
  public MethodReference getFinalReference() {
    return dexMethod.getReference().asMethodReference();
  }

  @Override
  public TypeSubject getParameter(int index) {
    return new TypeSubject(codeInspector, getMethod().getParameter(index));
  }

  @Override
  public List<TypeSubject> getParameters() {
    return getMethod().getParameters().stream()
        .map(parameter -> new TypeSubject(codeInspector, parameter))
        .collect(Collectors.toList());
  }

  @Override
  public List<FoundAnnotationSubject> getParameterAnnotations(int index) {
    return FoundAnnotationSubject.listFromDex(
        getMethod().getParameterAnnotation(index), codeInspector);
  }

  @Override
  public List<List<FoundAnnotationSubject>> getParameterAnnotations() {
    List<List<FoundAnnotationSubject>> parameterAnnotations =
        new ArrayList<>(getMethod().getParameters().size());
    for (int parameterIndex = 0;
        parameterIndex < getMethod().getParameters().size();
        parameterIndex++) {
      parameterAnnotations.add(getParameterAnnotations(parameterIndex));
    }
    return parameterAnnotations;
  }

  @Override
  public ProgramMethod getProgramMethod() {
    return new ProgramMethod(clazz.getDexProgramClass(), getMethod());
  }

  @Override
  public MethodSignature getOriginalSignature() {
    MethodSignature signature = getFinalSignature();
    if (clazz.getNaming() == null) {
      return signature;
    }

    if (clazz.hasResidualSignatureMapping()) {
      MemberNaming memberNaming = clazz.getNaming().lookup(signature);
      if (memberNaming != null) {
        return memberNaming.getOriginalSignature().asMethodSignature();
      }
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

    MemberNaming memberNaming = clazz.getNaming().lookup(lookupSignature);
    return memberNaming != null
        ? memberNaming.getOriginalSignature().asMethodSignature()
        : signature;
  }

  @Override
  public MethodSignature getFinalSignature() {
    return MethodSignature.fromDexMethod(dexMethod.getReference());
  }

  @Override
  public String getOriginalSignatureAttribute() {
    return codeInspector.getOriginalSignatureAttribute(
        getFinalSignatureAttribute(), GenericSignatureParser::parseMethodSignature);
  }

  public DexMethod getOriginalDexMethod(DexItemFactory dexItemFactory) {
    MethodSignature methodSignature = getOriginalSignature();
    if (methodSignature.isQualified()) {
      methodSignature = methodSignature.toUnqualified();
    }
    return methodSignature.toDexMethod(
        dexItemFactory, dexItemFactory.createType(clazz.getOriginalDescriptor()));
  }

  @Override
  public String getFinalSignatureAttribute() {
    return dexMethod.getGenericSignature().toString();
  }

  public Iterable<InstructionSubject> instructions() {
    return instructions(Predicates.alwaysTrue());
  }

  public Iterable<InstructionSubject> instructions(Predicate<InstructionSubject> predicate) {
    return () -> iterateInstructions(predicate);
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
    if (code == null) {
      return false;
    }
    if (code.isDexCode()) {
      DexCode dexCode = code.asDexCode();
      if (dexCode.getDebugInfo() == null) {
        return false;
      }
      EventBasedDebugInfo eventBasedInfo = dexCode.getDebugInfo().asEventBasedInfo();
      if (eventBasedInfo == null) {
        return false;
      }
      for (DexString parameter : eventBasedInfo.parameters) {
        if (parameter != null) {
          return true;
        }
      }
      for (DexDebugEvent event : eventBasedInfo.events) {
        if (event instanceof DexDebugEvent.StartLocal) {
          return true;
        }
      }
      return false;
    }
    if (code.isCfCode()) {
      return !code.asCfCode().getLocalVariables().isEmpty();
    }
    throw new Unreachable("Unexpected code type: " + code.getClass().getSimpleName());
  }

  @Override
  public LineNumberTable getLineNumberTable() {
    Code code = getMethod().getCode();
    if (code == null) {
      return null;
    }
    if (code.isDexCode()) {
      return getDexLineNumberTable(code.asDexCode());
    }
    if (code.isCfCode()) {
      return getCfLineNumberTable(code.asCfCode());
    }
    throw new Unreachable("Unexpected code type: " + code.getClass().getSimpleName());
  }

  private LineNumberTable getCfLineNumberTable(CfCode code) {
    int currentLine = -1;
    Object2IntMap<InstructionSubject> lineNumberTable =
        new Object2IntOpenHashMap<>(code.getInstructions().size());
    for (CfInstruction insn : code.getInstructions()) {
      if (insn instanceof CfPosition) {
        currentLine = ((CfPosition) insn).getPosition().getLine();
      }
      if (currentLine != -1) {
        lineNumberTable.put(new CfInstructionSubject(insn, this), currentLine);
      }
    }
    return currentLine == -1 ? null : new LineNumberTable(lineNumberTable);
  }

  private LineNumberTable getDexLineNumberTable(DexCode code) {
    EventBasedDebugInfo info = DexDebugInfo.convertToEventBased(code, codeInspector.getFactory());
    if (info == null) {
      return null;
    }
    Object2IntMap<InstructionSubject> lineNumberTable = new Object2IntOpenHashMap<>();
    DexDebugPositionState state =
        new DexDebugPositionState(info.startLine, getMethod().getReference());
    Iterator<DexDebugEvent> iterator = Arrays.asList(info.events).iterator();
    for (DexInstruction insn : code.instructions) {
      int offset = insn.getOffset();
      while (state.getCurrentPc() < offset && iterator.hasNext()) {
        iterator.next().accept(state);
      }
      lineNumberTable.put(new DexInstructionSubject(insn, this), state.getCurrentLine());
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
    throw new Unreachable("Unexpected code type: " + code.getClass().getSimpleName());
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
              new CfInstructionSubject(localVariable.getStart(), this),
              new CfInstructionSubject(localVariable.getEnd(), this)));
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
  public List<FoundAnnotationSubject> annotations() {
    return FoundAnnotationSubject.listFromDex(dexMethod.annotations(), codeInspector);
  }

  @Override
  public AnnotationSubject annotation(String name) {
    DexAnnotation annotation = codeInspector.findAnnotation(dexMethod.annotations(), name);
    return annotation == null
        ? new AbsentAnnotationSubject()
        : new FoundAnnotationSubject(annotation, codeInspector);
  }

  @Override
  public FoundMethodSubject asFoundMethodSubject() {
    return this;
  }

  public MethodReference asMethodReference() {
    DexMethod method = dexMethod.getReference();
    return Reference.method(
        Reference.classFromDescriptor(method.holder.toDescriptorString()),
        method.name.toString(),
        Arrays.stream(method.proto.parameters.values)
            .map(type -> Reference.typeFromDescriptor(type.toDescriptorString()))
            .collect(Collectors.toList()),
        Reference.returnTypeFromDescriptor(method.proto.returnType.toDescriptorString()));
  }

  @Override
  public String getJvmMethodSignatureAsString() {
    return dexMethod.getName().toString()
        + "("
        + StringUtils.join(
            "",
            Arrays.stream(dexMethod.getParameters().values)
                .map(DexType::toDescriptorString)
                .collect(Collectors.toList()))
        + ")"
        + dexMethod.returnType().toDescriptorString();
  }

  @Override
  public MethodSubject toMethodOnCompanionClass() {
    ClassSubject companionClass = clazz.toCompanionClass();
    MethodReference reference = asMethodReference();
    List<String> p =
        ImmutableList.<String>builder()
            .add(clazz.getFinalName())
            .addAll(reference.getFormalTypes().stream().map(TypeReference::getTypeName).iterator())
            .build();
    return companionClass.method(
        reference.getReturnType().getTypeName(),
        getDefaultMethodPrefix() + reference.getMethodName(),
        p);
  }
}
