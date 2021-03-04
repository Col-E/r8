// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfTypeInstruction;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.synthetic.CallObjectInitCfCodeProvider;
import com.android.tools.r8.synthesis.SyntheticNaming;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Collections;

public class RecordRewriter implements CfInstructionDesugaring, CfClassDesugaring {

  private final AppView<?> appView;
  private final DexItemFactory factory;

  public static RecordRewriter create(AppView<?> appView) {
    return appView.options().shouldDesugarRecords() ? new RecordRewriter(appView) : null;
  }

  private RecordRewriter(AppView<?> appView) {
    this.appView = appView;
    factory = appView.dexItemFactory();
  }

  public void scan(
      ProgramMethod programMethod, CfInstructionDesugaringEventConsumer eventConsumer) {
    CfCode cfCode = programMethod.getDefinition().getCode().asCfCode();
    for (CfInstruction instruction : cfCode.getInstructions()) {
      scanInstruction(instruction, eventConsumer);
    }
  }

  // The record rewriter scans the cf instructions to figure out if the record class needs to
  // be added in the output. the analysis cannot be done in desugarInstruction because the analysis
  // does not rewrite any instruction, and desugarInstruction is expected to rewrite at least one
  // instruction for assertions to be valid.
  private void scanInstruction(
      CfInstruction instruction, CfInstructionDesugaringEventConsumer eventConsumer) {
    assert !instruction.isInitClass();
    if (instruction.isInvoke()) {
      CfInvoke cfInvoke = instruction.asInvoke();
      if (refersToRecord(cfInvoke.getMethod())) {
        requiresRecordClass(eventConsumer);
      }
      return;
    }
    if (instruction.isFieldInstruction()) {
      CfFieldInstruction fieldInstruction = instruction.asFieldInstruction();
      if (refersToRecord(fieldInstruction.getField())) {
        requiresRecordClass(eventConsumer);
      }
      return;
    }
    if (instruction.isTypeInstruction()) {
      CfTypeInstruction typeInstruction = instruction.asTypeInstruction();
      if (refersToRecord(typeInstruction.getType())) {
        requiresRecordClass(eventConsumer);
      }
      return;
    }
    // TODO(b/179146128): Analyse MethodHandle and MethodType.
  }

  @Override
  public Collection<CfInstruction> desugarInstruction(
      CfInstruction instruction,
      FreshLocalProvider freshLocalProvider,
      LocalStackAllocator localStackAllocator,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext) {

    // TODO(b/179146128): This is a temporary work-around to test desugaring of records
    // without rewriting the record invoke-custom. This should be removed when the record support
    // is complete.
    if (instruction.isInvokeDynamic()
        && context.getHolder().superType == factory.recordType
        && (context.getReference().match(factory.recordMembers.toString)
            || context.getReference().match(factory.recordMembers.hashCode)
            || context.getReference().match(factory.recordMembers.equals))) {
      requiresRecordClass(eventConsumer);
      CfInstruction constant =
          context.getReference().match(factory.recordMembers.toString)
              ? new CfConstNull()
              : new CfConstNumber(0, ValueType.INT);
      return ImmutableList.of(new CfStackInstruction(CfStackInstruction.Opcode.Pop), constant);
    }

    CfInstruction desugaredInstruction = desugarInstruction(instruction, context);
    return desugaredInstruction == null ? null : Collections.singletonList(desugaredInstruction);
  }

  private CfInstruction desugarInstruction(CfInstruction instruction, ProgramMethod context) {
    assert !instruction.isInitClass();
    // TODO(b/179146128): Rewrite record invoke-dynamic here.
    if (instruction.isInvoke()) {
      CfInvoke cfInvoke = instruction.asInvoke();
      DexMethod newMethod =
          rewriteMethod(cfInvoke.getMethod(), cfInvoke.isInvokeSuper(context.getHolderType()));
      if (newMethod != cfInvoke.getMethod()) {
        return new CfInvoke(cfInvoke.getOpcode(), newMethod, cfInvoke.isInterface());
      }
    }
    return null;
  }

  @Override
  public boolean needsDesugaring(CfInstruction instruction, ProgramMethod context) {
    assert !instruction.isInitClass();
    // TODO(b/179146128): This is a temporary work-around to test desugaring of records
    // without rewriting the record invoke-custom. This should be removed when the record support
    // is complete.
    if (instruction.isInvokeDynamic()
        && context.getHolder().superType == factory.recordType
        && (context.getName() == factory.toStringMethodName
            || context.getName() == factory.hashCodeMethodName
            || context.getName() == factory.equalsMethodName)) {
      return true;
    }
    if (instruction.isInvoke()) {
      CfInvoke cfInvoke = instruction.asInvoke();
      return needsDesugaring(cfInvoke.getMethod(), cfInvoke.isInvokeSuper(context.getHolderType()));
    }
    return false;
  }

  private void requiresRecordClass(RecordDesugaringEventConsumer eventConsumer) {
    DexProgramClass recordClass = synthesizeR8Record();
    if (recordClass != null) {
      eventConsumer.acceptRecordClass(recordClass);
    }
  }

  @Override
  public boolean needsDesugaring(DexProgramClass clazz) {
    assert clazz.isRecord() || clazz.superType != factory.recordType;
    return clazz.isRecord();
  }

  @Override
  public void desugar(DexProgramClass clazz, CfClassDesugaringEventConsumer eventConsumer) {
    if (clazz.isRecord()) {
      assert clazz.superType == factory.recordType;
      requiresRecordClass(eventConsumer);
      clazz.accessFlags.unsetRecord();
    }
  }

  private boolean refersToRecord(DexField field) {
    assert !refersToRecord(field.holder) : "The java.lang.Record class has no fields.";
    return refersToRecord(field.type);
  }

  private boolean refersToRecord(DexMethod method) {
    if (refersToRecord(method.holder)) {
      return true;
    }
    return refersToRecord(method.proto);
  }

  private boolean refersToRecord(DexProto proto) {
    if (refersToRecord(proto.returnType)) {
      return true;
    }
    return refersToRecord(proto.parameters.values);
  }

  private boolean refersToRecord(DexType[] types) {
    for (DexType type : types) {
      if (refersToRecord(type)) {
        return true;
      }
    }
    return false;
  }

  private boolean refersToRecord(DexType type) {
    return type == factory.recordType;
  }

  private boolean needsDesugaring(DexMethod method, boolean isSuper) {
    return rewriteMethod(method, isSuper) != method;
  }

  @SuppressWarnings("ConstantConditions")
  private DexMethod rewriteMethod(DexMethod method, boolean isSuper) {
    if (method.holder != factory.recordType || method.isInstanceInitializer(factory)) {
      return method;
    }
    assert method == factory.recordMembers.equals
        || method == factory.recordMembers.hashCode
        || method == factory.recordMembers.toString;
    if (isSuper) {
      // TODO(b/179146128): Support rewriting invoke-super to a Record method.
      throw new CompilationError("Rewrite invoke-super to abstract method error.");
    }
    if (method == factory.recordMembers.equals) {
      return factory.objectMembers.equals;
    }
    if (method == factory.recordMembers.toString) {
      return factory.objectMembers.toString;
    }
    assert method == factory.recordMembers.hashCode;
    return factory.objectMembers.toString;
  }

  private DexProgramClass synthesizeR8Record() {
    DexItemFactory factory = appView.dexItemFactory();
    DexClass recordClass =
        appView.appInfo().definitionForWithoutExistenceAssert(factory.recordType);
    if (recordClass != null && recordClass.isProgramClass()) {
      return null;
    }
    assert recordClass == null || recordClass.isLibraryClass();
    DexEncodedMethod init = synthesizeRecordInitMethod();
    // TODO(b/179146128): We may want to remove here the class from the library classes if present
    //  in cf to cf.
    return appView
        .getSyntheticItems()
        .createFixedClassFromType(
            SyntheticNaming.SyntheticKind.RECORD_TAG,
            factory.recordType,
            factory,
            builder -> builder.setAbstract().setDirectMethods(Collections.singletonList(init)));
  }

  private DexEncodedMethod synthesizeRecordInitMethod() {
    MethodAccessFlags methodAccessFlags =
        MethodAccessFlags.fromSharedAccessFlags(
            Constants.ACC_SYNTHETIC | Constants.ACC_PROTECTED, true);
    DexEncodedMethod init =
        new DexEncodedMethod(
            factory.recordMembers.init,
            methodAccessFlags,
            MethodTypeSignature.noSignature(),
            DexAnnotationSet.empty(),
            ParameterAnnotationsList.empty(),
            null,
            true);
    init.setCode(
        new CallObjectInitCfCodeProvider(appView, factory.r8RecordType).generateCfCode(), appView);
    return init;
  }
}
