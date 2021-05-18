// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfInvokeDynamic;
import com.android.tools.r8.cf.code.CfTypeInstruction;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue.DexValueMethodHandle;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.graph.DexValue.DexValueType;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.synthetic.CallObjectInitCfCodeProvider;
import com.android.tools.r8.ir.synthetic.RecordGetFieldsAsObjectsCfCodeProvider;
import com.android.tools.r8.naming.dexitembasedstring.ClassNameComputationInfo;
import com.android.tools.r8.synthesis.SyntheticNaming;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import org.objectweb.asm.Opcodes;

public class RecordRewriter implements CfInstructionDesugaring, CfClassDesugaring {

  private final AppView<?> appView;
  private final DexItemFactory factory;
  private final DexProto recordToStringHelperProto;
  private final DexProto recordEqualsHelperProto;
  private final DexProto recordHashCodeHelperProto;

  public static final String GET_FIELDS_AS_OBJECTS_METHOD_NAME = "$record$getFieldsAsObjects";

  public static RecordRewriter create(AppView<?> appView) {
    return appView.options().shouldDesugarRecords() ? new RecordRewriter(appView) : null;
  }

  public static void registerSynthesizedCodeReferences(DexItemFactory factory) {
    RecordCfMethods.registerSynthesizedCodeReferences(factory);
    RecordGetFieldsAsObjectsCfCodeProvider.registerSynthesizedCodeReferences(factory);
  }

  private RecordRewriter(AppView<?> appView) {
    this.appView = appView;
    factory = appView.dexItemFactory();
    recordToStringHelperProto =
        factory.createProto(
            factory.stringType, factory.recordType, factory.stringType, factory.stringType);
    recordEqualsHelperProto =
        factory.createProto(factory.booleanType, factory.recordType, factory.objectType);
    recordHashCodeHelperProto = factory.createProto(factory.intType, factory.recordType);
  }

  @Override
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
      MethodProcessingContext methodProcessingContext,
      DexItemFactory dexItemFactory) {
    assert !instruction.isInitClass();
    if (instruction.isInvokeDynamic() && needsDesugaring(instruction.asInvokeDynamic(), context)) {
      return desugarInvokeDynamicOnRecord(
          instruction.asInvokeDynamic(),
          localStackAllocator,
          context,
          eventConsumer,
          methodProcessingContext);
    }
    if (instruction.isInvoke()) {
      CfInvoke cfInvoke = instruction.asInvoke();
      DexMethod newMethod =
          rewriteMethod(cfInvoke.getMethod(), cfInvoke.isInvokeSuper(context.getHolderType()));
      if (newMethod != cfInvoke.getMethod()) {
        return Collections.singletonList(
            new CfInvoke(cfInvoke.getOpcode(), newMethod, cfInvoke.isInterface()));
      }
    }
    return null;
  }

  public List<CfInstruction> desugarInvokeDynamicOnRecord(
      CfInvokeDynamic invokeDynamic,
      LocalStackAllocator localStackAllocator,
      ProgramMethod context,
      CfInstructionDesugaringEventConsumer eventConsumer,
      MethodProcessingContext methodProcessingContext) {
    assert needsDesugaring(invokeDynamic, context);
    DexCallSite callSite = invokeDynamic.getCallSite();
    DexValueType recordValueType = callSite.bootstrapArgs.get(0).asDexValueType();
    DexValueString valueString = callSite.bootstrapArgs.get(1).asDexValueString();
    DexString fieldNames = valueString.getValue();
    DexField[] fields = new DexField[callSite.bootstrapArgs.size() - 2];
    for (int i = 2; i < callSite.bootstrapArgs.size(); i++) {
      DexValueMethodHandle handle = callSite.bootstrapArgs.get(i).asDexValueMethodHandle();
      fields[i - 2] = handle.value.member.asDexField();
    }
    DexProgramClass recordClass =
        appView.definitionFor(recordValueType.getValue()).asProgramClass();
    if (callSite.methodName == factory.toStringMethodName) {
      DexString simpleName =
          ClassNameComputationInfo.ClassNameMapping.SIMPLE_NAME.map(
              recordValueType.getValue().toDescriptorString(), context.getHolder(), factory);
      return desugarInvokeRecordToString(
          recordClass,
          fieldNames,
          fields,
          simpleName,
          localStackAllocator,
          eventConsumer,
          methodProcessingContext);
    }
    if (callSite.methodName == factory.hashCodeMethodName) {
      return desugarInvokeRecordHashCode(
          recordClass, fields, eventConsumer, methodProcessingContext);
    }
    if (callSite.methodName == factory.equalsMethodName) {
      return desugarInvokeRecordEquals(recordClass, fields, eventConsumer, methodProcessingContext);
    }
    throw new Unreachable("Invoke dynamic needs record desugaring but could not be desugared.");
  }

  private ProgramMethod synthesizeGetFieldsAsObjectsMethod(
      DexProgramClass clazz, DexField[] fields, DexMethod method) {
    MethodAccessFlags methodAccessFlags =
        MethodAccessFlags.fromSharedAccessFlags(
            Constants.ACC_SYNTHETIC | Constants.ACC_PUBLIC, false);
    DexEncodedMethod encodedMethod =
        new DexEncodedMethod(
            method,
            methodAccessFlags,
            MethodTypeSignature.noSignature(),
            DexAnnotationSet.empty(),
            ParameterAnnotationsList.empty(),
            null,
            true);
    encodedMethod.setCode(
        new RecordGetFieldsAsObjectsCfCodeProvider(appView, factory.recordTagType, fields)
            .generateCfCode(),
        appView);
    return new ProgramMethod(clazz, encodedMethod);
  }

  private void ensureGetFieldsAsObjects(
      DexProgramClass clazz, DexField[] fields, RecordDesugaringEventConsumer eventConsumer) {
    DexMethod method = getFieldsAsObjectsMethod(clazz.type);
    synchronized (clazz.getMethodCollection()) {
      ProgramMethod getFieldsAsObjects = clazz.lookupProgramMethod(method);
      if (getFieldsAsObjects == null) {
        getFieldsAsObjects = synthesizeGetFieldsAsObjectsMethod(clazz, fields, method);
        clazz.addVirtualMethod(getFieldsAsObjects.getDefinition());
        if (eventConsumer != null) {
          eventConsumer.acceptRecordMethod(getFieldsAsObjects);
        }
      }
    }
  }

  private DexMethod getFieldsAsObjectsMethod(DexType holder) {
    return factory.createMethod(
        holder, factory.createProto(factory.objectArrayType), GET_FIELDS_AS_OBJECTS_METHOD_NAME);
  }

  private ProgramMethod synthesizeRecordHelper(
      DexProto helperProto,
      BiFunction<InternalOptions, DexMethod, CfCode> codeGenerator,
      MethodProcessingContext methodProcessingContext) {
    return appView
        .getSyntheticItems()
        .createMethod(
            SyntheticNaming.SyntheticKind.RECORD_HELPER,
            methodProcessingContext.createUniqueContext(),
            appView,
            builder ->
                builder
                    .setProto(helperProto)
                    .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                    .setCode(methodSig -> codeGenerator.apply(appView.options(), methodSig)));
  }

  private List<CfInstruction> desugarInvokeRecordHashCode(
      DexProgramClass recordClass,
      DexField[] fields,
      CfInstructionDesugaringEventConsumer eventConsumer,
      MethodProcessingContext methodProcessingContext) {
    ensureGetFieldsAsObjects(recordClass, fields, eventConsumer);
    ProgramMethod programMethod =
        synthesizeRecordHelper(
            recordHashCodeHelperProto,
            RecordCfMethods::RecordMethods_hashCode,
            methodProcessingContext);
    eventConsumer.acceptRecordMethod(programMethod);
    return ImmutableList.of(
        new CfInvoke(Opcodes.INVOKESTATIC, programMethod.getReference(), false));
  }

  private List<CfInstruction> desugarInvokeRecordEquals(
      DexProgramClass recordClass,
      DexField[] fields,
      CfInstructionDesugaringEventConsumer eventConsumer,
      MethodProcessingContext methodProcessingContext) {
    ensureGetFieldsAsObjects(recordClass, fields, eventConsumer);
    ProgramMethod programMethod =
        synthesizeRecordHelper(
            recordEqualsHelperProto,
            RecordCfMethods::RecordMethods_equals,
            methodProcessingContext);
    eventConsumer.acceptRecordMethod(programMethod);
    return ImmutableList.of(
        new CfInvoke(Opcodes.INVOKESTATIC, programMethod.getReference(), false));
  }

  private List<CfInstruction> desugarInvokeRecordToString(
      DexProgramClass recordClass,
      DexString fieldNames,
      DexField[] fields,
      DexString simpleName,
      LocalStackAllocator localStackAllocator,
      CfInstructionDesugaringEventConsumer eventConsumer,
      MethodProcessingContext methodProcessingContext) {
    ensureGetFieldsAsObjects(recordClass, fields, eventConsumer);
    ArrayList<CfInstruction> instructions = new ArrayList<>();
    instructions.add(new CfConstString(simpleName));
    instructions.add(new CfConstString(fieldNames));
    localStackAllocator.allocateLocalStack(2);
    ProgramMethod programMethod =
        synthesizeRecordHelper(
            recordToStringHelperProto,
            RecordCfMethods::RecordMethods_toString,
            methodProcessingContext);
    eventConsumer.acceptRecordMethod(programMethod);
    instructions.add(new CfInvoke(Opcodes.INVOKESTATIC, programMethod.getReference(), false));
    return instructions;
  }

  @Override
  public boolean needsDesugaring(CfInstruction instruction, ProgramMethod context) {
    assert !instruction.isInitClass();
    if (instruction.isInvokeDynamic()) {
      return needsDesugaring(instruction.asInvokeDynamic(), context);
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

  private boolean needsDesugaring(CfInvokeDynamic invokeDynamic, ProgramMethod context) {
    DexCallSite callSite = invokeDynamic.getCallSite();
    // 1. Validates this is an invoke-static to ObjectMethods#bootstrap.
    DexMethodHandle bootstrapMethod = callSite.bootstrapMethod;
    if (!bootstrapMethod.type.isInvokeStatic()) {
      return false;
    }
    if (bootstrapMethod.member != factory.objectMethodsMembers.bootstrap) {
      return false;
    }
    // From there on we assume in the assertions that the invoke to the library method is
    // well-formed. If the invoke is not well formed assertions will fail but the execution is
    // correct.
    if (bootstrapMethod.isInterface) {
      assert false
          : "Invoke-dynamic invoking non interface method ObjectMethods#bootstrap as an interface"
              + " method.";
      return false;
    }
    // 2. Validate the bootstrapArgs include the record type, the instance field names and
    // the corresponding instance getters.
    if (callSite.bootstrapArgs.size() < 2) {
      assert false
          : "Invoke-dynamic invoking method ObjectMethods#bootstrap with less than 2 parameters.";
      return false;
    }
    DexValueType recordType = callSite.bootstrapArgs.get(0).asDexValueType();
    if (recordType == null) {
      assert false : "Invoke-dynamic invoking method ObjectMethods#bootstrap with an invalid type.";
      return false;
    }
    DexClass recordClass = appView.definitionFor(recordType.getValue());
    if (recordClass == null || recordClass.isNotProgramClass()) {
      return false;
    }
    DexValueString valueString = callSite.bootstrapArgs.get(1).asDexValueString();
    if (valueString == null) {
      assert false
          : "Invoke-dynamic invoking method ObjectMethods#bootstrap with invalid field names.";
      return false;
    }
    DexString fieldNames = valueString.getValue();
    assert fieldNames.toString().isEmpty()
        || (fieldNames.toString().split(";").length == callSite.bootstrapArgs.size() - 2);
    assert recordClass.instanceFields().size() == callSite.bootstrapArgs.size() - 2;
    for (int i = 2; i < callSite.bootstrapArgs.size(); i++) {
      DexValueMethodHandle handle = callSite.bootstrapArgs.get(i).asDexValueMethodHandle();
      if (handle == null
          || !handle.value.type.isInstanceGet()
          || !handle.value.member.isDexField()) {
        assert false
            : "Invoke-dynamic invoking method ObjectMethods#bootstrap with invalid getters.";
        return false;
      }
    }
    // 3. Create the invoke-record instruction.
    if (callSite.methodName == factory.toStringMethodName) {
      assert callSite.methodProto == factory.createProto(factory.stringType, recordClass.getType());
      return true;
    }
    if (callSite.methodName == factory.hashCodeMethodName) {
      assert callSite.methodProto == factory.createProto(factory.intType, recordClass.getType());
      return true;
    }
    if (callSite.methodName == factory.equalsMethodName) {
      assert callSite.methodProto
          == factory.createProto(factory.booleanType, recordClass.getType(), factory.objectType);
      return true;
    }
    return false;
  }

  @SuppressWarnings("ConstantConditions")
  private DexMethod rewriteMethod(DexMethod method, boolean isSuper) {
    if (!(method == factory.recordMembers.equals
        || method == factory.recordMembers.hashCode
        || method == factory.recordMembers.toString)) {
      return method;
    }
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
    DexClass r8RecordClass =
        appView.appInfo().definitionForWithoutExistenceAssert(factory.recordTagType);
    if (r8RecordClass != null && r8RecordClass.isProgramClass()) {
      appView
          .options()
          .reporter
          .error(
              "D8/R8 is compiling a mix of desugared and non desugared input using"
                  + " java.lang.Record, but the application reader did not import correctly "
                  + factory.recordTagType.toString());
    }
    DexClass recordClass =
        appView.appInfo().definitionForWithoutExistenceAssert(factory.recordType);
    if (recordClass != null && recordClass.isProgramClass()) {
      return null;
    }
    return synchronizedSynthesizeR8Record();
  }

  private synchronized DexProgramClass synchronizedSynthesizeR8Record() {
    DexItemFactory factory = appView.dexItemFactory();
    DexClass recordClass =
        appView.appInfo().definitionForWithoutExistenceAssert(factory.recordType);
    if (recordClass != null && recordClass.isProgramClass()) {
      return null;
    }
    DexEncodedMethod init = synthesizeRecordInitMethod();
    DexEncodedMethod abstractGetFieldsAsObjectsMethod =
        synthesizeAbstractGetFieldsAsObjectsMethod();
    return appView
        .getSyntheticItems()
        .createFixedClassFromType(
            SyntheticNaming.SyntheticKind.RECORD_TAG,
            factory.recordType,
            factory,
            builder ->
                builder
                    .setAbstract()
                    .setVirtualMethods(ImmutableList.of(abstractGetFieldsAsObjectsMethod))
                    .setDirectMethods(ImmutableList.of(init)));
  }

  private DexEncodedMethod synthesizeAbstractGetFieldsAsObjectsMethod() {
    MethodAccessFlags methodAccessFlags =
        MethodAccessFlags.fromSharedAccessFlags(
            Constants.ACC_SYNTHETIC | Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT, false);
    DexMethod fieldsAsObjectsMethod = getFieldsAsObjectsMethod(factory.recordType);
    return new DexEncodedMethod(
        fieldsAsObjectsMethod,
        methodAccessFlags,
        MethodTypeSignature.noSignature(),
        DexAnnotationSet.empty(),
        ParameterAnnotationsList.empty(),
        null,
        true);
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
        new CallObjectInitCfCodeProvider(appView, factory.recordTagType).generateCfCode(), appView);
    return init;
  }
}
