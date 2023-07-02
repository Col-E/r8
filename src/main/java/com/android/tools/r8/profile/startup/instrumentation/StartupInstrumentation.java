// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.startup.instrumentation;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.utils.PredicateUtils.not;

import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexReturnVoid;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue.DexValueBoolean;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.IRToDexFinalizer;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.conversion.MethodConversionOptions.MutableMethodConversionOptions;
import com.android.tools.r8.ir.conversion.MethodProcessorEventConsumer;
import com.android.tools.r8.startup.generated.InstrumentationServerFactory;
import com.android.tools.r8.startup.generated.InstrumentationServerImplFactory;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class StartupInstrumentation {

  private final AppView<AppInfo> appView;
  private final IRConverter converter;
  private final DexItemFactory dexItemFactory;
  private final InternalOptions options;
  private final StartupInstrumentationReferences references;
  private final StartupInstrumentationOptions startupInstrumentationOptions;

  private StartupInstrumentation(AppView<AppInfo> appView) {
    this.appView = appView;
    this.converter = new IRConverter(appView);
    this.dexItemFactory = appView.dexItemFactory();
    this.options = appView.options();
    this.references = new StartupInstrumentationReferences(dexItemFactory);
    this.startupInstrumentationOptions = options.getStartupInstrumentationOptions();
  }

  public static void run(AppView<AppInfo> appView, ExecutorService executorService)
      throws ExecutionException {
    if (appView.options().getStartupInstrumentationOptions().isStartupInstrumentationEnabled()) {
      StartupInstrumentation startupInstrumentation = new StartupInstrumentation(appView);
      startupInstrumentation.instrumentAllClasses(executorService);
      startupInstrumentation.injectStartupRuntimeLibrary(executorService);
    }
  }

  private void instrumentAllClasses(ExecutorService executorService) throws ExecutionException {
    ThreadUtils.processItems(appView.appInfo().classes(), this::instrumentClass, executorService);
  }

  private void injectStartupRuntimeLibrary(ExecutorService executorService)
      throws ExecutionException {
    // Only inject the startup instrumentation server if it is not already in the app.
    if (appView.definitionFor(references.instrumentationServerImplType) != null) {
      return;
    }

    // If the startup options has a synthetic context for the startup instrumentation server, then
    // only inject the runtime library if the synthetic context exists in program to avoid injecting
    // the runtime library multiple times when there is separate compilation.
    if (startupInstrumentationOptions.hasStartupInstrumentationServerSyntheticContext()) {
      DexType syntheticContext =
          dexItemFactory.createType(
              DescriptorUtils.javaTypeToDescriptor(
                  startupInstrumentationOptions.getStartupInstrumentationServerSyntheticContext()));
      if (asProgramClassOrNull(appView.definitionFor(syntheticContext)) == null) {
        return;
      }
    }

    List<DexProgramClass> extraProgramClasses = createStartupRuntimeLibraryClasses();
    MethodProcessorEventConsumer eventConsumer = MethodProcessorEventConsumer.empty();
    converter.processClassesConcurrently(
        extraProgramClasses,
        eventConsumer,
        MethodConversionOptions.forD8(appView),
        executorService);

    DexApplication newApplication =
        appView.app().builder().addProgramClasses(extraProgramClasses).build();
    AppInfo info = appView.appInfo();
    appView.setAppInfo(
        new AppInfo(
            info.getSyntheticItems().commit(newApplication),
            info.getMainDexInfo()));
    appView.appInfo().setFilter(info.getFilter());
  }

  private List<DexProgramClass> createStartupRuntimeLibraryClasses() {
    DexProgramClass instrumentationServerImplClass =
        InstrumentationServerImplFactory.createClass(dexItemFactory);
    if (startupInstrumentationOptions.hasStartupInstrumentationTag()) {
      instrumentationServerImplClass
          .lookupUniqueStaticFieldWithName(dexItemFactory.createString("writeToLogcat"))
          .setStaticValue(DexValueBoolean.create(true));
      instrumentationServerImplClass
          .lookupUniqueStaticFieldWithName(dexItemFactory.createString("logcatTag"))
          .setStaticValue(
              new DexValueString(
                  dexItemFactory.createString(
                      startupInstrumentationOptions.getStartupInstrumentationTag())));
    }

    return ImmutableList.of(
        InstrumentationServerFactory.createClass(dexItemFactory), instrumentationServerImplClass);
  }

  private void instrumentClass(DexProgramClass clazz) {
    // Do not instrument the instrumentation server if it is already in the app.
    if (clazz.getType() == references.instrumentationServerType
        || clazz.getType() == references.instrumentationServerImplType) {
      return;
    }

    boolean addedClassInitializer = ensureClassInitializer(clazz);
    clazz.forEachProgramMethodMatching(
        DexEncodedMethod::hasCode,
        method ->
            instrumentMethod(
                method, method.getDefinition().isClassInitializer() && addedClassInitializer));
  }

  private boolean ensureClassInitializer(DexProgramClass clazz) {
    if (clazz.hasClassInitializer()) {
      return false;
    }
    ComputedApiLevel computedApiLevel =
        appView.apiLevelCompute().computeInitialMinApiLevel(options);
    DexReturnVoid returnInstruction = new DexReturnVoid();
    returnInstruction.setOffset(0);
    clazz.addDirectMethod(
        DexEncodedMethod.syntheticBuilder()
            .setAccessFlags(MethodAccessFlags.createForClassInitializer())
            .setApiLevelForCode(computedApiLevel)
            .setApiLevelForDefinition(computedApiLevel)
            .setClassFileVersion(CfVersion.V1_6)
            .setCode(new DexCode(0, 0, 0, new DexInstruction[] {returnInstruction}))
            .setMethod(dexItemFactory.createClassInitializer(clazz.getType()))
            .build());
    return true;
  }

  private void instrumentMethod(ProgramMethod method, boolean skipMethodLogging) {
    // Disable StringSwitch conversion to avoid having to run the StringSwitchRemover before
    // finalizing the code.
    MutableMethodConversionOptions conversionOptions =
        MethodConversionOptions.forD8(appView).disableStringSwitchConversion();
    IRCode code = method.buildIR(appView, conversionOptions);
    InstructionListIterator instructionIterator = code.entryBlock().listIterator(code);
    instructionIterator.positionBeforeNextInstructionThatMatches(not(Instruction::isArgument));

    // Insert invoke to record that the enclosing class is a startup class.
    if (method.getDefinition().isClassInitializer()) {
      DexType classToPrint = method.getHolderType();
      Value descriptorValue =
          instructionIterator.insertConstStringInstruction(
              appView, code, dexItemFactory.createString(classToPrint.toSmaliString()));
      instructionIterator.add(
          InvokeStatic.builder()
              .setMethod(references.addMethod)
              .setSingleArgument(descriptorValue)
              .setPosition(Position.syntheticNone())
              .build());
    }

    // Insert invoke to record the execution of the current method.
    if (!skipMethodLogging) {
      DexReference referenceToPrint = method.getReference();
      Value descriptorValue =
          instructionIterator.insertConstStringInstruction(
              appView, code, dexItemFactory.createString(referenceToPrint.toSmaliString()));
      instructionIterator.add(
          InvokeStatic.builder()
              .setMethod(references.addMethod)
              .setSingleArgument(descriptorValue)
              .setPosition(Position.syntheticNone())
              .build());
    }

    converter.deadCodeRemover.run(code, Timing.empty());

    DexCode instrumentedCode =
        new IRToDexFinalizer(appView, converter.deadCodeRemover)
            .finalizeCode(code, BytecodeMetadataProvider.empty(), Timing.empty());
    method.setCode(instrumentedCode, appView);
  }
}
