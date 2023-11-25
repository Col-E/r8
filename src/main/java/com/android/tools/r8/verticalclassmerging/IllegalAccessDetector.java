package com.android.tools.r8.verticalclassmerging;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistryWithResult;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.utils.OptionalBool;

// Searches for a reference to a non-private, non-public class, field or method declared in the
// same package as [source].
public class IllegalAccessDetector extends UseRegistryWithResult<Boolean, ProgramMethod> {

  private final AppView<? extends AppInfoWithClassHierarchy> appViewWithClassHierarchy;

  public IllegalAccessDetector(
      AppView<? extends AppInfoWithClassHierarchy> appViewWithClassHierarchy,
      ProgramMethod context) {
    super(appViewWithClassHierarchy, context, false);
    this.appViewWithClassHierarchy = appViewWithClassHierarchy;
  }

  protected boolean checkFoundPackagePrivateAccess() {
    assert getResult();
    return true;
  }

  protected boolean setFoundPackagePrivateAccess() {
    setResult(true);
    return true;
  }

  protected static boolean continueSearchForPackagePrivateAccess() {
    return false;
  }

  private boolean checkFieldReference(DexField field) {
    return checkRewrittenFieldReference(appViewWithClassHierarchy.graphLens().lookupField(field));
  }

  private boolean checkRewrittenFieldReference(DexField field) {
    assert field.getHolderType().isClassType();
    DexType fieldHolder = field.getHolderType();
    if (fieldHolder.isSamePackage(getContext().getHolderType())) {
      if (checkRewrittenTypeReference(fieldHolder)) {
        return checkFoundPackagePrivateAccess();
      }
      DexClassAndField resolvedField =
          appViewWithClassHierarchy.appInfo().resolveField(field).getResolutionPair();
      if (resolvedField == null) {
        return setFoundPackagePrivateAccess();
      }
      if (resolvedField.getHolder() != getContext().getHolder()
          && !resolvedField.getAccessFlags().isPublic()) {
        return setFoundPackagePrivateAccess();
      }
      if (checkRewrittenFieldType(resolvedField)) {
        return checkFoundPackagePrivateAccess();
      }
    }
    return continueSearchForPackagePrivateAccess();
  }

  protected boolean checkRewrittenFieldType(DexClassAndField field) {
    return continueSearchForPackagePrivateAccess();
  }

  private boolean checkRewrittenMethodReference(
      DexMethod rewrittenMethod, OptionalBool isInterface) {
    DexType baseType =
        rewrittenMethod.getHolderType().toBaseType(appViewWithClassHierarchy.dexItemFactory());
    if (baseType.isClassType() && baseType.isSamePackage(getContext().getHolderType())) {
      if (checkTypeReference(rewrittenMethod.getHolderType())) {
        return checkFoundPackagePrivateAccess();
      }
      MethodResolutionResult resolutionResult =
          isInterface.isUnknown()
              ? appViewWithClassHierarchy
                  .appInfo()
                  .unsafeResolveMethodDueToDexFormat(rewrittenMethod)
              : appViewWithClassHierarchy
                  .appInfo()
                  .resolveMethod(rewrittenMethod, isInterface.isTrue());
      if (!resolutionResult.isSingleResolution()) {
        return setFoundPackagePrivateAccess();
      }
      DexClassAndMethod resolvedMethod = resolutionResult.asSingleResolution().getResolutionPair();
      if (resolvedMethod.getHolder() != getContext().getHolder()
          && !resolvedMethod.getAccessFlags().isPublic()) {
        return setFoundPackagePrivateAccess();
      }
    }
    return continueSearchForPackagePrivateAccess();
  }

  private boolean checkTypeReference(DexType type) {
    return internalCheckTypeReference(type, appViewWithClassHierarchy.graphLens());
  }

  private boolean checkRewrittenTypeReference(DexType type) {
    return internalCheckTypeReference(type, GraphLens.getIdentityLens());
  }

  private boolean internalCheckTypeReference(DexType type, GraphLens graphLens) {
    DexType baseType =
        graphLens.lookupType(type.toBaseType(appViewWithClassHierarchy.dexItemFactory()));
    if (baseType.isClassType() && baseType.isSamePackage(getContext().getHolderType())) {
      DexClass clazz = appViewWithClassHierarchy.definitionFor(baseType);
      if (clazz == null || !clazz.isPublic()) {
        return setFoundPackagePrivateAccess();
      }
    }
    return continueSearchForPackagePrivateAccess();
  }

  @Override
  public void registerInitClass(DexType clazz) {
    if (appViewWithClassHierarchy.initClassLens().isFinal()) {
      // The InitClass lens is always rewritten up until the most recent graph lens, so first map
      // the class type to the most recent graph lens.
      DexType rewrittenType = appViewWithClassHierarchy.graphLens().lookupType(clazz);
      DexField initClassField =
          appViewWithClassHierarchy.initClassLens().getInitClassField(rewrittenType);
      checkRewrittenFieldReference(initClassField);
    } else {
      checkTypeReference(clazz);
    }
  }

  @Override
  public void registerInvokeVirtual(DexMethod method) {
    MethodLookupResult lookup =
        appViewWithClassHierarchy.graphLens().lookupInvokeVirtual(method, getContext());
    checkRewrittenMethodReference(lookup.getReference(), OptionalBool.FALSE);
  }

  @Override
  public void registerInvokeDirect(DexMethod method) {
    MethodLookupResult lookup =
        appViewWithClassHierarchy.graphLens().lookupInvokeDirect(method, getContext());
    checkRewrittenMethodReference(lookup.getReference(), OptionalBool.UNKNOWN);
  }

  @Override
  public void registerInvokeStatic(DexMethod method) {
    MethodLookupResult lookup =
        appViewWithClassHierarchy.graphLens().lookupInvokeStatic(method, getContext());
    checkRewrittenMethodReference(lookup.getReference(), OptionalBool.UNKNOWN);
  }

  @Override
  public void registerInvokeInterface(DexMethod method) {
    MethodLookupResult lookup =
        appViewWithClassHierarchy.graphLens().lookupInvokeInterface(method, getContext());
    checkRewrittenMethodReference(lookup.getReference(), OptionalBool.TRUE);
  }

  @Override
  public void registerInvokeSuper(DexMethod method) {
    MethodLookupResult lookup =
        appViewWithClassHierarchy.graphLens().lookupInvokeSuper(method, getContext());
    checkRewrittenMethodReference(lookup.getReference(), OptionalBool.UNKNOWN);
  }

  @Override
  public void registerInstanceFieldWrite(DexField field) {
    checkFieldReference(field);
  }

  @Override
  public void registerInstanceFieldRead(DexField field) {
    checkFieldReference(field);
  }

  @Override
  public void registerNewInstance(DexType type) {
    checkTypeReference(type);
  }

  @Override
  public void registerStaticFieldRead(DexField field) {
    checkFieldReference(field);
  }

  @Override
  public void registerStaticFieldWrite(DexField field) {
    checkFieldReference(field);
  }

  @Override
  public void registerTypeReference(DexType type) {
    checkTypeReference(type);
  }

  @Override
  public void registerInstanceOf(DexType type) {
    checkTypeReference(type);
  }
}
