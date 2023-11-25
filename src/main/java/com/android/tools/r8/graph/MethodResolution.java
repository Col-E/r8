// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.MethodResolution.UniquePathOracle.SplitToken.NO_SPLIT_TOKEN;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.MethodResolutionResult.ArrayCloneMethodResult;
import com.android.tools.r8.graph.MethodResolutionResult.ClassNotFoundResult;
import com.android.tools.r8.graph.MethodResolutionResult.IllegalAccessOrNoSuchMethodResult;
import com.android.tools.r8.graph.MethodResolutionResult.IncompatibleClassResult;
import com.android.tools.r8.graph.MethodResolutionResult.NoSuchMethodResult;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.ir.desugar.LambdaDescriptor;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.SetUtils;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;

/**
 * Implements resolution of a method descriptor against a type.
 *
 * <p>See <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-5.html#jvms-5.4.3.3">
 * Section 5.4.3.3 of the JVM Spec</a>.
 */
public class MethodResolution {

  private final Function<DexType, ClassResolutionResult> definitionFor;
  private final DexItemFactory factory;
  private final boolean escapeIfLibraryHasProgramSuperType;
  private final boolean canHaveIncompletePaths;

  private MethodResolution(
      Function<DexType, ClassResolutionResult> definitionFor,
      DexItemFactory factory,
      boolean escapeIfLibraryHasProgramSuperType,
      boolean canHaveIncompletePaths) {
    this.definitionFor = definitionFor;
    this.factory = factory;
    this.escapeIfLibraryHasProgramSuperType = escapeIfLibraryHasProgramSuperType;
    this.canHaveIncompletePaths = canHaveIncompletePaths;
  }

  @Deprecated
  public static MethodResolution createLegacy(
      Function<DexType, DexClass> definitionFor, DexItemFactory factory) {
    // TODO(b/230289235): Remove this when R8/D8 can handle multiple definitions.
    return new MethodResolution(
        type -> {
          DexClass clazz = definitionFor.apply(type);
          return clazz == null ? ClassResolutionResult.NoResolutionResult.noResult() : clazz;
        },
        factory,
        false,
        false);
  }

  public static MethodResolution create(
      Function<DexType, ClassResolutionResult> definitionFor, DexItemFactory factory) {
    return new MethodResolution(definitionFor, factory, true, true);
  }

  private ClassResolutionResult definitionFor(DexType type) {
    return definitionFor.apply(type);
  }

  /**
   * This method will query the definition of the holder to decide on which resolution to use. If
   * the holder is an interface, it delegates to {@link #resolveMethodOnInterface(DexClass,
   * DexProto, DexString)}, otherwise {@link #resolveMethodOnClass(DexClass, DexProto, DexString)}
   * is used.
   *
   * <p>This is to overcome the shortcoming of the DEX file format that does not allow to encode the
   * kind of a method reference.
   */
  public MethodResolutionResult unsafeResolveMethodDueToDexFormat(DexMethod method) {
    DexType holder = method.holder;
    if (holder.isArrayType()) {
      return resolveMethodOnArray(holder, method.getProto(), method.getName());
    }
    MethodResolutionResult.Builder builder = MethodResolutionResult.builder();
    definitionFor(holder)
        .forEachClassResolutionResult(
            clazz -> {
              builder.addResolutionResult(
                  clazz.isInterface()
                      ? resolveMethodOnInterface(clazz, method.getProto(), method.getName())
                      : resolveMethodOnClass(clazz, method.getProto(), method.getName()));
            });
    return builder.buildOrIfEmpty(ClassNotFoundResult.INSTANCE, holder);
  }

  /**
   * Implements resolution of a method descriptor against an array type.
   *
   * <p>See <a href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-10.html#jls-10.7">Section
   * 10.7 of the Java Language Specification</a>. All invokations will have target java.lang.Object
   * except clone which has no target.
   */
  @SuppressWarnings("ReferenceEquality")
  private MethodResolutionResult resolveMethodOnArray(
      DexType holder, DexProto methodProto, DexString methodName) {
    assert holder.isArrayType();
    if (methodName == factory.cloneMethodName) {
      return ArrayCloneMethodResult.INSTANCE;
    } else {
      return resolveMethodOnClass(factory.objectType, methodProto, methodName);
    }
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
  public MethodResolutionResult resolveMethodOnClass(
      DexType holder, DexProto methodProto, DexString methodName) {
    if (holder.isArrayType()) {
      return resolveMethodOnArray(holder, methodProto, methodName);
    }
    MethodResolutionResult.Builder builder = MethodResolutionResult.builder();
    definitionFor(holder)
        .forEachClassResolutionResult(
            clazz -> {
              // Step 1: If holder is an interface, resolution fails with an ICCE.
              if (clazz.isInterface()) {
                builder.addResolutionResult(IncompatibleClassResult.INSTANCE);
              } else {
                builder.addResolutionResult(resolveMethodOnClass(clazz, methodProto, methodName));
              }
            });
    return builder.buildOrIfEmpty(ClassNotFoundResult.INSTANCE, holder);
  }

  public MethodResolutionResult resolveMethodOnClass(
      DexClass clazz, DexProto methodProto, DexString methodName) {
    assert !clazz.isInterface();
    // Step 2:
    MethodResolutionResult result =
        resolveMethodOnClassStep2(clazz, methodProto, methodName, clazz);
    if (result != null) {
      return result;
    }
    // Finally Step 3:
    return resolveMethodStep3(clazz, methodProto, methodName);
  }

  /**
   * Implements step 2 of method resolution on classes as per <a
   * href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-5.html#jvms-5.4.3.3">Section
   * 5.4.3.3 of the JVM Spec</a>.
   */
  private MethodResolutionResult resolveMethodOnClassStep2(
      DexClass clazz,
      DexProto methodProto,
      DexString methodName,
      DexClass initialResolutionHolder) {
    // Pt. 1: Signature polymorphic method check.
    // See also <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-2.html#jvms-2.9">
    // Section 2.9 of the JVM Spec</a>.
    DexEncodedMethod result = clazz.lookupSignaturePolymorphicMethod(methodName, factory);
    if (result != null) {
      return MethodResolutionResult.createSingleResolutionResult(
          initialResolutionHolder, clazz, result);
    }
    // Pt 2: Find a method that matches the descriptor.
    result = clazz.lookupMethod(methodProto, methodName);
    if (result != null) {
      // If the resolved method is private, then it can only be accessed if the symbolic reference
      // that initiated the resolution was the type at which the method resolved on. If that is not
      // the case, then the error is either an IllegalAccessError, or in the case where access is
      // allowed because of nests, a NoSuchMethodError. Which error cannot be determined without
      // knowing the calling context.
      if (result.isPrivateMethod() && clazz != initialResolutionHolder) {
        return new IllegalAccessOrNoSuchMethodResult(initialResolutionHolder, result);
      }
      return MethodResolutionResult.createSingleResolutionResult(
          initialResolutionHolder, clazz, result);
    }
    // Pt 3: Apply step two to direct superclass of holder.
    if (clazz.superType != null) {
      MethodResolutionResult.Builder builder = MethodResolutionResult.builder();
      definitionFor(clazz.superType)
          .forEachClassResolutionResult(
              superClass -> {
                // Guard against going back into the program for resolution.
                if (escapeIfLibraryHasProgramSuperType
                    && clazz.isLibraryClass()
                    && !superClass.isLibraryClass()) {
                  return;
                }
                MethodResolutionResult superTypeResult =
                    resolveMethodOnClassStep2(
                        superClass, methodProto, methodName, initialResolutionHolder);
                if (superTypeResult != null) {
                  builder.addResolutionResult(superTypeResult);
                }
              });
      return builder.buildOrIfEmpty(null, clazz.superType);
    }
    return null;
  }

  /**
   * Implements step 3 of <a
   * href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-5.html#jvms-5.4.3.3">Section
   * 5.4.3.3 of the JVM Spec</a>. As this is the same for interfaces and classes, we share one
   * implementation.
   */
  private MethodResolutionResult resolveMethodStep3(
      DexClass clazz, DexProto methodProto, DexString methodName) {
    MaximallySpecificMethodsBuilder builder =
        new MaximallySpecificMethodsBuilder(definitionFor, factory);
    resolveMethodStep3Helper(methodProto, methodName, clazz, builder);
    return builder.resolve(clazz);
  }

  MethodResolutionResult resolveMaximallySpecificTarget(DexClass clazz, DexMethod method) {
    return resolveMaximallySpecificTargetHelper(clazz, method).resolve(clazz);
  }

  MethodResolutionResult resolveMaximallySpecificTarget(LambdaDescriptor lambda, DexMethod method) {
    return resolveMaximallySpecificTargetHelper(lambda, method).internalResolve(null);
  }

  // Non-private lookup (ie, not resolution) to find interface targets.
  DexClassAndMethod lookupMaximallySpecificTarget(DexClass clazz, DexMethod method) {
    return resolveMaximallySpecificTargetHelper(clazz, method).lookup();
  }

  // Non-private method used for emulated interface only.
  List<Entry<DexClass, DexEncodedMethod>> getAbstractInterfaceMethods(
      DexClass clazz, DexMethod method) {
    return resolveMaximallySpecificTargetHelper(clazz, method).getAbstractMethods();
  }

  private MaximallySpecificMethodsBuilder resolveMaximallySpecificTargetHelper(
      DexClass clazz, DexMethod method) {
    MaximallySpecificMethodsBuilder builder =
        new MaximallySpecificMethodsBuilder(definitionFor, factory);
    resolveMethodStep3Helper(method.getProto(), method.getName(), clazz, builder);
    return builder;
  }

  private MaximallySpecificMethodsBuilder resolveMaximallySpecificTargetHelper(
      LambdaDescriptor lambda, DexMethod method) {
    MaximallySpecificMethodsBuilder builder =
        new MaximallySpecificMethodsBuilder(definitionFor, factory);
    resolveMethodStep3Helper(
        method.getProto(), method.getName(), null, builder, factory.objectType, lambda.interfaces);
    return builder;
  }

  /**
   * UniquePathOracle will compute for all parent definitions in the hierarchy if a class is visited
   * by all paths or not depending on class resolution resolving a type to either library or
   * program/classpath definitions. It does so by visiting all paths upwards in the hierarchy and
   * when a split point is seen it will create a "left" and "right" half rooted by the split type.
   * If a definition is only seen by a single token it will always be on an incomplete path
   * otherwise if the class is seen by both the left and right token it is visited by all:
   *
   * <pre>
   *                     C_Library { left_B, right_B }
   *                    /                     \
   *     B_Program extends C { left_B }     B_Library extends C { right_B }
   *                    \                     /
   *                      A_Program extends B { }
   * </pre>
   */
  static class UniquePathOracle {

    static class SplitToken {

      static final SplitToken NO_SPLIT_TOKEN = new SplitToken(null);

      private final DexType split;

      private SplitToken(DexType split) {
        this.split = split;
      }

      boolean isSplitToken() {
        return this != NO_SPLIT_TOKEN;
      }
    }

    private final Function<DexType, ClassResolutionResult> definitionFor;
    private final boolean escapeIfLibraryHasProgramSuperType;
    private final Map<DexClass, Map<DexType, SplitToken>> incompletePaths = new IdentityHashMap<>();
    private final Set<DexType> seenTypes = Sets.newIdentityHashSet();

    public UniquePathOracle(
        Function<DexType, ClassResolutionResult> definitionFor,
        boolean escapeIfLibraryHasProgramSuperType) {
      this.definitionFor = definitionFor;
      this.escapeIfLibraryHasProgramSuperType = escapeIfLibraryHasProgramSuperType;
    }

    public boolean onIncompletePath(DexClass definition) {
      Map<DexType, SplitToken> tokenMap = incompletePaths.get(definition);
      assert tokenMap != null;
      for (SplitToken value : tokenMap.values()) {
        if (value.isSplitToken()) {
          return true;
        }
      }
      return false;
    }

    void lookupPath(DexType type, DexClass previousClass) {
      lookupPath(type, previousClass, Collections.emptySet());
    }

    private void lookupPath(DexType type, DexClass previousClass, Set<SplitToken> splitTokens) {
      if (splitTokens.isEmpty() && !seenTypes.add(type)) {
        return;
      }
      ClassResolutionResult resolutionResult = definitionFor.apply(type);
      resolutionResult.forEachClassResolutionResult(
          clazz -> {
            if (escapeIfLibraryHasProgramSuperType
                && previousClass.isLibraryClass()
                && !clazz.isLibraryClass()) {
              return;
            }
            Set<SplitToken> currentSplitTokens;
            if (resolutionResult.isMultipleClassResolutionResult()) {
              currentSplitTokens = SetUtils.newIdentityHashSet(splitTokens);
              currentSplitTokens.add(new SplitToken(type));
            } else {
              currentSplitTokens = splitTokens;
            }
            Map<DexType, SplitToken> paths =
                incompletePaths.computeIfAbsent(clazz, ignoreKey(IdentityHashMap::new));
            currentSplitTokens.forEach(
                splitToken -> {
                  SplitToken otherSplitToken = paths.get(splitToken.split);
                  if (otherSplitToken == null) {
                    paths.put(splitToken.split, splitToken);
                  } else if (otherSplitToken != splitToken && otherSplitToken.isSplitToken()) {
                    // We have not seen this half of the split token, so it is visited on all paths.
                    paths.put(splitToken.split, NO_SPLIT_TOKEN);
                  }
                });
            clazz.interfaces.forEach(iface -> lookupPath(iface, clazz, currentSplitTokens));
            if (clazz.superType != null) {
              lookupPath(clazz.superType, clazz, currentSplitTokens);
            }
          });
    }
  }

  static class AllUniquePathsOracle extends UniquePathOracle {

    private static final AllUniquePathsOracle INSTANCE = new AllUniquePathsOracle();

    static AllUniquePathsOracle getInstance() {
      return INSTANCE;
    }

    private AllUniquePathsOracle() {
      super(null, false);
    }

    @Override
    void lookupPath(DexType type, DexClass previousClass) {
      // Intentionally empty
    }

    @Override
    public boolean onIncompletePath(DexClass definition) {
      return false;
    }
  }

  /** Helper method that builds the set of maximally specific methods. */
  private void resolveMethodStep3Helper(
      DexProto methodProto,
      DexString methodName,
      DexClass clazz,
      MaximallySpecificMethodsBuilder builder) {
    resolveMethodStep3Helper(
        methodProto,
        methodName,
        clazz,
        builder,
        clazz.superType,
        Arrays.asList(clazz.interfaces.values));
  }

  private void resolveMethodStep3Helper(
      DexProto methodProto,
      DexString methodName,
      DexClass clazz,
      MaximallySpecificMethodsBuilder builder,
      DexType superType,
      List<DexType> interfaces) {
    UniquePathOracle uniquePathOracle;
    if (canHaveIncompletePaths) {
      uniquePathOracle = new UniquePathOracle(definitionFor, escapeIfLibraryHasProgramSuperType);
      interfaces.forEach(iFace -> uniquePathOracle.lookupPath(iFace, clazz));
      if (superType != null) {
        uniquePathOracle.lookupPath(superType, clazz);
      }
    } else {
      uniquePathOracle = AllUniquePathsOracle.getInstance();
    }
    resolveMethodStep3Helper(
        methodProto, methodName, clazz, builder, superType, interfaces, uniquePathOracle);
  }

  private void resolveMethodStep3Helper(
      DexProto methodProto,
      DexString methodName,
      DexClass clazz,
      MaximallySpecificMethodsBuilder builder,
      DexType superType,
      List<DexType> interfaces,
      UniquePathOracle uniquePathOracle) {
    for (DexType iface : interfaces) {
      ClassResolutionResult classResolutionResult = definitionFor(iface);
      classResolutionResult.forEachClassResolutionResult(
          definition -> {
            // Guard against going back into the program for resolution.
            if (escapeIfLibraryHasProgramSuperType
                && clazz != null
                && clazz.isLibraryClass()
                && !definition.isLibraryClass()) {
              return;
            }
            if (classResolutionResult.isMultipleClassResolutionResult()) {
              builder.addTypeWithMultipleDefinitions(iface);
            }
            assert definition.isInterface();
            DexEncodedMethod result = definition.lookupMethod(methodProto, methodName);
            if (isMaximallySpecificCandidate(result)) {
              // The candidate is added and doing so will prohibit shadowed methods from being
              // in the set.
              builder.addCandidate(
                  definition, result, uniquePathOracle.onIncompletePath(definition));
            } else {
              // Look at the super-interfaces of this class and keep searching.
              resolveMethodStep3Helper(
                  methodProto,
                  methodName,
                  definition,
                  builder,
                  definition.superType,
                  Arrays.asList(definition.interfaces.values),
                  uniquePathOracle);
            }
          });
    }
    // Now look at indirect super interfaces.
    if (superType != null) {
      definitionFor(superType)
          .forEachClassResolutionResult(
              superClass -> {
                // Guard against going back into the program for resolution.
                if (escapeIfLibraryHasProgramSuperType
                    && clazz != null
                    && clazz.isLibraryClass()
                    && !superClass.isLibraryClass()) {
                  return;
                }
                resolveMethodStep3Helper(
                    methodProto,
                    methodName,
                    superClass,
                    builder,
                    superClass.superType,
                    Arrays.asList(superClass.interfaces.values),
                    uniquePathOracle);
              });
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
  public MethodResolutionResult resolveMethodOnInterface(
      DexType holder, DexProto proto, DexString methodName) {
    if (holder.isArrayType()) {
      return IncompatibleClassResult.INSTANCE;
    }
    MethodResolutionResult.Builder builder = MethodResolutionResult.builder();
    // Step 1: Lookup interface.
    definitionFor(holder)
        .forEachClassResolutionResult(
            definition -> {
              // If the definition is not an interface, resolution fails with an ICCE.
              if (!definition.isInterface()) {
                builder.addResolutionResult(IncompatibleClassResult.INSTANCE);
              } else {
                builder.addResolutionResult(
                    resolveMethodOnInterface(definition, proto, methodName));
              }
            });
    return builder.buildOrIfEmpty(ClassNotFoundResult.INSTANCE, holder);
  }

  public MethodResolutionResult resolveMethodOnInterface(
      DexClass definition, DexProto methodProto, DexString methodName) {
    assert definition.isInterface();
    // Step 2: Look for exact method on interface.
    DexEncodedMethod result = definition.lookupMethod(methodProto, methodName);
    if (result != null) {
      return MethodResolutionResult.createSingleResolutionResult(definition, definition, result);
    }
    // Step 3: Look for matching method on object class.
    MethodResolutionResult.Builder builder = MethodResolutionResult.builder();
    definitionFor(factory.objectType)
        .forEachClassResolutionResult(
            objectClass -> {
              DexEncodedMethod objectResult = objectClass.lookupMethod(methodProto, methodName);
              if (objectResult != null
                  && objectResult.accessFlags.isPublic()
                  && !objectResult.accessFlags.isAbstract()) {
                builder.addResolutionResult(
                    MethodResolutionResult.createSingleResolutionResult(
                        definition, objectClass, objectResult));
              } else {
                // Step 3: Look for maximally-specific superinterface methods or any interface
                // definition. This is the same for classes and interfaces.
                builder.addResolutionResult(
                    resolveMethodStep3(definition, methodProto, methodName));
              }
            });
    return builder.buildOrIfEmpty(ClassNotFoundResult.INSTANCE, Collections.emptySet());
  }

  static class MaximallySpecificMethodsBuilder {

    // The set of actual maximally specific methods.
    // This set is linked map so that in the case where a number of methods remain a deterministic
    // choice can be made. The map is from definition classes to their maximally specific method, or
    // in the case that a type has a candidate which is shadowed by a subinterface, the map will
    // map the class to a null entry, thus any addition to the map must check for key containment
    // prior to writing.
    private final LinkedHashMap<DexClass, DexEncodedMethod>
        maximallySpecificMethodsOnCompletePaths = new LinkedHashMap<>();
    private final LinkedHashMap<DexClass, DexEncodedMethod>
        maximallySpecificMethodsOnIncompletePaths = new LinkedHashMap<>();
    private final Function<DexType, ClassResolutionResult> definitionFor;
    private final Set<DexType> typesWithMultipleDefinitions = Sets.newIdentityHashSet();
    private final DexItemFactory factory;

    private MaximallySpecificMethodsBuilder(
        Function<DexType, ClassResolutionResult> definitionFor, DexItemFactory factory) {
      this.definitionFor = definitionFor;
      this.factory = factory;
    }

    void addTypeWithMultipleDefinitions(DexType type) {
      typesWithMultipleDefinitions.add(type);
    }

    @SuppressWarnings("ReferenceEquality")
    void addCandidate(DexClass holder, DexEncodedMethod method, boolean isIncompletePath) {
      // If this candidate is already a candidate or it is shadowed, then no need to continue.
      if (isIncompletePath) {
        if (!maximallySpecificMethodsOnIncompletePaths.containsKey(holder)) {
          maximallySpecificMethodsOnIncompletePaths.put(holder, method);
        }
        // Incomplete paths can shadow complete path results if all partial paths is
        // shadowing.
        //       I_L { f(); }
        //     /            \
        // J_L { f(); }  // J_P { f(); }
        // One way to track them is to count all coverings are equal to the splits. For now
        // we bail out.
        return;
      } else if (maximallySpecificMethodsOnCompletePaths.containsKey(holder)) {
        return;
      }
      maximallySpecificMethodsOnCompletePaths.put(holder, method);
      // Prune exiting candidates and prohibit future candidates in the super hierarchy.
      assert holder.isInterface();
      assert holder.superType == factory.objectType;
      for (DexType iface : holder.interfaces.values) {
        markShadowed(iface);
      }
    }

    @SuppressWarnings("ReferenceEquality")
    private void markShadowed(DexType type) {
      if (type == null) {
        return;
      }
      definitionFor
          .apply(type)
          .forEachClassResolutionResult(
              clazz -> {
                assert clazz.isInterface();
                assert clazz.superType == factory.objectType;
                // A null entry signifies that the candidate is shadowed blocking future candidates.
                // If the candidate is already shadowed at this type there is no need to shadow
                // further up.
                if (maximallySpecificMethodsOnCompletePaths.getOrDefault(
                        clazz, DexEncodedMethod.SENTINEL)
                    == null) {
                  return;
                }
                maximallySpecificMethodsOnCompletePaths.put(clazz, null);
                maximallySpecificMethodsOnIncompletePaths.put(clazz, null);
                for (DexType iface : clazz.interfaces.values) {
                  markShadowed(iface);
                }
              });
    }

    DexClassAndMethod lookup() {
      return internalResolve(null).getResolutionPair();
    }

    MethodResolutionResult resolve(DexClass initialResolutionHolder) {
      assert initialResolutionHolder != null;
      return internalResolve(initialResolutionHolder);
    }

    private MethodResolutionResult internalResolve(DexClass initialResolutionHolder) {
      if (maximallySpecificMethodsOnCompletePaths.isEmpty()
          && maximallySpecificMethodsOnIncompletePaths.isEmpty()) {
        return NoSuchMethodResult.INSTANCE;
      }
      List<Entry<DexClass, DexEncodedMethod>> nonAbstractOnComplete =
          getNonAbstractMethods(maximallySpecificMethodsOnCompletePaths);
      if (nonAbstractOnComplete.size() > 1) {
        return IncompatibleClassResult.create(
            ListUtils.map(nonAbstractOnComplete, Entry::getValue));
      }
      List<Entry<DexClass, DexEncodedMethod>> nonAbstractOnIncomplete =
          getNonAbstractMethods(maximallySpecificMethodsOnIncompletePaths);
      if (nonAbstractOnIncomplete.isEmpty()) {
        // If there are no non-abstract methods, then any candidate on a complete path will suffice
        // as a target. For deterministic resolution, we return the first mapped method (of the
        // linked map).
        if (nonAbstractOnComplete.isEmpty()) {
          Entry<DexClass, DexEncodedMethod> abstractMethod =
              firstNonNullEntry(maximallySpecificMethodsOnCompletePaths);
          if (abstractMethod == null) {
            abstractMethod = firstNonNullEntry(maximallySpecificMethodsOnIncompletePaths);
          }
          assert abstractMethod != null && abstractMethod.getValue().isAbstract();
          return singleResultHelper(initialResolutionHolder, abstractMethod);
        } else {
          // If there is exactly one non-abstract method (a default method) it is the resolution
          // target.
          return singleResultHelper(initialResolutionHolder, nonAbstractOnComplete.get(0));
        }
      } else {
        // We cannot guarantee a single target or an error, so we have to report all on incomplete
        // paths.
        MethodResolutionResult.Builder builder =
            MethodResolutionResult.builder().allowMultipleProgramResults();
        if (nonAbstractOnComplete.isEmpty()) {
          assert !typesWithMultipleDefinitions.isEmpty();
          builder.addResolutionResult(NoSuchMethodResult.INSTANCE);
        } else {
          nonAbstractOnComplete.forEach(
              entry ->
                  builder.addResolutionResult(singleResultHelper(initialResolutionHolder, entry)));
        }
        nonAbstractOnIncomplete.forEach(
            entry ->
                builder.addResolutionResult(singleResultHelper(initialResolutionHolder, entry)));
        return builder.buildOrIfEmpty(NoSuchMethodResult.INSTANCE, typesWithMultipleDefinitions);
      }
    }

    private List<Entry<DexClass, DexEncodedMethod>> getNonAbstractMethods(
        Map<DexClass, DexEncodedMethod> candidates) {
      List<Entry<DexClass, DexEncodedMethod>> nonAbstractMethods = new ArrayList<>();
      for (Entry<DexClass, DexEncodedMethod> entry : candidates.entrySet()) {
        DexEncodedMethod method = entry.getValue();
        if (method == null) {
          // Ignore shadowed candidates.
          continue;
        }
        if (method.isNonAbstractVirtualMethod()) {
          nonAbstractMethods.add(entry);
        }
      }
      return nonAbstractMethods;
    }

    List<Entry<DexClass, DexEncodedMethod>> getAbstractMethods() {
      List<Entry<DexClass, DexEncodedMethod>> abstractMethods = new ArrayList<>();
      addAbstractMethods(abstractMethods, maximallySpecificMethodsOnCompletePaths);
      addAbstractMethods(abstractMethods, maximallySpecificMethodsOnIncompletePaths);
      return abstractMethods;
    }

    private void addAbstractMethods(
        List<Entry<DexClass, DexEncodedMethod>> abstractMethods,
        Map<DexClass, DexEncodedMethod> candidates) {
      for (Entry<DexClass, DexEncodedMethod> entry : candidates.entrySet()) {
        DexEncodedMethod method = entry.getValue();
        if (method == null) {
          // Ignore shadowed candidates.
          continue;
        }
        if (method.isAbstract()) {
          abstractMethods.add(entry);
        }
      }
    }

    private static SingleResolutionResult<?> singleResultHelper(
        DexClass initialResolutionResult, Entry<DexClass, DexEncodedMethod> entry) {
      return MethodResolutionResult.createSingleResolutionResult(
          initialResolutionResult != null ? initialResolutionResult : entry.getKey(),
          entry.getKey(),
          entry.getValue());
    }

    private static Entry<DexClass, DexEncodedMethod> firstNonNullEntry(
        Map<DexClass, DexEncodedMethod> candidates) {
      for (Entry<DexClass, DexEncodedMethod> entry : candidates.entrySet()) {
        DexEncodedMethod method = entry.getValue();
        if (method != null) {
          return entry;
        }
      }
      return null;
    }
  }
}
