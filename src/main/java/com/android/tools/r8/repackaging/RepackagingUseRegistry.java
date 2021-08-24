// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackaging;

import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMember;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.graph.InitClassLens;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.graph.MemberResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.NestHostClassAttribute;
import com.android.tools.r8.graph.NestMemberClassAttribute;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.SuccessfulMemberResolutionResult;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class RepackagingUseRegistry extends UseRegistry {

  private final AppInfoWithLiveness appInfo;
  private final InternalOptions options;
  private final RepackagingConstraintGraph constraintGraph;
  private final ProgramDefinition context;
  private final InitClassLens initClassLens;
  private final RepackagingConstraintGraph.Node node;
  private final RepackagingConstraintGraph.Node missingTypeNode;

  public RepackagingUseRegistry(
      AppView<AppInfoWithLiveness> appView,
      RepackagingConstraintGraph constraintGraph,
      ProgramDefinition context,
      RepackagingConstraintGraph.Node missingTypeNode) {
    super(appView.dexItemFactory());
    this.appInfo = appView.appInfo();
    this.options = appView.options();
    this.constraintGraph = constraintGraph;
    this.context = context;
    this.initClassLens = appView.initClassLens();
    this.node = constraintGraph.getNode(context.getDefinition());
    this.missingTypeNode = missingTypeNode;
  }

  private boolean isOnlyAccessibleFromSamePackage(DexClass referencedClass) {
    ClassAccessFlags accessFlags = referencedClass.getAccessFlags();
    if (accessFlags.isPackagePrivate()) {
      return true;
    }
    if (accessFlags.isProtected()
        && !appInfo.isSubtype(context.getContextType(), referencedClass.getType())) {
      return true;
    }
    return false;
  }

  private boolean isOnlyAccessibleFromSamePackage(
      SuccessfulMemberResolutionResult<?, ?> resolutionResult, boolean isInvoke) {
    AccessFlags<?> accessFlags = resolutionResult.getResolutionPair().getAccessFlags();
    if (accessFlags.isPackagePrivate()) {
      return true;
    }
    if (accessFlags.isProtected()) {
      if (!appInfo.isSubtype(
          context.getContextType(), resolutionResult.getResolvedHolder().getType())) {
        return true;
      }
      // Check for assignability if we are generating CF:
      // https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.10.1.8
      if (isInvoke
          && options.isGeneratingClassFiles()
          && !appInfo.isSubtype(
              resolutionResult.getInitialResolutionHolder().getType(), context.getContextType())) {
        return true;
      }
    }
    return false;
  }

  public void registerFieldAccess(DexField field) {
    registerMemberAccess(appInfo.resolveField(field), false);
  }

  public ProgramMethod registerMethodReference(DexMethod method) {
    MethodResolutionResult resolutionResult = appInfo.unsafeResolveMethodDueToDexFormat(method);
    registerMemberAccess(resolutionResult, false);
    return resolutionResult.isSingleResolution()
        ? resolutionResult.asSingleResolution().getResolvedProgramMethod()
        : null;
  }

  private void registerMemberAccessForInvoke(MemberResolutionResult<?, ?> resolutionResult) {
    registerMemberAccess(resolutionResult, true);
  }

  public void registerMemberAccess(MemberResolutionResult<?, ?> resolutionResult) {
    registerMemberAccess(resolutionResult, false);
  }

  private void registerMemberAccess(
      MemberResolutionResult<?, ?> resolutionResult, boolean isInvoke) {
    if (!resolutionResult.isSuccessfulMemberResolutionResult()) {
      // To preserve errors in the original program, we need to look at the failure dependencies.
      // For example, if this member accesses a package-private method in another package, and we
      // move the two members to the same package, then the invoke would no longer fail with an
      // IllegalAccessError.

      // For fields and methods that cannot be found the chance of recovering by repackaging is
      // pretty slim thus we allow for repackaging the callers.
      if (resolutionResult.isFieldResolutionResult()) {
        assert resolutionResult.asFieldResolutionResult().isFailedResolution();
        return;
      }
      MethodResolutionResult methodResult = resolutionResult.asMethodResolutionResult();
      if (methodResult.isClassNotFoundResult()
          || methodResult.isArrayCloneMethodResult()
          || methodResult.isNoSuchMethodErrorResult(context.getContextClass(), appInfo)) {
        return;
      }
      node.addNeighbor(missingTypeNode);
      return;
    }

    SuccessfulMemberResolutionResult<?, ?> successfulResolutionResult =
        resolutionResult.asSuccessfulMemberResolutionResult();

    // Check access to the initial resolution holder.
    DexClass initialResolutionHolder = successfulResolutionResult.getInitialResolutionHolder();
    registerClassTypeAccess(initialResolutionHolder);

    // Similarly, check access to the resolved member.
    DexClassAndMember<?, ?> resolutionPair = successfulResolutionResult.getResolutionPair();
    if (resolutionPair != null) {
      RepackagingConstraintGraph.Node resolvedMemberNode =
          constraintGraph.getNode(resolutionPair.getDefinition());
      if (resolvedMemberNode != null
          && isOnlyAccessibleFromSamePackage(successfulResolutionResult, isInvoke)) {
        node.addNeighbor(resolvedMemberNode);
      }
    }
  }

  private void registerTypeAccess(DexType type) {
    registerTypeAccess(type, this::registerClassTypeAccess);
  }

  private void registerTypeAccess(DexType type, Consumer<DexClass> consumer) {
    if (type.isArrayType()) {
      registerTypeAccess(type.toBaseType(appInfo.dexItemFactory()), consumer);
      return;
    }
    if (type.isPrimitiveType() || type.isVoidType()) {
      return;
    }
    assert type.isClassType();
    DexClass clazz = appInfo.definitionFor(type);
    if (clazz != null) {
      consumer.accept(clazz);
    }
  }

  private void registerClassTypeAccess(DexClass clazz) {
    registerClassTypeAccess(clazz, this::isOnlyAccessibleFromSamePackage);
  }

  private void registerClassTypeAccess(DexClass clazz, Predicate<DexClass> predicate) {
    // We only want to connect the current method node to the class node if the access requires the
    // two nodes to be in the same package. Therefore, we ignore accesses to program classes outside
    // the current package.
    RepackagingConstraintGraph.Node classNode = constraintGraph.getNode(clazz);
    if (classNode != null && predicate.test(clazz)) {
      node.addNeighbor(classNode);
    }
  }

  @Override
  public void registerInitClass(DexType type) {
    registerFieldAccess(initClassLens.getInitClassField(type));
  }

  @Override
  public void registerInvokeVirtual(DexMethod invokedMethod) {
    registerMemberAccessForInvoke(appInfo.resolveMethod(invokedMethod, false));
  }

  @Override
  public void registerInvokeDirect(DexMethod invokedMethod) {
    registerMemberAccessForInvoke(appInfo.unsafeResolveMethodDueToDexFormat(invokedMethod));
  }

  @Override
  public void registerInvokeStatic(DexMethod invokedMethod) {
    registerMemberAccessForInvoke(appInfo.unsafeResolveMethodDueToDexFormat(invokedMethod));
  }

  @Override
  public void registerInvokeInterface(DexMethod invokedMethod) {
    registerMemberAccessForInvoke(appInfo.resolveMethod(invokedMethod, true));
  }

  @Override
  public void registerInvokeSuper(DexMethod invokedMethod) {
    registerMemberAccessForInvoke(appInfo.unsafeResolveMethodDueToDexFormat(invokedMethod));
  }

  @Override
  public void registerInstanceFieldRead(DexField field) {
    registerFieldAccess(field);
  }

  @Override
  public void registerInstanceFieldWrite(DexField field) {
    registerFieldAccess(field);
  }

  @Override
  public void registerNewInstance(DexType type) {
    registerTypeAccess(type);
  }

  @Override
  public void registerStaticFieldRead(DexField field) {
    registerFieldAccess(field);
  }

  @Override
  public void registerStaticFieldWrite(DexField field) {
    registerFieldAccess(field);
  }

  @Override
  public void registerTypeReference(DexType type) {
    registerTypeAccess(type);
  }

  @Override
  public void registerInstanceOf(DexType type) {
    registerTypeAccess(type);
  }

  public void registerEnclosingMethodAttribute(EnclosingMethodAttribute enclosingMethodAttribute) {
    if (enclosingMethodAttribute == null) {
      return;
    }
    // For references in enclosing method attributes we add an edge from the context to the
    // referenced item even if the item would be accessible from another package, to make sure that
    // we don't split such classes into different packages.
    if (enclosingMethodAttribute.getEnclosingClass() != null) {
      registerTypeAccess(
          enclosingMethodAttribute.getEnclosingClass(),
          clazz -> registerClassTypeAccess(clazz, alwaysTrue()));
    }
    if (enclosingMethodAttribute.getEnclosingMethod() != null) {
      ProgramMethod method = registerMethodReference(enclosingMethodAttribute.getEnclosingMethod());
      if (method != null) {
        registerClassTypeAccess(method.getHolder(), alwaysTrue());
      }
    }
  }

  public void registerInnerClassAttribute(
      DexProgramClass outer, InnerClassAttribute innerClassAttribute) {
    // For references in inner class attributes we add an edge from the context to the referenced
    // class even if the referenced class would be accessible from another package, to make sure
    // that we don't split such classes into different packages.
    innerClassAttribute.forEachType(
        type -> registerTypeAccess(type, clazz -> registerClassTypeAccess(clazz, alwaysTrue())));
  }

  public void registerNestHostAttribute(NestHostClassAttribute nestHostClassAttribute) {
    if (nestHostClassAttribute == null) {
      return;
    }
    // JVM require nest-members to be in the same package.
    registerTypeAccess(
        nestHostClassAttribute.getNestHost(),
        clazz -> registerClassTypeAccess(clazz, alwaysTrue()));
  }

  public void registerNestMemberClassAttributes(
      List<NestMemberClassAttribute> memberClassAttributes) {
    if (memberClassAttributes == null) {
      return;
    }
    // JVM require nest-members to be in the same package.
    memberClassAttributes.forEach(
        nestMember ->
            registerTypeAccess(
                nestMember.getNestMember(), clazz -> registerClassTypeAccess(clazz, alwaysTrue())));
  }
}
