// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.DesugarDescription;
import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.Map;
import java.util.function.BiFunction;
import org.objectweb.asm.Opcodes;

/**
 * This holds specific rewritings when using desugared library and specific libraries such as
 * androidx.
 */
public class DesugaredLibraryLibRewriter implements CfInstructionDesugaring {

  private final AppView<?> appView;
  private final Map<DexMethod, BiFunction<DexItemFactory, DexMethod, CfCode>> rewritings;

  private DesugaredLibraryLibRewriter(
      AppView<?> appView,
      Map<DexMethod, BiFunction<DexItemFactory, DexMethod, CfCode>> rewritings) {
    this.appView = appView;
    this.rewritings = rewritings;
  }

  public static DesugaredLibraryLibRewriter create(AppView<?> appView) {
    if (appView.options().machineDesugaredLibrarySpecification.getRewriteType().isEmpty()) {
      return null;
    }
    Map<DexMethod, BiFunction<DexItemFactory, DexMethod, CfCode>> rewritings = computeMap(appView);
    if (rewritings.isEmpty()) {
      return null;
    }
    return new DesugaredLibraryLibRewriter(appView, rewritings);
  }

  public static Map<DexMethod, BiFunction<DexItemFactory, DexMethod, CfCode>> computeMap(
      AppView<?> appView) {
    DexItemFactory factory = appView.dexItemFactory();
    DexType navType = factory.createType("Landroidx/navigation/NavType;");
    if (!appView.appInfo().hasDefinitionForWithoutExistenceAssert(navType)) {
      return ImmutableMap.of();
    }
    ImmutableMap.Builder<DexMethod, BiFunction<DexItemFactory, DexMethod, CfCode>> builder =
        ImmutableMap.builder();
    DexType navTypeCompanion = factory.createType("Landroidx/navigation/NavType$Companion;");
    DexProto fromProto = factory.createProto(navType, factory.stringType, factory.stringType);
    DexString name = factory.createString("fromArgType");
    DexMethod from = factory.createMethod(navTypeCompanion, fromProto, name);
    DexClassAndMethod dexClassAndMethod = appView.definitionFor(from);
    if (dexClassAndMethod == null) {
      appView
          .options()
          .reporter
          .warning(
              "The class "
                  + navType
                  + " is present but not the method "
                  + from
                  + " which suggests some unsupported set-up where androidx is pre-shrunk without"
                  + " keeping the method "
                  + from
                  + ".");
      return ImmutableMap.of();
    }
    BiFunction<DexItemFactory, DexMethod, CfCode> cfCodeProvider =
        DesugaredLibraryCfMethods::DesugaredLibraryBridge_fromArgType;
    builder.put(from, cfCodeProvider);
    return builder.build();
  }

  @Override
  public DesugarDescription compute(CfInstruction instruction, ProgramMethod context) {
    if (appView
        .getSyntheticItems()
        .isSyntheticOfKind(context.getHolderType(), kinds -> kinds.DESUGARED_LIBRARY_BRIDGE)) {
      return DesugarDescription.nothing();
    }
    if (instruction.isInvoke() && rewritings.containsKey(instruction.asInvoke().getMethod())) {
      return DesugarDescription.builder()
          .setDesugarRewrite(
              (freshLocalProvider,
                  localStackAllocator,
                  desugaringInfo,
                  eventConsumer,
                  localContext,
                  methodProcessingContext,
                  desugarings,
                  dexItemFactory) -> {
                DexMethod newInvokeTarget =
                    ensureBridge(
                        instruction.asInvoke().getMethod(),
                        eventConsumer,
                        methodProcessingContext,
                        localContext);
                assert appView.definitionFor(newInvokeTarget.getHolderType()) != null;
                assert !appView.definitionFor(newInvokeTarget.getHolderType()).isInterface();
                return Collections.singletonList(
                    new CfInvoke(Opcodes.INVOKESTATIC, newInvokeTarget, false));
              })
          .build();
    }
    return DesugarDescription.nothing();
  }

  private DexMethod ensureBridge(
      DexMethod source,
      CfInstructionDesugaringEventConsumer eventConsumer,
      MethodProcessingContext methodProcessingContext,
      ProgramMethod localContext) {
    BiFunction<DexItemFactory, DexMethod, CfCode> target = rewritings.get(source);
    ProgramMethod newMethod =
        appView
            .getSyntheticItems()
            .createMethod(
                kinds -> kinds.DESUGARED_LIBRARY_BRIDGE,
                methodProcessingContext.createUniqueContext(),
                appView,
                builder ->
                    builder
                        .disableAndroidApiLevelCheck()
                        .setProto(appView.dexItemFactory().prependHolderToProto(source))
                        .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                        .setCode(methodSig -> target.apply(appView.dexItemFactory(), methodSig)));
    eventConsumer.acceptDesugaredLibraryBridge(newMethod, localContext);
    return newMethod.getReference();
  }
}
