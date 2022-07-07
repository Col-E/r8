// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup;

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
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
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
import com.android.tools.r8.startup.generated.InstrumentationServerFactory;
import com.android.tools.r8.startup.generated.InstrumentationServerImplFactory;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class StartupInstrumentation {

  private final AppView<AppInfo> appView;
  private final IRConverter converter;
  private final DexItemFactory dexItemFactory;
  private final StartupOptions options;
  private final StartupReferences references;

  private StartupInstrumentation(AppView<AppInfo> appView) {
    this.appView = appView;
    this.converter = new IRConverter(appView, Timing.empty());
    this.dexItemFactory = appView.dexItemFactory();
    this.options = appView.options().getStartupOptions();
    this.references = new StartupReferences(dexItemFactory);
  }

  public static void run(AppView<AppInfo> appView, ExecutorService executorService)
      throws ExecutionException {
    if (appView.options().getStartupOptions().isStartupInstrumentationEnabled()) {
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
    List<DexProgramClass> extraProgramClasses = createStartupRuntimeLibraryClasses();
    converter.processClassesConcurrently(extraProgramClasses, executorService);

    DexApplication newApplication =
        appView.app().builder().addProgramClasses(extraProgramClasses).build();
    appView.setAppInfo(
        new AppInfo(
            appView.appInfo().getSyntheticItems().commit(newApplication),
            appView.appInfo().getMainDexInfo()));
  }

  private List<DexProgramClass> createStartupRuntimeLibraryClasses() {
    DexProgramClass instrumentationServerImplClass =
        InstrumentationServerImplFactory.createClass(dexItemFactory);
    if (options.hasStartupInstrumentationTag()) {
      instrumentationServerImplClass
          .lookupUniqueStaticFieldWithName(dexItemFactory.createString("writeToLogcat"))
          .setStaticValue(DexValueBoolean.create(true));
      instrumentationServerImplClass
          .lookupUniqueStaticFieldWithName(dexItemFactory.createString("logcatTag"))
          .setStaticValue(
              new DexValueString(
                  dexItemFactory.createString(options.getStartupInstrumentationTag())));
    }

    return ImmutableList.of(
        InstrumentationServerFactory.createClass(dexItemFactory), instrumentationServerImplClass);
  }

  private void instrumentClass(DexProgramClass clazz) {
    ensureClassInitializer(clazz);
    clazz.forEachProgramMethod(this::instrumentMethod);
  }

  private void ensureClassInitializer(DexProgramClass clazz) {
    if (!clazz.hasClassInitializer()) {
      ComputedApiLevel computedApiLevel =
          appView.apiLevelCompute().computeInitialMinApiLevel(appView.options());
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
    }
  }

  private void instrumentMethod(ProgramMethod method) {
    DexMethod methodToInvoke;
    DexMethod methodToPrint;
    SyntheticItems syntheticItems = appView.getSyntheticItems();
    if (syntheticItems.isSyntheticClass(method.getHolder())) {
      Collection<DexType> synthesizingContexts =
          syntheticItems.getSynthesizingContextTypes(method.getHolderType());
      assert synthesizingContexts.size() == 1;
      DexType synthesizingContext = synthesizingContexts.iterator().next();
      methodToInvoke = references.addSyntheticMethod;
      methodToPrint = method.getReference().withHolder(synthesizingContext, dexItemFactory);
    } else {
      methodToInvoke = references.addNonSyntheticMethod;
      methodToPrint = method.getReference();
    }

    IRCode code = method.buildIR(appView);
    InstructionListIterator instructionIterator = code.entryBlock().listIterator(code);
    instructionIterator.positionBeforeNextInstructionThatMatches(not(Instruction::isArgument));

    Value descriptorValue =
        instructionIterator.insertConstStringInstruction(
            appView, code, dexItemFactory.createString(methodToPrint.toSmaliString()));
    instructionIterator.add(
        InvokeStatic.builder()
            .setMethod(methodToInvoke)
            .setSingleArgument(descriptorValue)
            .setPosition(Position.syntheticNone())
            .build());
    DexCode instrumentedCode =
        new IRToDexFinalizer(appView, converter.deadCodeRemover)
            .finalizeCode(code, BytecodeMetadataProvider.empty(), Timing.empty());
    method.setCode(instrumentedCode, appView);
  }
}
