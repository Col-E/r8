// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

// This class is used to check that all non-abstract classes in an application implement the
// abstract/interface methods from their super types. This is used as a sanity check for
// minification.
public class ClassHierarchyVerifier {

  private final boolean allowDefaultInterfaceMethods;
  private final ClassHierarchy hierarchy;
  private final CodeInspector inspector;

  // State of the traversal.
  private final Deque<DexType> path = new ArrayDeque<>();
  private final Set<DexType> visited = Sets.newIdentityHashSet();

  public ClassHierarchyVerifier(CodeInspector inspector) {
    this(inspector, true);
  }

  public ClassHierarchyVerifier(CodeInspector inspector, boolean allowDefaultInterfaceMethods) {
    this.allowDefaultInterfaceMethods = allowDefaultInterfaceMethods;
    this.hierarchy = ClassHierarchy.build(inspector);
    this.inspector = inspector;
  }

  public void run() {
    try {
      // Top-down traversal of the class hierarchy starting from all the roots.
      for (FoundClassSubject classSubject : hierarchy.getRoots()) {
        visitType(classSubject, ImmutableSet.of());
      }
    } finally {
      path.clear();
      visited.clear();
    }
  }

  private void visitType(
      FoundClassSubject classSubject, ImmutableSet<DexMethod> unimplementedInSupertype) {
    DexClass clazz = classSubject.getDexClass();
    DexType type = clazz.type;

    ImmutableSet.Builder<DexMethod> unimplementedInCurrentTypeBuilder = ImmutableSet.builder();
    if (clazz.accessFlags.isAbstract() || clazz.accessFlags.isInterface()) {
      // Collected methods that are still not implemented. This step needs to be repeated although
      // the type has been visited previously because we are coming from another path where the
      // set of unimplemented methods are (potentially) different.
      for (DexMethod method : unimplementedInSupertype) {
        if (clazz.lookupVirtualMethod(method) == null) {
          unimplementedInCurrentTypeBuilder.add(method);
        }
      }
      // Add methods that do not have an implementation. If this type has been visited previously,
      // then we have already checked that all subtypes implement these methods, and therefore we
      // do not have to add them to the set of unimplemented methods.
      if (!visited.contains(type)) {
        for (DexEncodedMethod method : clazz.virtualMethods()) {
          if (method.accessFlags.isAbstract()) {
            unimplementedInCurrentTypeBuilder.add(method.method);
          } else {
            assert allowDefaultInterfaceMethods || !clazz.accessFlags.isInterface();
          }
        }
      }
    } else {
      // Check that there are no unimplemented methods. This step needs to be repeated although the
      // type has been visited previously because we are coming from another path where the
      // set of unimplemented methods are (potentially) different.
      for (DexMethod method : unimplementedInSupertype) {
        if (clazz.lookupVirtualMethod(method) == null) {
          fail(classSubject, method);
        }
      }
      // Check that all methods have an implementation. This step can be skipped if the type has
      // already been visited previously.
      if (!visited.contains(type)) {
        for (DexEncodedMethod method : clazz.virtualMethods()) {
          if (method.accessFlags.isAbstract()) {
            fail(classSubject, method.method);
          }
        }
      }
    }

    path.push(type);
    visited.add(type);

    // Continue traversal downwards.
    ImmutableSet<DexMethod> unimplementedInCurrentType = unimplementedInCurrentTypeBuilder.build();
    for (FoundClassSubject subClassSubject : hierarchy.getDirectSubtypes(classSubject)) {
      visitType(subClassSubject, unimplementedInCurrentType);
    }

    DexType popped = path.pop();
    assert popped == type;
  }

  private void fail(ClassSubject classSubject, DexMethod method) {
    StringBuilder builder = new StringBuilder();
    builder.append("Non-abstract class ").append(classSubject.getFinalName());
    builder.append(" must implement method '").append(method.toSourceString()).append("'");

    if (classSubject.isRenamed()) {
      builder.append(" (original: ").append(classSubject.getOriginalName()).append(")");
    }

    Iterator<DexType> superTypeIterator = path.descendingIterator();
    if (superTypeIterator.hasNext()) {
      builder.append(", super types:");
      while (superTypeIterator.hasNext()) {
        DexType superType = superTypeIterator.next();
        ClassSubject superClassSubject = inspector.clazz(superType.toSourceString());
        assert superClassSubject.isPresent();

        builder.append(System.lineSeparator()).append(" - ");
        builder.append(superClassSubject.getFinalName());

        List<String> attributes = new ArrayList<>();
        if (superClassSubject.isRenamed()) {
          attributes.add("original: " + superClassSubject.getOriginalName());
        }
        MethodSubject methodSubject =
            superClassSubject.method(MethodSignature.fromDexMethod(method));
        if (methodSubject.isPresent()) {
          attributes.add("method: " + methodSubject.getOriginalSignature());
        }
        builder.append(" (").append(String.join(", ", attributes)).append(")");
      }
    } else {
      builder.append(", super types: none");
    }
    throw new AssertionError(builder.toString());
  }
}
