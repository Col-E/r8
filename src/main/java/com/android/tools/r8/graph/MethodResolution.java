// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.graph.MethodResolutionResult.ArrayCloneMethodResult;
import com.android.tools.r8.graph.MethodResolutionResult.ClassNotFoundResult;
import com.android.tools.r8.graph.MethodResolutionResult.IllegalAccessOrNoSuchMethodResult;
import com.android.tools.r8.graph.MethodResolutionResult.IncompatibleClassResult;
import com.android.tools.r8.graph.MethodResolutionResult.NoSuchMethodResult;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.ir.desugar.LambdaDescriptor;
import com.android.tools.r8.utils.ListUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Function;

/**
 * Implements resolution of a method descriptor against a type.
 *
 * <p>See <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-5.html#jvms-5.4.3.3">
 * Section 5.4.3.3 of the JVM Spec</a>.
 */
public class MethodResolution {

  private final Function<DexType, DexClass> definitionFor;
  private final DexItemFactory factory;

  public MethodResolution(Function<DexType, DexClass> definitionFor, DexItemFactory factory) {
    this.definitionFor = definitionFor;
    this.factory = factory;
  }

  private DexClass definitionFor(DexType type) {
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
    DexClass definition = definitionFor(holder);
    if (definition == null) {
      return ClassNotFoundResult.INSTANCE;
    } else if (definition.isInterface()) {
      return resolveMethodOnInterface(definition, method.getProto(), method.getName());
    } else {
      return resolveMethodOnClass(definition, method.getProto(), method.getName());
    }
  }

  /**
   * Implements resolution of a method descriptor against an array type.
   *
   * <p>See <a href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-10.html#jls-10.7">Section
   * 10.7 of the Java Language Specification</a>. All invokations will have target java.lang.Object
   * except clone which has no target.
   */
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
    DexClass clazz = definitionFor(holder);
    if (clazz == null) {
      return ClassNotFoundResult.INSTANCE;
    }
    // Step 1: If holder is an interface, resolution fails with an ICCE. We return null.
    if (clazz.isInterface()) {
      return IncompatibleClassResult.INSTANCE;
    }
    return resolveMethodOnClass(clazz, methodProto, methodName);
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
      return new SingleResolutionResult(initialResolutionHolder, clazz, result);
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
      return new SingleResolutionResult(initialResolutionHolder, clazz, result);
    }
    // Pt 3: Apply step two to direct superclass of holder.
    if (clazz.superType != null) {
      DexClass superClass = definitionFor(clazz.superType);
      if (superClass != null) {
        return resolveMethodOnClassStep2(
            superClass, methodProto, methodName, initialResolutionHolder);
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
        method.getProto(), method.getName(), factory.objectType, lambda.interfaces, builder);
    return builder;
  }

  /** Helper method that builds the set of maximally specific methods. */
  private void resolveMethodStep3Helper(
      DexProto methodProto,
      DexString methodName,
      DexClass clazz,
      MaximallySpecificMethodsBuilder builder) {
    resolveMethodStep3Helper(
        methodProto, methodName, clazz.superType, Arrays.asList(clazz.interfaces.values), builder);
  }

  private void resolveMethodStep3Helper(
      DexProto methodProto,
      DexString methodName,
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
      DexEncodedMethod result = definition.lookupMethod(methodProto, methodName);
      if (isMaximallySpecificCandidate(result)) {
        // The candidate is added and doing so will prohibit shadowed methods from being in the set.
        builder.addCandidate(definition, result);
      } else {
        // Look at the super-interfaces of this class and keep searching.
        resolveMethodStep3Helper(methodProto, methodName, definition, builder);
      }
    }
    // Now look at indirect super interfaces.
    if (superType != null) {
      DexClass superClass = definitionFor(superType);
      if (superClass != null) {
        resolveMethodStep3Helper(methodProto, methodName, superClass, builder);
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
    return resolveMethodOnInterface(definition, proto, methodName);
  }

  public MethodResolutionResult resolveMethodOnInterface(
      DexClass definition, DexProto methodProto, DexString methodName) {
    assert definition.isInterface();
    // Step 2: Look for exact method on interface.
    DexEncodedMethod result = definition.lookupMethod(methodProto, methodName);
    if (result != null) {
      return new SingleResolutionResult(definition, definition, result);
    }
    // Step 3: Look for matching method on object class.
    DexClass objectClass = definitionFor(factory.objectType);
    if (objectClass == null) {
      return ClassNotFoundResult.INSTANCE;
    }
    result = objectClass.lookupMethod(methodProto, methodName);
    if (result != null && result.accessFlags.isPublic() && !result.accessFlags.isAbstract()) {
      return new SingleResolutionResult(definition, objectClass, result);
    }
    // Step 3: Look for maximally-specific superinterface methods or any interface definition.
    //         This is the same for classes and interfaces.
    return resolveMethodStep3(definition, methodProto, methodName);
  }

  static class MaximallySpecificMethodsBuilder {

    // The set of actual maximally specific methods.
    // This set is linked map so that in the case where a number of methods remain a deterministic
    // choice can be made. The map is from definition classes to their maximally specific method, or
    // in the case that a type has a candidate which is shadowed by a subinterface, the map will
    // map the class to a null entry, thus any addition to the map must check for key containment
    // prior to writing.
    private final LinkedHashMap<DexClass, DexEncodedMethod> maximallySpecificMethods =
        new LinkedHashMap<>();
    private final Function<DexType, DexClass> definitionFor;
    private final DexItemFactory factory;

    private MaximallySpecificMethodsBuilder(
        Function<DexType, DexClass> definitionFor, DexItemFactory factory) {
      this.definitionFor = definitionFor;
      this.factory = factory;
    }

    void addCandidate(DexClass holder, DexEncodedMethod method) {
      // If this candidate is already a candidate or it is shadowed, then no need to continue.
      if (maximallySpecificMethods.containsKey(holder)) {
        return;
      }
      maximallySpecificMethods.put(holder, method);
      // Prune exiting candidates and prohibit future candidates in the super hierarchy.
      assert holder.isInterface();
      assert holder.superType == factory.objectType;
      for (DexType iface : holder.interfaces.values) {
        markShadowed(iface);
      }
    }

    private void markShadowed(DexType type) {
      if (type == null) {
        return;
      }
      DexClass clazz = definitionFor.apply(type);
      if (clazz == null) {
        return;
      }
      assert clazz.isInterface();
      assert clazz.superType == factory.objectType;
      // A null entry signifies that the candidate is shadowed blocking future candidates.
      // If the candidate is already shadowed at this type there is no need to shadow further up.
      if (maximallySpecificMethods.containsKey(clazz)
          && maximallySpecificMethods.get(clazz) == null) {
        return;
      }
      maximallySpecificMethods.put(clazz, null);
      for (DexType iface : clazz.interfaces.values) {
        markShadowed(iface);
      }
    }

    DexClassAndMethod lookup() {
      return internalResolve(null).getResolutionPair();
    }

    MethodResolutionResult resolve(DexClass initialResolutionHolder) {
      assert initialResolutionHolder != null;
      return internalResolve(initialResolutionHolder);
    }

    private MethodResolutionResult internalResolve(DexClass initialResolutionHolder) {
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
