// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.google.common.base.MoreObjects;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public abstract class DexClass extends DexItem {

  private static final DexEncodedMethod[] NO_METHODS = {};
  private static final DexEncodedField[] NO_FIELDS = {};

  public final Origin origin;
  public DexType type;
  public final ClassAccessFlags accessFlags;
  public DexType superType;
  public DexTypeList interfaces;
  public DexString sourceFile;

  /**
   * Access has to be synchronized during concurrent collection/writing phase.
   */
  protected DexEncodedField[] staticFields;
  /**
   * Access has to be synchronized during concurrent collection/writing phase.
   */
  protected DexEncodedField[] instanceFields;
  /**
   * Access has to be synchronized during concurrent collection/writing phase.
   */
  protected DexEncodedMethod[] directMethods;
  /**
   * Access has to be synchronized during concurrent collection/writing phase.
   */
  protected DexEncodedMethod[] virtualMethods;

  /** Enclosing context of this class if it is an inner class, null otherwise. */
  private EnclosingMethodAttribute enclosingMethod;

  /** InnerClasses table. If this class is an inner class, it will have an entry here. */
  private final List<InnerClassAttribute> innerClasses;

  public DexAnnotationSet annotations;

  public DexClass(
      DexString sourceFile,
      DexTypeList interfaces,
      ClassAccessFlags accessFlags,
      DexType superType,
      DexType type,
      DexEncodedField[] staticFields,
      DexEncodedField[] instanceFields,
      DexEncodedMethod[] directMethods,
      DexEncodedMethod[] virtualMethods,
      EnclosingMethodAttribute enclosingMethod,
      List<InnerClassAttribute> innerClasses,
      DexAnnotationSet annotations,
      Origin origin) {
    assert origin != null;
    this.origin = origin;
    this.sourceFile = sourceFile;
    this.interfaces = interfaces;
    this.accessFlags = accessFlags;
    this.superType = superType;
    this.type = type;
    setStaticFields(staticFields);
    setInstanceFields(instanceFields);
    setDirectMethods(directMethods);
    setVirtualMethods(virtualMethods);
    this.enclosingMethod = enclosingMethod;
    this.innerClasses = innerClasses;
    this.annotations = annotations;
    if (type == superType) {
      throw new CompilationError("Class " + type.toString() + " cannot extend itself");
    }
    for (DexType interfaceType : interfaces.values) {
      if (type == interfaceType) {
        throw new CompilationError("Interface " + type.toString() + " cannot implement itself");
      }
    }
    if (!type.descriptor.isValidClassDescriptor()) {
      throw new CompilationError(
          "Class descriptor '"
              + type.descriptor.toString()
              + "' cannot be represented in dex format.");
    }
  }

  @Override
  void collectMixedSectionItems(MixedSectionCollection mixedItems) {
    throw new Unreachable();
  }

  public DexEncodedMethod[] directMethods() {
    return directMethods;
  }

  public void setDirectMethods(DexEncodedMethod[] values) {
    directMethods = MoreObjects.firstNonNull(values, NO_METHODS);
  }

  public DexEncodedMethod[] virtualMethods() {
    return virtualMethods;
  }

  public void setVirtualMethods(DexEncodedMethod[] values) {
    virtualMethods = MoreObjects.firstNonNull(values, NO_METHODS);
  }

  public void forEachMethod(Consumer<DexEncodedMethod> consumer) {
    for (DexEncodedMethod method : directMethods()) {
      consumer.accept(method);
    }
    for (DexEncodedMethod method : virtualMethods()) {
      consumer.accept(method);
    }
  }

  public <E extends Throwable> void forEachMethodThrowing(
      ThrowingConsumer<DexEncodedMethod, E> consumer) throws E {
    for (DexEncodedMethod method : directMethods()) {
      consumer.accept(method);
    }
    for (DexEncodedMethod method : virtualMethods()) {
      consumer.accept(method);
    }
  }

  public DexEncodedMethod[] allMethodsSorted() {
    int vLen = virtualMethods.length;
    int dLen = directMethods.length;
    DexEncodedMethod[] result = new DexEncodedMethod[vLen + dLen];
    System.arraycopy(virtualMethods, 0, result, 0, vLen);
    System.arraycopy(directMethods, 0, result, vLen, dLen);
    Arrays.sort(result,
        (DexEncodedMethod a, DexEncodedMethod b) -> a.method.slowCompareTo(b.method));
    return result;
  }

  public void forEachField(Consumer<DexEncodedField> consumer) {
    for (DexEncodedField field : staticFields()) {
      consumer.accept(field);
    }
    for (DexEncodedField field : instanceFields()) {
      consumer.accept(field);
    }
  }

  public DexEncodedField[] staticFields() {
    return staticFields;
  }

  public void setStaticFields(DexEncodedField[] values) {
    staticFields = MoreObjects.firstNonNull(values, NO_FIELDS);
  }

  public boolean definesStaticField(DexField field) {
    for (DexEncodedField encodedField : staticFields()) {
      if (encodedField.field == field) {
        return true;
      }
    }
    return false;
  }

  public DexEncodedField[] instanceFields() {
    return instanceFields;
  }

  public void setInstanceFields(DexEncodedField[] values) {
    instanceFields = MoreObjects.firstNonNull(values, NO_FIELDS);
  }

  public DexEncodedField[] allFieldsSorted() {
    int iLen = instanceFields.length;
    int sLen = staticFields.length;
    DexEncodedField[] result = new DexEncodedField[iLen + sLen];
    System.arraycopy(instanceFields, 0, result, 0, iLen);
    System.arraycopy(staticFields, 0, result, iLen, sLen);
    Arrays.sort(result,
        (DexEncodedField a, DexEncodedField b) -> a.field.slowCompareTo(b.field));
    return result;
  }

  /**
   * Find static field in this class matching field
   */
  public DexEncodedField lookupStaticField(DexField field) {
    return lookupTarget(staticFields(), field);
  }

  /**
   * Find instance field in this class matching field.
   */
  public DexEncodedField lookupInstanceField(DexField field) {
    return lookupTarget(instanceFields(), field);
  }

  /**
   * Find field in this class matching field.
   */
  public DexEncodedField lookupField(DexField field) {
    DexEncodedField result = lookupInstanceField(field);
    return result == null ? lookupStaticField(field) : result;
  }

  /**
   * Find direct method in this class matching method
   */
  public DexEncodedMethod lookupDirectMethod(DexMethod method) {
    return lookupTarget(directMethods(), method);
  }

  /**
   * Find virtual method in this class matching method
   */
  public DexEncodedMethod lookupVirtualMethod(DexMethod method) {
    return lookupTarget(virtualMethods(), method);
  }

  private <T extends DexItem, S extends Descriptor<T, S>> T lookupTarget(T[] items, S descriptor) {
    for (T entry : items) {
      if (descriptor.match(entry)) {
        return entry;
      }
    }
    return null;
  }

  // Tells whether this is an interface.
  public boolean isInterface() {
    return accessFlags.isInterface();
  }

  public abstract void addDependencies(MixedSectionCollection collector);

  public boolean isProgramClass() {
    return false;
  }

  public DexProgramClass asProgramClass() {
    return null;
  }

  public boolean isClasspathClass() {
    return false;
  }

  public boolean isLibraryClass() {
    return false;
  }

  public DexLibraryClass asLibraryClass() {
    return null;
  }

  public DexEncodedMethod getClassInitializer() {
    return Arrays.stream(directMethods()).filter(DexEncodedMethod::isClassInitializer).findAny()
        .orElse(null);
  }

  public Origin getOrigin() {
    return this.origin;
  }

  public DexType getType() {
    return this.type;
  }

  public boolean hasClassInitializer() {
    return getClassInitializer() != null;
  }

  public boolean hasTrivialClassInitializer() {
    DexEncodedMethod clinit = getClassInitializer();
    return clinit != null
        && clinit.getCode() != null
        && clinit.getCode().asDexCode().isEmptyVoidMethod();
  }


  public boolean hasNonTrivialClassInitializer() {
    DexEncodedMethod clinit = getClassInitializer();
    if (clinit == null || clinit.getCode() == null) {
      return false;
    }
    if (clinit.getCode().isDexCode()) {
      return !clinit.getCode().asDexCode().isEmptyVoidMethod();
    }
    // For non-dex code we don't try to check the code.
    return true;
  }

  public boolean hasDefaultInitializer() {
    return getDefaultInitializer() != null;
  }

  public DexEncodedMethod getDefaultInitializer() {
    for (DexEncodedMethod method : directMethods()) {
      if (method.isDefaultInitializer()) {
        return method;
      }
    }
    return null;
  }

  public boolean defaultValuesForStaticFieldsMayTriggerAllocation() {
    return Arrays.stream(staticFields())
        .anyMatch(field -> !field.staticValue.mayTriggerAllocation());
  }

  public List<InnerClassAttribute> getInnerClasses() {
    return innerClasses;
  }

  public EnclosingMethodAttribute getEnclosingMethod() {
    return enclosingMethod;
  }

  public void clearEnclosingMethod() {
    enclosingMethod = null;
  }

  public void clearInnerClasses() {
    innerClasses.clear();
  }
}
