// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.nest;

import static com.android.tools.r8.ir.desugar.itf.InterfaceMethodRewriter.reportDependencyEdge;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClasspathMethod;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.conversion.MethodProcessor;
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
              reportDependencyEdge(hostClass, memberClass, appView.appInfo());
              reportDependencyEdge(memberClass, hostClass, appView.appInfo());
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
      MethodProcessor methodProcessor, ExecutorService executorService) throws ExecutionException {
    List<DexClasspathClass> classpathClassesInNests = new ArrayList<>();
    forEachNest(
        nest -> {
          if (nest.getHostClass().isClasspathClass()) {
            classpathClassesInNests.add(nest.getHostClass().asClasspathClass());
          }
          Iterables.addAll(classpathClassesInNests, nest.getClasspathMembers());
        });

    NestBasedAccessDesugaringEventConsumer eventConsumer =
        new NestBasedAccessDesugaringEventConsumer() {

          @Override
          public void acceptNestFieldGetBridge(ProgramField target, ProgramMethod bridge) {
            methodProcessor.scheduleDesugaredMethodForProcessing(bridge);
          }

          @Override
          public void acceptNestFieldPutBridge(ProgramField target, ProgramMethod bridge) {
            methodProcessor.scheduleDesugaredMethodForProcessing(bridge);
          }

          @Override
          public void acceptNestMethodBridge(ProgramMethod target, ProgramMethod bridge) {
            methodProcessor.scheduleDesugaredMethodForProcessing(bridge);
          }
        };
    ThreadUtils.processItems(
        classpathClassesInNests,
        clazz -> synthesizeBridgesForNestBasedAccessesOnClasspath(clazz, eventConsumer),
        executorService);
  }

  public void synthesizeBridgesForNestBasedAccessesOnClasspath(
      DexClasspathClass clazz, NestBasedAccessDesugaringEventConsumer eventConsumer) {
    clazz.forEachClasspathMethod(
        method ->
            method.registerCodeReferencesForDesugaring(
                new NestBasedAccessDesugaringUseRegistry(method, eventConsumer)));
  }

  private class NestBasedAccessDesugaringUseRegistry extends UseRegistry<ClasspathMethod> {

    private final NestBasedAccessDesugaringEventConsumer eventConsumer;

    NestBasedAccessDesugaringUseRegistry(
        ClasspathMethod context, NestBasedAccessDesugaringEventConsumer eventConsumer) {
      super(appView, context);
      this.eventConsumer = eventConsumer;
    }

    private void registerFieldAccess(DexField reference, boolean isGet) {
      DexClassAndField field =
          reference.lookupMemberOnClass(appView.definitionForHolder(reference));
      if (field != null && needsDesugaring(field, getContext())) {
        ensureFieldAccessBridge(field, isGet, eventConsumer);
      }
    }

    private void registerInvoke(DexMethod reference) {
      if (!reference.getHolderType().isClassType()) {
        return;
      }
      DexClassAndMethod method =
          reference.lookupMemberOnClass(appView.definitionForHolder(reference));
      if (method != null && needsDesugaring(method, getContext())) {
        ensureMethodBridge(method, eventConsumer);
      }
    }

    @Override
    public void registerInvokeDirect(DexMethod method) {
      registerInvoke(method);
    }

    @Override
    public void registerInvokeInterface(DexMethod method) {
      registerInvoke(method);
    }

    @Override
    public void registerInvokeStatic(DexMethod method) {
      registerInvoke(method);
    }

    @Override
    public void registerInvokeSuper(DexMethod method) {
      registerInvoke(method);
    }

    @Override
    public void registerInvokeVirtual(DexMethod method) {
      registerInvoke(method);
    }

    @Override
    public void registerInstanceFieldWrite(DexField field) {
      registerFieldAccess(field, false);
    }

    @Override
    public void registerInstanceFieldRead(DexField field) {
      registerFieldAccess(field, true);
    }

    @Override
    public void registerStaticFieldRead(DexField field) {
      registerFieldAccess(field, true);
    }

    @Override
    public void registerStaticFieldWrite(DexField field) {
      registerFieldAccess(field, false);
    }

    @Override
    public void registerInitClass(DexType clazz) {
      // Intentionally empty.
    }

    @Override
    public void registerInstanceOf(DexType type) {
      // Intentionally empty.
    }

    @Override
    public void registerNewInstance(DexType type) {
      // Intentionally empty.
    }

    @Override
    public void registerTypeReference(DexType type) {
      // Intentionally empty.
    }
  }
}
