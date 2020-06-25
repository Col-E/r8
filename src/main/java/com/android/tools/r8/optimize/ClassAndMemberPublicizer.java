// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize;

import static com.android.tools.r8.dex.Constants.ACC_PRIVATE;
import static com.android.tools.r8.dex.Constants.ACC_PROTECTED;
import static com.android.tools.r8.dex.Constants.ACC_PUBLIC;
import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.ir.optimize.MethodPoolCollection;
import com.android.tools.r8.optimize.PublicizerLense.PublicizedLenseBuilder;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.Timing;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public final class ClassAndMemberPublicizer {

  private final DexApplication application;
  private final AppView<AppInfoWithLiveness> appView;
  private final SubtypingInfo subtypingInfo;
  private final MethodPoolCollection methodPoolCollection;

  private final PublicizedLenseBuilder lenseBuilder = PublicizerLense.createBuilder();

  private ClassAndMemberPublicizer(
      DexApplication application,
      AppView<AppInfoWithLiveness> appView,
      SubtypingInfo subtypingInfo) {
    this.application = application;
    this.appView = appView;
    this.subtypingInfo = subtypingInfo;
    this.methodPoolCollection =
        // We will add private instance methods when we promote them.
        new MethodPoolCollection(
            appView, subtypingInfo, MethodPoolCollection::excludesPrivateInstanceMethod);
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
      AppView<AppInfoWithLiveness> appView,
      SubtypingInfo subtypingInfo)
      throws ExecutionException {
    return new ClassAndMemberPublicizer(application, appView, subtypingInfo)
        .run(executorService, timing);
  }

  private GraphLense run(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    // Phase 1: Collect methods to check if private instance methods don't have conflicts.
    methodPoolCollection.buildAll(executorService, timing);

    // Phase 2: Visit classes and promote class/member to public if possible.
    timing.begin("Phase 2: promoteToPublic");
    for (DexClass iface : appView.appInfo().computeReachableInterfaces()) {
      publicizeType(iface.type);
    }
    publicizeType(appView.dexItemFactory().objectType);
    timing.end();

    return lenseBuilder.build(appView);
  }

  private void publicizeType(DexType type) {
    DexProgramClass clazz = asProgramClassOrNull(application.definitionFor(type));
    if (clazz != null) {
      publicizeClass(clazz);
    }
    subtypingInfo.forAllImmediateExtendsSubtypes(type, this::publicizeType);
  }

  private void publicizeClass(DexProgramClass clazz) {
    clazz.accessFlags.promoteToPublic();

    // Publicize fields.
    clazz.forEachField(
        field -> {
          if (field.isPublic()) {
            return;
          }
          if (!appView.appInfo().isAccessModificationAllowed(field.field)) {
            // TODO(b/131130038): Also do not publicize package-private and protected fields that
            //  are kept.
            if (field.isPrivate()) {
              return;
            }
          }
          field.accessFlags.promoteToPublic();
        });

    // Publicize methods.
    Set<DexEncodedMethod> privateInstanceMethods = new LinkedHashSet<>();
    clazz.forEachMethod(
        method -> {
          if (publicizeMethod(clazz, method)) {
            privateInstanceMethods.add(method);
          }
        });
    if (!privateInstanceMethods.isEmpty()) {
      clazz.virtualizeMethods(privateInstanceMethods);
    }

    // Publicize inner class attribute.
    InnerClassAttribute attr = clazz.getInnerClassAttributeForThisClass();
    if (attr != null) {
      int accessFlags = ((attr.getAccess() | ACC_PUBLIC) & ~ACC_PRIVATE) & ~ACC_PROTECTED;
      clazz.replaceInnerClassAttributeForThisClass(
          new InnerClassAttribute(
              accessFlags, attr.getInner(), attr.getOuter(), attr.getInnerName()));
    }
  }

  private boolean publicizeMethod(DexProgramClass holder, DexEncodedMethod method) {
    MethodAccessFlags accessFlags = method.accessFlags;
    if (accessFlags.isPublic()) {
      return false;
    }
    // If this method is mentioned in keep rules, do not transform (rule applications changed).
    if (!appView.appInfo().isAccessModificationAllowed(method.method)) {
      // TODO(b/131130038): Also do not publicize package-private and protected methods that are
      //  kept.
      if (method.isPrivate()) {
        return false;
      }
    }

    if (!accessFlags.isPrivate() || appView.dexItemFactory().isConstructor(method.method)) {
      // TODO(b/150589374): This should check for dispatch targets or just abandon in
      //  package-private.
      accessFlags.promoteToPublic();
      return false;
    }

    if (!accessFlags.isStatic()) {

      // We can't publicize private instance methods in interfaces or methods that are copied from
      // interfaces to lambda-desugared classes because this will be added as a new default method.
      // TODO(b/111118390): It might be possible to transform it into static methods, though.
      if (holder.isInterface() || accessFlags.isSynthetic()) {
        return false;
      }

      boolean wasSeen = methodPoolCollection.markIfNotSeen(holder, method.method);
      if (wasSeen) {
        // We can't do anything further because even renaming is not allowed due to the keep rule.
        if (!appView.appInfo().isMinificationAllowed(method.method)) {
          return false;
        }
        // TODO(b/111118390): Renaming will enable more private instance methods to be publicized.
        return false;
      }
      lenseBuilder.add(method.method);
      accessFlags.promoteToFinal();
      accessFlags.promoteToPublic();
      // The method just became public and is therefore not a library override.
      method.setLibraryMethodOverride(OptionalBool.FALSE);
      return true;
    }

    // For private static methods we can just relax the access to public, since
    // even though JLS prevents from declaring static method in derived class if
    // an instance method with same signature exists in superclass, JVM actually
    // does not take into account access of the static methods.
    accessFlags.promoteToPublic();
    return false;
  }
}
