// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.synthesis;

import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.ClassKind;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.graph.NestHostClassAttribute;
import com.android.tools.r8.graph.NestMemberClassAttribute;
import com.android.tools.r8.graph.PermittedSubclassAttribute;
import com.android.tools.r8.graph.RecordComponentInfo;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public abstract class SyntheticClassBuilder<
    B extends SyntheticClassBuilder<B, C>, C extends DexClass> {

  private final DexItemFactory factory;

  private final DexType type;
  private final SyntheticKind syntheticKind;
  private final Origin origin;

  private boolean isAbstract = false;
  private boolean isFinal = true;
  private boolean isInterface = false;
  private Kind originKind;
  private DexType superType;
  private DexTypeList interfaces = DexTypeList.empty();
  private DexString sourceFile = null;
  private boolean useSortedMethodBacking = false;
  private List<DexEncodedField> staticFields = new ArrayList<>();
  private List<DexEncodedField> instanceFields = new ArrayList<>();
  private List<DexEncodedMethod> directMethods = new ArrayList<>();
  private List<DexEncodedMethod> virtualMethods = new ArrayList<>();
  private List<SyntheticMethodBuilder> methods = new ArrayList<>();
  private ClassSignature signature = ClassSignature.noSignature();

  SyntheticClassBuilder(
      DexType type,
      SyntheticKind syntheticKind,
      SynthesizingContext context,
      DexItemFactory factory) {
    this.factory = factory;
    this.type = type;
    this.syntheticKind = syntheticKind;
    this.origin = context.getInputContextOrigin();
    this.superType = factory.objectType;
  }

  public abstract B self();

  public abstract ClassKind<C> getClassKind();

  public DexItemFactory getFactory() {
    return factory;
  }

  public DexType getType() {
    return type;
  }

  public SyntheticKind getSyntheticKind() {
    return syntheticKind;
  }

  public B setInterfaces(List<DexType> interfaces) {
    this.interfaces =
        interfaces.isEmpty()
            ? DexTypeList.empty()
            : new DexTypeList(interfaces.toArray(DexType.EMPTY_ARRAY));
    return self();
  }

  public B setAbstract() {
    isAbstract = true;
    isFinal = false;
    return self();
  }

  public B unsetFinal() {
    isFinal = false;
    return self();
  }

  public B setInterface() {
    setAbstract();
    isInterface = true;
    return self();
  }

  public B setSuperType(DexType superType) {
    this.superType = superType;
    return self();
  }

  public B setOriginKind(Kind originKind) {
    this.originKind = originKind;
    return self();
  }

  public B setSourceFile(DexString sourceFile) {
    this.sourceFile = sourceFile;
    return self();
  }

  public B setGenericSignature(ClassSignature signature) {
    this.signature = signature;
    return self();
  }

  public B setStaticFields(List<DexEncodedField> fields) {
    staticFields.clear();
    staticFields.addAll(fields);
    return self();
  }

  public B setInstanceFields(List<DexEncodedField> fields) {
    instanceFields.clear();
    instanceFields.addAll(fields);
    return self();
  }

  public B setDirectMethods(Iterable<DexEncodedMethod> methods) {
    directMethods.clear();
    methods.forEach(directMethods::add);
    return self();
  }

  public B setVirtualMethods(Iterable<DexEncodedMethod> methods) {
    virtualMethods.clear();
    methods.forEach(virtualMethods::add);
    return self();
  }

  public B addMethod(Consumer<SyntheticMethodBuilder> fn) {
    SyntheticMethodBuilder method = new SyntheticMethodBuilder(this);
    fn.accept(method);
    methods.add(method);
    return self();
  }

  public B setUseSortedMethodBacking(boolean useSortedMethodBacking) {
    this.useSortedMethodBacking = useSortedMethodBacking;
    return self();
  }

  public C build() {
    int abstractFlag = isAbstract ? Constants.ACC_ABSTRACT : 0;
    int finalFlag = isFinal ? Constants.ACC_FINAL : 0;
    int itfFlag = isInterface ? Constants.ACC_INTERFACE : 0;
    assert !isInterface || isAbstract;
    ClassAccessFlags accessFlags =
        ClassAccessFlags.fromSharedAccessFlags(
            abstractFlag | finalFlag | itfFlag | Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC);
    NestHostClassAttribute nestHost = null;
    List<NestMemberClassAttribute> nestMembers = Collections.emptyList();
    List<PermittedSubclassAttribute> permittedSubclasses = Collections.emptyList();
    List<RecordComponentInfo> recordComponents = Collections.emptyList();
    EnclosingMethodAttribute enclosingMembers = null;
    List<InnerClassAttribute> innerClasses = Collections.emptyList();
    for (SyntheticMethodBuilder builder : methods) {
      DexEncodedMethod method = builder.build(getClassKind());
      if (method.isNonPrivateVirtualMethod()) {
        virtualMethods.add(method);
      } else {
        directMethods.add(method);
      }
    }
    long checksum =
        7 * (long) directMethods.hashCode()
            + 11 * (long) virtualMethods.hashCode()
            + 13 * (long) staticFields.hashCode()
            + 17 * (long) instanceFields.hashCode();
    C clazz =
        getClassKind()
            .create(
                type,
                originKind,
                origin,
                accessFlags,
                superType,
                interfaces,
                sourceFile,
                nestHost,
                nestMembers,
                permittedSubclasses,
                recordComponents,
                enclosingMembers,
                innerClasses,
                signature,
                DexAnnotationSet.empty(),
                staticFields.toArray(DexEncodedField.EMPTY_ARRAY),
                instanceFields.toArray(DexEncodedField.EMPTY_ARRAY),
                DexEncodedMethod.EMPTY_ARRAY,
                DexEncodedMethod.EMPTY_ARRAY,
                factory.getSkipNameValidationForTesting(),
                c -> checksum,
                null);
    if (useSortedMethodBacking) {
      clazz.getMethodCollection().useSortedBacking();
    }
    clazz.setDirectMethods(directMethods.toArray(DexEncodedMethod.EMPTY_ARRAY));
    clazz.setVirtualMethods(virtualMethods.toArray(DexEncodedMethod.EMPTY_ARRAY));
    return clazz;
  }
}
