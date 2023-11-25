// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecification;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.references.ArrayReference;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.PackageReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer;
import com.android.tools.r8.tracereferences.TraceReferencesKeepRules;
import com.android.tools.r8.tracereferences.Tracer;
import com.android.tools.r8.tracereferences.internal.TracedClassImpl;
import com.android.tools.r8.tracereferences.internal.TracedFieldImpl;
import com.android.tools.r8.tracereferences.internal.TracedMethodImpl;
import com.android.tools.r8.utils.ClassReferenceUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.NopDiagnosticsHandler;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.TypeReferenceUtils;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** Generates keep rules for L8 using trace references. */
public class DesugaredLibraryKeepRuleGenerator {

  private final AppView<AppInfoWithClassHierarchy> appView;
  private final InternalOptions options;

  public DesugaredLibraryKeepRuleGenerator(AppView<AppInfoWithClassHierarchy> appView) {
    this.appView = appView;
    this.options = appView.options();
  }

  public void runIfNecessary(Timing timing) {
    if (shouldRun()) {
      timing.begin("Desugared library keep rule generator");
      run();
      timing.end();
    }
  }

  private boolean shouldRun() {
    if (options.isDesugaredLibraryCompilation()
        || options.desugaredLibraryKeepRuleConsumer == null
        || !options.testing.enableExperimentalDesugaredLibraryKeepRuleGenerator) {
      return false;
    }
    return appView.getNamingLens().hasPrefixRewritingLogic()
        || options.machineDesugaredLibrarySpecification.hasEmulatedInterfaces();
  }

  private void run() {
    Tracer tracer = new Tracer(appView, new NopDiagnosticsHandler(), createTargetPredicate());
    tracer.run(createTraceReferencesConsumer());
  }

  private Predicate<DexType> createTargetPredicate() {
    MachineDesugaredLibrarySpecification desugaredLibrarySpecification =
        options.machineDesugaredLibrarySpecification;
    byte[] synthesizedLibraryClassesPackageDescriptorPrefix =
        DexString.encodeToMutf8(
            "L" + desugaredLibrarySpecification.getSynthesizedLibraryClassesPackagePrefix());
    NamingLens namingLens = appView.getNamingLens();
    return type ->
        namingLens.prefixRewrittenType(type) != null
            || desugaredLibrarySpecification.isEmulatedInterfaceRewrittenType(type)
            || desugaredLibrarySpecification.isCustomConversionRewrittenType(type)
            || type.getDescriptor().startsWith(synthesizedLibraryClassesPackageDescriptorPrefix);
  }

  private KeepRuleGenerator createTraceReferencesConsumer() {
    return new KeepRuleGenerator(appView);
  }

  private static class KeepRuleGenerator extends TraceReferencesConsumer.ForwardingConsumer {

    private final DexItemFactory factory;
    private final NamingLens namingLens;

    // May receive concurrent callbacks from trace references.
    private final Map<ClassReference, ClassReference> classRewritingCache =
        new ConcurrentHashMap<>();
    private final Map<FieldReference, FieldReference> fieldRewritingCache =
        new ConcurrentHashMap<>();
    private final Map<MethodReference, MethodReference> methodRewritingCache =
        new ConcurrentHashMap<>();

    // We currently cache the conversion from TypeReference to DexType, but not conversions from
    // ArrayReference to DexType, nor conversions from (formal types, return type) to DexProto.
    private final Map<TypeReference, DexType> typeConversionCache = new ConcurrentHashMap<>();

    private KeepRuleGenerator(AppView<? extends AppInfoWithClassHierarchy> appView) {
      super(
          TraceReferencesKeepRules.builder()
              .setOutputConsumer(appView.options().desugaredLibraryKeepRuleConsumer)
              .build());
      this.factory = appView.dexItemFactory();
      this.namingLens = appView.getNamingLens();
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public void acceptType(TracedClass tracedClass, DiagnosticsHandler handler) {
      ClassReference rewrittenReference = rewrittenWithLens(tracedClass.getReference());
      super.acceptType(
          rewrittenReference != tracedClass.getReference()
              ? new TracedClassImpl(
                  rewrittenReference,
                  tracedClass.getReferencedFromContext(),
                  tracedClass.getAccessFlags())
              : tracedClass,
          handler);
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public void acceptField(TracedField tracedField, DiagnosticsHandler handler) {
      FieldReference rewrittenReference = rewrittenWithLens(tracedField.getReference());
      super.acceptField(
          rewrittenReference != tracedField.getReference()
              ? new TracedFieldImpl(
                  rewrittenReference,
                  tracedField.getReferencedFromContext(),
                  tracedField.getAccessFlags())
              : tracedField,
          handler);
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public void acceptMethod(TracedMethod tracedMethod, DiagnosticsHandler handler) {
      MethodReference rewrittenReference = rewrittenWithLens(tracedMethod.getReference());
      super.acceptMethod(
          rewrittenReference != tracedMethod.getReference()
              ? new TracedMethodImpl(
                  rewrittenReference,
                  tracedMethod.getReferencedFromContext(),
                  tracedMethod.getAccessFlags())
              : tracedMethod,
          handler);
    }

    @Override
    public void acceptPackage(PackageReference pkg, DiagnosticsHandler handler) {
      super.acceptPackage(pkg, handler);
    }

    private DexType convertClassReference(ClassReference classReference) {
      return typeConversionCache.computeIfAbsent(
          classReference, key -> ClassReferenceUtils.toDexType(key.asClass(), factory));
    }

    private DexProto convertProto(List<TypeReference> formalTypes, TypeReference returnType) {
      return TypeReferenceUtils.toDexProto(
          formalTypes, returnType, factory, this::convertClassReference);
    }

    private DexType convertTypeReference(TypeReference typeReference) {
      return typeConversionCache.computeIfAbsent(
          typeReference,
          key -> TypeReferenceUtils.toDexType(key, factory, this::convertClassReference));
    }

    private ClassReference rewrittenWithLens(ClassReference classReference) {
      // First check if we have the result.
      ClassReference cached = classRewritingCache.get(classReference);
      if (cached != null) {
        return cached;
      }
      // Otherwise, convert to DexType, apply the naming lens, and cache the result.
      return internalRewrittenWithLens(classReference, convertClassReference(classReference));
    }

    private ClassReference rewrittenWithLens(ClassReference classReference, DexType type) {
      // First check if we have the result.
      ClassReference cached = classRewritingCache.get(classReference);
      if (cached != null) {
        return cached;
      }
      // Otherwise, apply the naming lens and cache the result.
      return internalRewrittenWithLens(classReference, type);
    }

    @SuppressWarnings("ReferenceEquality")
    private ClassReference internalRewrittenWithLens(ClassReference classReference, DexType type) {
      DexString rewrittenDescriptor = namingLens.lookupClassDescriptor(type);
      return addCacheEntry(
          classReference,
          rewrittenDescriptor != type.getDescriptor()
              ? Reference.classFromDescriptor(rewrittenDescriptor.toString())
              : classReference,
          classRewritingCache);
    }

    private FieldReference rewrittenWithLens(FieldReference fieldReference) {
      // First check if we have the result.
      FieldReference cached = fieldRewritingCache.get(fieldReference);
      if (cached != null) {
        return cached;
      }
      // Convert to DexField, using the typeConversionCache.
      DexField field =
          factory.createField(
              convertClassReference(fieldReference.getHolderClass()),
              convertTypeReference(fieldReference.getFieldType()),
              fieldReference.getFieldName());
      // Rewrite the field components, using the classRewritingCache.
      ClassReference rewrittenFieldHolder =
          rewrittenWithLens(fieldReference.getHolderClass(), field.getHolderType());
      String rewrittenFieldName = namingLens.lookupName(field).toString();
      TypeReference rewrittenFieldType =
          rewrittenWithLens(fieldReference.getFieldType(), field.getType());
      // Cache the result.
      FieldReference rewrittenFieldReference =
          Reference.field(rewrittenFieldHolder, rewrittenFieldName, rewrittenFieldType);
      return addCacheEntry(
          fieldReference,
          rewrittenFieldReference.equals(fieldReference) ? fieldReference : rewrittenFieldReference,
          fieldRewritingCache);
    }

    private MethodReference rewrittenWithLens(MethodReference methodReference) {
      // First check if we have the result.
      MethodReference cached = methodRewritingCache.get(methodReference);
      if (cached != null) {
        return cached;
      }
      // Convert to DexMethod, using the typeConversionCache.
      DexMethod method =
          factory.createMethod(
              convertClassReference(methodReference.getHolderClass()),
              convertProto(methodReference.getFormalTypes(), methodReference.getReturnType()),
              methodReference.getMethodName());
      // Rewrite the method components, using the classRewritingCache.
      ClassReference rewrittenMethodHolder =
          rewrittenWithLens(methodReference.getHolderClass(), method.getHolderType());
      String rewrittenMethodName = namingLens.lookupName(method).toString();
      Iterator<DexType> parameterIterator = method.getParameters().iterator();
      List<TypeReference> rewrittenMethodFormalTypes =
          ListUtils.mapOrElse(
              methodReference.getFormalTypes(),
              formalType -> rewrittenWithLens(formalType, parameterIterator.next()),
              methodReference.getFormalTypes());
      TypeReference rewrittenMethodReturnType =
          rewrittenWithLens(methodReference.getReturnType(), method.getReturnType());
      // Cache the result.
      MethodReference rewrittenMethodReference =
          Reference.method(
              rewrittenMethodHolder,
              rewrittenMethodName,
              rewrittenMethodFormalTypes,
              rewrittenMethodReturnType);
      return addCacheEntry(
          methodReference,
          rewrittenMethodReference.equals(methodReference)
              ? methodReference
              : rewrittenMethodReference,
          methodRewritingCache);
    }

    private TypeReference rewrittenWithLens(TypeReference typeReference, DexType type) {
      // The naming lens does not impact 'void' and primitives.
      if (typeReference == null || typeReference.isPrimitive()) {
        return typeReference;
      }
      // For array types we only cache the result for the base type.
      if (typeReference.isArray()) {
        ArrayReference arrayReference = typeReference.asArray();
        TypeReference baseType = arrayReference.getBaseType();
        if (baseType.isPrimitive()) {
          return typeReference;
        }
        assert baseType.isClass();
        ClassReference rewrittenBaseType = rewrittenWithLens(baseType.asClass());
        return rewrittenBaseType != baseType
            ? Reference.array(rewrittenBaseType, arrayReference.getDimensions())
            : typeReference;
      }
      // Rewrite the class type using classRewritingCache.
      assert typeReference.isClass();
      return rewrittenWithLens(typeReference.asClass(), type);
    }

    private <T> T addCacheEntry(T reference, T rewrittenReference, Map<T, T> rewritingCache) {
      rewritingCache.put(reference, rewrittenReference);
      return rewrittenReference;
    }
  }
}
