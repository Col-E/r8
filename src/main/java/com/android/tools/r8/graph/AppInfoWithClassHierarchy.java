// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import static com.android.tools.r8.utils.TraversalContinuation.BREAK;
import static com.android.tools.r8.utils.TraversalContinuation.CONTINUE;

import com.android.tools.r8.features.ClassToFeatureSplitMap;
import com.android.tools.r8.graph.FieldResolutionResult.SuccessfulFieldResolutionResult;
import com.android.tools.r8.graph.ResolutionResult.ArrayCloneMethodResult;
import com.android.tools.r8.graph.ResolutionResult.ClassNotFoundResult;
import com.android.tools.r8.graph.ResolutionResult.IllegalAccessOrNoSuchMethodResult;
import com.android.tools.r8.graph.ResolutionResult.IncompatibleClassResult;
import com.android.tools.r8.graph.ResolutionResult.NoSuchMethodResult;
import com.android.tools.r8.graph.ResolutionResult.SingleResolutionResult;
import com.android.tools.r8.ir.desugar.LambdaDescriptor;
import com.android.tools.r8.shaking.MainDexClasses;
import com.android.tools.r8.synthesis.CommittedItems;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/* Specific subclass of AppInfo designed to support desugaring in D8. Desugaring requires a
 * minimal amount of knowledge in the overall program, provided through classpath. Basic
 * features are present, such as static and super look-ups, or isSubtype.
 */
public class AppInfoWithClassHierarchy extends AppInfo {

  private static final CreateDesugaringViewOnAppInfo WITNESS = new CreateDesugaringViewOnAppInfo();

  static class CreateDesugaringViewOnAppInfo {
    private CreateDesugaringViewOnAppInfo() {}
  }

  public static AppInfoWithClassHierarchy createInitialAppInfoWithClassHierarchy(
      DexApplication application,
      ClassToFeatureSplitMap classToFeatureSplitMap,
      MainDexClasses mainDexClasses) {
    return new AppInfoWithClassHierarchy(
        SyntheticItems.createInitialSyntheticItems(application),
        classToFeatureSplitMap,
        mainDexClasses);
  }

  private final ClassToFeatureSplitMap classToFeatureSplitMap;

  // For AppInfoWithLiveness subclass.
  protected AppInfoWithClassHierarchy(
      CommittedItems committedItems,
      ClassToFeatureSplitMap classToFeatureSplitMap,
      MainDexClasses mainDexClasses) {
    super(committedItems, mainDexClasses);
    this.classToFeatureSplitMap = classToFeatureSplitMap;
  }

  // For desugaring.
  private AppInfoWithClassHierarchy(CreateDesugaringViewOnAppInfo witness, AppInfo appInfo) {
    super(witness, appInfo);
    this.classToFeatureSplitMap = ClassToFeatureSplitMap.createEmptyClassToFeatureSplitMap();
  }

  public static AppInfoWithClassHierarchy createForDesugaring(AppInfo appInfo) {
    assert !appInfo.hasClassHierarchy();
    return new AppInfoWithClassHierarchy(WITNESS, appInfo);
  }

  public final AppInfoWithClassHierarchy rebuildWithClassHierarchy(CommittedItems commit) {
    return new AppInfoWithClassHierarchy(commit, getClassToFeatureSplitMap(), getMainDexClasses());
  }

  public AppInfoWithClassHierarchy rebuildWithClassHierarchy(
      Function<DexApplication, DexApplication> fn) {
    return new AppInfoWithClassHierarchy(
        getSyntheticItems().commit(fn.apply(app())),
        getClassToFeatureSplitMap(),
        getMainDexClasses());
  }

  public ClassToFeatureSplitMap getClassToFeatureSplitMap() {
    return classToFeatureSplitMap;
  }

  @Override
  public boolean hasClassHierarchy() {
    assert checkIfObsolete();
    return true;
  }

  @Override
  public AppInfoWithClassHierarchy withClassHierarchy() {
    assert checkIfObsolete();
    return this;
  }

  /**
   * Primitive traversal over all supertypes of a given type.
   *
   * <p>No order is guaranteed for the traversal, but a given type will be visited at most once. The
   * given type is *not* visited. The function indicates if traversal should continue or break. The
   * result of the traversal is BREAK iff the function returned BREAK.
   */
  public TraversalContinuation traverseSuperTypes(
      final DexClass clazz, BiFunction<DexType, Boolean, TraversalContinuation> fn) {
    // We do an initial zero-allocation pass over the class super chain as it does not require a
    // worklist/seen-set. Only if the traversal is not aborted and there actually are interfaces,
    // do we continue traversal over the interface types. This is assuming that the second pass
    // over the super chain is less expensive than the eager allocation of the worklist.
    int interfaceCount = 0;
    {
      DexClass currentClass = clazz;
      while (currentClass != null) {
        interfaceCount += currentClass.interfaces.values.length;
        if (currentClass.superType == null) {
          break;
        }
        TraversalContinuation stepResult = fn.apply(currentClass.superType, false);
        if (stepResult.shouldBreak()) {
          return stepResult;
        }
        currentClass = definitionFor(currentClass.superType);
      }
    }
    if (interfaceCount == 0) {
      return CONTINUE;
    }
    // Interfaces exist, create a worklist and seen set to ensure single visits.
    Set<DexType> seen = Sets.newIdentityHashSet();
    Deque<DexType> worklist = new ArrayDeque<>();
    // Populate the worklist with the direct interfaces of the super chain.
    {
      DexClass currentClass = clazz;
      while (currentClass != null) {
        for (DexType iface : currentClass.interfaces.values) {
          if (seen.add(iface)) {
            TraversalContinuation stepResult = fn.apply(iface, true);
            if (stepResult.shouldBreak()) {
              return stepResult;
            }
            worklist.addLast(iface);
          }
        }
        if (currentClass.superType == null) {
          break;
        }
        currentClass = definitionFor(currentClass.superType);
      }
    }
    // Iterate all interfaces.
    while (!worklist.isEmpty()) {
      DexType type = worklist.removeFirst();
      DexClass definition = definitionFor(type);
      if (definition != null) {
        for (DexType iface : definition.interfaces.values) {
          if (seen.add(iface)) {
            TraversalContinuation stepResult = fn.apply(iface, true);
            if (stepResult.shouldBreak()) {
              return stepResult;
            }
            worklist.addLast(iface);
          }
        }
      }
    }
    return CONTINUE;
  }

  /**
   * Iterate each super type of class.
   *
   * <p>Same as traverseSuperTypes, but unconditionally visits all.
   */
  public void forEachSuperType(final DexClass clazz, BiConsumer<DexType, Boolean> fn) {
    traverseSuperTypes(
        clazz,
        (type, isInterface) -> {
          fn.accept(type, isInterface);
          return CONTINUE;
        });
  }

  public boolean isSubtype(DexType subtype, DexType supertype) {
    assert subtype != null;
    assert supertype != null;
    assert subtype.isClassType();
    assert supertype.isClassType();
    return subtype == supertype || isStrictSubtypeOf(subtype, supertype);
  }

  public boolean isStrictSubtypeOf(DexType subtype, DexType supertype) {
    assert subtype != null;
    assert supertype != null;
    assert subtype.isClassType();
    assert supertype.isClassType();
    if (subtype == supertype) {
      return false;
    }
    // Treat object special: it is always the supertype even for broken hierarchies.
    if (subtype == dexItemFactory().objectType) {
      return false;
    }
    if (supertype == dexItemFactory().objectType) {
      return true;
    }
    if (!subtype.isClassType() || !supertype.isClassType()) {
      return false;
    }
    DexClass clazz = definitionFor(subtype);
    if (clazz == null) {
      return false;
    }
    // TODO(b/123506120): Report missing types when the predicate is inconclusive.
    return traverseSuperTypes(
            clazz,
            (type, isInterface) -> type == supertype ? BREAK : CONTINUE)
        .shouldBreak();
  }

  public boolean inSameHierarchy(DexType type, DexType other) {
    assert type.isClassType();
    assert other.isClassType();
    return isSubtype(type, other) || isSubtype(other, type);
  }

  public boolean inDifferentHierarchy(DexType type1, DexType type2) {
    return !inSameHierarchy(type1, type2);
  }

  public boolean isMissingOrHasMissingSuperType(DexType type) {
    DexClass clazz = definitionFor(type);
    return clazz == null || clazz.hasMissingSuperType(this);
  }

  /** Collect all interfaces that this type directly or indirectly implements. */
  public Set<DexType> implementedInterfaces(DexType type) {
    assert type.isClassType();
    DexClass clazz = definitionFor(type);
    if (clazz == null) {
      return Collections.emptySet();
    }

    // Fast path for a type below object with no interfaces.
    if (clazz.superType == dexItemFactory().objectType && clazz.interfaces.isEmpty()) {
      return clazz.isInterface() ? Collections.singleton(type) : Collections.emptySet();
    }

    // Slow path traverses the full super type hierarchy.
    Set<DexType> interfaces = Sets.newIdentityHashSet();
    if (clazz.isInterface()) {
      interfaces.add(type);
    }
    forEachSuperType(clazz, (dexType, isInterface) -> {
      if (isInterface) {
        interfaces.add(dexType);
      }
    });
    return interfaces;
  }

  public boolean isExternalizable(DexType type) {
    return isSubtype(type, dexItemFactory().externalizableType);
  }

  public boolean isSerializable(DexType type) {
    return isSubtype(type, dexItemFactory().serializableType);
  }

  public List<DexProgramClass> computeProgramClassRelationChain(
      DexProgramClass subClass, DexProgramClass superClass) {
    assert isSubtype(subClass.type, superClass.type);
    assert !subClass.isInterface();
    if (!superClass.isInterface()) {
      return computeChainInClassHierarchy(subClass, superClass.type);
    }
    // If the super type is an interface we first compute the program chain upwards, and in a
    // top-down order check if the interface is a super-type to the class. Computing it this way
    // guarantees to find the instantiated program-classes of the longest chain.
    List<DexProgramClass> relationChain =
        computeChainInClassHierarchy(subClass, dexItemFactory().objectType);
    WorkList<DexType> interfaceWorklist = WorkList.newIdentityWorkList();
    for (int i = relationChain.size() - 1; i >= 0; i--) {
      DexProgramClass clazz = relationChain.get(i);
      if (isInterfaceInSuperTypes(clazz, superClass.type, interfaceWorklist)) {
        return relationChain.subList(0, i + 1);
      }
    }
    return Collections.emptyList();
  }

  private boolean isInterfaceInSuperTypes(
      DexProgramClass clazz, DexType ifaceToFind, WorkList<DexType> workList) {
    workList.addIfNotSeen(clazz.allImmediateSupertypes());
    while (workList.hasNext()) {
      DexType superType = workList.next();
      if (superType == ifaceToFind) {
        return true;
      }
      DexClass superClass = definitionFor(superType);
      if (superClass != null) {
        workList.addIfNotSeen(superClass.allImmediateSupertypes());
      }
    }
    return false;
  }

  private List<DexProgramClass> computeChainInClassHierarchy(
      DexProgramClass subClass, DexType superType) {
    assert isSubtype(subClass.type, superType);
    assert !subClass.isInterface();
    assert superType == dexItemFactory().objectType
        || definitionFor(superType) == null
        || !definitionFor(superType).isInterface();
    List<DexProgramClass> relationChain = new ArrayList<>();
    DexClass current = subClass;
    while (current != null) {
      if (current.isProgramClass()) {
        relationChain.add(current.asProgramClass());
      }
      if (current.type == superType) {
        return relationChain;
      }
      current = definitionFor(current.superType);
    }
    return relationChain;
  }

  public boolean methodDefinedInInterfaces(DexEncodedMethod method, DexType implementingClass) {
    DexClass holder = definitionFor(implementingClass);
    if (holder == null) {
      return false;
    }
    for (DexType iface : holder.interfaces.values) {
      if (methodDefinedInInterface(method, iface)) {
        return true;
      }
    }
    return false;
  }

  public boolean methodDefinedInInterface(DexEncodedMethod method, DexType iface) {
    DexClass potentialHolder = definitionFor(iface);
    if (potentialHolder == null) {
      return false;
    }
    assert potentialHolder.isInterface();
    for (DexEncodedMethod virtualMethod : potentialHolder.virtualMethods()) {
      if (virtualMethod.method.hasSameProtoAndName(method.method)
          && virtualMethod.accessFlags.isSameVisibility(method.accessFlags)) {
        return true;
      }
    }
    for (DexType parentInterface : potentialHolder.interfaces.values) {
      if (methodDefinedInInterface(method, parentInterface)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Helper method used for emulated interface resolution (not in JVM specifications). The result
   * may be abstract.
   */
  public DexClassAndMethod lookupMaximallySpecificMethod(DexClass clazz, DexMethod method) {
    return lookupMaximallySpecificTarget(clazz, method);
  }

  public DexClassAndMethod lookupMaximallySpecificMethod(
      LambdaDescriptor lambda, DexMethod method) {
    return lookupMaximallySpecificTarget(lambda, method);
  }

  /**
   * Lookup instance field starting in type and following the interface and super chain.
   *
   * <p>The result is the field that will be hit at runtime, if such field is known. A result of
   * null indicates that the field is either undefined or not an instance field.
   */
  public DexEncodedField lookupInstanceTargetOn(DexType type, DexField field) {
    assert checkIfObsolete();
    assert type.isClassType();
    DexEncodedField result = resolveFieldOn(type, field).getResolvedField();
    return result == null || result.accessFlags.isStatic() ? null : result;
  }

  public DexEncodedField lookupInstanceTarget(DexField field) {
    return lookupInstanceTargetOn(field.holder, field);
  }

  /**
   * Lookup static field starting in type and following the interface and super chain.
   *
   * <p>The result is the field that will be hit at runtime, if such field is known. A result of
   * null indicates that the field is either undefined or not a static field.
   */
  public DexEncodedField lookupStaticTargetOn(DexType type, DexField field) {
    assert checkIfObsolete();
    assert type.isClassType();
    DexEncodedField result = resolveFieldOn(type, field).getResolvedField();
    return result == null || !result.accessFlags.isStatic() ? null : result;
  }

  public DexEncodedField lookupStaticTarget(DexField field) {
    return lookupStaticTargetOn(field.holder, field);
  }

  /**
   * Lookup static method following the super chain from the holder of {@code method}.
   *
   * <p>This method will resolve the method on the holder of {@code method} and only return a
   * non-null value if the result of resolution was a static, non-abstract method.
   *
   * @param method the method to lookup
   * @return The actual target for {@code method} or {@code null} if none found.
   */
  // TODO(b/155968472): This should take a parameter `boolean isInterface` and use resolveMethod().
  public DexEncodedMethod lookupStaticTarget(DexMethod method, DexProgramClass context) {
    assert checkIfObsolete();
    return unsafeResolveMethodDueToDexFormat(method).lookupInvokeStaticTarget(context, this);
  }

  // TODO(b/155968472): This should take a parameter `boolean isInterface` and use resolveMethod().
  public DexEncodedMethod lookupStaticTarget(DexMethod method, ProgramMethod context) {
    return lookupStaticTarget(method, context.getHolder());
  }

  /**
   * Lookup super method following the super chain from the holder of {@code method}.
   *
   * <p>This method will resolve the method on the holder of {@code method} and only return a
   * non-null value if the result of resolution was an instance (i.e. non-static) method.
   *
   * @param method the method to lookup
   * @param context the class the invoke is contained in, i.e., the holder of the caller.
   * @return The actual target for {@code method} or {@code null} if none found.
   */
  // TODO(b/155968472): This should take a parameter `boolean isInterface` and use resolveMethod().
  public DexEncodedMethod lookupSuperTarget(DexMethod method, DexProgramClass context) {
    assert checkIfObsolete();
    return unsafeResolveMethodDueToDexFormat(method).lookupInvokeSuperTarget(context, this);
  }

  // TODO(b/155968472): This should take a parameter `boolean isInterface` and use resolveMethod().
  public DexEncodedMethod lookupSuperTarget(DexMethod method, ProgramMethod context) {
    return lookupSuperTarget(method, context.getHolder());
  }

  /**
   * Lookup direct method following the super chain from the holder of {@code method}.
   *
   * <p>This method will lookup private and constructor methods.
   *
   * @param method the method to lookup
   * @return The actual target for {@code method} or {@code null} if none found.
   */
  // TODO(b/155968472): This should take a parameter `boolean isInterface` and use resolveMethod().
  public DexEncodedMethod lookupDirectTarget(DexMethod method, DexProgramClass context) {
    assert checkIfObsolete();
    return unsafeResolveMethodDueToDexFormat(method).lookupInvokeDirectTarget(context, this);
  }

  // TODO(b/155968472): This should take a parameter `boolean isInterface` and use resolveMethod().
  public DexEncodedMethod lookupDirectTarget(DexMethod method, ProgramMethod context) {
    return lookupDirectTarget(method, context.getHolder());
  }

  /**
   * Implements resolution of a method descriptor.
   *
   * <p>This method will query the definition of the holder to decide on which resolution to use. If
   * the holder is an interface, it delegates to {@link #resolveMethodOnInterface(DexType,
   * DexMethod)}, otherwise {@link #resolveMethodOnClass(DexMethod, DexType)} is used.
   *
   * <p>This is to overcome the shortcoming of the DEX file format that does not allow to encode the
   * kind of a method reference.
   */
  public ResolutionResult unsafeResolveMethodDueToDexFormat(DexMethod method) {
    assert checkIfObsolete();
    DexType holder = method.holder;
    if (holder.isArrayType()) {
      return resolveMethodOnArray(holder, method);
    }
    DexClass definition = definitionFor(holder);
    if (definition == null) {
      return ClassNotFoundResult.INSTANCE;
    }
    return resolveMethodOn(definition, method);
  }

  public ResolutionResult resolveMethod(DexMethod method, boolean isInterface) {
    return isInterface
        ? resolveMethodOnInterface(method.holder, method)
        : resolveMethodOnClass(method, method.holder);
  }

  public ResolutionResult resolveMethodOn(DexClass holder, DexMethod method) {
    return holder.isInterface()
        ? resolveMethodOnInterface(holder, method)
        : resolveMethodOnClass(method, holder);
  }

  /**
   * Implements resolution of a method descriptor against a target type.
   *
   * <p>The boolean isInterface parameter denotes if the method reference is an interface method
   * reference, and if so method resolution is done according to interface method resolution.
   *
   * @param holder Type at which to initiate the resolution.
   * @param method Method descriptor for resolution (the field method.holder is ignored).
   * @param isInterface Indicates if resolution is to be done according to class or interface.
   * @return The result of resolution.
   */
  public ResolutionResult resolveMethodOn(DexType holder, DexMethod method, boolean isInterface) {
    assert checkIfObsolete();
    return isInterface
        ? resolveMethodOnInterface(holder, method)
        : resolveMethodOnClass(method, holder);
  }

  /**
   * Implements resolution of a method descriptor against an array type.
   *
   * <p>See <a href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-10.html#jls-10.7">Section
   * 10.7 of the Java Language Specification</a>. All invokations will have target java.lang.Object
   * except clone which has no target.
   */
  private ResolutionResult resolveMethodOnArray(DexType holder, DexMethod method) {
    assert checkIfObsolete();
    assert holder.isArrayType();
    if (method.name == dexItemFactory().cloneMethodName) {
      return ArrayCloneMethodResult.INSTANCE;
    } else {
      return resolveMethodOnClass(method, dexItemFactory().objectType);
    }
  }

  public ResolutionResult resolveMethodOnClass(DexMethod method) {
    return resolveMethodOnClass(method, method.holder);
  }

  /**
   * Implements resolution of a method descriptor against a class type.
   *
   * <p>See <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-5.html#jvms-5.4.3.3">
   * Section 5.4.3.3 of the JVM Spec</a>.
   *
   * <p>The resolved method is not the method that will actually be invoked. Which methods gets
   * invoked depends on the invoke instruction used. However, it is always safe to rewrite any
   * invoke on the given descriptor to a corresponding invoke on the resolved descriptor, as the
   * resolved method is used as basis for dispatch.
   */
  public ResolutionResult resolveMethodOnClass(DexMethod method, DexType holder) {
    assert checkIfObsolete();
    if (holder.isArrayType()) {
      return resolveMethodOnArray(holder, method);
    }
    DexClass clazz = definitionFor(holder);
    if (clazz == null) {
      return ClassNotFoundResult.INSTANCE;
    }
    // Step 1: If holder is an interface, resolution fails with an ICCE. We return null.
    if (clazz.isInterface()) {
      return IncompatibleClassResult.INSTANCE;
    }
    return resolveMethodOnClass(method, clazz);
  }

  public ResolutionResult resolveMethodOnClass(DexMethod method, DexClass clazz) {
    assert checkIfObsolete();
    assert !clazz.isInterface();
    // Step 2:
    ResolutionResult result = resolveMethodOnClassStep2(clazz, method, clazz);
    if (result != null) {
      return result;
    }
    // Finally Step 3:
    return resolveMethodStep3(clazz, method);
  }

  /**
   * Implements step 2 of method resolution on classes as per <a
   * href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-5.html#jvms-5.4.3.3">Section
   * 5.4.3.3 of the JVM Spec</a>.
   */
  private ResolutionResult resolveMethodOnClassStep2(
      DexClass clazz, DexMethod method, DexClass initialResolutionHolder) {
    // Pt. 1: Signature polymorphic method check.
    // See also <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-2.html#jvms-2.9">
    // Section 2.9 of the JVM Spec</a>.
    DexEncodedMethod result = clazz.lookupSignaturePolymorphicMethod(method.name, dexItemFactory());
    if (result != null) {
      return new SingleResolutionResult(initialResolutionHolder, clazz, result);
    }
    // Pt 2: Find a method that matches the descriptor.
    result = clazz.lookupMethod(method);
    if (result != null) {
      // If the resolved method is private, then it can only be accessed if the symbolic reference
      // that initiated the resolution was the type at which the method resolved on. If that is not
      // the case, then the error is either an IllegalAccessError, or in the case where access is
      // allowed because of nests, a NoSuchMethodError. Which error cannot be determined without
      // knowing the calling context.
      if (result.isPrivateMethod() && clazz != initialResolutionHolder) {
        return new IllegalAccessOrNoSuchMethodResult(result);
      }
      return new SingleResolutionResult(initialResolutionHolder, clazz, result);
    }
    // Pt 3: Apply step two to direct superclass of holder.
    if (clazz.superType != null) {
      DexClass superClass = definitionFor(clazz.superType);
      if (superClass != null) {
        return resolveMethodOnClassStep2(superClass, method, initialResolutionHolder);
      }
    }
    return null;
  }

  /**
   * Implements step 3 of <a
   * href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-5.html#jvms-5.4.3.3">Section
   * 5.4.3.3 of the JVM Spec</a>. As this is the same for interfaces and classes, we share one
   * implementation.
   */
  private ResolutionResult resolveMethodStep3(DexClass clazz, DexMethod method) {
    MaximallySpecificMethodsBuilder builder = new MaximallySpecificMethodsBuilder();
    resolveMethodStep3Helper(method, clazz, builder);
    return builder.resolve(clazz);
  }

  // Non-private lookup (ie, not resolution) to find interface targets.
  DexClassAndMethod lookupMaximallySpecificTarget(DexClass clazz, DexMethod method) {
    MaximallySpecificMethodsBuilder builder = new MaximallySpecificMethodsBuilder();
    resolveMethodStep3Helper(method, clazz, builder);
    return builder.lookup();
  }

  // Non-private lookup (ie, not resolution) to find interface targets.
  DexClassAndMethod lookupMaximallySpecificTarget(LambdaDescriptor lambda, DexMethod method) {
    MaximallySpecificMethodsBuilder builder = new MaximallySpecificMethodsBuilder();
    resolveMethodStep3Helper(method, dexItemFactory().objectType, lambda.interfaces, builder);
    return builder.lookup();
  }

  /** Helper method that builds the set of maximally specific methods. */
  private void resolveMethodStep3Helper(
      DexMethod method, DexClass clazz, MaximallySpecificMethodsBuilder builder) {
    resolveMethodStep3Helper(
        method, clazz.superType, Arrays.asList(clazz.interfaces.values), builder);
  }

  private void resolveMethodStep3Helper(
      DexMethod method,
      DexType superType,
      List<DexType> interfaces,
      MaximallySpecificMethodsBuilder builder) {
    for (DexType iface : interfaces) {
      DexClass definition = definitionFor(iface);
      if (definition == null) {
        // Ignore missing interface definitions.
        continue;
      }
      assert definition.isInterface();
      DexEncodedMethod result = definition.lookupMethod(method);
      if (isMaximallySpecificCandidate(result)) {
        // The candidate is added and doing so will prohibit shadowed methods from being in the set.
        builder.addCandidate(definition, result, this);
      } else {
        // Look at the super-interfaces of this class and keep searching.
        resolveMethodStep3Helper(method, definition, builder);
      }
    }
    // Now look at indirect super interfaces.
    if (superType != null) {
      DexClass superClass = definitionFor(superType);
      if (superClass != null) {
        resolveMethodStep3Helper(method, superClass, builder);
      }
    }
  }

  /**
   * A candidate for being a maximally specific method must have neither its private, nor its static
   * flag set. A candidate may still not be maximally specific, which entails that no subinterfaces
   * from also contribute with a candidate to the type. That is not determined by this method.
   */
  private boolean isMaximallySpecificCandidate(DexEncodedMethod method) {
    return method != null && !method.accessFlags.isPrivate() && !method.accessFlags.isStatic();
  }

  public ResolutionResult resolveMethodOnInterface(DexMethod method) {
    return resolveMethodOnInterface(method.holder, method);
  }

  /**
   * Implements resolution of a method descriptor against an interface type.
   *
   * <p>See <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-5.html#jvms-5.4.3.3">
   * Section 5.4.3.4 of the JVM Spec</a>.
   *
   * <p>The resolved method is not the method that will actually be invoked. Which methods gets
   * invoked depends on the invoke instruction used. However, it is always save to rewrite any
   * invoke on the given descriptor to a corresponding invoke on the resolved descriptor, as the
   * resolved method is used as basis for dispatch.
   */
  public ResolutionResult resolveMethodOnInterface(DexType holder, DexMethod desc) {
    assert checkIfObsolete();
    if (holder.isArrayType()) {
      return IncompatibleClassResult.INSTANCE;
    }
    // Step 1: Lookup interface.
    DexClass definition = definitionFor(holder);
    // If the definition is not an interface, resolution fails with an ICCE. We just return the
    // empty result here.
    if (definition == null) {
      return ClassNotFoundResult.INSTANCE;
    }
    if (!definition.isInterface()) {
      return IncompatibleClassResult.INSTANCE;
    }
    return resolveMethodOnInterface(definition, desc);
  }

  public ResolutionResult resolveMethodOnInterface(DexClass definition, DexMethod desc) {
    assert checkIfObsolete();
    assert definition.isInterface();
    // Step 2: Look for exact method on interface.
    DexEncodedMethod result = definition.lookupMethod(desc);
    if (result != null) {
      return new SingleResolutionResult(definition, definition, result);
    }
    // Step 3: Look for matching method on object class.
    DexClass objectClass = definitionFor(dexItemFactory().objectType);
    if (objectClass == null) {
      return ClassNotFoundResult.INSTANCE;
    }
    result = objectClass.lookupMethod(desc);
    if (result != null && result.accessFlags.isPublic() && !result.accessFlags.isAbstract()) {
      return new SingleResolutionResult(definition, objectClass, result);
    }
    // Step 3: Look for maximally-specific superinterface methods or any interface definition.
    //         This is the same for classes and interfaces.
    return resolveMethodStep3(definition, desc);
  }

  /**
   * Implements resolution of a field descriptor against the holder of the field. See also {@link
   * #resolveFieldOn}.
   */
  public FieldResolutionResult resolveField(DexField field) {
    assert checkIfObsolete();
    return resolveFieldOn(field.holder, field);
  }

  /** Intentionally drops {@param context} since this is only needed in D8. */
  @Override
  public FieldResolutionResult resolveFieldOn(DexType type, DexField field, ProgramMethod context) {
    return resolveFieldOn(type, field);
  }

  /**
   * Implements resolution of a field descriptor against a type.
   *
   * <p>See <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-5.html#jvms-5.4.3.2">
   * Section 5.4.3.2 of the JVM Spec</a>.
   */
  public FieldResolutionResult resolveFieldOn(DexType type, DexField field) {
    assert checkIfObsolete();
    DexClass holder = definitionFor(type);
    return holder != null ? resolveFieldOn(holder, field) : FieldResolutionResult.failure();
  }

  public FieldResolutionResult resolveFieldOn(DexClass holder, DexField field) {
    assert checkIfObsolete();
    assert holder != null;
    return resolveFieldOn(holder, field, holder, SetUtils.newIdentityHashSet(8));
  }

  private FieldResolutionResult resolveFieldOn(
      DexClass holder,
      DexField field,
      DexClass initialResolutionHolder,
      Set<DexType> visitedInterfaces) {
    assert checkIfObsolete();
    assert holder != null;
    // Step 1: Class declares the field.
    DexEncodedField definition = holder.lookupField(field);
    if (definition != null) {
      return new SuccessfulFieldResolutionResult(initialResolutionHolder, holder, definition);
    }
    // Step 2: Apply recursively to direct superinterfaces. First match succeeds.
    DexClassAndField result = resolveFieldOnDirectInterfaces(holder, field, visitedInterfaces);
    if (result != null) {
      return new SuccessfulFieldResolutionResult(
          initialResolutionHolder, result.getHolder(), result.getDefinition());
    }
    // Step 3: Apply recursively to superclass.
    if (holder.superType != null) {
      DexClass superClass = definitionFor(holder.superType);
      if (superClass != null) {
        return resolveFieldOn(superClass, field, initialResolutionHolder, visitedInterfaces);
      }
    }
    return FieldResolutionResult.failure();
  }

  private DexClassAndField resolveFieldOnDirectInterfaces(
      DexClass clazz, DexField field, Set<DexType> visitedInterfaces) {
    for (DexType interfaceType : clazz.interfaces.values) {
      if (visitedInterfaces.add(interfaceType)) {
        DexClass interfaceClass = definitionFor(interfaceType);
        if (interfaceClass != null) {
          DexClassAndField result =
              resolveFieldOnInterface(interfaceClass, field, visitedInterfaces);
          if (result != null) {
            return result;
          }
        }
      }
    }
    return null;
  }

  private DexClassAndField resolveFieldOnInterface(
      DexClass interfaceClass, DexField field, Set<DexType> visitedInterfaces) {
    // Step 1: Class declares the field.
    DexEncodedField definition = interfaceClass.lookupField(field);
    if (definition != null) {
      return DexClassAndField.create(interfaceClass, definition);
    }
    // Step 2: Apply recursively to direct superinterfaces. First match succeeds.
    return resolveFieldOnDirectInterfaces(interfaceClass, field, visitedInterfaces);
  }

  private static class MaximallySpecificMethodsBuilder {

    // The set of actual maximally specific methods.
    // This set is linked map so that in the case where a number of methods remain a deterministic
    // choice can be made. The map is from definition classes to their maximally specific method, or
    // in the case that a type has a candidate which is shadowed by a subinterface, the map will
    // map the class to a null entry, thus any addition to the map must check for key containment
    // prior to writing.
    LinkedHashMap<DexClass, DexEncodedMethod> maximallySpecificMethods = new LinkedHashMap<>();

    void addCandidate(DexClass holder, DexEncodedMethod method, AppInfo appInfo) {
      // If this candidate is already a candidate or it is shadowed, then no need to continue.
      if (maximallySpecificMethods.containsKey(holder)) {
        return;
      }
      maximallySpecificMethods.put(holder, method);
      // Prune exiting candidates and prohibit future candidates in the super hierarchy.
      assert holder.isInterface();
      assert holder.superType == appInfo.dexItemFactory().objectType;
      for (DexType iface : holder.interfaces.values) {
        markShadowed(iface, appInfo);
      }
    }

    private void markShadowed(DexType type, AppInfo appInfo) {
      if (type == null) {
        return;
      }
      DexClass clazz = appInfo.definitionFor(type);
      if (clazz == null) {
        return;
      }
      assert clazz.isInterface();
      assert clazz.superType == appInfo.dexItemFactory().objectType;
      // A null entry signifies that the candidate is shadowed blocking future candidates.
      // If the candidate is already shadowed at this type there is no need to shadow further up.
      if (maximallySpecificMethods.containsKey(clazz)
          && maximallySpecificMethods.get(clazz) == null) {
        return;
      }
      maximallySpecificMethods.put(clazz, null);
      for (DexType iface : clazz.interfaces.values) {
        markShadowed(iface, appInfo);
      }
    }

    DexClassAndMethod lookup() {
      SingleResolutionResult result = internalResolve(null).asSingleResolution();
      return result != null
          ? DexClassAndMethod.create(result.getResolvedHolder(), result.getResolvedMethod())
          : null;
    }

    ResolutionResult resolve(DexClass initialResolutionHolder) {
      assert initialResolutionHolder != null;
      return internalResolve(initialResolutionHolder);
    }

    private ResolutionResult internalResolve(DexClass initialResolutionHolder) {
      if (maximallySpecificMethods.isEmpty()) {
        return NoSuchMethodResult.INSTANCE;
      }
      // Fast path in the common case of a single method.
      if (maximallySpecificMethods.size() == 1) {
        return singleResultHelper(
            initialResolutionHolder, maximallySpecificMethods.entrySet().iterator().next());
      }
      Entry<DexClass, DexEncodedMethod> firstMaximallySpecificMethod = null;
      List<Entry<DexClass, DexEncodedMethod>> nonAbstractMethods =
          new ArrayList<>(maximallySpecificMethods.size());
      for (Entry<DexClass, DexEncodedMethod> entry : maximallySpecificMethods.entrySet()) {
        DexEncodedMethod method = entry.getValue();
        if (method == null) {
          // Ignore shadowed candidates.
          continue;
        }
        if (firstMaximallySpecificMethod == null) {
          firstMaximallySpecificMethod = entry;
        }
        if (method.isNonAbstractVirtualMethod()) {
          nonAbstractMethods.add(entry);
        }
      }
      // If there are no non-abstract methods, then any candidate will suffice as a target.
      // For deterministic resolution, we return the first mapped method (of the linked map).
      if (nonAbstractMethods.isEmpty()) {
        return singleResultHelper(initialResolutionHolder, firstMaximallySpecificMethod);
      }
      // If there is exactly one non-abstract method (a default method) it is the resolution target.
      if (nonAbstractMethods.size() == 1) {
        return singleResultHelper(initialResolutionHolder, nonAbstractMethods.get(0));
      }
      return IncompatibleClassResult.create(ListUtils.map(nonAbstractMethods, Entry::getValue));
    }

    private static SingleResolutionResult singleResultHelper(
        DexClass initialResolutionResult, Entry<DexClass, DexEncodedMethod> entry) {
      return new SingleResolutionResult(
          initialResolutionResult != null ? initialResolutionResult : entry.getKey(),
          entry.getKey(),
          entry.getValue());
    }
  }
}
