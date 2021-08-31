// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.records;

import static com.android.tools.r8.cf.code.CfStackInstruction.Opcode.Dup;
import static com.android.tools.r8.cf.code.CfStackInstruction.Opcode.Swap;

import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfInvokeDynamic;
import com.android.tools.r8.cf.code.CfStackInstruction;
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
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaring;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.CfPostProcessingDesugaring;
import com.android.tools.r8.ir.desugar.CfPostProcessingDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.FreshLocalProvider;
import com.android.tools.r8.ir.desugar.LocalStackAllocator;
import com.android.tools.r8.ir.desugar.ProgramAdditions;
import com.android.tools.r8.ir.desugar.records.RecordDesugaringEventConsumer.RecordInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.synthetic.CallObjectInitCfCodeProvider;
import com.android.tools.r8.ir.synthetic.RecordCfCodeProvider.RecordEqualsCfCodeProvider;
import com.android.tools.r8.ir.synthetic.RecordCfCodeProvider.RecordGetFieldsAsObjectsCfCodeProvider;
import com.android.tools.r8.ir.synthetic.SyntheticCfCodeProvider;
import com.android.tools.r8.naming.dexitembasedstring.ClassNameComputationInfo;
import com.android.tools.r8.synthesis.SyntheticNaming;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import org.objectweb.asm.Opcodes;

public class RecordRewriter
    implements CfInstructionDesugaring, CfClassSynthesizerDesugaring, CfPostProcessingDesugaring {

  private final AppView<?> appView;
  private final DexItemFactory factory;
  private final DexProto recordToStringHelperProto;
  private final DexProto recordHashCodeHelperProto;

  public static final String GET_FIELDS_AS_OBJECTS_METHOD_NAME = "$record$getFieldsAsObjects";
  public static final String EQUALS_RECORD_METHOD_NAME = "$record$equals";

  public static RecordRewriter create(AppView<?> appView) {
    return appView.options().shouldDesugarRecords() ? new RecordRewriter(appView) : null;
  }

  public static void registerSynthesizedCodeReferences(DexItemFactory factory) {
    RecordCfMethods.registerSynthesizedCodeReferences(factory);
    RecordGetFieldsAsObjectsCfCodeProvider.registerSynthesizedCodeReferences(factory);
    RecordEqualsCfCodeProvider.registerSynthesizedCodeReferences(factory);
  }

  private RecordRewriter(AppView<?> appView) {
    this.appView = appView;
    factory = appView.dexItemFactory();
    recordToStringHelperProto =
        factory.createProto(
            factory.stringType, factory.objectArrayType, factory.stringType, factory.stringType);
    recordHashCodeHelperProto =
        factory.createProto(factory.intType, factory.classType, factory.objectArrayType);
  }

  @Override
  public void prepare(ProgramMethod method, ProgramAdditions programAdditions) {
    CfCode cfCode = method.getDefinition().getCode().asCfCode();
    for (CfInstruction instruction : cfCode.getInstructions()) {
      if (instruction.isInvokeDynamic() && needsDesugaring(instruction, method)) {
        prepareInvokeDynamicOnRecord(instruction.asInvokeDynamic(), method, programAdditions);
      }
    }
  }

  private void prepareInvokeDynamicOnRecord(
      CfInvokeDynamic invokeDynamic, ProgramMethod context, ProgramAdditions programAdditions) {
    RecordInvokeDynamic recordInvokeDynamic = parseInvokeDynamicOnRecord(invokeDynamic, context);
    if (recordInvokeDynamic.getMethodName() == factory.toStringMethodName
        || recordInvokeDynamic.getMethodName() == factory.hashCodeMethodName) {
      ensureGetFieldsAsObjects(recordInvokeDynamic, programAdditions);
      return;
    }
    if (recordInvokeDynamic.getMethodName() == factory.equalsMethodName) {
      ensureEqualsRecord(recordInvokeDynamic, programAdditions);
      return;
    }
    throw new Unreachable("Invoke dynamic needs record desugaring but could not be desugared.");
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
        ensureRecordClass(eventConsumer);
      }
      return;
    }
    if (instruction.isFieldInstruction()) {
      CfFieldInstruction fieldInstruction = instruction.asFieldInstruction();
      if (refersToRecord(fieldInstruction.getField())) {
        ensureRecordClass(eventConsumer);
      }
      return;
    }
    if (instruction.isTypeInstruction()) {
      CfTypeInstruction typeInstruction = instruction.asTypeInstruction();
      if (refersToRecord(typeInstruction.getType())) {
        ensureRecordClass(eventConsumer);
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
    if (!needsDesugaring(instruction, context)) {
      return null;
    }
    if (instruction.isInvokeDynamic()) {
      return desugarInvokeDynamicOnRecord(
          instruction.asInvokeDynamic(),
          localStackAllocator,
          context,
          eventConsumer,
          methodProcessingContext);
    }
    assert instruction.isInvoke();
    CfInvoke cfInvoke = instruction.asInvoke();
    DexMethod newMethod =
        rewriteMethod(cfInvoke.getMethod(), cfInvoke.isInvokeSuper(context.getHolderType()));
    assert newMethod != cfInvoke.getMethod();
    return Collections.singletonList(
        new CfInvoke(cfInvoke.getOpcode(), newMethod, cfInvoke.isInterface()));
  }

  static class RecordInvokeDynamic {

    private final DexString methodName;
    private final DexString fieldNames;
    private final DexField[] fields;
    private final DexProgramClass recordClass;

    private RecordInvokeDynamic(
        DexString methodName,
        DexString fieldNames,
        DexField[] fields,
        DexProgramClass recordClass) {
      this.methodName = methodName;
      this.fieldNames = fieldNames;
      this.fields = fields;
      this.recordClass = recordClass;
    }

    DexField[] getFields() {
      return fields;
    }

    DexProgramClass getRecordClass() {
      return recordClass;
    }

    DexString getFieldNames() {
      return fieldNames;
    }

    DexString getMethodName() {
      return methodName;
    }
  }

  private RecordInvokeDynamic parseInvokeDynamicOnRecord(
      CfInvokeDynamic invokeDynamic, ProgramMethod context) {
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
    return new RecordInvokeDynamic(callSite.methodName, fieldNames, fields, recordClass);
  }

  private List<CfInstruction> desugarInvokeDynamicOnRecord(
      CfInvokeDynamic invokeDynamic,
      LocalStackAllocator localStackAllocator,
      ProgramMethod context,
      CfInstructionDesugaringEventConsumer eventConsumer,
      MethodProcessingContext methodProcessingContext) {
    RecordInvokeDynamic recordInvokeDynamic = parseInvokeDynamicOnRecord(invokeDynamic, context);
    if (recordInvokeDynamic.getMethodName() == factory.toStringMethodName) {
      return desugarInvokeRecordToString(
          recordInvokeDynamic,
          localStackAllocator,
          context,
          eventConsumer,
          methodProcessingContext);
    }
    if (recordInvokeDynamic.getMethodName() == factory.hashCodeMethodName) {
      return desugarInvokeRecordHashCode(
          recordInvokeDynamic, localStackAllocator, eventConsumer, methodProcessingContext);
    }
    if (recordInvokeDynamic.getMethodName() == factory.equalsMethodName) {
      return desugarInvokeRecordEquals(recordInvokeDynamic);
    }
    throw new Unreachable("Invoke dynamic needs record desugaring but could not be desugared.");
  }

  private ProgramMethod synthesizeEqualsRecordMethod(
      DexProgramClass clazz, DexMethod getFieldsAsObjects, DexMethod method) {
    return synthesizeMethod(
        clazz, new RecordEqualsCfCodeProvider(appView, clazz.type, getFieldsAsObjects), method);
  }

  private ProgramMethod synthesizeGetFieldsAsObjectsMethod(
      DexProgramClass clazz, DexField[] fields, DexMethod method) {
    return synthesizeMethod(
        clazz,
        new RecordGetFieldsAsObjectsCfCodeProvider(appView, factory.recordTagType, fields),
        method);
  }

  private ProgramMethod synthesizeMethod(
      DexProgramClass clazz, SyntheticCfCodeProvider provider, DexMethod method) {
    MethodAccessFlags methodAccessFlags =
        MethodAccessFlags.fromSharedAccessFlags(
            Constants.ACC_SYNTHETIC | Constants.ACC_PRIVATE, false);
    DexEncodedMethod encodedMethod =
        new DexEncodedMethod(
            method,
            methodAccessFlags,
            MethodTypeSignature.noSignature(),
            DexAnnotationSet.empty(),
            ParameterAnnotationsList.empty(),
            null,
            true);
    encodedMethod.setCode(provider.generateCfCode(), appView);
    return new ProgramMethod(clazz, encodedMethod);
  }

  private DexMethod ensureEqualsRecord(
      RecordInvokeDynamic recordInvokeDynamic, ProgramAdditions programAdditions) {
    DexMethod getFieldsAsObjects = ensureGetFieldsAsObjects(recordInvokeDynamic, programAdditions);
    DexProgramClass clazz = recordInvokeDynamic.getRecordClass();
    DexMethod method = equalsRecordMethod(clazz.type);
    assert clazz.lookupProgramMethod(method) == null;
    programAdditions.accept(
        method, () -> synthesizeEqualsRecordMethod(clazz, getFieldsAsObjects, method));
    return method;
  }

  private DexMethod ensureGetFieldsAsObjects(
      RecordInvokeDynamic recordInvokeDynamic, ProgramAdditions programAdditions) {
    DexProgramClass clazz = recordInvokeDynamic.getRecordClass();
    DexMethod method = getFieldsAsObjectsMethod(clazz.type);
    assert clazz.lookupProgramMethod(method) == null;
    programAdditions.accept(
        method,
        () -> synthesizeGetFieldsAsObjectsMethod(clazz, recordInvokeDynamic.getFields(), method));
    return method;
  }

  private DexMethod getFieldsAsObjectsMethod(DexType holder) {
    return factory.createMethod(
        holder, factory.createProto(factory.objectArrayType), GET_FIELDS_AS_OBJECTS_METHOD_NAME);
  }

  private DexMethod equalsRecordMethod(DexType holder) {
    return factory.createMethod(
        holder,
        factory.createProto(factory.booleanType, factory.objectType),
        EQUALS_RECORD_METHOD_NAME);
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
      RecordInvokeDynamic recordInvokeDynamic,
      LocalStackAllocator localStackAllocator,
      RecordInstructionDesugaringEventConsumer eventConsumer,
      MethodProcessingContext methodProcessingContext) {
    localStackAllocator.allocateLocalStack(1);
    DexMethod getFieldsAsObjects =
        getFieldsAsObjectsMethod(recordInvokeDynamic.getRecordClass().type);
    assert recordInvokeDynamic.getRecordClass().lookupProgramMethod(getFieldsAsObjects) != null;
    ArrayList<CfInstruction> instructions = new ArrayList<>();
    instructions.add(new CfStackInstruction(Dup));
    instructions.add(new CfInvoke(Opcodes.INVOKEVIRTUAL, factory.objectMembers.getClass, false));
    instructions.add(new CfStackInstruction(Swap));
    instructions.add(new CfInvoke(Opcodes.INVOKESPECIAL, getFieldsAsObjects, false));
    ProgramMethod programMethod =
        synthesizeRecordHelper(
            recordHashCodeHelperProto,
            RecordCfMethods::RecordMethods_hashCode,
            methodProcessingContext);
    eventConsumer.acceptRecordMethod(programMethod);
    instructions.add(new CfInvoke(Opcodes.INVOKESTATIC, programMethod.getReference(), false));
    return instructions;
  }

  private List<CfInstruction> desugarInvokeRecordEquals(RecordInvokeDynamic recordInvokeDynamic) {
    DexMethod equalsRecord = equalsRecordMethod(recordInvokeDynamic.getRecordClass().type);
    assert recordInvokeDynamic.getRecordClass().lookupProgramMethod(equalsRecord) != null;
    return Collections.singletonList(new CfInvoke(Opcodes.INVOKESPECIAL, equalsRecord, false));
  }

  private List<CfInstruction> desugarInvokeRecordToString(
      RecordInvokeDynamic recordInvokeDynamic,
      LocalStackAllocator localStackAllocator,
      ProgramMethod context,
      RecordInstructionDesugaringEventConsumer eventConsumer,
      MethodProcessingContext methodProcessingContext) {
    DexString simpleName =
        ClassNameComputationInfo.ClassNameMapping.SIMPLE_NAME.map(
            recordInvokeDynamic.getRecordClass().type.toDescriptorString(),
            context.getHolder(),
            factory);
    localStackAllocator.allocateLocalStack(2);
    DexMethod getFieldsAsObjects =
        getFieldsAsObjectsMethod(recordInvokeDynamic.getRecordClass().type);
    assert recordInvokeDynamic.getRecordClass().lookupProgramMethod(getFieldsAsObjects) != null;
    ArrayList<CfInstruction> instructions = new ArrayList<>();
    instructions.add(new CfInvoke(Opcodes.INVOKESPECIAL, getFieldsAsObjects, false));
    instructions.add(new CfConstString(simpleName));
    instructions.add(new CfConstString(recordInvokeDynamic.getFieldNames()));
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
    if (instruction.isInvokeDynamic()) {
      return needsDesugaring(instruction.asInvokeDynamic(), context);
    }
    if (instruction.isInvoke()) {
      CfInvoke cfInvoke = instruction.asInvoke();
      return needsDesugaring(cfInvoke.getMethod(), cfInvoke.isInvokeSuper(context.getHolderType()));
    }
    return false;
  }

  private void ensureRecordClass(RecordDesugaringEventConsumer eventConsumer) {
    DexItemFactory factory = appView.dexItemFactory();
    checkRecordTagNotPresent(factory);
    appView
        .getSyntheticItems()
        .ensureFixedClassFromType(
            SyntheticNaming.SyntheticKind.RECORD_TAG,
            factory.recordType,
            appView,
            builder -> {
              DexEncodedMethod init = synthesizeRecordInitMethod();
              builder
                  .setAbstract()
                  .setDirectMethods(ImmutableList.of(init));
            },
            eventConsumer::acceptRecordClass);
  }

  private void checkRecordTagNotPresent(DexItemFactory factory) {
    DexClass r8RecordClass =
        appView.appInfo().definitionForWithoutExistenceAssert(factory.recordTagType);
    if (r8RecordClass != null && r8RecordClass.isProgramClass()) {
      appView
          .options()
          .reporter
          .error(
              "D8/R8 is compiling a mix of desugared and non desugared input using"
                  + " java.lang.Record, but the application reader did not import correctly "
                  + factory.recordTagType);
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

  @Override
  public void synthesizeClasses(CfClassSynthesizerDesugaringEventConsumer eventConsumer) {
    if (appView.appInfo().app().getFlags().hasReadProgramRecord()) {
      ensureRecordClass(eventConsumer);
    }
  }

  @Override
  public void postProcessingDesugaring(
      Collection<DexProgramClass> programClasses,
      CfPostProcessingDesugaringEventConsumer eventConsumer,
      ExecutorService executorService)
      throws ExecutionException {
    for (DexProgramClass clazz : programClasses) {
      if (clazz.isRecord()) {
        assert clazz.superType == factory.recordType;
        clazz.accessFlags.unsetRecord();
      }
    }
  }
}
