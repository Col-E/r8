// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import java.util.LinkedList;

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

  public boolean isSubtype(DexType subtype, DexType supertype) {
    LinkedList<DexType> workList = new LinkedList<>();
    workList.add(subtype);
    while (!workList.isEmpty()) {
      DexClass subtypeClass = definitionFor(subtype);
      if (subtypeClass == null) {
        // What to do here? Report missing type?
        continue;
      }
      if (subtypeClass.superType == supertype) {
        return true;
      } else {
        workList.add(subtypeClass.superType);
      }
      for (DexType itf : subtypeClass.interfaces.values) {
        if (itf == supertype) {
          return true;
        } else {
          workList.add(itf);
        }
      }
    }
    return false;
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

  private DexProgramClass toProgramClass(DexType type) {
    assert type.isClassType();
    DexClass clazz = definitionFor(type);
    if (clazz == null) {
      return null;
    }
    assert clazz.isProgramClass();
    return clazz.asProgramClass();
  }
}
