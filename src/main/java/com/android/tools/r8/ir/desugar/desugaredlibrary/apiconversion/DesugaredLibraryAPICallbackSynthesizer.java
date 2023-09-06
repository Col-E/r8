// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion;

import static com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryAPIConverter.generateTrackDesugaredAPIWarnings;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryAPIConverter.isAPIConversionSyntheticType;

import com.android.tools.r8.contexts.CompilationContext.MainThreadContext;
import com.android.tools.r8.contexts.CompilationContext.ProcessorContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfPostProcessingDesugaring;
import com.android.tools.r8.ir.desugar.CfPostProcessingDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecification;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.WorkList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;

public class DesugaredLibraryAPICallbackSynthesizer implements CfPostProcessingDesugaring {

  private final AppView<?> appView;
  private final DexItemFactory factory;

  private final DesugaredLibraryWrapperSynthesizer wrapperSynthesizor;
  private final Set<DexMethod> trackedCallBackAPIs;

  private final Predicate<ProgramMethod> isLiveMethod;

  public DesugaredLibraryAPICallbackSynthesizer(
      AppView<?> appView, Predicate<ProgramMethod> isLiveMethod) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
    this.isLiveMethod = isLiveMethod;
    this.wrapperSynthesizor = new DesugaredLibraryWrapperSynthesizer(appView);
    if (appView.options().testing.trackDesugaredAPIConversions) {
      trackedCallBackAPIs = SetUtils.newConcurrentHashSet();
    } else {
      trackedCallBackAPIs = null;
    }
  }

  // TODO(b/191656218): Consider parallelizing post processing.
  @Override
  public void postProcessingDesugaring(
      Collection<DexProgramClass> programClasses,
      CfPostProcessingDesugaringEventConsumer eventConsumer,
      ExecutorService executorService) {
    ProcessorContext processorContext = appView.createProcessorContext();
    MainThreadContext mainThreadContext = processorContext.createMainThreadContext();
    Set<DexMethod> newlyLiveMethods = eventConsumer.getNewlyLiveMethods();
    assert noPendingWrappersOrConversions();
    for (DexProgramClass clazz : programClasses) {
      if (!appView.isAlreadyLibraryDesugared(clazz)) {
        ArrayList<DexEncodedMethod> callbacks = new ArrayList<>();
        // We note that methods requiring callbacks are library overrides, therefore, they should
        // always be live in R8.
        for (ProgramMethod virtualProgramMethod : clazz.virtualProgramMethods()) {
          if (shouldRegisterCallback(virtualProgramMethod)) {
            if (!isLiveMethod(virtualProgramMethod, newlyLiveMethods)) {
              // This happens for live non instantiated types, library overrides are not live there.
              continue;
            }
            if (trackedCallBackAPIs != null) {
              trackedCallBackAPIs.add(virtualProgramMethod.getReference());
            }
            ProgramMethod callback =
                wrapperSynthesizor
                    .getConversionCfProvider()
                    .generateCallbackConversion(
                        virtualProgramMethod, eventConsumer, mainThreadContext);
            callbacks.add(callback.getDefinition());
          }
        }
        if (!callbacks.isEmpty()) {
          clazz.addVirtualMethods(callbacks);
        }
      }
    }
    assert noPendingWrappersOrConversions();
    generateTrackingWarnings();
  }

  private boolean isLiveMethod(
      ProgramMethod virtualProgramMethod, Set<DexMethod> newlyLiveMethods) {
    return isLiveMethod.test(virtualProgramMethod)
        || newlyLiveMethods.contains(virtualProgramMethod.getReference());
  }

  private boolean noPendingWrappersOrConversions() {
    for (DexProgramClass pendingSyntheticClass :
        appView.getSyntheticItems().getPendingSyntheticClasses()) {
      assert !isAPIConversionSyntheticType(pendingSyntheticClass.type, wrapperSynthesizor, appView);
    }
    return true;
  }

  public boolean shouldRegisterCallback(ProgramMethod method) {
    // Any override of a library method can be called by the library.
    // We duplicate the method to have a vivified type version callable by the library and
    // a type version callable by the program. We need to add the vivified version to the rootset
    // as it is actually overriding a library method (after changing the vivified type to the core
    // library type), but the enqueuer cannot see that.
    // To avoid too much computation we first look if the method would need to be rewritten if
    // it would override a library method, then check if it overrides a library method.
    DexEncodedMethod definition = method.getDefinition();
    if (definition.isPrivateMethod()
        || definition.isStatic()
        || definition.isAbstract()
        || definition.isLibraryMethodOverride().isFalse()) {
      return false;
    }
    if (!appView.typeRewriter.hasRewrittenTypeInSignature(definition.getProto(), appView)
        || appView
            .options()
            .machineDesugaredLibrarySpecification
            .getEmulatedInterfaces()
            .containsKey(method.getHolderType())) {
      return false;
    }
    // In R8 we should be in the enqueuer, therefore we can duplicate a default method and both
    // methods will be desugared.
    // In D8, this happens after interface method desugaring, we cannot introduce new default
    // methods, but we do not need to since this is a library override (invokes will resolve) and
    // all implementors have been enhanced with a forwarding method which will be duplicated.
    if (!appView.enableWholeProgramOptimizations()) {
      if (method.getHolder().isInterface()
          && method.getDefinition().isDefaultMethod()
          && (!appView.options().canUseDefaultAndStaticInterfaceMethods()
              || appView.options().isDesugaredLibraryCompilation())) {
        return false;
      }
    }
    if (!appView.options().machineDesugaredLibrarySpecification.supportAllCallbacksFromLibrary()
        && appView.options().isDesugaredLibraryCompilation()) {
      return false;
    }
    return overridesNonFinalLibraryMethod(method);
  }

  private boolean overridesNonFinalLibraryMethod(ProgramMethod method) {
    // We look up everywhere to see if there is a supertype/interface implementing the method...
    DexProgramClass holder = method.getHolder();
    WorkList<DexType> workList = WorkList.newIdentityWorkList();
    workList.addIfNotSeen(holder.interfaces.values);
    boolean foundOverrideToRewrite = false;
    // There is no methods with desugared types on Object.
    if (holder.superType != factory.objectType) {
      workList.addIfNotSeen(holder.superType);
    }
    while (workList.hasNext()) {
      DexType current = workList.next();
      DexClass dexClass = appView.definitionFor(current);
      if (dexClass == null) {
        continue;
      }
      workList.addIfNotSeen(dexClass.interfaces.values);
      if (dexClass.superType != factory.objectType) {
        workList.addIfNotSeen(dexClass.superType);
      }
      if (!dexClass.isLibraryClass() && !appView.options().isDesugaredLibraryCompilation()) {
        continue;
      }
      if (!shouldGenerateCallbacksForEmulateInterfaceAPIs(dexClass)) {
        continue;
      }
      DexEncodedMethod dexEncodedMethod = dexClass.lookupVirtualMethod(method.getReference());
      if (dexEncodedMethod != null) {
        // In this case, the object will be wrapped.
        if (appView.typeRewriter.hasRewrittenType(dexClass.type, appView)) {
          return false;
        }
        if (dexEncodedMethod.isFinal()) {
          // We do not introduce overrides of final methods, in this case, the runtime always
          // execute the default behavior in the final method.
          return false;
        }
        foundOverrideToRewrite = true;
      }
    }
    return foundOverrideToRewrite;
  }

  private boolean shouldGenerateCallbacksForEmulateInterfaceAPIs(DexClass dexClass) {
    if (appView.options().machineDesugaredLibrarySpecification.supportAllCallbacksFromLibrary()) {
      return true;
    }
    MachineDesugaredLibrarySpecification specification =
        appView.options().machineDesugaredLibrarySpecification;
    return !(specification.getEmulatedInterfaces().containsKey(dexClass.type)
        || specification.isEmulatedInterfaceRewrittenType(dexClass.type));
  }

  private void generateTrackingWarnings() {
    generateTrackDesugaredAPIWarnings(trackedCallBackAPIs, "callback ", appView);
  }
}
