// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.nest;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClasspathMethod;
import com.android.tools.r8.graph.DefaultUseRegistry;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.D8MethodProcessor;
import com.android.tools.r8.profile.rewriting.ProfileRewritingNestBasedAccessDesugaringEventConsumer;
import com.android.tools.r8.utils.ThreadUtils;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * Responsible for reporting desugar dependencies and for synthesizing bridges in the program for
 * accesses from the classpath into the program.
 */
public class D8NestBasedAccessDesugaring extends NestBasedAccessDesugaring {

  D8NestBasedAccessDesugaring(AppView<?> appView) {
    super(appView);
  }

  public void reportDesugarDependencies() {
    forEachNest(
        nest -> {
          if (nest.hasMissingMembers()) {
            throw appView.options().errorMissingNestMember(nest);
          }
          DexClass hostClass = nest.getHostClass();
          for (DexClass memberClass : nest.getMembers()) {
            if (hostClass.isProgramClass() || memberClass.isProgramClass()) {
              appView.appInfo().reportDependencyEdge(hostClass, memberClass);
              appView.appInfo().reportDependencyEdge(memberClass, hostClass);
            }
          }
        },
        classWithoutHost -> {
          throw appView.options().errorMissingNestHost(classWithoutHost);
        });
  }

  public static void checkAndFailOnIncompleteNests(AppView<?> appView) {
    forEachNest(
        nest -> {
          if (nest.hasMissingMembers()) {
            throw appView.options().errorMissingNestMember(nest);
          }
        },
        classWithoutHost -> {
          throw appView.options().errorMissingNestHost(classWithoutHost);
        },
        appView);
  }

  public void clearNestAttributes() {
    forEachNest(
        nest -> {
          nest.getHostClass().clearNestMembers();
          nest.getMembers().forEach(DexClass::clearNestHost);
        },
        classWithoutHost -> {
          // Do Nothing
        });
  }

  public void synthesizeBridgesForNestBasedAccessesOnClasspath(
      D8MethodProcessor methodProcessor, ExecutorService executorService)
      throws ExecutionException {
    List<DexClasspathClass> classpathClassesInNests = new ArrayList<>();
    forEachNest(
        nest -> {
          if (nest.getHostClass().isClasspathClass()) {
            classpathClassesInNests.add(nest.getHostClass().asClasspathClass());
          }
          Iterables.addAll(classpathClassesInNests, nest.getClasspathMembers());
        });

    NestBasedAccessDesugaringEventConsumer eventConsumer =
        ProfileRewritingNestBasedAccessDesugaringEventConsumer.attach(
            methodProcessor.getProfileCollectionAdditions(),
            new NestBasedAccessDesugaringEventConsumer() {

              @Override
              public void acceptNestConstructorBridge(
                  ProgramMethod target,
                  ProgramMethod bridge,
                  DexProgramClass argumentClass,
                  DexClassAndMethod context) {
                methodProcessor.scheduleDesugaredMethodForProcessing(bridge);
              }

              @Override
              public void acceptNestFieldGetBridge(
                  ProgramField target, ProgramMethod bridge, DexClassAndMethod context) {
                methodProcessor.scheduleDesugaredMethodForProcessing(bridge);
              }

              @Override
              public void acceptNestFieldPutBridge(
                  ProgramField target, ProgramMethod bridge, DexClassAndMethod context) {
                methodProcessor.scheduleDesugaredMethodForProcessing(bridge);
              }

              @Override
              public void acceptNestMethodBridge(
                  ProgramMethod target, ProgramMethod bridge, DexClassAndMethod context) {
                methodProcessor.scheduleDesugaredMethodForProcessing(bridge);
              }
            });
    ThreadUtils.processItems(
        classpathClassesInNests,
        clazz -> synthesizeBridgesForNestBasedAccessesOnClasspath(clazz, eventConsumer),
        appView.options().getThreadingModule(),
        executorService);
  }

  public void synthesizeBridgesForNestBasedAccessesOnClasspath(
      DexClasspathClass clazz, NestBasedAccessDesugaringEventConsumer eventConsumer) {
    clazz.forEachClasspathMethod(
        method ->
            method.registerCodeReferencesForDesugaring(
                new NestBasedAccessDesugaringUseRegistry(method, eventConsumer)));
  }

  private class NestBasedAccessDesugaringUseRegistry extends DefaultUseRegistry<ClasspathMethod> {

    private final NestBasedAccessDesugaringEventConsumer eventConsumer;

    NestBasedAccessDesugaringUseRegistry(
        ClasspathMethod context, NestBasedAccessDesugaringEventConsumer eventConsumer) {
      super(D8NestBasedAccessDesugaring.this.appView, context);
      this.eventConsumer = eventConsumer;
    }

    private void registerFieldAccessFromClasspath(DexField reference, boolean isGet) {
      DexClassAndField field =
          reference.lookupMemberOnClass(appView.definitionForHolder(reference));
      if (field != null && needsDesugaring(field, getContext())) {
        ensureFieldAccessBridgeFromClasspathAccess(field, isGet, eventConsumer);
      }
    }

    private void ensureFieldAccessBridgeFromClasspathAccess(
        DexClassAndField field,
        boolean isGet,
        NestBasedAccessDesugaringEventConsumer eventConsumer) {
      if (field.isProgramField()) {
        ensureFieldAccessBridgeFromClasspathAccess(field.asProgramField(), isGet, eventConsumer);
      } else if (field.isClasspathField()) {
        // Intentionally empty.
      } else {
        assert field.isLibraryField();
        throw reportIncompleteNest(field.asLibraryField());
      }
    }

    private void ensureFieldAccessBridgeFromClasspathAccess(
        ProgramField field, boolean isGet, NestBasedAccessDesugaringEventConsumer eventConsumer) {
      DexMethod bridgeReference = getFieldAccessBridgeReference(field, isGet);
      synchronized (field.getHolder().getMethodCollection()) {
        if (field.getHolder().lookupMethod(bridgeReference) == null) {
          ProgramMethod bridge =
              AccessBridgeFactory.createFieldAccessorBridge(bridgeReference, field, isGet);
          bridge.getHolder().addDirectMethod(bridge.getDefinition());
          if (isGet) {
            eventConsumer.acceptNestFieldGetBridge(field, bridge, getContext());
          } else {
            eventConsumer.acceptNestFieldPutBridge(field, bridge, getContext());
          }
        }
      }
    }

    private void registerInvokeFromClasspath(DexMethod reference) {
      if (!reference.getHolderType().isClassType()) {
        return;
      }
      DexClassAndMethod method =
          reference.lookupMemberOnClass(appView.definitionForHolder(reference));
      if (method != null && needsDesugaring(method, getContext())) {
        ensureConstructorOrMethodBridgeFromClasspathAccess(method, eventConsumer);
      }
    }

    // This is only used for generating bridge methods for class path references.
    private void ensureConstructorOrMethodBridgeFromClasspathAccess(
        DexClassAndMethod method, NestBasedAccessDesugaringEventConsumer eventConsumer) {
      if (method.isProgramMethod()) {
        if (method.getDefinition().isInstanceInitializer()) {
          ensureConstructorBridgeFromClasspathAccess(method.asProgramMethod(), eventConsumer);
        } else {
          ensureMethodBridgeFromClasspathAccess(method.asProgramMethod(), eventConsumer);
        }
      } else if (method.isClasspathMethod()) {
        if (method.getDefinition().isInstanceInitializer()) {
          ensureConstructorArgumentClass(method);
        }
      } else {
        assert method.isLibraryMethod();
        throw reportIncompleteNest(method.asLibraryMethod());
      }
    }

    private void ensureConstructorBridgeFromClasspathAccess(
        ProgramMethod method, NestBasedAccessDesugaringEventConsumer eventConsumer) {
      assert method.getDefinition().isInstanceInitializer();
      DexProgramClass constructorArgumentClass =
          ensureConstructorArgumentClass(method).asProgramClass();
      DexMethod bridgeReference = getConstructorBridgeReference(method, constructorArgumentClass);
      synchronized (method.getHolder().getMethodCollection()) {
        if (method.getHolder().lookupMethod(bridgeReference) == null) {
          ProgramMethod bridge =
              AccessBridgeFactory.createInitializerAccessorBridge(
                  bridgeReference, method, dexItemFactory);
          bridge.getHolder().addDirectMethod(bridge.getDefinition());
          eventConsumer.acceptNestConstructorBridge(
              method, bridge, constructorArgumentClass, getContext());
        }
      }
    }

    private void ensureMethodBridgeFromClasspathAccess(
        ProgramMethod method, NestBasedAccessDesugaringEventConsumer eventConsumer) {
      assert !method.getDefinition().isInstanceInitializer();
      DexMethod bridgeReference = getMethodBridgeReference(method);
      synchronized (method.getHolder().getMethodCollection()) {
        if (method.getHolder().lookupMethod(bridgeReference) == null) {
          ProgramMethod bridge =
              AccessBridgeFactory.createMethodAccessorBridge(
                  bridgeReference, method, dexItemFactory);
          bridge.getHolder().addDirectMethod(bridge.getDefinition());
          eventConsumer.acceptNestMethodBridge(method, bridge, getContext());
        }
      }
    }

    @Override
    public void registerInvokeDirect(DexMethod method) {
      registerInvokeFromClasspath(method);
    }

    @Override
    public void registerInvokeInterface(DexMethod method) {
      registerInvokeFromClasspath(method);
    }

    @Override
    public void registerInvokeStatic(DexMethod method) {
      registerInvokeFromClasspath(method);
    }

    @Override
    public void registerInvokeSuper(DexMethod method) {
      registerInvokeFromClasspath(method);
    }

    @Override
    public void registerInvokeVirtual(DexMethod method) {
      registerInvokeFromClasspath(method);
    }

    @Override
    public void registerInstanceFieldWrite(DexField field) {
      registerFieldAccessFromClasspath(field, false);
    }

    @Override
    public void registerInstanceFieldRead(DexField field) {
      registerFieldAccessFromClasspath(field, true);
    }

    @Override
    public void registerStaticFieldRead(DexField field) {
      registerFieldAccessFromClasspath(field, true);
    }

    @Override
    public void registerStaticFieldWrite(DexField field) {
      registerFieldAccessFromClasspath(field, false);
    }
  }
}
