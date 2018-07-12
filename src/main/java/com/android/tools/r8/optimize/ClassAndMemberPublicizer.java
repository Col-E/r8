// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.ir.optimize.MethodPoolCollection;
import com.android.tools.r8.optimize.PublicizerLense.PublicizedLenseBuilder;
import com.android.tools.r8.shaking.RootSetBuilder.RootSet;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.android.tools.r8.utils.Timing;
import com.google.common.base.Equivalence;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public final class ClassAndMemberPublicizer {
  private final DexApplication application;
  private final AppView appView;
  private final RootSet rootSet;
  private final PublicizedLenseBuilder lenseBuilder;

  private final Equivalence<DexMethod> equivalence = MethodSignatureEquivalence.get();
  private final MethodPoolCollection methodPoolCollection;

  private ClassAndMemberPublicizer(DexApplication application, AppView appView, RootSet rootSet) {
    this.application = application;
    this.appView = appView;
    this.methodPoolCollection = new MethodPoolCollection(application);
    this.rootSet = rootSet;
    lenseBuilder = PublicizerLense.createBuilder();
  }

  /**
   * Marks all package private and protected methods and fields as public. Makes all private static
   * methods public. Makes private instance methods public final instance methods, if possible.
   *
   * <p>This will destructively update the DexApplication passed in as argument.
   */
  public static GraphLense run(
      ExecutorService executorService,
      Timing timing,
      DexApplication application,
      AppView appView,
      RootSet rootSet)
      throws ExecutionException {
    return new ClassAndMemberPublicizer(application, appView, rootSet).run(executorService, timing);
  }

  private GraphLense run(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    // Phase 1: Collect methods to check if private instance methods don't have conflicts.
    methodPoolCollection.buildAll(executorService, timing);

    // Phase 2: Visit classes and promote class/member to public if possible.
    timing.begin("Phase 2: promoteToPublic");
    DexType.forAllInterfaces(appView.getDexItemFactory(), this::publicizeType);
    publicizeType(appView.getDexItemFactory().objectType);
    timing.end();

    return lenseBuilder.build(appView);
  }

  private void publicizeType(DexType type) {
    DexClass clazz = application.definitionFor(type);
    if (clazz != null && clazz.isProgramClass()) {
      clazz.accessFlags.promoteToPublic();
      clazz.forEachField(field -> field.accessFlags.promoteToPublic());
      Set<DexEncodedMethod> privateInstanceEncodedMethods = new LinkedHashSet<>();
      clazz.forEachMethod(encodedMethod -> {
        if (publicizeMethod(clazz, encodedMethod)) {
          privateInstanceEncodedMethods.add(encodedMethod);
        }
      });
      if (!privateInstanceEncodedMethods.isEmpty()) {
        clazz.virtualizeMethods(privateInstanceEncodedMethods);
      }
    }

    type.forAllExtendsSubtypes(this::publicizeType);
  }

  private boolean publicizeMethod(DexClass holder, DexEncodedMethod encodedMethod) {
    MethodAccessFlags accessFlags = encodedMethod.accessFlags;
    if (accessFlags.isPublic()) {
      return false;
    }

    if (appView.getDexItemFactory().isClassConstructor(encodedMethod.method)) {
      return false;
    }

    if (!accessFlags.isPrivate()) {
      accessFlags.unsetProtected();
      accessFlags.setPublic();
      return false;
    }
    assert accessFlags.isPrivate();

    if (appView.getDexItemFactory().isConstructor(encodedMethod.method)) {
      // TODO(b/72211928)
      return false;
    }

    if (!accessFlags.isStatic()) {
      // If this method is mentioned in keep rules, do not transform (rule applications changed).
      if (rootSet.noShrinking.containsKey(encodedMethod)) {
        return false;
      }

      // We can't publicize private instance methods in interfaces or methods that are copied from
      // interfaces to lambda-desugared classes because this will be added as a new default method.
      // TODO(b/111118390): It might be possible to transform it into static methods, though.
      if (holder.isInterface() || accessFlags.isSynthetic()) {
        return false;
      }

      boolean wasSeen = methodPoolCollection.markIfNotSeen(holder, encodedMethod.method);
      if (wasSeen) {
        // We can't do anything further because even renaming is not allowed due to the keep rule.
        if (rootSet.noObfuscation.contains(encodedMethod)) {
          return false;
        }
        // TODO(b/111118390): Renaming will enable more private instance methods to be publicized.
        return false;
      }
      lenseBuilder.add(encodedMethod.method);
      accessFlags.unsetPrivate();
      accessFlags.setFinal();
      accessFlags.setPublic();
      // Although the current method became public, it surely has the single virtual target.
      encodedMethod.method.setSingleVirtualMethodCache(
          encodedMethod.method.getHolder(), encodedMethod);
      encodedMethod.markPublicized();
      return true;
    }

    // For private static methods we can just relax the access to private, since
    // even though JLS prevents from declaring static method in derived class if
    // an instance method with same signature exists in superclass, JVM actually
    // does not take into account access of the static methods.
    accessFlags.unsetPrivate();
    accessFlags.setPublic();
    return false;
  }
}
