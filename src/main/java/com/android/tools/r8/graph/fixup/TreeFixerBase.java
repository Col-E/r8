// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.fixup;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.graph.NestHostClassAttribute;
import com.android.tools.r8.graph.NestMemberClassAttribute;
import com.android.tools.r8.graph.PermittedSubclassAttribute;
import com.android.tools.r8.graph.RecordComponentInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ConsumerUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public abstract class TreeFixerBase {

  private final AppView<?> appView;
  private final DexItemFactory dexItemFactory;

  private final Map<DexType, DexProgramClass> programClassCache = new IdentityHashMap<>();
  private final Map<DexType, DexProgramClass> synthesizedFromClasses = new IdentityHashMap<>();
  private final Map<DexProto, DexProto> protoFixupCache = new IdentityHashMap<>();

  public TreeFixerBase(AppView<?> appView) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
  }

  /** Mapping of a class type to a potentially new class type. */
  public abstract DexType mapClassType(DexType type);

  /** Callback invoked each time an encoded field changes field reference. */
  public abstract void recordFieldChange(DexField from, DexField to);

  /** Callback invoked each time an encoded method changes method reference. */
  public abstract void recordMethodChange(DexMethod from, DexMethod to);

  /** Callback invoked each time a program class definition changes type reference. */
  public abstract void recordClassChange(DexType from, DexType to);

  private DexProgramClass recordClassChange(DexProgramClass from, DexProgramClass to) {
    recordClassChange(from.getType(), to.getType());
    return to;
  }

  private DexEncodedField recordFieldChange(DexEncodedField from, DexEncodedField to) {
    recordFieldChange(from.getReference(), to.getReference());
    return to;
  }

  /** Rewrite missing references */
  public void recordFailedResolutionChanges() {
    // In order for optimizations to correctly rewrite field and method references that do not
    // resolve, we create a mapping from each failed resolution target to its reference reference.
    if (!appView.appInfo().hasLiveness()) {
      return;
    }
    AppInfoWithLiveness appInfoWithLiveness = appView.appInfo().withLiveness();
    appInfoWithLiveness
        .getFailedFieldResolutionTargets()
        .forEach(
            field -> {
              DexField fixedUpField = fixupFieldReference(field);
              if (field != fixedUpField) {
                recordFieldChange(field, fixedUpField);
              }
            });
    appInfoWithLiveness
        .getFailedMethodResolutionTargets()
        .forEach(
            method -> {
              DexMethod fixedUpMethod = fixupMethodReference(method);
              if (method != fixedUpMethod) {
                recordMethodChange(method, fixedUpMethod);
              }
            });
  }

  /** Callback to allow custom handling when an encoded method changes. */
  public DexEncodedMethod recordMethodChange(DexEncodedMethod from, DexEncodedMethod to) {
    recordMethodChange(from.getReference(), to.getReference());
    return to;
  }

  /** Fixup a collection of classes. */
  public List<DexProgramClass> fixupClasses(Collection<DexProgramClass> classes) {
    List<DexProgramClass> newProgramClasses = new ArrayList<>();
    for (DexProgramClass clazz : classes) {
      newProgramClasses.add(
          programClassCache.computeIfAbsent(clazz.getType(), ignore -> fixupClass(clazz)));
    }
    return newProgramClasses;
  }

  // Should remain private as the correctness of the fixup requires the lazy 'newProgramClasses'.
  private DexProgramClass fixupClass(DexProgramClass clazz) {
    DexProgramClass newClass =
        new DexProgramClass(
            fixupType(clazz.getType()),
            clazz.getOriginKind(),
            clazz.getOrigin(),
            clazz.getAccessFlags(),
            clazz.superType == null ? null : fixupType(clazz.superType),
            fixupTypeList(clazz.interfaces),
            clazz.getSourceFile(),
            fixupNestHost(clazz.getNestHostClassAttribute()),
            fixupNestMemberAttributes(clazz.getNestMembersClassAttributes()),
            fixupPermittedSubclassAttribute(clazz.getPermittedSubclassAttributes()),
            fixupRecordComponents(clazz.getRecordComponents()),
            fixupEnclosingMethodAttribute(clazz.getEnclosingMethodAttribute()),
            fixupInnerClassAttributes(clazz.getInnerClasses()),
            clazz.getClassSignature(),
            clazz.annotations(),
            DexEncodedField.EMPTY_ARRAY,
            DexEncodedField.EMPTY_ARRAY,
            newHolder -> clazz.getMethodCollection().fixup(newHolder, this::fixupMethod),
            dexItemFactory.getSkipNameValidationForTesting(),
            clazz.getChecksumSupplier());
    newClass.setInstanceFields(fixupFields(clazz.instanceFields()));
    newClass.setStaticFields(fixupFields(clazz.staticFields()));
    // Transfer properties that are not passed to the constructor.
    if (clazz.hasClassFileVersion()) {
      newClass.setInitialClassFileVersion(clazz.getInitialClassFileVersion());
    }
    if (clazz.isDeprecated()) {
      newClass.setDeprecated();
    }
    if (clazz.getKotlinInfo() != null) {
      newClass.setKotlinInfo(clazz.getKotlinInfo());
    }
    // If the class type changed, record the move in the lens.
    if (newClass.getType() != clazz.getType()) {
      return recordClassChange(clazz, newClass);
    }
    return newClass;
  }

  protected EnclosingMethodAttribute fixupEnclosingMethodAttribute(
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

  /** Fixup a list of fields. */
  public DexEncodedField[] fixupFields(List<DexEncodedField> fields) {
    return fixupFields(fields, ConsumerUtils.emptyConsumer());
  }

  public DexEncodedField[] fixupFields(
      List<DexEncodedField> fields, Consumer<DexEncodedField.Builder> consumer) {
    if (fields == null) {
      return DexEncodedField.EMPTY_ARRAY;
    }
    DexEncodedField[] newFields = new DexEncodedField[fields.size()];
    for (int i = 0; i < fields.size(); i++) {
      newFields[i] = fixupField(fields.get(i), consumer);
    }
    return newFields;
  }

  private DexEncodedField fixupField(
      DexEncodedField field, Consumer<DexEncodedField.Builder> consumer) {
    DexField fieldReference = field.getReference();
    DexField newFieldReference = fixupFieldReference(fieldReference);
    if (newFieldReference != fieldReference) {
      return recordFieldChange(
          field, field.toTypeSubstitutedField(appView, newFieldReference, consumer));
    }
    return field;
  }

  /** Fixup a field reference. */
  public DexField fixupFieldReference(DexField field) {
    DexType newType = fixupType(field.type);
    DexType newHolder = fixupType(field.holder);
    return dexItemFactory.createField(newHolder, newType, field.name);
  }

  protected List<InnerClassAttribute> fixupInnerClassAttributes(
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
      DexString newInnerName = innerClassAttribute.getInnerName();
      // Compute the new inner name if the attribute changed. This could end up 'fixing' invalid
      // inner class attributes.
      boolean innerClassAttributeChanged =
          newInnerClassType != innerClassType || newOuterClassType != outerClassType;
      if (innerClassAttributeChanged
          && innerClassType != null
          && outerClassType != null
          && innerClassAttribute.getInnerName() != null) {
        String innerClassName =
            DescriptorUtils.getInnerClassNameFromSimpleName(
                newOuterClassType.getSimpleName(), newInnerClassType.getSimpleName());
        if (innerClassName != null) {
          newInnerName = dexItemFactory.createString(innerClassName);
        } else {
          // If run without treeshaking and the outer type is missing we are not pruning the
          // relationship.
          assert !appView.options().isTreeShakingEnabled();
          assert appView.appInfo().definitionForWithoutExistenceAssert(newOuterClassType) == null;
        }
      }
      newInnerClassAttributes.add(
          new InnerClassAttribute(
              innerClassAttribute.getAccess(), newInnerClassType, newOuterClassType, newInnerName));
      changed |= innerClassAttributeChanged;
    }
    return changed ? newInnerClassAttributes : innerClassAttributes;
  }

  /** Fixup a method definition. */
  public DexEncodedMethod fixupMethod(DexEncodedMethod method) {
    DexMethod methodReference = method.getReference();
    DexMethod newMethodReference = fixupMethodReference(methodReference);
    if (newMethodReference != methodReference) {
      return recordMethodChange(method, method.toTypeSubstitutedMethod(newMethodReference));
    }
    return method;
  }

  /** Fixup a method reference. */
  public DexMethod fixupMethodReference(DexMethod method) {
    return dexItemFactory.createMethod(
        fixupType(method.holder), fixupProto(method.proto), method.name);
  }

  protected NestHostClassAttribute fixupNestHost(NestHostClassAttribute nestHostClassAttribute) {
    return nestHostClassAttribute != null
        ? new NestHostClassAttribute(fixupType(nestHostClassAttribute.getNestHost()))
        : null;
  }

  protected List<NestMemberClassAttribute> fixupNestMemberAttributes(
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

  protected List<PermittedSubclassAttribute> fixupPermittedSubclassAttribute(
      List<PermittedSubclassAttribute> permittedSubclassAttributes) {
    if (permittedSubclassAttributes.isEmpty()) {
      return permittedSubclassAttributes;
    }
    boolean changed = false;
    List<PermittedSubclassAttribute> newPermittedSubclassAttributes =
        new ArrayList<>(permittedSubclassAttributes.size());
    for (PermittedSubclassAttribute permittedSubclassAttribute : permittedSubclassAttributes) {
      DexType permittedSubclassType = permittedSubclassAttribute.getPermittedSubclass();
      DexType newPermittedSubclassType = fixupType(permittedSubclassType);
      newPermittedSubclassAttributes.add(new PermittedSubclassAttribute(newPermittedSubclassType));
      changed |= newPermittedSubclassType != permittedSubclassType;
    }
    return changed ? newPermittedSubclassAttributes : permittedSubclassAttributes;
  }

  protected List<RecordComponentInfo> fixupRecordComponents(
      List<RecordComponentInfo> recordComponents) {
    if (recordComponents.isEmpty()) {
      return recordComponents;
    }
    // TODO(b/274888318): Check this.
    boolean changed = false;
    List<RecordComponentInfo> newRecordComponents = new ArrayList<>(recordComponents.size());
    for (RecordComponentInfo info : recordComponents) {
      DexField field = info.getField();
      DexField newField = fixupFieldReference(field);
      newRecordComponents.add(
          new RecordComponentInfo(newField, info.getSignature(), info.getAnnotations()));
      changed |= newField != field;
    }
    return changed ? newRecordComponents : recordComponents;
  }

  /** Fixup a proto descriptor. */
  public DexProto fixupProto(DexProto proto) {
    DexProto result = protoFixupCache.get(proto);
    if (result == null) {
      DexType returnType = fixupType(proto.returnType);
      DexType[] arguments = fixupTypes(proto.parameters.values);
      result = dexItemFactory.createProto(returnType, arguments);
      protoFixupCache.put(proto, result);
    }
    return result;
  }

  private DexType fixupTypeOrNull(DexType type) {
    return type != null ? fixupType(type) : null;
  }

  /** Fixup a type reference. */
  public DexType fixupType(DexType type) {
    if (type.isArrayType()) {
      DexType base = type.toBaseType(dexItemFactory);
      DexType fixed = fixupType(base);
      if (base == fixed) {
        return type;
      }
      return type.replaceBaseType(fixed, dexItemFactory);
    }
    if (type.isClassType()) {
      return mapClassType(type);
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

  /** Fixup a method signature. */
  public DexMethodSignature fixupMethodSignature(DexMethodSignature signature) {
    return signature.withProto(fixupProto(signature.getProto()));
  }
}
