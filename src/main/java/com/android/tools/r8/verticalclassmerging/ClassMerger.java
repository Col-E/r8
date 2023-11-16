// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging;

import static com.android.tools.r8.dex.Constants.TEMPORARY_INSTANCE_INITIALIZER_PREFIX;
import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.ir.code.InvokeType.DIRECT;
import static com.android.tools.r8.ir.code.InvokeType.STATIC;
import static com.android.tools.r8.ir.code.InvokeType.VIRTUAL;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AccessControl;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DefaultInstanceInitializerCode;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.GenericSignature.ClassSignature.ClassSignatureBuilder;
import com.android.tools.r8.graph.GenericSignature.ClassTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FormalTypeParameter;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.GenericSignatureContextBuilder;
import com.android.tools.r8.graph.GenericSignatureContextBuilder.TypeParameterContext;
import com.android.tools.r8.graph.GenericSignatureCorrectnessHelper;
import com.android.tools.r8.graph.GenericSignaturePartialTypeArgumentApplier;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.CollectionUtils;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.android.tools.r8.utils.ObjectUtils;
import com.android.tools.r8.utils.collections.MutableBidirectionalManyToOneRepresentativeMap;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.Streams;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Stream;

class ClassMerger {

  private enum Rename {
    ALWAYS,
    IF_NEEDED,
    NEVER
  }

  private static final OptimizationFeedbackSimple feedback =
      OptimizationFeedback.getSimpleFeedback();

  private final AppView<AppInfoWithLiveness> appView;
  private final VerticalClassMergerGraphLens.Builder deferredRenamings;
  private final DexItemFactory dexItemFactory;
  private final VerticalClassMergerGraphLens.Builder lensBuilder;
  private final MutableBidirectionalManyToOneRepresentativeMap<DexType, DexType> mergedClasses;

  private final DexProgramClass source;
  private final DexProgramClass target;

  private final List<SynthesizedBridgeCode> synthesizedBridges = new ArrayList<>();

  private boolean abortMerge = false;

  ClassMerger(
      AppView<AppInfoWithLiveness> appView,
      VerticalClassMergerGraphLens.Builder lensBuilder,
      MutableBidirectionalManyToOneRepresentativeMap<DexType, DexType> mergedClasses,
      DexProgramClass source,
      DexProgramClass target) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    this.appView = appView;
    this.deferredRenamings = new VerticalClassMergerGraphLens.Builder(dexItemFactory);
    this.dexItemFactory = dexItemFactory;
    this.lensBuilder = lensBuilder;
    this.mergedClasses = mergedClasses;
    this.source = source;
    this.target = target;
  }

  public boolean merge() throws ExecutionException {
    // Merge the class [clazz] into [targetClass] by adding all methods to
    // targetClass that are not currently contained.
    // Step 1: Merge methods
    Set<Wrapper<DexMethod>> existingMethods = new HashSet<>();
    addAll(existingMethods, target.methods(), MethodSignatureEquivalence.get());

    Map<Wrapper<DexMethod>, DexEncodedMethod> directMethods = new HashMap<>();
    Map<Wrapper<DexMethod>, DexEncodedMethod> virtualMethods = new HashMap<>();

    Predicate<DexMethod> availableMethodSignatures =
        (method) -> {
          Wrapper<DexMethod> wrapped = MethodSignatureEquivalence.get().wrap(method);
          return !existingMethods.contains(wrapped)
              && !directMethods.containsKey(wrapped)
              && !virtualMethods.containsKey(wrapped);
        };

    source.forEachProgramDirectMethod(
        directMethod -> {
          DexEncodedMethod definition = directMethod.getDefinition();
          if (definition.isInstanceInitializer()) {
            DexEncodedMethod resultingConstructor =
                renameConstructor(
                    definition,
                    candidate ->
                        availableMethodSignatures.test(candidate)
                            && source.lookupVirtualMethod(candidate) == null);
            add(directMethods, resultingConstructor, MethodSignatureEquivalence.get());
            blockRedirectionOfSuperCalls(resultingConstructor.getReference());
          } else {
            DexEncodedMethod resultingDirectMethod =
                renameMethod(
                    definition,
                    availableMethodSignatures,
                    definition.isClassInitializer() ? Rename.NEVER : Rename.IF_NEEDED);
            add(directMethods, resultingDirectMethod, MethodSignatureEquivalence.get());
            deferredRenamings.map(
                directMethod.getReference(), resultingDirectMethod.getReference());
            deferredRenamings.recordMove(
                directMethod.getReference(), resultingDirectMethod.getReference());
            blockRedirectionOfSuperCalls(resultingDirectMethod.getReference());

            // Private methods in the parent class may be targeted with invoke-super if the two
            // classes are in the same nest. Ensure such calls are mapped to invoke-direct.
            if (definition.isInstance()
                && definition.isPrivate()
                && AccessControl.isMemberAccessible(directMethod, source, target, appView)
                    .isTrue()) {
              deferredRenamings.mapVirtualMethodToDirectInType(
                  directMethod.getReference(),
                  prototypeChanges ->
                      new MethodLookupResult(
                          resultingDirectMethod.getReference(), null, DIRECT, prototypeChanges),
                  target.getType());
            }
          }
        });

    for (DexEncodedMethod virtualMethod : source.virtualMethods()) {
      DexEncodedMethod shadowedBy = findMethodInTarget(virtualMethod);
      if (shadowedBy != null) {
        if (virtualMethod.isAbstract()) {
          // Remove abstract/interface methods that are shadowed. The identity mapping below is
          // needed to ensure we correctly fixup the mapping in case the signature refers to
          // merged classes.
          deferredRenamings
              .map(virtualMethod.getReference(), shadowedBy.getReference())
              .map(shadowedBy.getReference(), shadowedBy.getReference())
              .recordMerge(virtualMethod.getReference(), shadowedBy.getReference());

          // The override now corresponds to the method in the parent, so unset its synthetic flag
          // if the method in the parent is not synthetic.
          if (!virtualMethod.isSyntheticMethod() && shadowedBy.isSyntheticMethod()) {
            shadowedBy.accessFlags.demoteFromSynthetic();
          }
          continue;
        }
      } else {
        if (abortMerge) {
          // If [virtualMethod] does not resolve to a single method in [target], abort.
          assert restoreDebuggingState(
              Streams.concat(directMethods.values().stream(), virtualMethods.values().stream()));
          return false;
        }

        // The method is not shadowed. If it is abstract, we can simply move it to the subclass.
        // Non-abstract methods are handled below (they cannot simply be moved to the subclass as
        // a virtual method, because they might be the target of an invoke-super instruction).
        if (virtualMethod.isAbstract()) {
          // Abort if target is non-abstract and does not override the abstract method.
          if (!target.isAbstract()) {
            assert appView.options().testing.allowNonAbstractClassesWithAbstractMethods;
            abortMerge = true;
            return false;
          }
          // Update the holder of [virtualMethod] using renameMethod().
          DexEncodedMethod resultingVirtualMethod =
              renameMethod(virtualMethod, availableMethodSignatures, Rename.NEVER);
          resultingVirtualMethod.setLibraryMethodOverride(virtualMethod.isLibraryMethodOverride());
          deferredRenamings.map(
              virtualMethod.getReference(), resultingVirtualMethod.getReference());
          deferredRenamings.recordMove(
              virtualMethod.getReference(), resultingVirtualMethod.getReference());
          add(virtualMethods, resultingVirtualMethod, MethodSignatureEquivalence.get());
          continue;
        }
      }

      DexEncodedMethod resultingMethod;
      if (source.accessFlags.isInterface()) {
        // Moving a default interface method into its subtype. This method could be hit directly
        // via an invoke-super instruction from any of the transitive subtypes of this interface,
        // due to the way invoke-super works on default interface methods. In order to be able
        // to hit this method directly after the merge, we need to make it public, and find a
        // method name that does not collide with one in the hierarchy of this class.
        String resultingMethodBaseName =
            virtualMethod.getName().toString() + '$' + source.getTypeName().replace('.', '$');
        DexMethod resultingMethodReference =
            dexItemFactory.createMethod(
                target.getType(),
                virtualMethod.getProto().prependParameter(source.getType(), dexItemFactory),
                dexItemFactory.createGloballyFreshMemberString(resultingMethodBaseName));
        assert availableMethodSignatures.test(resultingMethodReference);
        resultingMethod =
            virtualMethod.toTypeSubstitutedMethodAsInlining(
                resultingMethodReference, dexItemFactory);
        makeStatic(resultingMethod);
      } else {
        // This virtual method could be called directly from a sub class via an invoke-super in-
        // struction. Therefore, we translate this virtual method into an instance method with a
        // unique name, such that relevant invoke-super instructions can be rewritten to target
        // this method directly.
        resultingMethod = renameMethod(virtualMethod, availableMethodSignatures, Rename.ALWAYS);
        if (appView.options().getProguardConfiguration().isAccessModificationAllowed()) {
          makePublic(resultingMethod);
        } else {
          makePrivate(resultingMethod);
        }
      }

      add(
          resultingMethod.belongsToDirectPool() ? directMethods : virtualMethods,
          resultingMethod,
          MethodSignatureEquivalence.get());

      // Record that invoke-super instructions in the target class should be redirected to the
      // newly created direct method.
      redirectSuperCallsInTarget(virtualMethod, resultingMethod);
      blockRedirectionOfSuperCalls(resultingMethod.getReference());

      if (shadowedBy == null) {
        // In addition to the newly added direct method, create a virtual method such that we do
        // not accidentally remove the method from the interface of this class.
        // Note that this method is added independently of whether it will actually be used. If
        // it turns out that the method is never used, it will be removed by the final round
        // of tree shaking.
        shadowedBy = buildBridgeMethod(virtualMethod, resultingMethod);
        deferredRenamings.recordCreationOfBridgeMethod(
            virtualMethod.getReference(), shadowedBy.getReference());
        add(virtualMethods, shadowedBy, MethodSignatureEquivalence.get());
      }

      // Copy over any keep info from the original virtual method.
      ProgramMethod programMethod = new ProgramMethod(target, shadowedBy);
      appView
          .getKeepInfo()
          .mutate(
              mutableKeepInfoCollection ->
                  mutableKeepInfoCollection.joinMethod(
                      programMethod,
                      info ->
                          info.merge(
                              mutableKeepInfoCollection
                                  .getMethodInfo(virtualMethod, source)
                                  .joiner())));

      deferredRenamings.map(virtualMethod.getReference(), shadowedBy.getReference());
      deferredRenamings.recordMove(
          virtualMethod.getReference(), resultingMethod.getReference(), resultingMethod.isStatic());
    }

    if (abortMerge) {
      assert restoreDebuggingState(
          Streams.concat(directMethods.values().stream(), virtualMethods.values().stream()));
      return false;
    }

    // Rewrite generic signatures before we merge a base with a generic signature.
    rewriteGenericSignatures(target, source, directMethods.values(), virtualMethods.values());

    // Convert out of DefaultInstanceInitializerCode, since this piece of code will require lens
    // code rewriting.
    target.forEachProgramInstanceInitializerMatching(
        method -> method.getCode().isDefaultInstanceInitializerCode(),
        method -> DefaultInstanceInitializerCode.uncanonicalizeCode(appView, method));

    // Step 2: Merge fields
    Set<DexString> existingFieldNames = new HashSet<>();
    for (DexEncodedField field : target.fields()) {
      existingFieldNames.add(field.getReference().name);
    }

    // In principle, we could allow multiple fields with the same name, and then only rename the
    // field in the end when we are done merging all the classes, if it it turns out that the two
    // fields ended up having the same type. This would not be too expensive, since we visit the
    // entire program using VerticalClassMerger.TreeFixer anyway.
    //
    // For now, we conservatively report that a signature is already taken if there is a field
    // with the same name. If minification is used with -overloadaggressively, this is solved
    // later anyway.
    Predicate<DexField> availableFieldSignatures =
        field -> !existingFieldNames.contains(field.name);

    DexEncodedField[] mergedInstanceFields =
        mergeFields(
            source.instanceFields(),
            target.instanceFields(),
            availableFieldSignatures,
            existingFieldNames);

    DexEncodedField[] mergedStaticFields =
        mergeFields(
            source.staticFields(),
            target.staticFields(),
            availableFieldSignatures,
            existingFieldNames);

    // Step 3: Merge interfaces
    Set<DexType> interfaces = mergeArrays(target.interfaces.values, source.interfaces.values);
    // Now destructively update the class.
    // Step 1: Update supertype or fix interfaces.
    if (source.isInterface()) {
      interfaces.remove(source.type);
    } else {
      assert !target.isInterface();
      target.superType = source.superType;
    }
    target.interfaces =
        interfaces.isEmpty()
            ? DexTypeList.empty()
            : new DexTypeList(interfaces.toArray(DexType.EMPTY_ARRAY));
    // Step 2: ensure -if rules cannot target the members that were merged into the target class.
    directMethods.values().forEach(feedback::markMethodCannotBeKept);
    virtualMethods.values().forEach(feedback::markMethodCannotBeKept);
    for (int i = 0; i < source.instanceFields().size(); i++) {
      feedback.markFieldCannotBeKept(mergedInstanceFields[i]);
    }
    for (int i = 0; i < source.staticFields().size(); i++) {
      feedback.markFieldCannotBeKept(mergedStaticFields[i]);
    }
    // Step 3: replace fields and methods.
    target.addDirectMethods(directMethods.values());
    target.addVirtualMethods(virtualMethods.values());
    target.setInstanceFields(mergedInstanceFields);
    target.setStaticFields(mergedStaticFields);
    // Step 4: Clear the members of the source class since they have now been moved to the target.
    source.getMethodCollection().clearDirectMethods();
    source.getMethodCollection().clearVirtualMethods();
    source.clearInstanceFields();
    source.clearStaticFields();
    // Step 5: Record merging.
    assert !abortMerge;
    assert GenericSignatureCorrectnessHelper.createForVerification(
            appView, GenericSignatureContextBuilder.createForSingleClass(appView, target))
        .evaluateSignaturesForClass(target)
        .isValid();
    return true;
  }

  /**
   * The rewriting of generic signatures is pretty simple, but require some bookkeeping. We take the
   * arguments to the base type:
   *
   * <pre>
   *   class Sub<X> extends Base<X, String>
   * </pre>
   *
   * <p>for
   *
   * <pre>
   *   class Base<T,R> extends OtherBase<T> implements I<R> {
   *     T t() { ... };
   *   }
   * </pre>
   *
   * <p>and substitute T -> X and R -> String
   */
  private void rewriteGenericSignatures(
      DexProgramClass target,
      DexProgramClass source,
      Collection<DexEncodedMethod> directMethods,
      Collection<DexEncodedMethod> virtualMethods) {
    ClassSignature targetSignature = target.getClassSignature();
    if (targetSignature.hasNoSignature()) {
      // Null out all source signatures that is moved, but do not clear out the class since this
      // could be referred to by other generic signatures.
      // TODO(b/147504070): If merging classes with enclosing/innerclasses, this needs to be
      //  reconsidered.
      directMethods.forEach(DexEncodedMethod::clearGenericSignature);
      virtualMethods.forEach(DexEncodedMethod::clearGenericSignature);
      source.fields().forEach(DexEncodedMember::clearGenericSignature);
      return;
    }
    GenericSignaturePartialTypeArgumentApplier classApplier =
        getGenericSignatureArgumentApplier(target, source);
    if (classApplier == null) {
      target.clearClassSignature();
      target.members().forEach(DexEncodedMember::clearGenericSignature);
      return;
    }
    // We could generate a substitution map.
    ClassSignature rewrittenSource = classApplier.visitClassSignature(source.getClassSignature());
    // The variables in the class signature is now rewritten to use the targets argument.
    ClassSignatureBuilder builder = ClassSignature.builder();
    builder.addFormalTypeParameters(targetSignature.getFormalTypeParameters());
    if (!source.isInterface()) {
      if (rewrittenSource.hasSignature()) {
        builder.setSuperClassSignature(rewrittenSource.getSuperClassSignatureOrNull());
      } else {
        builder.setSuperClassSignature(new ClassTypeSignature(source.superType));
      }
    } else {
      builder.setSuperClassSignature(targetSignature.getSuperClassSignatureOrNull());
    }
    // Compute the seen set for interfaces to add. This is similar to the merging of interfaces
    // but allow us to maintain the type arguments.
    Set<DexType> seenInterfaces = new HashSet<>();
    if (source.isInterface()) {
      seenInterfaces.add(source.type);
    }
    for (ClassTypeSignature iFace : targetSignature.getSuperInterfaceSignatures()) {
      if (seenInterfaces.add(iFace.type())) {
        builder.addSuperInterfaceSignature(iFace);
      }
    }
    if (rewrittenSource.hasSignature()) {
      for (ClassTypeSignature iFace : rewrittenSource.getSuperInterfaceSignatures()) {
        if (!seenInterfaces.contains(iFace.type())) {
          builder.addSuperInterfaceSignature(iFace);
        }
      }
    } else {
      // Synthesize raw uses of interfaces to align with the actual class
      for (DexType iFace : source.interfaces) {
        if (!seenInterfaces.contains(iFace)) {
          builder.addSuperInterfaceSignature(new ClassTypeSignature(iFace));
        }
      }
    }
    target.setClassSignature(builder.build(dexItemFactory));

    // Go through all type-variable references for members and update them.
    CollectionUtils.forEach(
        method -> {
          MethodTypeSignature methodSignature = method.getGenericSignature();
          if (methodSignature.hasNoSignature()) {
            return;
          }
          method.setGenericSignature(
              classApplier
                  .buildForMethod(methodSignature.getFormalTypeParameters())
                  .visitMethodSignature(methodSignature));
        },
        directMethods,
        virtualMethods);

    source.forEachField(
        field -> {
          if (field.getGenericSignature().hasNoSignature()) {
            return;
          }
          field.setGenericSignature(
              classApplier.visitFieldTypeSignature(field.getGenericSignature()));
        });
  }

  private GenericSignaturePartialTypeArgumentApplier getGenericSignatureArgumentApplier(
      DexProgramClass target, DexProgramClass source) {
    assert target.getClassSignature().hasSignature();
    // We can assert proper structure below because the generic signature validator has run
    // before and pruned invalid signatures.
    List<FieldTypeSignature> genericArgumentsToSuperType =
        target.getClassSignature().getGenericArgumentsToSuperType(source.type, dexItemFactory);
    if (genericArgumentsToSuperType == null) {
      assert false : "Type should be present in generic signature";
      return null;
    }
    Map<String, FieldTypeSignature> substitutionMap = new HashMap<>();
    List<FormalTypeParameter> formals = source.getClassSignature().getFormalTypeParameters();
    if (genericArgumentsToSuperType.size() != formals.size()) {
      if (!genericArgumentsToSuperType.isEmpty()) {
        assert false : "Invalid argument count to formals";
        return null;
      }
    } else {
      for (int i = 0; i < formals.size(); i++) {
        // It is OK to override a generic type variable so we just use put.
        substitutionMap.put(formals.get(i).getName(), genericArgumentsToSuperType.get(i));
      }
    }
    return GenericSignaturePartialTypeArgumentApplier.build(
        appView,
        TypeParameterContext.empty().addPrunedSubstitutions(substitutionMap),
        (type1, type2) -> true,
        type -> true);
  }

  private boolean restoreDebuggingState(Stream<DexEncodedMethod> toBeDiscarded) {
    toBeDiscarded.forEach(
        method -> {
          assert !method.isObsolete();
          method.setObsolete();
        });
    source.forEachMethod(
        method -> {
          if (method.isObsolete()) {
            method.unsetObsolete();
          }
        });
    assert Streams.concat(Streams.stream(source.methods()), Streams.stream(target.methods()))
        .allMatch(method -> !method.isObsolete());
    return true;
  }

  public VerticalClassMergerGraphLens.Builder getRenamings() {
    return deferredRenamings;
  }

  public List<SynthesizedBridgeCode> getSynthesizedBridges() {
    return synthesizedBridges;
  }

  private void redirectSuperCallsInTarget(DexEncodedMethod oldTarget, DexEncodedMethod newTarget) {
    DexMethod oldTargetReference = oldTarget.getReference();
    DexMethod newTargetReference = newTarget.getReference();
    InvokeType newTargetType = newTarget.isNonPrivateVirtualMethod() ? VIRTUAL : DIRECT;
    if (source.accessFlags.isInterface()) {
      // If we merge a default interface method from interface I to its subtype C, then we need
      // to rewrite invocations on the form "invoke-super I.m()" to "invoke-direct C.m$I()".
      //
      // Unlike when we merge a class into its subclass (the else-branch below), we should *not*
      // rewrite any invocations on the form "invoke-super J.m()" to "invoke-direct C.m$I()",
      // if I has a supertype J. This is due to the fact that invoke-super instructions that
      // resolve to a method on an interface never hit an implementation below that interface.
      deferredRenamings.mapVirtualMethodToDirectInType(
          oldTargetReference,
          prototypeChanges ->
              new MethodLookupResult(newTargetReference, null, STATIC, prototypeChanges),
          target.type);
    } else {
      // If we merge class B into class C, and class C contains an invocation super.m(), then it
      // is insufficient to rewrite "invoke-super B.m()" to "invoke-{direct,virtual} C.m$B()" (the
      // method C.m$B denotes the direct/virtual method that has been created in C for B.m). In
      // particular, there might be an instruction "invoke-super A.m()" in C that resolves to B.m
      // at runtime (A is a superclass of B), which also needs to be rewritten to
      // "invoke-{direct,virtual} C.m$B()".
      //
      // We handle this by adding a mapping for [target] and all of its supertypes.
      DexProgramClass holder = target;
      while (holder != null && holder.isProgramClass()) {
        DexMethod signatureInHolder = oldTargetReference.withHolder(holder, dexItemFactory);
        // Only rewrite the invoke-super call if it does not lead to a NoSuchMethodError.
        boolean resolutionSucceeds =
            holder.lookupVirtualMethod(signatureInHolder) != null
                || appView.appInfo().lookupSuperTarget(signatureInHolder, holder, appView) != null;
        if (resolutionSucceeds) {
          deferredRenamings.mapVirtualMethodToDirectInType(
              signatureInHolder,
              prototypeChanges ->
                  new MethodLookupResult(newTargetReference, null, newTargetType, prototypeChanges),
              target.type);
        } else {
          break;
        }

        // Consider that A gets merged into B and B's subclass C gets merged into D. Instructions
        // on the form "invoke-super {B,C,D}.m()" in D are changed into "invoke-direct D.m$C()" by
        // the code above. However, instructions on the form "invoke-super A.m()" should also be
        // changed into "invoke-direct D.m$C()". This is achieved by also considering the classes
        // that have been merged into [holder].
        Set<DexType> mergedTypes = mergedClasses.getKeys(holder.getType());
        for (DexType type : mergedTypes) {
          DexMethod signatureInType = oldTargetReference.withHolder(type, dexItemFactory);
          // Resolution would have succeeded if the method used to be in [type], or if one of
          // its super classes declared the method.
          boolean resolutionSucceededBeforeMerge =
              lensBuilder.hasMappingForSignatureInContext(holder, signatureInType)
                  || appView.appInfo().lookupSuperTarget(signatureInHolder, holder, appView)
                      != null;
          if (resolutionSucceededBeforeMerge) {
            deferredRenamings.mapVirtualMethodToDirectInType(
                signatureInType,
                prototypeChanges ->
                    new MethodLookupResult(
                        newTargetReference, null, newTargetType, prototypeChanges),
                target.type);
          }
        }
        holder =
            holder.hasSuperType()
                ? asProgramClassOrNull(appView.definitionFor(holder.getSuperType()))
                : null;
      }
    }
  }

  private void blockRedirectionOfSuperCalls(DexMethod method) {
    // We are merging a class B into C. The methods from B are being moved into C, and then we
    // subsequently rewrite the invoke-super instructions in C that hit a method in B, such that
    // they use an invoke-direct instruction instead. In this process, we need to avoid rewriting
    // the invoke-super instructions that originally was in the superclass B.
    //
    // Example:
    //   class A {
    //     public void m() {}
    //   }
    //   class B extends A {
    //     public void m() { super.m(); } <- invoke must not be rewritten to invoke-direct
    //                                       (this would lead to an infinite loop)
    //   }
    //   class C extends B {
    //     public void m() { super.m(); } <- invoke needs to be rewritten to invoke-direct
    //   }
    deferredRenamings.markMethodAsMerged(method);
  }

  private DexEncodedMethod buildBridgeMethod(
      DexEncodedMethod method, DexEncodedMethod invocationTarget) {
    DexMethod newMethod = method.getReference().withHolder(target, dexItemFactory);
    MethodAccessFlags accessFlags = method.getAccessFlags().copy();
    accessFlags.setBridge();
    accessFlags.setSynthetic();
    accessFlags.unsetAbstract();

    assert invocationTarget.isStatic()
        || invocationTarget.isNonPrivateVirtualMethod()
        || invocationTarget.isNonStaticPrivateMethod();
    SynthesizedBridgeCode code =
        new SynthesizedBridgeCode(
            newMethod,
            invocationTarget.getReference(),
            invocationTarget.isStatic()
                ? STATIC
                : (invocationTarget.isNonPrivateVirtualMethod() ? VIRTUAL : DIRECT),
            target.isInterface());

    // Add the bridge to the list of synthesized bridges such that the method signatures will
    // be updated by the end of vertical class merging.
    synthesizedBridges.add(code);

    CfVersion classFileVersion = method.hasClassFileVersion() ? method.getClassFileVersion() : null;
    DexEncodedMethod bridge =
        DexEncodedMethod.syntheticBuilder()
            .setMethod(newMethod)
            .setAccessFlags(accessFlags)
            .setCode(code)
            .setClassFileVersion(classFileVersion)
            .setApiLevelForDefinition(method.getApiLevelForDefinition())
            .setApiLevelForCode(method.getApiLevelForDefinition())
            .setIsLibraryMethodOverride(method.isLibraryMethodOverride())
            .setGenericSignature(method.getGenericSignature())
            .build();
    // The bridge is now the public method serving the role of the original method, and should
    // reflect that this method was publicized.
    assert !method.getAccessFlags().isPromotedToPublic()
        || bridge.getAccessFlags().isPromotedToPublic();
    return bridge;
  }

  // Returns the method that shadows the given method, or null if method is not shadowed.
  private DexEncodedMethod findMethodInTarget(DexEncodedMethod method) {
    SingleResolutionResult<?> resolutionResult =
        appView.appInfo().resolveMethodOnLegacy(target, method.getReference()).asSingleResolution();
    if (resolutionResult == null) {
      // May happen in case of missing classes, or if multiple implementations were found.
      abortMerge = true;
      return null;
    }
    DexEncodedMethod actual = resolutionResult.getResolvedMethod();
    if (ObjectUtils.notIdentical(actual, method)) {
      assert actual.isVirtualMethod() == method.isVirtualMethod();
      return actual;
    }
    // The method is not actually overridden. This means that we will move `method` to the
    // subtype. If `method` is abstract, then so should the subtype be.
    return null;
  }

  private <D extends DexEncodedMember<D, R>, R extends DexMember<D, R>> void add(
      Map<Wrapper<R>, D> map, D item, Equivalence<R> equivalence) {
    map.put(equivalence.wrap(item.getReference()), item);
  }

  private <D extends DexEncodedMember<D, R>, R extends DexMember<D, R>> void addAll(
      Collection<Wrapper<R>> collection, Iterable<D> items, Equivalence<R> equivalence) {
    for (D item : items) {
      collection.add(equivalence.wrap(item.getReference()));
    }
  }

  private <T> Set<T> mergeArrays(T[] one, T[] other) {
    Set<T> merged = new LinkedHashSet<>();
    Collections.addAll(merged, one);
    Collections.addAll(merged, other);
    return merged;
  }

  private DexEncodedField[] mergeFields(
      Collection<DexEncodedField> sourceFields,
      Collection<DexEncodedField> targetFields,
      Predicate<DexField> availableFieldSignatures,
      Set<DexString> existingFieldNames) {
    DexEncodedField[] result = new DexEncodedField[sourceFields.size() + targetFields.size()];
    // Add fields from source
    int i = 0;
    for (DexEncodedField field : sourceFields) {
      DexEncodedField resultingField = renameFieldIfNeeded(field, availableFieldSignatures);
      existingFieldNames.add(resultingField.getReference().name);
      deferredRenamings.map(field.getReference(), resultingField.getReference());
      result[i] = resultingField;
      i++;
    }
    // Add fields from target.
    for (DexEncodedField field : targetFields) {
      result[i] = field;
      i++;
    }
    return result;
  }

  // Note that names returned by this function are not necessarily unique. Clients should
  // repeatedly try to generate a fresh name until it is unique.
  private DexString getFreshName(String nameString, int index, DexType holder) {
    String freshName = nameString + "$" + holder.toSourceString().replace('.', '$');
    if (index > 1) {
      freshName += index;
    }
    return dexItemFactory.createString(freshName);
  }

  private DexEncodedMethod renameConstructor(
      DexEncodedMethod method, Predicate<DexMethod> availableMethodSignatures) {
    assert method.isInstanceInitializer();
    DexType oldHolder = method.getHolderType();

    DexMethod newSignature;
    int count = 1;
    do {
      DexString newName = getFreshName(TEMPORARY_INSTANCE_INITIALIZER_PREFIX, count, oldHolder);
      newSignature = dexItemFactory.createMethod(target.getType(), method.getProto(), newName);
      count++;
    } while (!availableMethodSignatures.test(newSignature));

    DexEncodedMethod result =
        method.toTypeSubstitutedMethodAsInlining(newSignature, dexItemFactory);
    result.getMutableOptimizationInfo().markForceInline();
    deferredRenamings.map(method.getReference(), result.getReference());
    deferredRenamings.recordMove(method.getReference(), result.getReference());
    // Renamed constructors turn into ordinary private functions. They can be private, as
    // they are only references from their direct subclass, which they were merged into.
    result.getAccessFlags().unsetConstructor();
    makePrivate(result);
    return result;
  }

  private DexEncodedMethod renameMethod(
      DexEncodedMethod method, Predicate<DexMethod> availableMethodSignatures, Rename strategy) {
    return renameMethod(method, availableMethodSignatures, strategy, method.getProto());
  }

  private DexEncodedMethod renameMethod(
      DexEncodedMethod method,
      Predicate<DexMethod> availableMethodSignatures,
      Rename strategy,
      DexProto newProto) {
    // We cannot handle renaming static initializers yet and constructors should have been
    // renamed already.
    assert !method.accessFlags.isConstructor() || strategy == Rename.NEVER;
    DexString oldName = method.getName();
    DexType oldHolder = method.getHolderType();

    DexMethod newSignature;
    switch (strategy) {
      case IF_NEEDED:
        newSignature = dexItemFactory.createMethod(target.getType(), newProto, oldName);
        if (availableMethodSignatures.test(newSignature)) {
          break;
        }
        // Fall-through to ALWAYS so that we assign a new name.

      case ALWAYS:
        int count = 1;
        do {
          DexString newName = getFreshName(oldName.toSourceString(), count, oldHolder);
          newSignature = dexItemFactory.createMethod(target.getType(), newProto, newName);
          count++;
        } while (!availableMethodSignatures.test(newSignature));
        break;

      case NEVER:
        newSignature = dexItemFactory.createMethod(target.getType(), newProto, oldName);
        assert availableMethodSignatures.test(newSignature);
        break;

      default:
        throw new Unreachable();
    }

    return method.toTypeSubstitutedMethodAsInlining(newSignature, dexItemFactory);
  }

  private DexEncodedField renameFieldIfNeeded(
      DexEncodedField field, Predicate<DexField> availableFieldSignatures) {
    DexString oldName = field.getName();
    DexType oldHolder = field.getHolderType();

    DexField newSignature = dexItemFactory.createField(target.getType(), field.getType(), oldName);
    if (!availableFieldSignatures.test(newSignature)) {
      int count = 1;
      do {
        DexString newName = getFreshName(oldName.toSourceString(), count, oldHolder);
        newSignature = dexItemFactory.createField(target.getType(), field.getType(), newName);
        count++;
      } while (!availableFieldSignatures.test(newSignature));
    }

    return field.toTypeSubstitutedField(appView, newSignature);
  }

  private static void makePrivate(DexEncodedMethod method) {
    MethodAccessFlags accessFlags = method.getAccessFlags();
    assert !accessFlags.isAbstract();
    accessFlags.unsetPublic();
    accessFlags.unsetProtected();
    accessFlags.setPrivate();
  }

  private static void makePublic(DexEncodedMethod method) {
    MethodAccessFlags accessFlags = method.getAccessFlags();
    assert !accessFlags.isAbstract();
    accessFlags.unsetPrivate();
    accessFlags.unsetProtected();
    accessFlags.setPublic();
  }

  private void makeStatic(DexEncodedMethod method) {
    method.getAccessFlags().setStatic();
    if (!method.getCode().isCfCode()) {
      // Due to member rebinding we may have inserted bridge methods with synthesized code.
      // Currently, there is no easy way to make such code static.
      abortMerge = true;
    }
  }
}
