// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.features.ClassToFeatureSplitMap;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueArray;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ResolutionResult;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.desugar.LambdaDescriptor;
import com.android.tools.r8.shaking.MainDexClasses;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Map;
import java.util.Set;

class Tracer {

  private final Set<String> descriptors;
  private Set<DexType> types = Sets.newIdentityHashSet();
  private Map<DexType, Set<DexMethod>> methods = Maps.newIdentityHashMap();
  private Map<DexType, Set<DexField>> fields = Maps.newIdentityHashMap();
  private Set<String> keepPackageNames = Sets.newHashSet();
  private Set<DexReference> missingDefinitions = Sets.newHashSet();
  private final DirectMappedDexApplication application;
  private final AppInfoWithClassHierarchy appInfo;

  Tracer(Set<String> descriptors, AndroidApp inputApp) throws Exception {
    this.descriptors = descriptors;
    InternalOptions options = new InternalOptions();
    application =
        new ApplicationReader(inputApp, options, new Timing("ReferenceTrace")).read().toDirect();
    appInfo =
        AppInfoWithClassHierarchy.createInitialAppInfoWithClassHierarchy(
            application,
            ClassToFeatureSplitMap.createEmptyClassToFeatureSplitMap(),
            MainDexClasses.createEmptyMainDexClasses());
  }

  Result run() {
    UseCollector useCollector = new UseCollector(appInfo.dexItemFactory());
    for (DexProgramClass clazz : application.classes()) {
      useCollector.setContext(clazz);
      useCollector.registerSuperType(clazz, clazz.superType);
      for (DexType implementsType : clazz.interfaces.values) {
        useCollector.registerSuperType(clazz, implementsType);
      }
      clazz.forEachProgramMethod(useCollector::registerMethod);
      clazz.forEachField(useCollector::registerField);
    }

    return new Result(application, types, keepPackageNames, fields, methods, missingDefinitions);
  }

  private boolean isTargetType(DexType type) {
    return descriptors.contains(type.toDescriptorString());
  }

  private void addType(DexType type) {
    if (isTargetType(type) && types.add(type)) {
      DexClass clazz = appInfo.definitionFor(type);
      if (clazz != null && clazz.accessFlags.isVisibilityDependingOnPackage()) {
        keepPackageNames.add(clazz.type.getPackageName());
      }
      methods.put(type, Sets.newIdentityHashSet());
      fields.put(type, Sets.newIdentityHashSet());
    }
  }

  private void addField(DexField field) {
    addType(field.type);
    DexEncodedField baseField = appInfo.resolveField(field).getResolvedField();
    if (baseField != null && baseField.holder() != field.holder) {
      field = baseField.field;
    }
    addType(field.holder);
    if (isTargetType(field.holder)) {
      Set<DexField> typeFields = fields.get(field.holder);
      assert typeFields != null;
      if (baseField != null) {
        if (baseField.accessFlags.isVisibilityDependingOnPackage()) {
          keepPackageNames.add(baseField.holder().getPackageName());
        }
      } else {
        missingDefinitions.add(field);
      }
      typeFields.add(field);
    }
  }

  private void addMethod(DexMethod method) {
    addType(method.holder);
    for (DexType parameterType : method.proto.parameters.values) {
      addType(parameterType);
    }
    addType(method.proto.returnType);
    if (isTargetType(method.holder)) {
      Set<DexMethod> typeMethods = methods.get(method.holder);
      assert typeMethods != null;
      DexClass holder = appInfo.definitionForHolder(method);
      DexEncodedMethod definition = method.lookupOnClass(holder);
      if (definition != null) {
        if (definition.accessFlags.isVisibilityDependingOnPackage()) {
          keepPackageNames.add(definition.holder().getPackageName());
        }
      } else {
        missingDefinitions.add(method);
      }
      typeMethods.add(method);
    }
  }

  class UseCollector extends UseRegistry {

    private DexProgramClass context;

    UseCollector(DexItemFactory factory) {
      super(factory);
    }

    public void setContext(DexProgramClass context) {
      this.context = context;
    }

    @Override
    public void registerInitClass(DexType clazz) {
      addType(clazz);
    }

    @Override
    public void registerInvokeVirtual(DexMethod method) {
      ResolutionResult resolutionResult = appInfo.unsafeResolveMethodDueToDexFormat(method);
      DexEncodedMethod target =
          resolutionResult.isVirtualTarget() ? resolutionResult.getSingleTarget() : null;
      if (target != null && target.method != method) {
        addType(method.holder);
        addMethod(target.method);
      } else {
        addMethod(method);
      }
    }

    @Override
    public void registerInvokeDirect(DexMethod method) {
      addMethod(method);
    }

    @Override
    public void registerInvokeStatic(DexMethod method) {
      DexEncodedMethod target = appInfo.unsafeResolveMethodDueToDexFormat(method).getSingleTarget();
      if (target != null && target.method != method) {
        addType(method.holder);
        addMethod(target.method);
      } else {
        addMethod(method);
      }
    }

    @Override
    public void registerInvokeInterface(DexMethod method) {
      registerInvokeVirtual(method);
    }

    @Override
    public void registerInvokeSuper(DexMethod method) {
      DexEncodedMethod superTarget = appInfo.lookupSuperTarget(method, context);
      if (superTarget != null) {
        addMethod(superTarget.method);
      } else {
        addMethod(method);
      }
    }

    @Override
    public void registerInstanceFieldWrite(DexField field) {
      addField(field);
    }

    @Override
    public void registerInstanceFieldRead(DexField field) {
      addField(field);
    }

    @Override
    public void registerNewInstance(DexType type) {
      addType(type);
    }

    @Override
    public void registerStaticFieldRead(DexField field) {
      addField(field);
    }

    @Override
    public void registerStaticFieldWrite(DexField field) {
      addField(field);
    }

    @Override
    public void registerTypeReference(DexType type) {
      addType(type);
    }

    @Override
    public void registerInstanceOf(DexType type) {
      addType(type);
    }

    private void registerField(DexEncodedField field) {
      registerTypeReference(field.field.type);
    }

    private void registerMethod(ProgramMethod method) {
      DexEncodedMethod superTarget =
          appInfo
              .resolveMethodOn(method.getHolder(), method.getReference())
              .lookupInvokeSpecialTarget(context, appInfo);
      if (superTarget != null) {
        addMethod(superTarget.method);
      }
      for (DexType type : method.getDefinition().parameters().values) {
        registerTypeReference(type);
      }
      for (DexAnnotation annotation : method.getDefinition().annotations().annotations) {
        if (annotation.annotation.type == appInfo.dexItemFactory().annotationThrows) {
          DexValueArray dexValues = annotation.annotation.elements[0].value.asDexValueArray();
          for (DexValue dexValType : dexValues.getValues()) {
            registerTypeReference(dexValType.asDexValueType().value);
          }
        }
      }
      registerTypeReference(method.getDefinition().returnType());
      method.registerCodeReferences(this);
    }

    private void registerSuperType(DexProgramClass clazz, DexType superType) {
      registerTypeReference(superType);
      // If clazz overrides any methods in superType, we should keep those as well.
      clazz.forEachMethod(
          method -> {
            ResolutionResult resolutionResult =
                appInfo.resolveMethodOn(superType, method.method, superType != clazz.superType);
            DexEncodedMethod dexEncodedMethod = resolutionResult.getSingleTarget();
            if (dexEncodedMethod != null) {
              addMethod(dexEncodedMethod.method);
            }
          });
    }

    @Override
    public void registerCallSite(DexCallSite callSite) {
      super.registerCallSite(callSite);

      // For Lambda's, in order to find the correct use, we need to register the method for the
      // functional interface.
      List<DexType> directInterfaces = LambdaDescriptor.getInterfaces(callSite, appInfo);
      if (directInterfaces != null) {
        for (DexType directInterface : directInterfaces) {
          DexProgramClass clazz = asProgramClassOrNull(appInfo.definitionFor(directInterface));
          if (clazz != null) {
            clazz.forEachProgramVirtualMethodMatching(
                definition -> definition.getReference().name.equals(callSite.methodName),
                this::registerMethod);
          }
        }
      }
    }
  }
}
