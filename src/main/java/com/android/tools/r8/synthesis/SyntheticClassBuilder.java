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
import com.android.tools.r8.origin.Origin;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

abstract class SyntheticClassBuilder<B extends SyntheticClassBuilder<B, C>, C extends DexClass> {

  private final DexItemFactory factory;

  private final DexType type;
  private final Origin origin;

  private boolean isAbstract = false;
  private Kind originKind;
  private DexType superType;
  private DexTypeList interfaces = DexTypeList.empty();
  private DexString sourceFile = null;
  private List<DexEncodedField> staticFields = new ArrayList<>();
  private List<DexEncodedField> instanceFields = new ArrayList<>();
  private List<DexEncodedMethod> directMethods = new ArrayList<>();
  private List<DexEncodedMethod> virtualMethods = new ArrayList<>();
  private List<SyntheticMethodBuilder> methods = new ArrayList<>();

  SyntheticClassBuilder(DexType type, SynthesizingContext context, DexItemFactory factory) {
    this.factory = factory;
    this.type = type;
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

  public B setInterfaces(List<DexType> interfaces) {
    this.interfaces =
        interfaces.isEmpty()
            ? DexTypeList.empty()
            : new DexTypeList(interfaces.toArray(DexType.EMPTY_ARRAY));
    return self();
  }

  public B setAbstract() {
    isAbstract = true;
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

  public C build() {
    int flag = isAbstract ? Constants.ACC_ABSTRACT : Constants.ACC_FINAL;
    ClassAccessFlags accessFlags =
        ClassAccessFlags.fromSharedAccessFlags(
            flag | Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC);
    NestHostClassAttribute nestHost = null;
    List<NestMemberClassAttribute> nestMembers = Collections.emptyList();
    EnclosingMethodAttribute enclosingMembers = null;
    List<InnerClassAttribute> innerClasses = Collections.emptyList();
    for (SyntheticMethodBuilder builder : methods) {
      DexEncodedMethod method = builder.build();
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
    return getClassKind()
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
            enclosingMembers,
            innerClasses,
            ClassSignature.noSignature(),
            DexAnnotationSet.empty(),
            staticFields.toArray(DexEncodedField.EMPTY_ARRAY),
            instanceFields.toArray(DexEncodedField.EMPTY_ARRAY),
            directMethods.toArray(DexEncodedMethod.EMPTY_ARRAY),
            virtualMethods.toArray(DexEncodedMethod.EMPTY_ARRAY),
            factory.getSkipNameValidationForTesting(),
            c -> checksum,
            null);
  }
}
