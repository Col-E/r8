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
    private TracedClassImpl(DexType reference, DexClass definition) {
      super(
          Reference.classFromDescriptor(reference.toDescriptorString()),
          definition != null ? new ClassAccessFlagsImpl(definition.getAccessFlags()) : null,
          definition == null);
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
    private TracedFieldImpl(DexField reference, DexEncodedField definition) {
      super(
          Reference.field(
              Reference.classFromDescriptor(reference.holder.toDescriptorString()),
              reference.name.toString(),
              Reference.typeFromDescriptor(reference.type.toDescriptorString())),
          definition != null ? new FieldAccessFlagsImpl(definition.getAccessFlags()) : null,
          definition == null);
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
    private TracedMethodImpl(DexMethod reference, DexEncodedMethod definition) {
      super(
          reference.asMethodReference(),
          definition != null ? new MethodAccessFlagsImpl(definition.getAccessFlags()) : null,
          definition == null);
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

  Tracer(Set<String> descriptors, AndroidApp inputApp, DiagnosticsHandler diagnostics)
      throws IOException {
    this(
        AppInfoWithClassHierarchy.createInitialAppInfoWithClassHierarchy(
            new ApplicationReader(inputApp, new InternalOptions(), Timing.empty())
                .read()
                .toDirect(),
            ClassToFeatureSplitMap.createEmptyClassToFeatureSplitMap(),
            MainDexInfo.none()),
        diagnostics,
        type -> descriptors.contains(type.toDescriptorString()));
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
      DexClass clazz = appInfo.definitionFor(type);
      TracedClassImpl tracedClass = new TracedClassImpl(type, clazz);
      checkMissingDefinition(tracedClass);
      if (isTargetType(type) || tracedClass.isMissingDefinition()) {
        consumer.acceptType(tracedClass, diagnostics);
        if (!tracedClass.isMissingDefinition()
            && clazz.accessFlags.isVisibilityDependingOnPackage()) {
          consumer.acceptPackage(
              Reference.packageFromString(clazz.type.getPackageName()), diagnostics);
        }
      }
    }

    private void addTypes(DexTypeList types) {
      types.forEach(this::addType);
    }

    private void addField(DexField field) {
      addType(field.type);
      DexEncodedField baseField = appInfo.resolveField(field).getResolvedField();
      if (baseField != null && baseField.getHolderType() != field.holder) {
        field = baseField.getReference();
      }
      addType(field.holder);
      TracedFieldImpl tracedField = new TracedFieldImpl(field, baseField);
      checkMissingDefinition(tracedField);
      if (isTargetType(field.holder) || tracedField.isMissingDefinition()) {
        consumer.acceptField(tracedField, diagnostics);
        if (!tracedField.isMissingDefinition()
            && baseField.accessFlags.isVisibilityDependingOnPackage()) {
          consumer.acceptPackage(
              Reference.packageFromString(baseField.getHolderType().getPackageName()), diagnostics);
        }
      }
    }

    private void addMethod(DexMethod method) {
      addType(method.holder);
      addTypes(method.getParameters());
      addType(method.getReturnType());
      DexClass holder = appInfo.definitionForHolder(method);
      DexEncodedMethod definition = method.lookupOnClass(holder);
      TracedMethodImpl tracedMethod = new TracedMethodImpl(method, definition);
      if (isTargetType(method.holder) || tracedMethod.isMissingDefinition()) {
        consumer.acceptMethod(tracedMethod, diagnostics);
        checkMissingDefinition(tracedMethod);
        if (!tracedMethod.isMissingDefinition()
            && definition.accessFlags.isVisibilityDependingOnPackage()) {
          consumer.acceptPackage(
              Reference.packageFromString(definition.getHolderType().getPackageName()),
              diagnostics);
        }
      }
    }

    private void checkMissingDefinition(TracedClassImpl tracedClass) {
      collectMissing(tracedClass, missingClasses);
    }

    private void checkMissingDefinition(TracedFieldImpl tracedField) {
      collectMissing(tracedField, missingFields);
    }

    private void checkMissingDefinition(TracedMethodImpl tracedMethod) {
      collectMissing(tracedMethod, missingMethods);
    }

    private <R, T extends TracedReferenceBase<R, ?>> void collectMissing(
        T tracedReference, Set<R> missingCollection) {
      if (tracedReference.isMissingDefinition()) {
        missingCollection.add(tracedReference.getReference());
      }
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
      DexClassAndMethod superTarget =
          appInfo
              .resolveMethodOn(method.getHolder(), method.getReference())
              .lookupInvokeSpecialTarget(method.getHolder(), appInfo);
      if (superTarget != null) {
        addMethod(superTarget.getReference());
      }
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
    }

    private void traceCode(ProgramMethod method) {
      method.registerCodeReferences(new MethodUseCollector(method));
    }

    private void registerSuperType(DexProgramClass clazz, DexType superType) {
      addType(superType);
      // If clazz overrides any methods in superType, we should keep those as well.
      clazz.forEachMethod(
          method -> {
            ResolutionResult resolutionResult =
                appInfo.resolveMethodOn(
                    superType, method.getReference(), superType != clazz.superType);
            DexEncodedMethod dexEncodedMethod = resolutionResult.getSingleTarget();
            if (dexEncodedMethod != null) {
              addMethod(dexEncodedMethod.getReference());
            }
          });
    }

    class MethodUseCollector extends UseRegistry {

      private final ProgramMethod context;

      public MethodUseCollector(ProgramMethod context) {
        super(appInfo.dexItemFactory());
        this.context = context;
      }

      // Method refererences.

      @Override
      public void registerInvokeDirect(DexMethod method) {
        addMethod(method);
      }

      @Override
      public void registerInvokeInterface(DexMethod method) {
        registerInvokeVirtual(method);
      }

      @Override
      public void registerInvokeStatic(DexMethod method) {
        DexEncodedMethod target =
            appInfo.unsafeResolveMethodDueToDexFormat(method).getSingleTarget();
        if (target != null && target.getReference() != method) {
          addType(method.holder);
          addMethod(target.getReference());
        } else {
          addMethod(method);
        }
      }

      @Override
      public void registerInvokeSuper(DexMethod method) {
        DexClassAndMethod superTarget = appInfo.lookupSuperTarget(method, context);
        if (superTarget != null) {
          addMethod(superTarget.getReference());
        } else {
          addMethod(method);
        }
      }

      @Override
      public void registerInvokeVirtual(DexMethod method) {
        if (method.holder.isArrayType()) {
          addType(method.holder);
          return;
        }
        ResolutionResult resolutionResult = appInfo.unsafeResolveMethodDueToDexFormat(method);
        DexEncodedMethod target =
            resolutionResult.isVirtualTarget() ? resolutionResult.getSingleTarget() : null;
        if (target != null && target.getReference() != method) {
          addType(method.holder);
          addMethod(target.getReference());
        } else {
          addMethod(method);
        }
      }

      // Field references.

      @Override
      public void registerInitClass(DexType clazz) {
        addType(clazz);
      }

      @Override
      public void registerInstanceFieldRead(DexField field) {
        addField(field);
      }

      @Override
      public void registerInstanceFieldWrite(DexField field) {
        addField(field);
      }

      @Override
      public void registerStaticFieldRead(DexField field) {
        addField(field);
      }

      @Override
      public void registerStaticFieldWrite(DexField field) {
        addField(field);
      }

      // Type references.

      @Override
      public void registerTypeReference(DexType type) {
        addType(type);
      }
    }
  }
}
