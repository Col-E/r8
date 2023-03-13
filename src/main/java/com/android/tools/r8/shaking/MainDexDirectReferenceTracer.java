// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.Box;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class MainDexDirectReferenceTracer {
  private final AnnotationDirectReferenceCollector annotationDirectReferenceCollector =
      new AnnotationDirectReferenceCollector();

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final Consumer<DexType> consumer;

  public MainDexDirectReferenceTracer(
      AppView<? extends AppInfoWithClassHierarchy> appView, Consumer<DexType> consumer) {
    this.appView = appView;
    this.consumer = consumer;
  }

  private AppView<? extends AppInfoWithClassHierarchy> appView() {
    return appView;
  }

  public void run(Set<DexType> roots) {
    SyntheticItems syntheticItems = appView.getSyntheticItems();
    DexItemFactory factory = appView.dexItemFactory();
    AndroidApiLevelCompute apiLevelCompute = appView.apiLevelCompute();
    for (DexType type : roots) {
      DexProgramClass clazz = asProgramClassOrNull(appView.definitionFor(type));
      // Should only happen for library classes, which are filtered out.
      assert clazz != null;
      consumer.accept(type);
      // Super and interfaces are live, no need to add them.
      if (!DexAnnotation.hasSynthesizedClassAnnotation(
          clazz.annotations(), factory, syntheticItems, apiLevelCompute)) {
        traceAnnotationsDirectDependencies(clazz.annotations());
      }
      clazz.forEachField(field -> consumer.accept(field.getReference().type));
      clazz.forEachProgramMethodMatching(
          definition -> {
            traceMethodDirectDependencies(definition.getReference(), consumer);
            return definition.hasCode();
          },
          this::runOnCode);
    }
  }

  public void runOnCode(ProgramMethod method) {
    method.registerCodeReferences(new DirectReferencesCollector(method));
  }

  public static boolean hasReferencesOutsideMainDexClasses(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ProgramMethod method,
      Predicate<DexType> isOutside) {
    return getFirstReferenceOutsideFromCode(appView, method, isOutside) != null;
  }

  public static DexProgramClass getFirstReferenceOutsideFromCode(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ProgramMethod method,
      Predicate<DexType> isOutside) {
    Box<DexProgramClass> result = new Box<>();
    new MainDexDirectReferenceTracer(
            appView,
            type -> {
              DexType baseType = type.toBaseType(appView.dexItemFactory());
              if (baseType.isClassType() && isOutside.test(baseType)) {
                DexClass cls = appView.definitionFor(baseType);
                if (cls != null && cls.isProgramClass()) {
                  result.set(cls.asProgramClass());
                }
              }
            })
        .runOnCode(method);
    return result.get();
  }

  private void traceAnnotationsDirectDependencies(DexAnnotationSet annotations) {
    annotations.collectIndexedItems(appView, annotationDirectReferenceCollector);
  }

  private void traceMethodDirectDependencies(DexMethod method, Consumer<DexType> consumer) {
    DexProto proto = method.proto;
    consumer.accept(proto.returnType);
    for (DexType parameterType : proto.parameters.values) {
      consumer.accept(parameterType);
    }
  }

  private class DirectReferencesCollector extends UseRegistry<ProgramMethod> {

    private DirectReferencesCollector(ProgramMethod context) {
      super(appView(), context);
    }

    @Override
    public void registerInitClass(DexType clazz) {
      consumer.accept(clazz);
    }

    @Override
    public void registerInvokeVirtual(DexMethod method) {
      registerInvoke(method);
    }

    @Override
    public void registerInvokeDirect(DexMethod method) {
      registerInvoke(method);
    }

    @Override
    public void registerInvokeStatic(DexMethod method) {
      registerInvoke(method);
    }

    @Override
    public void registerInvokeInterface(DexMethod method) {
      registerInvoke(method);
    }

    @Override
    public void registerInvokeSuper(DexMethod method) {
      registerInvoke(method);
    }

    protected void registerInvoke(DexMethod method) {
      consumer.accept(method.holder);
      traceMethodDirectDependencies(method, consumer);
    }

    @Override
    public void registerInstanceFieldWrite(DexField field) {
      registerFieldAccess(field);
    }

    @Override
    public void registerInstanceFieldRead(DexField field) {
      registerFieldAccess(field);
    }

    @Override
    public void registerStaticFieldRead(DexField field) {
      registerFieldAccess(field);
    }

    @Override
    public void registerStaticFieldWrite(DexField field) {
      registerFieldAccess(field);
    }

    protected void registerFieldAccess(DexField field) {
      consumer.accept(field.holder);
      consumer.accept(field.type);
    }

    @Override
    public void registerNewInstance(DexType type) {
      consumer.accept(type);
    }

    @Override
    public void registerTypeReference(DexType type) {
      consumer.accept(type);
    }

    @Override
    public void registerInstanceOf(DexType type) {
      consumer.accept(type);
    }
  }

  private class AnnotationDirectReferenceCollector implements IndexedItemCollection {

    @Override
    public boolean addClass(DexProgramClass dexProgramClass) {
      consumer.accept(dexProgramClass.type);
      return false;
    }

    @Override
    public boolean addField(DexField field) {
      consumer.accept(field.holder);
      consumer.accept(field.type);
      return false;
    }

    @Override
    public boolean addMethod(DexMethod method) {
      consumer.accept(method.holder);
      addProto(method.proto);
      return false;
    }

    @Override
    public boolean addString(DexString string) {
      return false;
    }

    @Override
    public boolean addProto(DexProto proto) {
      consumer.accept(proto.returnType);
      for (DexType parameterType : proto.parameters.values) {
        consumer.accept(parameterType);
      }
      return false;
    }

    @Override
    public boolean addType(DexType type) {
      consumer.accept(type);
      return false;
    }

    @Override
    public boolean addCallSite(DexCallSite callSite) {
      throw new AssertionError("CallSite are not supported when tracing for legacy multi dex");
    }

    @Override
    public boolean addMethodHandle(DexMethodHandle methodHandle) {
      throw new AssertionError(
          "DexMethodHandle are not supported when tracing for legacy multi dex");
    }
  }
}
