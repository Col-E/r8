// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;


import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.features.ClassToFeatureSplitMap;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueArray;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ResolutionResult;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.shaking.MainDexInfo;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer.AccessFlags;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer.ClassAccessFlags;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer.FieldAccessFlags;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer.MethodAccessFlags;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer.TracedClass;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer.TracedField;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer.TracedMethod;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer.TracedReference;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

class Tracer {

  static class AccessFlagsImpl<T extends com.android.tools.r8.graph.AccessFlags<T>>
      implements AccessFlags {
    T accessFlags;

    AccessFlagsImpl(T accessFlags) {
      this.accessFlags = accessFlags;
    }

    @Override
    public boolean isStatic() {
      return accessFlags.isStatic();
    }

    @Override
    public boolean isPublic() {
      return accessFlags.isPublic();
    }

    @Override
    public boolean isProtected() {
      return accessFlags.isProtected();
    }

    @Override
    public boolean isPrivate() {
      return accessFlags.isPrivate();
    }
  }

  static class ClassAccessFlagsImpl
      extends AccessFlagsImpl<com.android.tools.r8.graph.ClassAccessFlags>
      implements ClassAccessFlags {
    ClassAccessFlagsImpl(com.android.tools.r8.graph.ClassAccessFlags accessFlags) {
      super(accessFlags);
    }

    @Override
    public boolean isInterface() {
      return accessFlags.isInterface();
    }

    @Override
    public boolean isEnum() {
      return accessFlags.isEnum();
    }
  }

  static class FieldAccessFlagsImpl
      extends AccessFlagsImpl<com.android.tools.r8.graph.FieldAccessFlags>
      implements FieldAccessFlags {
    FieldAccessFlagsImpl(com.android.tools.r8.graph.FieldAccessFlags accessFlags) {
      super(accessFlags);
    }
  }

  static class MethodAccessFlagsImpl
      extends AccessFlagsImpl<com.android.tools.r8.graph.MethodAccessFlags>
      implements MethodAccessFlags {
    MethodAccessFlagsImpl(com.android.tools.r8.graph.MethodAccessFlags accessFlags) {
      super(accessFlags);
    }
  }

  abstract static class TracedReferenceBase<T, F> implements TracedReference<T, F> {
    private final T reference;
    private final F accessFlags;
    private final boolean missingDefinition;

    private TracedReferenceBase(T reference, F accessFlags, boolean missingDefinition) {
      assert accessFlags != null || missingDefinition;
      this.reference = reference;
      this.accessFlags = accessFlags;
      this.missingDefinition = missingDefinition;
    }

    @Override
    public T getReference() {
      return reference;
    }

    @Override
    public boolean isMissingDefinition() {
      return missingDefinition;
    }

    @Override
    public F getAccessFlags() {
      return accessFlags;
    }

    @Override
    public int hashCode() {
      // Equality is only based on the reference.
      return reference.hashCode();
    }

    @Override
    public boolean equals(Object other) {
      // Equality is only based on the reference.
      if (!(other instanceof TracedReferenceBase)) {
        return false;
      }
      return reference.equals(((TracedReferenceBase<?, ?>) other).reference);
    }

    public abstract String getKindName();
  }

  static class TracedClassImpl extends TracedReferenceBase<ClassReference, ClassAccessFlags>
      implements TracedClass {
    private TracedClassImpl(DexType type) {
      this(type, null);
    }

    private TracedClassImpl(DexType reference, DexClass definition) {
      super(
          reference.asClassReference(),
          definition != null ? new ClassAccessFlagsImpl(definition.getAccessFlags()) : null,
          definition == null);
    }

    private TracedClassImpl(DexClass clazz) {
      this(clazz.getType(), clazz);
    }

    @Override
    public String getKindName() {
      return "type";
    }

    @Override
    public String toString() {
      return getReference().getTypeName();
    }
  }

  static class TracedFieldImpl extends TracedReferenceBase<FieldReference, FieldAccessFlags>
      implements TracedField {
    private TracedFieldImpl(DexField field) {
      this(field, null);
    }

    private TracedFieldImpl(DexField reference, DexEncodedField definition) {
      super(
          reference.asFieldReference(),
          definition != null ? new FieldAccessFlagsImpl(definition.getAccessFlags()) : null,
          definition == null);
    }

    private TracedFieldImpl(DexClassAndField field) {
      this(field.getReference(), field.getDefinition());
    }

    @Override
    public String getKindName() {
      return "field";
    }

    @Override
    public String toString() {
      return getReference().toString();
    }
  }

  static class TracedMethodImpl extends TracedReferenceBase<MethodReference, MethodAccessFlags>
      implements TracedMethod {
    private TracedMethodImpl(DexMethod reference) {
      this(reference, null);
    }

    private TracedMethodImpl(DexMethod reference, DexEncodedMethod definition) {
      super(
          reference.asMethodReference(),
          definition != null ? new MethodAccessFlagsImpl(definition.getAccessFlags()) : null,
          definition == null);
    }

    private TracedMethodImpl(DexClassAndMethod method) {
      this(method.getReference(), method.getDefinition());
    }

    @Override
    public String getKindName() {
      return "method";
    }

    @Override
    public String toString() {
      return getReference().toString();
    }
  }

  private final AppInfoWithClassHierarchy appInfo;
  private final DiagnosticsHandler diagnostics;
  private final Predicate<DexType> targetPredicate;

  Tracer(Set<String> targetDescriptors, AndroidApp inputApp, DiagnosticsHandler diagnostics)
      throws IOException {
    this(
        AppInfoWithClassHierarchy.createInitialAppInfoWithClassHierarchy(
            new ApplicationReader(inputApp, new InternalOptions(), Timing.empty())
                .read()
                .toDirect(),
            ClassToFeatureSplitMap.createEmptyClassToFeatureSplitMap(),
            MainDexInfo.none()),
        diagnostics,
        type -> targetDescriptors.contains(type.toDescriptorString()));
  }

  private Tracer(
      AppInfoWithClassHierarchy appInfo,
      DiagnosticsHandler diagnostics,
      Predicate<DexType> targetPredicate) {
    this.appInfo = appInfo;
    this.diagnostics = diagnostics;
    this.targetPredicate = targetPredicate;
  }

  void run(TraceReferencesConsumer consumer) {
    UseCollector useCollector = new UseCollector(appInfo.dexItemFactory(), consumer, diagnostics);
    for (DexProgramClass clazz : appInfo.classes()) {
      useCollector.registerSuperType(clazz, clazz.superType);
      for (DexType implementsType : clazz.getInterfaces()) {
        useCollector.registerSuperType(clazz, implementsType);
      }
      clazz.forEachField(useCollector::registerField);
      clazz.forEachProgramMethod(
          method -> {
            useCollector.registerMethod(method);
            useCollector.traceCode(method);
          });
    }
    consumer.finished(diagnostics);
    useCollector.reportMissingDefinitions();
  }

  class UseCollector {

    private DexItemFactory factory;
    private final TraceReferencesConsumer consumer;
    private final DiagnosticsHandler diagnostics;
    private final Set<ClassReference> missingClasses = new HashSet<>();
    private final Set<FieldReference> missingFields = new HashSet<>();
    private final Set<MethodReference> missingMethods = new HashSet<>();

    UseCollector(
        DexItemFactory factory, TraceReferencesConsumer consumer, DiagnosticsHandler diagnostics) {
      this.factory = factory;
      this.consumer = consumer;
      this.diagnostics = diagnostics;
    }

    private boolean isTargetType(DexType type) {
      return targetPredicate.test(type);
    }

    private void addType(DexType type) {
      if (type.isArrayType()) {
        addType(type.toBaseType(factory));
        return;
      }
      if (type.isPrimitiveType() || type.isVoidType()) {
        return;
      }
      assert type.isClassType();
      addClassType(type);
    }

    private void addTypes(DexTypeList types) {
      types.forEach(this::addType);
    }

    private void addClassType(DexType type) {
      assert type.isClassType();
      DexClass clazz = appInfo.definitionFor(type);
      if (clazz != null) {
        addClass(clazz);
      } else {
        TracedClassImpl tracedClass = new TracedClassImpl(type);
        collectMissingClass(tracedClass);
        if (isTargetType(type)) {
          consumer.acceptType(tracedClass, diagnostics);
        }
      }
    }

    private void addClass(DexClass clazz) {
      if (isTargetType(clazz.getType())) {
        TracedClassImpl tracedClass = new TracedClassImpl(clazz);
        consumer.acceptType(tracedClass, diagnostics);
        if (clazz.getAccessFlags().isVisibilityDependingOnPackage()) {
          consumer.acceptPackage(
              Reference.packageFromString(clazz.getType().getPackageName()), diagnostics);
        }
      }
    }

    private void addSuperMethodFromTarget(DexClassAndMethod method) {
      assert !method.isProgramMethod();
      assert isTargetType(method.getHolderType());

      // There should be no need to register the types referenced from the method signature:
      // - The return type and the parameter types are registered when visiting the source method
      //   that overrides this target method,
      // - The holder type is registered from visiting the extends/implements clause of the sub
      //   class.

      TracedMethodImpl tracedMethod = new TracedMethodImpl(method);
      if (isTargetType(method.getHolderType())) {
        consumer.acceptMethod(tracedMethod, diagnostics);
        if (method.getAccessFlags().isVisibilityDependingOnPackage()) {
          consumer.acceptPackage(
              Reference.packageFromString(method.getHolderType().getPackageName()), diagnostics);
        }
      }
    }

    private <R, T extends TracedReferenceBase<R, ?>> void collectMissing(
        T tracedReference, Set<R> missingCollection) {
      if (tracedReference.isMissingDefinition()) {
        missingCollection.add(tracedReference.getReference());
      }
    }

    private void collectMissingClass(TracedClassImpl tracedClass) {
      assert tracedClass.isMissingDefinition();
      collectMissing(tracedClass, missingClasses);
    }

    private void collectMissingField(TracedFieldImpl tracedField) {
      assert tracedField.isMissingDefinition();
      collectMissing(tracedField, missingFields);
    }

    private void collectMissingMethod(TracedMethodImpl tracedMethod) {
      assert tracedMethod.isMissingDefinition();
      collectMissing(tracedMethod, missingMethods);
    }

    private void reportMissingDefinitions() {
      if (missingClasses.size() > 0 || missingFields.size() > 0 || missingMethods.size() > 0) {
        diagnostics.error(
            new MissingDefinitionsDiagnostic(missingClasses, missingFields, missingMethods));
      }
    }

    private void registerField(DexEncodedField field) {
      addType(field.getType());
    }

    private void registerMethod(ProgramMethod method) {
      addTypes(method.getParameters());
      addType(method.getReturnType());
      for (DexAnnotation annotation : method.getDefinition().annotations().annotations) {
        if (annotation.getAnnotationType() == appInfo.dexItemFactory().annotationThrows) {
          DexValueArray dexValues = annotation.annotation.elements[0].value.asDexValueArray();
          for (DexValue dexValType : dexValues.getValues()) {
            addType(dexValType.asDexValueType().value);
          }
        }
      }

      DexClassAndMethod superTarget =
          appInfo
              .resolveMethodOn(method.getHolder(), method.getReference())
              .lookupInvokeSpecialTarget(method.getHolder(), appInfo);
      if (superTarget != null
          && !superTarget.isProgramMethod()
          && isTargetType(superTarget.getHolderType())) {
        addSuperMethodFromTarget(superTarget);
      }
    }

    private void traceCode(ProgramMethod method) {
      method.registerCodeReferences(new MethodUseCollector(method));
    }

    private void registerSuperType(DexProgramClass clazz, DexType superType) {
      addType(superType);
      // If clazz overrides any methods in superType, we should keep those as well.
      clazz.forEachMethod(
          method -> {
            DexClassAndMethod resolvedMethod =
                appInfo
                    .resolveMethodOn(superType, method.getReference(), superType != clazz.superType)
                    .getResolutionPair();
            if (resolvedMethod != null
                && !resolvedMethod.isProgramMethod()
                && isTargetType(resolvedMethod.getHolderType())) {
              addSuperMethodFromTarget(resolvedMethod);
            }
          });
    }

    class MethodUseCollector extends UseRegistry {

      private final ProgramMethod context;

      public MethodUseCollector(ProgramMethod context) {
        super(appInfo.dexItemFactory());
        this.context = context;
      }

      // Method references.

      @Override
      public void registerInvokeDirect(DexMethod method) {
        DexClass holder = appInfo.definitionFor(method.getHolderType());
        handleRewrittenMethodReference(method, method.lookupMemberOnClass(holder));
      }

      @Override
      public void registerInvokeInterface(DexMethod method) {
        handleInvokeWithDynamicDispatch(method, Type.INTERFACE);
      }

      @Override
      public void registerInvokeStatic(DexMethod method) {
        DexClassAndMethod resolvedMethod =
            appInfo.unsafeResolveMethodDueToDexFormat(method).getResolutionPair();
        handleRewrittenMethodReference(method, resolvedMethod);
      }

      @Override
      public void registerInvokeSuper(DexMethod method) {
        DexClassAndMethod superTarget = appInfo.lookupSuperTarget(method, context);
        handleRewrittenMethodReference(method, superTarget);
      }

      @Override
      public void registerInvokeVirtual(DexMethod method) {
        handleInvokeWithDynamicDispatch(method, Type.VIRTUAL);
      }

      private void handleInvokeWithDynamicDispatch(DexMethod method, Type type) {
        if (method.getHolderType().isArrayType()) {
          addType(method.getHolderType());
          return;
        }
        ResolutionResult resolutionResult =
            type.isInterface()
                ? appInfo.resolveMethodOnInterface(method)
                : appInfo.resolveMethodOnClass(method);
        DexClassAndMethod resolvedMethod =
            resolutionResult.isVirtualTarget() ? resolutionResult.getResolutionPair() : null;
        handleRewrittenMethodReference(method, resolvedMethod);
      }

      private void handleRewrittenMethodReference(
          DexMethod method, DexClassAndMethod resolvedMethod) {
        assert resolvedMethod == null || resolvedMethod.getReference().match(method);
        addType(method.getHolderType());
        addTypes(method.getParameters());
        addType(method.getReturnType());
        if (resolvedMethod != null) {
          if (isTargetType(resolvedMethod.getHolderType())) {
            if (resolvedMethod.getHolderType() != method.getHolderType()) {
              addType(resolvedMethod.getHolderType());
            }
            TracedMethodImpl tracedMethod = new TracedMethodImpl(resolvedMethod);
            consumer.acceptMethod(tracedMethod, diagnostics);
            if (resolvedMethod.getAccessFlags().isVisibilityDependingOnPackage()) {
              consumer.acceptPackage(
                  Reference.packageFromString(resolvedMethod.getHolderType().getPackageName()),
                  diagnostics);
            }
          }
        } else {
          TracedMethodImpl tracedMethod = new TracedMethodImpl(method);
          collectMissingMethod(tracedMethod);
          if (isTargetType(method.getHolderType())) {
            consumer.acceptMethod(tracedMethod, diagnostics);
          }
        }
      }

      // Field references.

      @Override
      public void registerInitClass(DexType clazz) {
        addType(clazz);
      }

      @Override
      public void registerInstanceFieldRead(DexField field) {
        handleFieldAccess(field);
      }

      @Override
      public void registerInstanceFieldWrite(DexField field) {
        handleFieldAccess(field);
      }

      @Override
      public void registerStaticFieldRead(DexField field) {
        handleFieldAccess(field);
      }

      @Override
      public void registerStaticFieldWrite(DexField field) {
        handleFieldAccess(field);
      }

      private void handleFieldAccess(DexField field) {
        handleRewrittenFieldReference(field);
      }

      private void handleRewrittenFieldReference(DexField field) {
        addType(field.getHolderType());
        addType(field.getType());

        DexClassAndField resolvedField = appInfo.resolveField(field).getResolutionPair();
        if (resolvedField != null) {
          if (isTargetType(resolvedField.getHolderType())) {
            if (resolvedField.getHolderType() != field.getHolderType()) {
              addClass(resolvedField.getHolder());
            }
            TracedFieldImpl tracedField = new TracedFieldImpl(resolvedField);
            consumer.acceptField(tracedField, diagnostics);
            if (resolvedField.getAccessFlags().isVisibilityDependingOnPackage()) {
              consumer.acceptPackage(
                  Reference.packageFromString(resolvedField.getHolderType().getPackageName()),
                  diagnostics);
            }
          }
        } else {
          TracedFieldImpl tracedField = new TracedFieldImpl(field);
          collectMissingField(tracedField);
          if (isTargetType(field.getHolderType())) {
            consumer.acceptField(tracedField, diagnostics);
          }
        }
      }

      // Type references.

      @Override
      public void registerTypeReference(DexType type) {
        addType(type);
      }
    }
  }
}
