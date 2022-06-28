// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.records;

import static com.android.tools.r8.cf.code.CfStackInstruction.Opcode.Dup;
import static com.android.tools.r8.cf.code.CfStackInstruction.Opcode.Swap;
import static com.android.tools.r8.ir.desugar.records.RecordRewriterHelper.isInvokeDynamicOnRecord;
import static com.android.tools.r8.ir.desugar.records.RecordRewriterHelper.parseInvokeDynamicOnRecord;

import com.android.tools.r8.cf.code.CfConstClass;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfDexItemBasedConstString;
import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfInvokeDynamic;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfTypeInstruction;
import com.android.tools.r8.contexts.CompilationContext.ClassSynthesisDesugaringContext;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.MissingGlobalSyntheticsConsumerDiagnostic;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexApplicationReadFlags;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaring;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringCollection;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.CfPostProcessingDesugaring;
import com.android.tools.r8.ir.desugar.CfPostProcessingDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.FreshLocalProvider;
import com.android.tools.r8.ir.desugar.LocalStackAllocator;
import com.android.tools.r8.ir.desugar.ProgramAdditions;
import com.android.tools.r8.ir.desugar.records.RecordDesugaringEventConsumer.RecordInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.records.RecordRewriterHelper.RecordInvokeDynamic;
import com.android.tools.r8.ir.synthetic.CallObjectInitCfCodeProvider;
import com.android.tools.r8.ir.synthetic.RecordCfCodeProvider.RecordEqualsCfCodeProvider;
import com.android.tools.r8.ir.synthetic.RecordCfCodeProvider.RecordGetFieldsAsObjectsCfCodeProvider;
import com.android.tools.r8.ir.synthetic.SyntheticCfCodeProvider;
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

public class RecordDesugaring
    implements CfInstructionDesugaring, CfClassSynthesizerDesugaring, CfPostProcessingDesugaring {

  private final AppView<?> appView;
  private final DexItemFactory factory;
  private final DexProto recordToStringHelperProto;
  private final DexProto recordHashCodeHelperProto;

  public static final String GET_FIELDS_AS_OBJECTS_METHOD_NAME = "$record$getFieldsAsObjects";
  public static final String EQUALS_RECORD_METHOD_NAME = "$record$equals";

  public static RecordDesugaring create(AppView<?> appView) {
    return appView.options().shouldDesugarRecords() ? new RecordDesugaring(appView) : null;
  }

  public static void registerSynthesizedCodeReferences(DexItemFactory factory) {
    RecordCfMethods.registerSynthesizedCodeReferences(factory);
    RecordGetFieldsAsObjectsCfCodeProvider.registerSynthesizedCodeReferences(factory);
    RecordEqualsCfCodeProvider.registerSynthesizedCodeReferences(factory);
  }

  private RecordDesugaring(AppView<?> appView) {
    this.appView = appView;
    factory = appView.dexItemFactory();
    recordToStringHelperProto =
        factory.createProto(
            factory.stringType, factory.objectArrayType, factory.classType, factory.stringType);
    recordHashCodeHelperProto =
        factory.createProto(factory.intType, factory.classType, factory.objectArrayType);
  }

  @Override
  public void prepare(ProgramMethod method, ProgramAdditions programAdditions) {
    CfCode cfCode = method.getDefinition().getCode().asCfCode();
    for (CfInstruction instruction : cfCode.getInstructions()) {
      if (instruction.isInvokeDynamic() && needsDesugaring(instruction, method)) {
        prepareInvokeDynamicOnRecord(instruction.asInvokeDynamic(), programAdditions, method);
      }
    }
  }

  private void prepareInvokeDynamicOnRecord(
      CfInvokeDynamic invokeDynamic, ProgramAdditions programAdditions, ProgramMethod context) {
    RecordInvokeDynamic recordInvokeDynamic =
        parseInvokeDynamicOnRecord(invokeDynamic, appView, context);
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
      scanInstruction(instruction, eventConsumer, programMethod);
    }
  }

  // The record rewriter scans the cf instructions to figure out if the record class needs to
  // be added in the output. the analysis cannot be done in desugarInstruction because the analysis
  // does not rewrite any instruction, and desugarInstruction is expected to rewrite at least one
  // instruction for assertions to be valid.
  private void scanInstruction(
      CfInstruction instruction,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context) {
    assert !instruction.isInitClass();
    if (instruction.isInvoke()) {
      CfInvoke cfInvoke = instruction.asInvoke();
      if (refersToRecord(cfInvoke.getMethod(), factory)) {
        ensureRecordClass(eventConsumer, context);
      }
      return;
    }
    if (instruction.isFieldInstruction()) {
      CfFieldInstruction fieldInstruction = instruction.asFieldInstruction();
      if (refersToRecord(fieldInstruction.getField(), factory)) {
        ensureRecordClass(eventConsumer, context);
      }
      return;
    }
    if (instruction.isTypeInstruction()) {
      CfTypeInstruction typeInstruction = instruction.asTypeInstruction();
      if (refersToRecord(typeInstruction.getType(), factory)) {
        ensureRecordClass(eventConsumer, context);
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
      CfInstructionDesugaringCollection desugaringCollection,
      DexItemFactory dexItemFactory) {
    assert !instruction.isInitClass();
    if (!needsDesugaring(instruction, context)) {
      return null;
    }
    if (instruction.isInvokeDynamic()) {
      return desugarInvokeDynamicOnRecord(
          instruction.asInvokeDynamic(),
          localStackAllocator,
          eventConsumer,
          context,
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

  private List<CfInstruction> desugarInvokeDynamicOnRecord(
      CfInvokeDynamic invokeDynamic,
      LocalStackAllocator localStackAllocator,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext) {
    RecordInvokeDynamic recordInvokeDynamic =
        parseInvokeDynamicOnRecord(invokeDynamic, appView, context);
    if (recordInvokeDynamic.getMethodName() == factory.toStringMethodName) {
      return desugarInvokeRecordToString(
          recordInvokeDynamic, localStackAllocator, eventConsumer, methodProcessingContext);
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
        DexEncodedMethod.syntheticBuilder()
            .setMethod(method)
            .setAccessFlags(methodAccessFlags)
            .setCode(null)
            // Will be traced by the enqueuer.
            .disableAndroidApiLevelCheck()
            .build();
    ProgramMethod result = new ProgramMethod(clazz, encodedMethod);
    result.setCode(provider.generateCfCode(), appView);
    return result;
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
            kinds -> kinds.RECORD_HELPER,
            methodProcessingContext.createUniqueContext(),
            appView,
            builder ->
                builder
                    .setProto(helperProto)
                    .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                    .setCode(methodSig -> codeGenerator.apply(appView.options(), methodSig))
                    .disableAndroidApiLevelCheck());
  }

  private List<CfInstruction> desugarInvokeRecordHashCode(
      RecordInvokeDynamic recordInvokeDynamic,
      LocalStackAllocator localStackAllocator,
      RecordInstructionDesugaringEventConsumer eventConsumer,
      MethodProcessingContext methodProcessingContext) {
    localStackAllocator.allocateLocalStack(1);
    DexMethod getFieldsAsObjects = getFieldsAsObjectsMethod(recordInvokeDynamic.getRecordType());
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
    DexMethod equalsRecord = equalsRecordMethod(recordInvokeDynamic.getRecordType());
    assert recordInvokeDynamic.getRecordClass().lookupProgramMethod(equalsRecord) != null;
    return Collections.singletonList(new CfInvoke(Opcodes.INVOKESPECIAL, equalsRecord, false));
  }

  private List<CfInstruction> desugarInvokeRecordToString(
      RecordInvokeDynamic recordInvokeDynamic,
      LocalStackAllocator localStackAllocator,
      RecordInstructionDesugaringEventConsumer eventConsumer,
      MethodProcessingContext methodProcessingContext) {
    localStackAllocator.allocateLocalStack(2);
    DexMethod getFieldsAsObjects = getFieldsAsObjectsMethod(recordInvokeDynamic.getRecordType());
    assert recordInvokeDynamic.getRecordClass().lookupProgramMethod(getFieldsAsObjects)
        != null;
    ArrayList<CfInstruction> instructions = new ArrayList<>();
    instructions.add(new CfInvoke(Opcodes.INVOKESPECIAL, getFieldsAsObjects, false));
    instructions.add(new CfConstClass(recordInvokeDynamic.getRecordType(), true));
    if (appView.options().testing.enableRecordModeling
        && appView.enableWholeProgramOptimizations()) {
      instructions.add(
          new CfDexItemBasedConstString(
              recordInvokeDynamic.getRecordType(),
              recordInvokeDynamic.computeRecordFieldNamesComputationInfo()));
    } else {
      instructions.add(new CfConstString(recordInvokeDynamic.getFieldNames()));
    }
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

  /**
   * If java.lang.Record is referenced from a class' supertype or a program method/field signature,
   * then the global synthetic is generated upfront of the compilation to avoid confusing D8/R8.
   *
   * <p>However, if java.lang.Record is referenced only from an instruction, for example, the code
   * contains "x instance of java.lang.Record" but no record type is present, then the global
   * synthetic is generated during instruction desugaring scanning.
   */
  private void ensureRecordClass(
      RecordDesugaringEventConsumer eventConsumer, Collection<ProgramDefinition> contexts) {
    DexItemFactory factory = appView.dexItemFactory();
    checkRecordTagNotPresent(factory);
    appView
        .getSyntheticItems()
        .ensureGlobalClass(
            () -> new MissingGlobalSyntheticsConsumerDiagnostic("Record desugaring"),
            kinds -> kinds.RECORD_TAG,
            factory.recordType,
            contexts,
            appView,
            builder -> {
              DexEncodedMethod init = synthesizeRecordInitMethod();
              builder.setAbstract().setDirectMethods(ImmutableList.of(init));
            },
            eventConsumer::acceptRecordClass);
  }

  private void ensureRecordClass(
      RecordDesugaringEventConsumer eventConsumer, ProgramDefinition context) {
    ensureRecordClass(eventConsumer, ImmutableList.of(context));
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

  public static boolean refersToRecord(DexField field, DexItemFactory factory) {
    assert !refersToRecord(field.holder, factory) : "The java.lang.Record class has no fields.";
    return refersToRecord(field.type, factory);
  }

  public static boolean refersToRecord(DexMethod method, DexItemFactory factory) {
    if (refersToRecord(method.holder, factory)) {
      return true;
    }
    return refersToRecord(method.proto, factory);
  }

  private static boolean refersToRecord(DexProto proto, DexItemFactory factory) {
    if (refersToRecord(proto.returnType, factory)) {
      return true;
    }
    return refersToRecord(proto.parameters.values, factory);
  }

  private static boolean refersToRecord(DexType[] types, DexItemFactory factory) {
    for (DexType type : types) {
      if (refersToRecord(type, factory)) {
        return true;
      }
    }
    return false;
  }

  private static boolean refersToRecord(DexType type, DexItemFactory factory) {
    return type == factory.recordType;
  }

  private boolean needsDesugaring(DexMethod method, boolean isSuper) {
    return rewriteMethod(method, isSuper) != method;
  }

  private boolean needsDesugaring(CfInvokeDynamic invokeDynamic, ProgramMethod context) {
    return isInvokeDynamicOnRecord(invokeDynamic, appView, context);
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
    return DexEncodedMethod.syntheticBuilder()
        .setMethod(factory.recordMembers.constructor)
        .setAccessFlags(methodAccessFlags)
        .setCode(new CallObjectInitCfCodeProvider(appView, factory.recordTagType).generateCfCode())
        // Will be traced by the enqueuer.
        .disableAndroidApiLevelCheck()
        .build();
  }

  @Override
  public String uniqueIdentifier() {
    return "$record";
  }

  @Override
  public void synthesizeClasses(
      ClassSynthesisDesugaringContext processingContext,
      CfClassSynthesizerDesugaringEventConsumer eventConsumer) {
    DexApplicationReadFlags flags = appView.appInfo().app().getFlags();
    if (flags.hasReadRecordReferenceFromProgramClass()) {
      List<ProgramDefinition> classes = new ArrayList<>();
      for (DexType recordWitness : flags.getRecordWitnesses()) {
        DexClass dexClass = appView.contextIndependentDefinitionFor(recordWitness);
        assert dexClass != null;
        assert dexClass.isProgramClass();
        classes.add(dexClass.asProgramClass());
      }
      ensureRecordClass(eventConsumer, classes);
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
