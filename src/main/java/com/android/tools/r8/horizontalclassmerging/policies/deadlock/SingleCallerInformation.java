// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies.deadlock;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.conversion.callgraph.CallGraph;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.collections.ProgramMethodMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * Stores the single caller (if any) for each non-virtual method. Virtual methods are not considered
 * since computing single caller information for such methods is expensive (it involves computing
 * the possible dispatch targets for each virtual invoke).
 *
 * <p>Unlike the {@link CallGraph} that is used to determine if a method can be single caller
 * inlined, this considers a method that is called from multiple call sites in the same method to
 * have a single caller.
 */
// TODO(b/205611444): account for -keep rules.
public class SingleCallerInformation {

  private final ProgramMethodMap<ProgramMethod> singleCallers;
  private final Map<DexProgramClass, ProgramMethod> singleClinitCallers;

  SingleCallerInformation(
      ProgramMethodMap<ProgramMethod> singleCallers,
      Map<DexProgramClass, ProgramMethod> singleClinitCallers) {
    this.singleCallers = singleCallers;
    this.singleClinitCallers = singleClinitCallers;
  }

  public static Builder builder(AppView<? extends AppInfoWithClassHierarchy> appView) {
    return new Builder(appView);
  }

  public ProgramMethod getSingleCaller(ProgramMethod method) {
    return singleCallers.get(method);
  }

  public ProgramMethod getSingleClassInitializerCaller(DexProgramClass clazz) {
    return singleClinitCallers.get(clazz);
  }

  public static class Builder {

    private final AppView<? extends AppInfoWithClassHierarchy> appView;

    // The single callers for each method and class initializer.
    // If a method is not in the map, then a call to that method has never been seen.
    // If a method is mapped to Optional.empty(), then the method has multiple calling contexts.
    // If a method is mapped to Optional.of(m), then the method is only called from method m.
    final ProgramMethodMap<Optional<ProgramMethod>> callers = ProgramMethodMap.createConcurrent();
    final Map<DexProgramClass, Optional<ProgramMethod>> clinitCallers = new ConcurrentHashMap<>();

    Builder(AppView<? extends AppInfoWithClassHierarchy> appView) {
      this.appView = appView;
    }

    public Builder analyze(ExecutorService executorService) throws ExecutionException {
      ThreadUtils.processItems(
          appView.appInfo()::forEachMethod,
          this::processMethod,
          appView.options().getThreadingModule(),
          executorService);
      return this;
    }

    public SingleCallerInformation build() {
      ProgramMethodMap<ProgramMethod> singleCallers = ProgramMethodMap.create();
      callers.forEach(
          (method, callers) -> callers.ifPresent(caller -> singleCallers.put(method, caller)));
      Map<DexProgramClass, ProgramMethod> singleClinitCallers = new IdentityHashMap<>();
      clinitCallers.forEach(
          (clazz, callers) -> callers.ifPresent(caller -> singleClinitCallers.put(clazz, caller)));
      return new SingleCallerInformation(singleCallers, singleClinitCallers);
    }

    private void processMethod(ProgramMethod method) {
      method.registerCodeReferences(new InvokeExtractor(appView, method));
    }

    private class InvokeExtractor extends UseRegistry<ProgramMethod> {

      private final AppView<? extends AppInfoWithClassHierarchy> appViewWithClassHierachy;

      InvokeExtractor(
          AppView<? extends AppInfoWithClassHierarchy> appViewWithClassHierachy,
          ProgramMethod context) {
        super(appViewWithClassHierachy, context);
        this.appViewWithClassHierachy = appViewWithClassHierachy;
      }

      @SuppressWarnings("ReferenceEquality")
      private void recordDispatchTarget(ProgramMethod target) {
        callers.compute(
            target,
            (key, value) -> {
              if (value == null) {
                // This target is now called from the current context (only).
                return Optional.of(getContext());
              }
              // If the target is only called from the current context, then that is still the
              // case.
              if (value.orElse(null) == getContext()) {
                return value;
              }
              // The target is now called from more than one place.
              return Optional.empty();
            });
      }

      private void triggerClassInitializerIfNotAlreadyTriggeredInContext(DexType type) {
        DexProgramClass clazz = type.asProgramClass(appViewWithClassHierachy);
        if (clazz != null) {
          triggerClassInitializerIfNotAlreadyTriggeredInContext(clazz);
        }
      }

      private void triggerClassInitializerIfNotAlreadyTriggeredInContext(DexProgramClass clazz) {
        if (!isClassAlreadyInitializedInCurrentContext(clazz)) {
          triggerClassInitializer(clazz);
        }
      }

      private boolean isClassAlreadyInitializedInCurrentContext(DexProgramClass clazz) {
        return appViewWithClassHierachy.appInfo().isSubtype(getContext().getHolder(), clazz);
      }

      private void triggerClassInitializer(DexType type) {
        DexProgramClass clazz = type.asProgramClass(appViewWithClassHierachy);
        if (clazz != null) {
          triggerClassInitializer(clazz);
        }
      }

      @SuppressWarnings("ReferenceEquality")
      private void triggerClassInitializer(DexProgramClass clazz) {
        Optional<ProgramMethod> callers = clinitCallers.get(clazz);
        if (callers != null) {
          if (!callers.isPresent()) {
            // Optional.empty() represents that this class initializer has multiple (unknown)
            // callers. Since this <clinit> and all of the parent <clinit>s are already triggered
            // from multiple places, there is no need to record it is also triggered from the
            // current context.
            return;
          }
          if (callers.get() == getContext()) {
            // This <clinit> is already triggered from the current context. No need to record this
            // again.
            return;
          }
        }

        // Record that the given class is now initialized from the current context.
        clinitCallers.compute(
            clazz,
            (key, value) -> {
              if (value == null) {
                // This <clinit> was not triggered before.
                return Optional.of(getContext());
              }
              // This <clinit> was triggered from another context than the current.
              assert value.orElse(null) != getContext();
              return Optional.empty();
            });

        // Repeat for the parent classes.
        triggerClassInitializer(clazz.getSuperType());
      }

      @Override
      public void registerInitClass(DexType type) {
        DexType rewrittenType = appViewWithClassHierachy.graphLens().lookupType(type);
        triggerClassInitializerIfNotAlreadyTriggeredInContext(rewrittenType);
      }

      @Override
      public void registerInstanceFieldRead(DexField field) {
        // Intentionally empty.
      }

      @Override
      public void registerInstanceFieldWrite(DexField field) {
        // Intentionally empty.
      }

      @Override
      public void registerInvokeDirect(DexMethod method) {
        DexMethod rewrittenMethod =
            appViewWithClassHierachy
                .graphLens()
                .lookupInvokeDirect(method, getContext())
                .getReference();
        DexProgramClass holder =
            rewrittenMethod.getHolderType().asProgramClass(appViewWithClassHierachy);
        ProgramMethod target = rewrittenMethod.lookupOnProgramClass(holder);
        if (target != null) {
          recordDispatchTarget(target);
        }
      }

      @Override
      public void registerInvokeInterface(DexMethod method) {
        // Intentionally empty, as we don't aim to collect single caller information for virtual
        // methods.
      }

      @Override
      public void registerInvokeStatic(DexMethod method) {
        DexMethod rewrittenMethod =
            appViewWithClassHierachy
                .graphLens()
                .lookupInvokeDirect(method, getContext())
                .getReference();
        ProgramMethod target =
            appViewWithClassHierachy
                .appInfo()
                .unsafeResolveMethodDueToDexFormatLegacy(rewrittenMethod)
                .getResolvedProgramMethod();
        if (target != null) {
          recordDispatchTarget(target);
          triggerClassInitializerIfNotAlreadyTriggeredInContext(target.getHolder());
        }
      }

      @Override
      public void registerInvokeSuper(DexMethod method) {
        // Intentionally empty, as we don't aim to collect single caller information for virtual
        // methods.
      }

      @Override
      public void registerInvokeVirtual(DexMethod method) {
        // Intentionally empty, as we don't aim to collect single caller information for virtual
        // methods.
      }

      @Override
      public void registerNewInstance(DexType type) {
        DexType rewrittenType = appViewWithClassHierachy.graphLens().lookupType(type);
        triggerClassInitializerIfNotAlreadyTriggeredInContext(rewrittenType);
      }

      @Override
      public void registerStaticFieldRead(DexField field) {
        DexField rewrittenField = appViewWithClassHierachy.graphLens().lookupField(field);
        triggerClassInitializerIfNotAlreadyTriggeredInContext(rewrittenField.getHolderType());
      }

      @Override
      public void registerStaticFieldWrite(DexField field) {
        DexField rewrittenField = appViewWithClassHierachy.graphLens().lookupField(field);
        triggerClassInitializerIfNotAlreadyTriggeredInContext(rewrittenField.getHolderType());
      }

      @Override
      public void registerTypeReference(DexType type) {
        // Intentionally empty.
      }
    }
  }
}
