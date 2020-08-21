// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackaging;

import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MemberResolutionResult;
import com.android.tools.r8.graph.ProgramMember;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.SuccessfulMemberResolutionResult;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class RepackagingUseRegistry extends UseRegistry {

  private final AppInfoWithLiveness appInfo;
  private final RepackagingConstraintGraph constraintGraph;
  private final ProgramMethod context;
  private final RepackagingConstraintGraph.Node node;

  public RepackagingUseRegistry(
      AppView<AppInfoWithLiveness> appView,
      RepackagingConstraintGraph constraintGraph,
      ProgramMethod context) {
    super(appView.dexItemFactory());
    this.appInfo = appView.appInfo();
    this.constraintGraph = constraintGraph;
    this.context = context;
    this.node = constraintGraph.getNode(context.getDefinition());
  }

  private boolean isOnlyAccessibleFromSamePackage(DexProgramClass referencedClass) {
    ClassAccessFlags accessFlags = referencedClass.getAccessFlags();
    if (accessFlags.isPackagePrivate()) {
      return true;
    }
    if (accessFlags.isProtected()
        && !appInfo.isSubtype(context.getHolderType(), referencedClass.getType())) {
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
        && !appInfo.isSubtype(context.getHolderType(), member.getHolderType())) {
      return true;
    }
    return false;
  }

  private void registerMemberAccess(MemberResolutionResult<?, ?> resolutionResult) {
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
    if (type.isArrayType()) {
      registerTypeAccess(type.toBaseType(appInfo.dexItemFactory()));
      return;
    }
    if (type.isPrimitiveType()) {
      return;
    }
    assert type.isClassType();
    DexClass clazz = appInfo.definitionFor(type);
    if (clazz != null) {
      registerTypeAccess(clazz);
    }
  }

  private void registerTypeAccess(DexClass clazz) {
    // We only want to connect the current method node to the class node if the access requires the
    // two nodes to be in the same package. Therefore, we ignore accesses to non-program classes
    // and program classes outside the current package.
    DexProgramClass programClass = clazz.asProgramClass();
    if (programClass != null) {
      RepackagingConstraintGraph.Node classNode = constraintGraph.getNode(programClass);
      if (classNode != null && isOnlyAccessibleFromSamePackage(programClass)) {
        node.addNeighbor(classNode);
      }
    }
  }

  @Override
  public boolean registerInitClass(DexType type) {
    registerTypeAccess(type);
    return false;
  }

  @Override
  public boolean registerInvokeVirtual(DexMethod invokedMethod) {
    registerMemberAccess(appInfo.resolveMethod(invokedMethod, false));
    return false;
  }

  @Override
  public boolean registerInvokeDirect(DexMethod invokedMethod) {
    registerMemberAccess(appInfo.unsafeResolveMethodDueToDexFormat(invokedMethod));
    return false;
  }

  @Override
  public boolean registerInvokeStatic(DexMethod invokedMethod) {
    registerMemberAccess(appInfo.unsafeResolveMethodDueToDexFormat(invokedMethod));
    return false;
  }

  @Override
  public boolean registerInvokeInterface(DexMethod invokedMethod) {
    registerMemberAccess(appInfo.resolveMethod(invokedMethod, true));
    return false;
  }

  @Override
  public boolean registerInvokeSuper(DexMethod invokedMethod) {
    registerMemberAccess(appInfo.unsafeResolveMethodDueToDexFormat(invokedMethod));
    return false;
  }

  @Override
  public boolean registerInstanceFieldRead(DexField field) {
    registerMemberAccess(appInfo.resolveField(field));
    return false;
  }

  @Override
  public boolean registerInstanceFieldWrite(DexField field) {
    registerMemberAccess(appInfo.resolveField(field));
    return false;
  }

  @Override
  public boolean registerNewInstance(DexType type) {
    registerTypeAccess(type);
    return false;
  }

  @Override
  public boolean registerStaticFieldRead(DexField field) {
    registerMemberAccess(appInfo.resolveField(field));
    return false;
  }

  @Override
  public boolean registerStaticFieldWrite(DexField field) {
    registerMemberAccess(appInfo.resolveField(field));
    return false;
  }

  @Override
  public boolean registerTypeReference(DexType type) {
    registerTypeAccess(type);
    return false;
  }

  @Override
  public boolean registerInstanceOf(DexType type) {
    registerTypeAccess(type);
    return false;
  }
}
