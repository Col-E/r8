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
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.optimize.MemberPoolCollection.MemberPool;
import com.android.tools.r8.ir.optimize.MethodPoolCollection;
import com.android.tools.r8.optimize.PublicizerLens.PublicizedLensBuilder;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.Timing;
import com.google.common.base.Equivalence.Wrapper;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public final class AccessModifier {

  private final DexApplication application;
  private final AppView<AppInfoWithLiveness> appView;
  private final InternalOptions options;
  private final SubtypingInfo subtypingInfo;
  private final MethodPoolCollection methodPoolCollection;

  private final PublicizedLensBuilder lensBuilder = PublicizerLens.createBuilder();

  private AccessModifier(
      DexApplication application,
      AppView<AppInfoWithLiveness> appView,
      SubtypingInfo subtypingInfo) {
    this.application = application;
    this.appView = appView;
    this.options = appView.options();
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
  public static GraphLens run(
      ExecutorService executorService,
      Timing timing,
      DexApplication application,
      AppView<AppInfoWithLiveness> appView,
      SubtypingInfo subtypingInfo)
      throws ExecutionException {
    return new AccessModifier(application, appView, subtypingInfo).run(executorService, timing);
  }

  private GraphLens run(ExecutorService executorService, Timing timing) throws ExecutionException {
    // Phase 1: Collect methods to check if private instance methods don't have conflicts.
    methodPoolCollection.buildAll(executorService, timing);

    // Phase 2: Visit classes and promote class/member to public if possible.
    timing.begin("Phase 2: promoteToPublic");
    appView.appInfo().forEachReachableInterface(clazz -> processType(clazz.getType()));
    processType(appView.dexItemFactory().objectType);
    timing.end();

    return lensBuilder.build(appView);
  }

  private void doPublicize(ProgramDefinition definition) {
    definition.getAccessFlags().promoteToPublic();
  }

  private void processType(DexType type) {
    DexProgramClass clazz = asProgramClassOrNull(application.definitionFor(type));
    if (clazz != null) {
      processClass(clazz);
    }
    subtypingInfo.forAllImmediateExtendsSubtypes(type, this::processType);
  }

  private void processClass(DexProgramClass clazz) {
    if (appView.appInfo().isAccessModificationAllowed(clazz)) {
      doPublicize(clazz);
    }

    // Publicize fields.
    clazz.forEachProgramField(this::processField);

    // Publicize methods.
    Set<DexEncodedMethod> privateInstanceMethods = new LinkedHashSet<>();
    clazz.forEachProgramMethod(
        method -> {
          if (publicizeMethod(method)) {
            privateInstanceMethods.add(method.getDefinition());
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

  private void processField(ProgramField field) {
    if (appView.appInfo().isAccessModificationAllowed(field)) {
      publicizeField(field);
    }
  }

  private void publicizeField(ProgramField field) {
    FieldAccessFlags flags = field.getAccessFlags();
    if (!flags.isPublic()) {
      flags.promoteToPublic();
    }
  }

  private boolean publicizeMethod(ProgramMethod method) {
    MethodAccessFlags accessFlags = method.getAccessFlags();
    if (accessFlags.isPublic()) {
      return false;
    }
    // If this method is mentioned in keep rules, do not transform (rule applications changed).
    DexEncodedMethod definition = method.getDefinition();
    if (!appView.appInfo().isAccessModificationAllowed(method)) {
      // TODO(b/131130038): Also do not publicize package-private and protected methods that are
      //  kept.
      if (definition.isPrivate()) {
        return false;
      }
    }

    if (method.getDefinition().isInstanceInitializer() || accessFlags.isProtected()) {
      doPublicize(method);
      return false;
    }

    if (accessFlags.isPackagePrivate()) {
      // If we publicize a package private method we have to ensure there is no overrides of it. We
      // could potentially publicize a method if it only has package-private overrides.
      // TODO(b/182136236): See if we can break the hierarchy for clusters.
      MemberPool<DexMethod> memberPool = methodPoolCollection.get(method.getHolder());
      Wrapper<DexMethod> methodKey = MethodSignatureEquivalence.get().wrap(method.getReference());
      if (memberPool.below(
          methodKey,
          false,
          true,
          (clazz, ignored) ->
              !method.getContextType().getPackageName().equals(clazz.getType().getPackageName()))) {
        return false;
      }
      doPublicize(method);
      return false;
    }

    assert accessFlags.isPrivate();

    if (accessFlags.isStatic()) {
      // For private static methods we can just relax the access to public, since
      // even though JLS prevents from declaring static method in derived class if
      // an instance method with same signature exists in superclass, JVM actually
      // does not take into account access of the static methods.
      doPublicize(method);
      return false;
    }

    // We can't publicize private instance methods in interfaces or methods that are copied from
    // interfaces to lambda-desugared classes because this will be added as a new default method.
    // TODO(b/111118390): It might be possible to transform it into static methods, though.
    if (method.getHolder().isInterface() || accessFlags.isSynthetic()) {
      return false;
    }

    boolean wasSeen = methodPoolCollection.markIfNotSeen(method.getHolder(), method.getReference());
    if (wasSeen) {
      // We can't do anything further because even renaming is not allowed due to the keep rule.
      if (!appView.appInfo().isMinificationAllowed(method)) {
        return false;
      }
      // TODO(b/111118390): Renaming will enable more private instance methods to be publicized.
      return false;
    }
    lensBuilder.add(method.getReference());
    accessFlags.promoteToFinal();
    doPublicize(method);
    // The method just became public and is therefore not a library override.
    definition.setLibraryMethodOverride(OptionalBool.FALSE);
    return true;
  }
}
