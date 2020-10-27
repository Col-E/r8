// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackaging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.graph.NestHostClassAttribute;
import com.android.tools.r8.graph.NestMemberClassAttribute;
import com.android.tools.r8.shaking.AnnotationFixer;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class RepackagingTreeFixer {

  private final DirectMappedDexApplication.Builder appBuilder;
  private final AppView<AppInfoWithLiveness> appView;
  private final DexItemFactory dexItemFactory;
  private final RepackagingLens.Builder lensBuilder = new RepackagingLens.Builder();
  private final Map<DexType, DexType> repackagedClasses;

  private final Map<DexType, DexProgramClass> newProgramClasses = new IdentityHashMap<>();
  private final Map<DexProto, DexProto> protoFixupCache = new IdentityHashMap<>();

  public RepackagingTreeFixer(
      DirectMappedDexApplication.Builder appBuilder,
      AppView<AppInfoWithLiveness> appView,
      Map<DexType, DexType> repackagedClasses) {
    this.appBuilder = appBuilder;
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
    this.repackagedClasses = repackagedClasses;
  }

  public RepackagingLens run() {
    // Globally substitute repackaged class types.
    for (DexProgramClass clazz : appView.appInfo().classesWithDeterministicOrder()) {
      newProgramClasses.computeIfAbsent(clazz.getType(), ignore -> fixupClass(clazz));
    }
    appBuilder.replaceProgramClasses(new ArrayList<>(newProgramClasses.values()));
    RepackagingLens lens = lensBuilder.build(appView);
    new AnnotationFixer(lens).run(appView.appInfo().classes());
    return lens;
  }

  private DexProgramClass fixupClass(DexProgramClass clazz) {
    DexProgramClass newClass =
        new DexProgramClass(
            fixupType(clazz.getType()),
            clazz.getOriginKind(),
            clazz.getOrigin(),
            clazz.getAccessFlags(),
            fixupType(clazz.superType),
            fixupTypeList(clazz.interfaces),
            clazz.getSourceFile(),
            fixupNestHost(clazz.getNestHostClassAttribute()),
            fixupNestMemberAttributes(clazz.getNestMembersClassAttributes()),
            fixupEnclosingMethodAttribute(clazz.getEnclosingMethodAttribute()),
            fixupInnerClassAttributes(clazz.getInnerClasses()),
            clazz.getClassSignature(),
            clazz.annotations(),
            DexEncodedField.EMPTY_ARRAY,
            DexEncodedField.EMPTY_ARRAY,
            DexEncodedMethod.EMPTY_ARRAY,
            DexEncodedMethod.EMPTY_ARRAY,
            dexItemFactory.getSkipNameValidationForTesting(),
            DexProgramClass::checksumFromType,
            fixupSynthesizedFrom(clazz.getSynthesizedFrom()));
    newClass.setInstanceFields(fixupFields(clazz.instanceFields()));
    newClass.setStaticFields(fixupFields(clazz.staticFields()));
    newClass.setDirectMethods(
        fixupMethods(
            clazz.getMethodCollection().directMethods(),
            clazz.getMethodCollection().numberOfDirectMethods()));
    newClass.setVirtualMethods(
        fixupMethods(
            clazz.getMethodCollection().virtualMethods(),
            clazz.getMethodCollection().numberOfVirtualMethods()));
    if (newClass.getType() != clazz.getType()) {
      lensBuilder.recordMove(clazz.getType(), newClass.getType());
    }
    return newClass;
  }

  private EnclosingMethodAttribute fixupEnclosingMethodAttribute(
      EnclosingMethodAttribute enclosingMethodAttribute) {
    if (enclosingMethodAttribute == null) {
      return null;
    }
    DexType enclosingClassType = enclosingMethodAttribute.getEnclosingClass();
    if (enclosingClassType != null) {
      DexType newEnclosingClassType = fixupType(enclosingClassType);
      return newEnclosingClassType != enclosingClassType
          ? new EnclosingMethodAttribute(newEnclosingClassType)
          : enclosingMethodAttribute;
    }
    DexMethod enclosingMethod = enclosingMethodAttribute.getEnclosingMethod();
    assert enclosingMethod != null;
    DexMethod newEnclosingMethod =
        fixupMethodReference(enclosingMethodAttribute.getEnclosingMethod());
    return newEnclosingMethod != enclosingMethod
        ? new EnclosingMethodAttribute(newEnclosingMethod)
        : enclosingMethodAttribute;
  }

  private DexEncodedField[] fixupFields(List<DexEncodedField> fields) {
    if (fields == null) {
      return DexEncodedField.EMPTY_ARRAY;
    }
    DexEncodedField[] newFields = new DexEncodedField[fields.size()];
    for (int i = 0; i < fields.size(); i++) {
      newFields[i] = fixupField(fields.get(i));
    }
    return newFields;
  }

  private DexEncodedField fixupField(DexEncodedField field) {
    DexField fieldReference = field.getReference();
    DexField newFieldReference = fixupFieldReference(fieldReference);
    if (newFieldReference != fieldReference) {
      lensBuilder.recordMove(fieldReference, newFieldReference);
      return field.toTypeSubstitutedField(newFieldReference);
    }
    return field;
  }

  private DexField fixupFieldReference(DexField field) {
    DexType newType = fixupType(field.type);
    DexType newHolder = fixupType(field.holder);
    return dexItemFactory.createField(newHolder, newType, field.name);
  }

  private List<InnerClassAttribute> fixupInnerClassAttributes(
      List<InnerClassAttribute> innerClassAttributes) {
    if (innerClassAttributes.isEmpty()) {
      return innerClassAttributes;
    }
    boolean changed = false;
    List<InnerClassAttribute> newInnerClassAttributes = new ArrayList<>();
    for (InnerClassAttribute innerClassAttribute : innerClassAttributes) {
      DexType innerClassType = innerClassAttribute.getInner();
      DexType newInnerClassType = fixupTypeOrNull(innerClassType);
      DexType outerClassType = innerClassAttribute.getOuter();
      DexType newOuterClassType = fixupTypeOrNull(outerClassType);
      newInnerClassAttributes.add(
          new InnerClassAttribute(
              innerClassAttribute.getAccess(),
              newInnerClassType,
              newOuterClassType,
              innerClassAttribute.getInnerName()));
      changed |= newInnerClassType != innerClassType || newOuterClassType != outerClassType;
    }
    return changed ? newInnerClassAttributes : innerClassAttributes;
  }

  private DexEncodedMethod[] fixupMethods(Iterable<DexEncodedMethod> methods, int size) {
    if (size == 0) {
      return DexEncodedMethod.EMPTY_ARRAY;
    }
    int i = 0;
    DexEncodedMethod[] newMethods = new DexEncodedMethod[size];
    for (DexEncodedMethod method : methods) {
      newMethods[i++] = fixupMethod(method);
    }
    return newMethods;
  }

  private DexEncodedMethod fixupMethod(DexEncodedMethod method) {
    DexMethod methodReference = method.getReference();
    DexMethod newMethodReference = fixupMethodReference(methodReference);
    if (newMethodReference != methodReference) {
      lensBuilder.recordMove(methodReference, newMethodReference);
      return method.toTypeSubstitutedMethod(newMethodReference);
    }
    return method;
  }

  private DexMethod fixupMethodReference(DexMethod method) {
    return appView
        .dexItemFactory()
        .createMethod(fixupType(method.holder), fixupProto(method.proto), method.name);
  }

  private NestHostClassAttribute fixupNestHost(NestHostClassAttribute nestHostClassAttribute) {
    return nestHostClassAttribute != null
        ? new NestHostClassAttribute(fixupType(nestHostClassAttribute.getNestHost()))
        : null;
  }

  private List<NestMemberClassAttribute> fixupNestMemberAttributes(
      List<NestMemberClassAttribute> nestMemberAttributes) {
    if (nestMemberAttributes.isEmpty()) {
      return nestMemberAttributes;
    }
    boolean changed = false;
    List<NestMemberClassAttribute> newNestMemberAttributes =
        new ArrayList<>(nestMemberAttributes.size());
    for (NestMemberClassAttribute nestMemberAttribute : nestMemberAttributes) {
      DexType nestMemberType = nestMemberAttribute.getNestMember();
      DexType newNestMemberType = fixupType(nestMemberType);
      newNestMemberAttributes.add(new NestMemberClassAttribute(newNestMemberType));
      changed |= newNestMemberType != nestMemberType;
    }
    return changed ? newNestMemberAttributes : nestMemberAttributes;
  }

  private DexProto fixupProto(DexProto proto) {
    DexProto result = protoFixupCache.get(proto);
    if (result == null) {
      DexType returnType = fixupType(proto.returnType);
      DexType[] arguments = fixupTypes(proto.parameters.values);
      result = dexItemFactory.createProto(returnType, arguments);
      protoFixupCache.put(proto, result);
    }
    return result;
  }

  private Collection<DexProgramClass> fixupSynthesizedFrom(
      Collection<DexProgramClass> synthesizedFrom) {
    if (synthesizedFrom.isEmpty()) {
      return synthesizedFrom;
    }
    boolean changed = false;
    List<DexProgramClass> newSynthesizedFrom = new ArrayList<>(synthesizedFrom.size());
    for (DexProgramClass clazz : synthesizedFrom) {
      // TODO(b/165783399): What do we want to put here if the class that this was synthesized from
      //  is no longer in the application?
      DexProgramClass newClass =
          newProgramClasses.computeIfAbsent(clazz.getType(), ignore -> fixupClass(clazz));
      newSynthesizedFrom.add(newClass);
      changed |= newClass != clazz;
    }
    return changed ? newSynthesizedFrom : synthesizedFrom;
  }

  private DexType fixupTypeOrNull(DexType type) {
    return type != null ? fixupType(type) : null;
  }

  private DexType fixupType(DexType type) {
    if (type.isArrayType()) {
      DexType base = type.toBaseType(dexItemFactory);
      DexType fixed = fixupType(base);
      if (base == fixed) {
        return type;
      }
      return type.replaceBaseType(fixed, dexItemFactory);
    }
    if (type.isClassType()) {
      return repackagedClasses.getOrDefault(type, type);
    }
    return type;
  }

  private DexType[] fixupTypes(DexType[] types) {
    boolean changed = false;
    DexType[] newTypes = new DexType[types.length];
    for (int i = 0; i < newTypes.length; i++) {
      DexType type = types[i];
      DexType newType = fixupType(types[i]);
      newTypes[i] = newType;
      changed |= newType != type;
    }
    return changed ? newTypes : types;
  }

  private DexTypeList fixupTypeList(DexTypeList types) {
    DexType[] newTypes = fixupTypes(types.values);
    return newTypes != types.values ? new DexTypeList(newTypes) : types;
  }
}
