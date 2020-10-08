// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
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

public class SyntheticClassBuilder {
  private final DexItemFactory factory;

  private final DexType type;
  private final Origin origin;

  private DexType superType;
  private DexTypeList interfaces = DexTypeList.empty();

  private int nextMethodId = 0;
  private List<SyntheticMethodBuilder> methods = new ArrayList<>();

  SyntheticClassBuilder(DexType type, SynthesizingContext context, DexItemFactory factory) {
    this.factory = factory;
    this.type = type;
    this.origin = context.getInputContextOrigin();
    this.superType = factory.objectType;
  }

  public DexItemFactory getFactory() {
    return factory;
  }

  public DexType getType() {
    return type;
  }

  private String getNextMethodName() {
    return SyntheticItems.INTERNAL_SYNTHETIC_METHOD_PREFIX + nextMethodId++;
  }

  public SyntheticClassBuilder addMethod(Consumer<SyntheticMethodBuilder> fn) {
    SyntheticMethodBuilder method = new SyntheticMethodBuilder(this, getNextMethodName());
    fn.accept(method);
    methods.add(method);
    return this;
  }

  DexProgramClass build() {
    ClassAccessFlags accessFlags =
        ClassAccessFlags.fromSharedAccessFlags(Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC);
    Kind originKind = null;
    DexString sourceFile = null;
    NestHostClassAttribute nestHost = null;
    List<NestMemberClassAttribute> nestMembers = Collections.emptyList();
    EnclosingMethodAttribute enclosingMembers = null;
    List<InnerClassAttribute> innerClasses = Collections.emptyList();
    DexEncodedField[] staticFields = DexEncodedField.EMPTY_ARRAY;
    DexEncodedField[] instanceFields = DexEncodedField.EMPTY_ARRAY;
    DexEncodedMethod[] directMethods = DexEncodedMethod.EMPTY_ARRAY;
    DexEncodedMethod[] virtualMethods = DexEncodedMethod.EMPTY_ARRAY;
    assert !methods.isEmpty();
    List<DexEncodedMethod> directs = new ArrayList<>(methods.size());
    List<DexEncodedMethod> virtuals = new ArrayList<>(methods.size());
    for (SyntheticMethodBuilder builder : methods) {
      DexEncodedMethod method = builder.build();
      if (method.isNonPrivateVirtualMethod()) {
        virtuals.add(method);
      } else {
        directs.add(method);
      }
    }
    if (!directs.isEmpty()) {
      directMethods = directs.toArray(new DexEncodedMethod[directs.size()]);
    }
    if (!virtuals.isEmpty()) {
      virtualMethods = virtuals.toArray(new DexEncodedMethod[virtuals.size()]);
    }
    long checksum = 7 * (long) directs.hashCode() + 11 * (long) virtuals.hashCode();
    return new DexProgramClass(
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
        staticFields,
        instanceFields,
        directMethods,
        virtualMethods,
        factory.getSkipNameValidationForTesting(),
        c -> checksum);
  }
}
