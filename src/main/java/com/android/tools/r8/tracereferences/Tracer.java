// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.diagnostic.DefinitionContext;
import com.android.tools.r8.diagnostic.internal.DefinitionContextUtils;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassResolutionResult;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueArray;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.graph.lens.FieldLookupResult;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.ir.desugar.LambdaDescriptor;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer.TracedReference;
import com.android.tools.r8.tracereferences.internal.TracedClassImpl;
import com.android.tools.r8.tracereferences.internal.TracedFieldImpl;
import com.android.tools.r8.tracereferences.internal.TracedMethodImpl;
import com.android.tools.r8.utils.BooleanBox;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public class Tracer {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final DiagnosticsHandler diagnostics;
  private final Predicate<DexType> targetPredicate;

  public Tracer(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      DiagnosticsHandler diagnostics,
      Predicate<DexType> targetPredicate) {
    this.appView = appView;
    this.diagnostics = diagnostics;
    this.targetPredicate = targetPredicate;
  }

  public void run(TraceReferencesConsumer consumer) {
    UseCollector useCollector = new UseCollector(appView, consumer, diagnostics, targetPredicate);
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      DefinitionContext classContext = DefinitionContextUtils.create(clazz);
      if (clazz.superType != null) {
        useCollector.registerSuperType(clazz, clazz.superType, classContext);
      }
      for (DexType implementsType : clazz.getInterfaces()) {
        useCollector.registerSuperType(clazz, implementsType, classContext);
      }
      clazz.forEachProgramField(useCollector::registerField);
      clazz.forEachProgramMethod(
          method -> {
            useCollector.registerMethod(method);
            useCollector.traceCode(method);
          });
    }
    consumer.finished(diagnostics);
  }

  // The graph lens is intentionally only made accessible to the MethodUseCollector, since the
  // graph lens should only be applied to the code.
  static class UseCollector {

    private final AppView<? extends AppInfoWithClassHierarchy> appView;
    private final DexItemFactory factory;
    private final TraceReferencesConsumer consumer;
    private final DiagnosticsHandler diagnostics;
    private final Predicate<DexType> targetPredicate;

    private final Set<ClassReference> missingClasses = new HashSet<>();
    private final Set<FieldReference> missingFields = new HashSet<>();
    private final Set<MethodReference> missingMethods = new HashSet<>();

    UseCollector(
        AppView<? extends AppInfoWithClassHierarchy> appView,
        TraceReferencesConsumer consumer,
        DiagnosticsHandler diagnostics,
        Predicate<DexType> targetPredicate) {
      this.appView = appView;
      this.factory = appView.dexItemFactory();
      this.consumer = consumer;
      this.diagnostics = diagnostics;
      this.targetPredicate = targetPredicate;
    }

    AppView<? extends AppInfoWithClassHierarchy> appView() {
      return appView;
    }

    AppInfoWithClassHierarchy appInfo() {
      return appView.appInfo();
    }

    GraphLens graphLens() {
      return appView.graphLens();
    }

    private boolean isTargetType(DexType type) {
      return targetPredicate.test(type);
    }

    private void addType(DexType type, DefinitionContext referencedFrom) {
      if (type.isArrayType()) {
        addType(type.toBaseType(factory), referencedFrom);
        return;
      }
      if (type.isPrimitiveType() || type.isVoidType()) {
        return;
      }
      assert type.isClassType();
      addClassType(type, referencedFrom);
    }

    private void addTypes(DexTypeList types, DefinitionContext referencedFrom) {
      for (DexType type : types) {
        addType(type, referencedFrom);
      }
    }

    private void addClassType(DexType type, DefinitionContext referencedFrom) {
      assert type.isClassType();
      ClassResolutionResult result =
          appView.contextIndependentDefinitionForWithResolutionResult(type);
      if (result.hasClassResolutionResult()) {
        result.forEachClassResolutionResult(clazz -> addClass(clazz, referencedFrom));
      } else {
        TracedClassImpl tracedClass = new TracedClassImpl(type, referencedFrom);
        collectMissingClass(tracedClass);
        consumer.acceptType(tracedClass, diagnostics);
      }
    }

    private void addClass(DexClass clazz, DefinitionContext referencedFrom) {
      if (isTargetType(clazz.getType())) {
        TracedClassImpl tracedClass = new TracedClassImpl(clazz, referencedFrom);
        consumer.acceptType(tracedClass, diagnostics);
        if (clazz.getAccessFlags().isVisibilityDependingOnPackage()) {
          consumer.acceptPackage(
              Reference.packageFromString(clazz.getType().getPackageName()), diagnostics);
        }
      }
    }

    private void addSuperMethodFromTarget(
        DexClassAndMethod method, DefinitionContext referencedFrom) {
      assert !method.isProgramMethod();
      assert isTargetType(method.getHolderType());

      // There should be no need to register the types referenced from the method signature:
      // - The return type and the parameter types are registered when visiting the source method
      //   that overrides this target method,
      // - The holder type is registered from visiting the extends/implements clause of the sub
      //   class.

      TracedMethodImpl tracedMethod = new TracedMethodImpl(method.getDefinition(), referencedFrom);
      if (isTargetType(method.getHolderType())) {
        consumer.acceptMethod(tracedMethod, diagnostics);
        if (method.getAccessFlags().isVisibilityDependingOnPackage()) {
          consumer.acceptPackage(
              Reference.packageFromString(method.getHolderType().getPackageName()), diagnostics);
        }
      }
    }

    private <R, T extends TracedReference<R, ?>> void collectMissing(
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

    private void registerField(ProgramField field) {
      DefinitionContext referencedFrom = DefinitionContextUtils.create(field);
      addType(field.getType(), referencedFrom);
    }

    @SuppressWarnings("ReferenceEquality")
    private void registerMethod(ProgramMethod method) {
      DefinitionContext referencedFrom = DefinitionContextUtils.create(method);
      addTypes(method.getParameters(), referencedFrom);
      addType(method.getReturnType(), referencedFrom);
      for (DexAnnotation annotation : method.getDefinition().annotations().annotations) {
        if (annotation.getAnnotationType() == factory.annotationThrows) {
          DexValueArray dexValues = annotation.annotation.elements[0].value.asDexValueArray();
          for (DexValue dexValType : dexValues.getValues()) {
            addType(dexValType.asDexValueType().value, referencedFrom);
          }
        }
      }
      MethodResolutionResult methodResolutionResult =
          method.getHolder().isInterface()
              ? appInfo().resolveMethodOnInterface(method.getHolder(), method.getReference())
              : appInfo().resolveMethodOnClass(method.getHolder(), method.getReference());
      DexClassAndMethod superTarget =
          methodResolutionResult.lookupInvokeSpecialTarget(method.getHolder(), appView);
      if (superTarget != null
          && !superTarget.isProgramMethod()
          && isTargetType(superTarget.getHolderType())) {
        addSuperMethodFromTarget(superTarget, referencedFrom);
      }
    }

    private void traceCode(ProgramMethod method) {
      method.registerCodeReferences(new MethodUseCollector(method));
    }

    @SuppressWarnings("ReferenceEquality")
    private void registerSuperType(
        DexProgramClass clazz, DexType superType, DefinitionContext referencedFrom) {
      addType(superType, referencedFrom);
      // If clazz overrides any methods in superType, we should keep those as well.
      clazz.forEachMethod(
          method -> {
            DexClassAndMethod resolvedMethod =
                appInfo()
                    .resolveMethodOn(superType, method.getReference(), superType != clazz.superType)
                    .getResolutionPair();
            if (resolvedMethod != null
                && !resolvedMethod.isProgramMethod()
                && isTargetType(resolvedMethod.getHolderType())) {
              addSuperMethodFromTarget(resolvedMethod, referencedFrom);
            }
          });
    }

    class MethodUseCollector extends UseRegistry<ProgramMethod> {

      private final DefinitionContext referencedFrom;

      public MethodUseCollector(ProgramMethod context) {
        super(appView(), context);
        this.referencedFrom = DefinitionContextUtils.create(context);
      }

      // Method references.

      @Override
      public void registerInvokeDirect(DexMethod method) {
        MethodLookupResult lookupResult = graphLens().lookupInvokeDirect(method, getContext());
        assert lookupResult.getType().isDirect();
        DexMethod rewrittenMethod = lookupResult.getReference();
        if (getContext().getHolder().originatesFromDexResource()) {
          handleRewrittenMethodResolution(
              rewrittenMethod,
              appInfo().unsafeResolveMethodDueToDexFormat(rewrittenMethod),
              SingleResolutionResult::getResolutionPair);
        } else {
          BooleanBox seenMethod = new BooleanBox();
          appView
              .contextIndependentDefinitionForWithResolutionResult(rewrittenMethod.getHolderType())
              .forEachClassResolutionResult(
                  holder -> {
                    DexClassAndMethod target = rewrittenMethod.lookupMemberOnClass(holder);
                    if (target != null) {
                      handleRewrittenMethodReference(rewrittenMethod, target);
                      seenMethod.set();
                    }
                  });
          if (seenMethod.isFalse()) {
            handleRewrittenMethodReference(rewrittenMethod, (DexClassAndMethod) null);
          }
        }
      }

      @Override
      public void registerInvokeInterface(DexMethod method) {
        MethodLookupResult lookupResult = graphLens().lookupInvokeInterface(method, getContext());
        assert lookupResult.getType().isInterface();
        handleInvokeWithDynamicDispatch(lookupResult);
      }

      @Override
      public void registerInvokeStatic(DexMethod method) {
        MethodLookupResult lookupResult = graphLens().lookupInvokeStatic(method, getContext());
        assert lookupResult.getType().isStatic();
        DexMethod rewrittenMethod = lookupResult.getReference();
        handleRewrittenMethodResolution(
            rewrittenMethod,
            appInfo().unsafeResolveMethodDueToDexFormat(rewrittenMethod),
            SingleResolutionResult::getResolutionPair);
      }

      @Override
      public void registerInvokeSuper(DexMethod method) {
        MethodLookupResult lookupResult = graphLens().lookupInvokeSuper(method, getContext());
        assert lookupResult.getType().isSuper();
        DexMethod rewrittenMethod = lookupResult.getReference();
        handleRewrittenMethodResolution(
            method,
            appInfo().unsafeResolveMethodDueToDexFormat(rewrittenMethod),
            result -> result.lookupInvokeSuperTarget(getContext().getHolder(), appView, appInfo()));
      }

      @Override
      public void registerInvokeVirtual(DexMethod method) {
        MethodLookupResult lookupResult = graphLens().lookupInvokeVirtual(method, getContext());
        assert lookupResult.getType().isVirtual();
        handleInvokeWithDynamicDispatch(lookupResult);
      }

      private void handleInvokeWithDynamicDispatch(MethodLookupResult lookupResult) {
        DexMethod method = lookupResult.getReference();
        if (method.getHolderType().isArrayType()) {
          assert lookupResult.getType().isVirtual();
          addType(method.getHolderType(), referencedFrom);
          return;
        }
        assert lookupResult.getType().isInterface() || lookupResult.getType().isVirtual();
        handleRewrittenMethodResolution(
            method,
            lookupResult.getType().isInterface()
                ? appInfo().resolveMethodOnInterfaceHolder(method)
                : appInfo().resolveMethodOnClassHolder(method),
            SingleResolutionResult::getResolutionPair);
      }

      private void handleRewrittenMethodResolution(
          DexMethod method,
          MethodResolutionResult resolutionResult,
          Function<SingleResolutionResult<?>, DexClassAndMethod> getResult) {
        BooleanBox seenSingleResult = new BooleanBox();
        resolutionResult.forEachMethodResolutionResult(
            result -> {
              if (result.isFailedResolution()) {
                result
                    .asFailedResolution()
                    .forEachFailureDependency(
                        type -> addType(type, referencedFrom),
                        methodCausingFailure ->
                            handleRewrittenMethodReference(method, methodCausingFailure));
                return;
              }
              seenSingleResult.set();
              handleRewrittenMethodReference(method, getResult.apply(result.asSingleResolution()));
            });
        if (seenSingleResult.isFalse()) {
          resolutionResult.forEachMethodResolutionResult(
              failingResult -> {
                assert failingResult.isFailedResolution();
                if (!failingResult.asFailedResolution().hasMethodsCausingError()) {
                  handleRewrittenMethodReference(method, (DexEncodedMethod) null);
                }
              });
        }
      }

      private void handleRewrittenMethodReference(
          DexMethod method, DexClassAndMethod resolvedMethod) {
        handleRewrittenMethodReference(
            method, resolvedMethod == null ? null : resolvedMethod.getDefinition());
      }

      @SuppressWarnings("ReferenceEquality")
      private void handleRewrittenMethodReference(
          DexMethod method, DexEncodedMethod resolvedMethod) {
        assert resolvedMethod == null
            || resolvedMethod.getReference().match(method)
            || DexClass.isSignaturePolymorphicMethod(resolvedMethod, factory);
        addType(method.getHolderType(), referencedFrom);
        addTypes(method.getParameters(), referencedFrom);
        addType(method.getReturnType(), referencedFrom);
        if (resolvedMethod != null) {
          if (isTargetType(resolvedMethod.getHolderType())) {
            if (resolvedMethod.getHolderType() != method.getHolderType()) {
              addType(resolvedMethod.getHolderType(), referencedFrom);
            }
            TracedMethodImpl tracedMethod = new TracedMethodImpl(resolvedMethod, referencedFrom);
            consumer.acceptMethod(tracedMethod, diagnostics);
            if (resolvedMethod.getAccessFlags().isVisibilityDependingOnPackage()) {
              consumer.acceptPackage(
                  Reference.packageFromString(resolvedMethod.getHolderType().getPackageName()),
                  diagnostics);
            }
          }
        } else {
          TracedMethodImpl tracedMethod = new TracedMethodImpl(method, referencedFrom);
          collectMissingMethod(tracedMethod);
          consumer.acceptMethod(tracedMethod, diagnostics);
        }
      }

      // Field references.

      @Override
      public void registerInitClass(DexType clazz) {
        DexType rewrittenClass = graphLens().lookupType(clazz);
        DexField clinitField = appView.initClassLens().getInitClassField(rewrittenClass);
        handleRewrittenFieldReference(clinitField);
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
        FieldLookupResult lookupResult = graphLens().lookupFieldResult(field);
        handleRewrittenFieldReference(lookupResult.getReference());
      }

      @SuppressWarnings("ReferenceEquality")
      private void handleRewrittenFieldReference(DexField field) {
        addType(field.getHolderType(), referencedFrom);
        addType(field.getType(), referencedFrom);

        FieldResolutionResult resolutionResult = appInfo().resolveField(field);
        resolutionResult.forEachFieldResolutionResult(
            singleResolutionResult -> {
              if (!singleResolutionResult.isSingleFieldResolutionResult()) {
                return;
              }
              DexClassAndField resolvedField = singleResolutionResult.getResolutionPair();
              if (isTargetType(resolvedField.getHolderType())) {
                if (resolvedField.getHolderType() != field.getHolderType()) {
                  addClass(resolvedField.getHolder(), referencedFrom);
                }
                TracedFieldImpl tracedField = new TracedFieldImpl(resolvedField, referencedFrom);
                consumer.acceptField(tracedField, diagnostics);
                if (resolvedField.getAccessFlags().isVisibilityDependingOnPackage()) {
                  consumer.acceptPackage(
                      Reference.packageFromString(resolvedField.getHolderType().getPackageName()),
                      diagnostics);
                }
              }
            });
        if (!resolutionResult.hasSuccessfulResolutionResult()) {
          TracedFieldImpl tracedField = new TracedFieldImpl(field, referencedFrom);
          collectMissingField(tracedField);
          consumer.acceptField(tracedField, diagnostics);
        }
      }

      // Type references.

      @Override
      public void registerTypeReference(DexType type) {
        addType(graphLens().lookupType(type), referencedFrom);
      }

      // Call sites.

      @Override
      public void registerCallSite(DexCallSite callSite) {
        super.registerCallSite(callSite);

        // For lambdas that implement an interface, also keep the interface method by simulating an
        // invoke to it from the current context.
        LambdaDescriptor descriptor =
            LambdaDescriptor.tryInfer(callSite, appView(), appInfo(), getContext());
        if (descriptor != null) {
          for (DexType interfaceType : descriptor.interfaces) {
            ClassResolutionResult classResolutionResult =
                appView.contextIndependentDefinitionForWithResolutionResult(interfaceType);
            if (classResolutionResult.hasClassResolutionResult()) {
              classResolutionResult.forEachClassResolutionResult(
                  interfaceDefinition -> {
                    DexEncodedMethod mainMethod =
                        interfaceDefinition.lookupMethod(descriptor.getMainMethod());
                    if (mainMethod != null) {
                      registerInvokeInterface(mainMethod.getReference());
                    }
                    for (DexProto bridgeProto : descriptor.bridges) {
                      DexEncodedMethod bridgeMethod =
                          interfaceDefinition.lookupMethod(bridgeProto, descriptor.name);
                      if (bridgeMethod != null) {
                        registerInvokeInterface(bridgeMethod.getReference());
                      }
                    }
                  });
            } else {
              TracedClassImpl tracedClass = new TracedClassImpl(interfaceType, referencedFrom);
              collectMissingClass(tracedClass);
              consumer.acceptType(tracedClass, diagnostics);
            }
          }
        }
      }
    }
  }
}
