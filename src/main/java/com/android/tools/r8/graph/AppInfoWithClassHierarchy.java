// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import static com.android.tools.r8.utils.TraversalContinuation.BREAK;
import static com.android.tools.r8.utils.TraversalContinuation.CONTINUE;

import com.android.tools.r8.ir.desugar.LambdaDescriptor;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/* Specific subclass of AppInfo designed to support desugaring in D8. Desugaring requires a
 * minimal amount of knowledge in the overall program, provided through classpath. Basic
 * features are present, such as static and super look-ups, or isSubtype.
 */
public class AppInfoWithClassHierarchy extends AppInfo {

  public AppInfoWithClassHierarchy(DexApplication application) {
    super(application);
  }

  public AppInfoWithClassHierarchy(AppInfo previous) {
    super(previous);
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
    return subtype == supertype || isStrictSubtypeOf(subtype, supertype);
  }

  public boolean isStrictSubtypeOf(DexType subtype, DexType supertype) {
    assert subtype != null;
    assert supertype != null;
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
    // TODO(b/147658738): Clean up the code to not call on non-class types or fix this.
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
  public DexEncodedField lookupInstanceTarget(DexType type, DexField field) {
    assert checkIfObsolete();
    assert type.isClassType();
    DexEncodedField result = resolveFieldOn(type, field);
    return result == null || result.accessFlags.isStatic() ? null : result;
  }

  /**
   * Lookup static field starting in type and following the interface and super chain.
   *
   * <p>The result is the field that will be hit at runtime, if such field is known. A result of
   * null indicates that the field is either undefined or not a static field.
   */
  public DexEncodedField lookupStaticTarget(DexType type, DexField field) {
    assert checkIfObsolete();
    assert type.isClassType();
    DexEncodedField result = resolveFieldOn(type, field);
    return result == null || !result.accessFlags.isStatic() ? null : result;
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
  @Deprecated // TODO(b/147578480): Remove
  public DexEncodedMethod lookupStaticTarget(DexMethod method, DexType invocationContext) {
    assert checkIfObsolete();
    return lookupStaticTarget(method, toProgramClass(invocationContext));
  }

  public final DexEncodedMethod lookupStaticTarget(
      DexMethod method, DexProgramClass invocationContext) {
    assert checkIfObsolete();
    return resolveMethod(method.holder, method).lookupInvokeStaticTarget(invocationContext, this);
  }

  /**
   * Lookup super method following the super chain from the holder of {@code method}.
   *
   * <p>This method will resolve the method on the holder of {@code method} and only return a
   * non-null value if the result of resolution was an instance (i.e. non-static) method.
   *
   * @param method the method to lookup
   * @param invocationContext the class the invoke is contained in, i.e., the holder of the caller.
   * @return The actual target for {@code method} or {@code null} if none found.
   */
  @Deprecated // TODO(b/147578480): Remove
  public DexEncodedMethod lookupSuperTarget(DexMethod method, DexType invocationContext) {
    assert checkIfObsolete();
    return lookupSuperTarget(method, toProgramClass(invocationContext));
  }

  public final DexEncodedMethod lookupSuperTarget(
      DexMethod method, DexProgramClass invocationContext) {
    assert checkIfObsolete();
    return resolveMethod(method.holder, method).lookupInvokeSuperTarget(invocationContext, this);
  }

  /**
   * Lookup direct method following the super chain from the holder of {@code method}.
   *
   * <p>This method will lookup private and constructor methods.
   *
   * @param method the method to lookup
   * @return The actual target for {@code method} or {@code null} if none found.
   */
  @Deprecated // TODO(b/147578480): Remove
  public DexEncodedMethod lookupDirectTarget(DexMethod method, DexType invocationContext) {
    assert checkIfObsolete();
    return lookupDirectTarget(method, toProgramClass(invocationContext));
  }

  public DexEncodedMethod lookupDirectTarget(DexMethod method, DexProgramClass invocationContext) {
    assert checkIfObsolete();
    return resolveMethod(method.holder, method).lookupInvokeDirectTarget(invocationContext, this);
  }
}
