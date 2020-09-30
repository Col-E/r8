// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackaging;

import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.graph.InitClassLens;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.graph.MemberResolutionResult;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMember;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ResolutionResult;
import com.android.tools.r8.graph.SuccessfulMemberResolutionResult;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class RepackagingUseRegistry extends UseRegistry {

  private final AppInfoWithLiveness appInfo;
  private final RepackagingConstraintGraph constraintGraph;
  private final ProgramDefinition context;
  private final InitClassLens initClassLens;
  private final RepackagingConstraintGraph.Node node;

  public RepackagingUseRegistry(
      AppView<AppInfoWithLiveness> appView,
      RepackagingConstraintGraph constraintGraph,
      ProgramDefinition context) {
    super(appView.dexItemFactory());
    this.appInfo = appView.appInfo();
    this.constraintGraph = constraintGraph;
    this.context = context;
    this.initClassLens = appView.initClassLens();
    this.node = constraintGraph.getNode(context.getDefinition());
  }

  private boolean isOnlyAccessibleFromSamePackage(DexProgramClass referencedClass) {
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

  private boolean isOnlyAccessibleFromSamePackage(ProgramMember<?, ?> member) {
    AccessFlags<?> accessFlags = member.getDefinition().getAccessFlags();
    if (accessFlags.isPackagePrivate()) {
      return true;
    }
    if (accessFlags.isProtected()
        && !appInfo.isSubtype(context.getContextType(), member.getHolderType())) {
      return true;
    }
    return false;
  }

  public void registerFieldAccess(DexField field) {
    registerMemberAccess(appInfo.resolveField(field));
  }

  public ProgramMethod registerMethodReference(DexMethod method) {
    ResolutionResult resolutionResult = appInfo.unsafeResolveMethodDueToDexFormat(method);
    registerMemberAccess(resolutionResult);
    return resolutionResult.isSingleResolution()
        ? resolutionResult.asSingleResolution().getResolvedProgramMethod()
        : null;
  }

  public void registerMemberAccess(MemberResolutionResult<?, ?> resolutionResult) {
    SuccessfulMemberResolutionResult<?, ?> successfulResolutionResult =
        resolutionResult.asSuccessfulMemberResolutionResult();
    if (successfulResolutionResult == null) {
      // TODO(b/165783399): If we want to preserve errors in the original program, we need to look
      //  at the failure dependencies. For example, if this method accesses in a package-private
      //  method in another package, and we move the two methods to the same package, then the
      //  invoke would no longer fail with an IllegalAccessError.
      return;
    }

    // Check access to the initial resolution holder.
    registerTypeAccess(successfulResolutionResult.getInitialResolutionHolder());

    // Similarly, check access to the resolved member.
    ProgramMember<?, ?> resolvedMember =
        successfulResolutionResult.getResolvedMember().asProgramMember(appInfo);
    if (resolvedMember != null) {
      RepackagingConstraintGraph.Node resolvedMemberNode =
          constraintGraph.getNode(resolvedMember.getDefinition());
      if (resolvedMemberNode != null && isOnlyAccessibleFromSamePackage(resolvedMember)) {
        node.addNeighbor(resolvedMemberNode);
      }
    }
  }

  private void registerTypeAccess(DexType type) {
    registerTypeAccess(type, this::registerTypeAccess);
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

  private void registerTypeAccess(DexClass clazz) {
    registerTypeAccess(clazz, this::isOnlyAccessibleFromSamePackage);
  }

  private void registerTypeAccess(DexClass clazz, Predicate<DexProgramClass> predicate) {
    // We only want to connect the current method node to the class node if the access requires the
    // two nodes to be in the same package. Therefore, we ignore accesses to non-program classes
    // and program classes outside the current package.
    DexProgramClass programClass = clazz.asProgramClass();
    if (programClass != null) {
      RepackagingConstraintGraph.Node classNode = constraintGraph.getNode(programClass);
      if (classNode != null && predicate.test(programClass)) {
        node.addNeighbor(classNode);
      }
    }
  }

  @Override
  public void registerInitClass(DexType type) {
    registerFieldAccess(initClassLens.getInitClassField(type));
  }

  @Override
  public void registerInvokeVirtual(DexMethod invokedMethod) {
    registerMemberAccess(appInfo.resolveMethod(invokedMethod, false));
  }

  @Override
  public void registerInvokeDirect(DexMethod invokedMethod) {
    registerMemberAccess(appInfo.unsafeResolveMethodDueToDexFormat(invokedMethod));
  }

  @Override
  public void registerInvokeStatic(DexMethod invokedMethod) {
    registerMemberAccess(appInfo.unsafeResolveMethodDueToDexFormat(invokedMethod));
  }

  @Override
  public void registerInvokeInterface(DexMethod invokedMethod) {
    registerMemberAccess(appInfo.resolveMethod(invokedMethod, true));
  }

  @Override
  public void registerInvokeSuper(DexMethod invokedMethod) {
    registerMemberAccess(appInfo.unsafeResolveMethodDueToDexFormat(invokedMethod));
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
    // For references in enclosing method attributes we add an edge from the context to the
    // referenced item even if the item would be accessible from another package, to make sure that
    // we don't split such classes into different packages.
    if (enclosingMethodAttribute.getEnclosingClass() != null) {
      registerTypeAccess(
          enclosingMethodAttribute.getEnclosingClass(),
          clazz -> registerTypeAccess(clazz, alwaysTrue()));
    }
    if (enclosingMethodAttribute.getEnclosingMethod() != null) {
      ProgramMethod method = registerMethodReference(enclosingMethodAttribute.getEnclosingMethod());
      if (method != null) {
        registerTypeAccess(method.getHolder(), alwaysTrue());
      }
    }
  }

  public void registerInnerClassAttribute(InnerClassAttribute innerClassAttribute) {
    // For references in inner class attributes we add an edge from the context to the referenced
    // class even if the referenced class would be accessible from another package, to make sure
    // that we don't split such classes into different packages.
    innerClassAttribute.forEachType(
        type -> registerTypeAccess(type, clazz -> registerTypeAccess(clazz, alwaysTrue())));
  }
}
