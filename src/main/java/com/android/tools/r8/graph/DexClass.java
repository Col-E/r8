// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.kotlin.KotlinClassLevelInfo;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.TraversalContinuation;
import com.google.common.base.MoreObjects;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public abstract class DexClass extends DexDefinition {

  public interface FieldSetter {
    void setField(int index, DexEncodedField field);
  }

  public final Origin origin;
  public DexType type;
  public final ClassAccessFlags accessFlags;
  public DexType superType;
  public DexTypeList interfaces;
  public DexString sourceFile;

  private OptionalBool isResolvable = OptionalBool.unknown();

  /** Access has to be synchronized during concurrent collection/writing phase. */
  protected DexEncodedField[] staticFields = DexEncodedField.EMPTY_ARRAY;

  /** Access has to be synchronized during concurrent collection/writing phase. */
  protected DexEncodedField[] instanceFields = DexEncodedField.EMPTY_ARRAY;

  /** Access has to be synchronized during concurrent collection/writing phase. */
  protected final MethodCollection methodCollection;

  /** Enclosing context of this class if it is an inner class, null otherwise. */
  private EnclosingMethodAttribute enclosingMethod;

  /** InnerClasses table. If this class is an inner class, it will have an entry here. */
  private List<InnerClassAttribute> innerClasses;

  private NestHostClassAttribute nestHost;
  private final List<NestMemberClassAttribute> nestMembers;

  /** Generic signature information if the attribute is present in the input */
  protected ClassSignature classSignature;

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
      NestHostClassAttribute nestHost,
      List<NestMemberClassAttribute> nestMembers,
      EnclosingMethodAttribute enclosingMethod,
      List<InnerClassAttribute> innerClasses,
      ClassSignature classSignature,
      DexAnnotationSet annotations,
      Origin origin,
      boolean skipNameValidationForTesting) {
    super(annotations);
    assert origin != null;
    this.origin = origin;
    this.sourceFile = sourceFile;
    this.interfaces = interfaces;
    this.accessFlags = accessFlags;
    this.superType = superType;
    this.type = type;
    setStaticFields(staticFields);
    setInstanceFields(instanceFields);
    this.methodCollection = new MethodCollection(this, directMethods, virtualMethods);
    this.nestHost = nestHost;
    this.nestMembers = nestMembers;
    assert nestMembers != null;
    this.enclosingMethod = enclosingMethod;
    this.innerClasses = innerClasses;
    assert classSignature != null;
    this.classSignature = classSignature;
    assert GenericSignatureUtils.verifyNoDuplicateGenericDefinitions(classSignature, annotations);
    if (type == superType) {
      throw new CompilationError("Class " + type.toString() + " cannot extend itself");
    }
    for (DexType interfaceType : interfaces.values) {
      if (type == interfaceType) {
        throw new CompilationError("Interface " + type.toString() + " cannot implement itself");
      }
    }
    if (!skipNameValidationForTesting && !type.descriptor.isValidClassDescriptor()) {
      throw new CompilationError(
          "Class descriptor '"
              + type.descriptor.toString()
              + "' cannot be represented in dex format.");
    }
  }

  @Override
  public ClassAccessFlags getAccessFlags() {
    return accessFlags;
  }

  public DexTypeList getInterfaces() {
    return interfaces;
  }

  public DexString getSourceFile() {
    return sourceFile;
  }

  public Iterable<DexEncodedField> fields() {
    return fields(Predicates.alwaysTrue());
  }

  public Iterable<DexEncodedField> fields(final Predicate<? super DexEncodedField> predicate) {
    return Iterables.concat(
        Iterables.filter(Arrays.asList(instanceFields), predicate::test),
        Iterables.filter(Arrays.asList(staticFields), predicate::test));
  }

  public Iterable<DexEncodedMember<?, ?>> members() {
    return Iterables.concat(fields(), methods());
  }

  public MethodCollection getMethodCollection() {
    return methodCollection;
  }

  public Iterable<DexEncodedMethod> methods() {
    return methodCollection.methods();
  }

  public Iterable<DexEncodedMethod> methods(Predicate<DexEncodedMethod> predicate) {
    return methodCollection.methods(predicate);
  }

  @Override
  void collectMixedSectionItems(MixedSectionCollection mixedItems) {
    throw new Unreachable();
  }

  public Iterable<DexEncodedMethod> directMethods() {
    return methodCollection.directMethods();
  }

  public Iterable<DexEncodedMethod> directMethods(Predicate<? super DexEncodedMethod> predicate) {
    return Iterables.filter(directMethods(), predicate::test);
  }

  public void addDirectMethods(Collection<DexEncodedMethod> methods) {
    methodCollection.addDirectMethods(methods);
  }

  public DexEncodedMethod removeMethod(DexMethod method) {
    return methodCollection.removeMethod(method);
  }

  public void setDirectMethods(DexEncodedMethod[] methods) {
    methodCollection.setDirectMethods(methods);
  }

  public Iterable<DexEncodedMethod> virtualMethods() {
    return methodCollection.virtualMethods();
  }

  public Iterable<DexEncodedMethod> virtualMethods(Predicate<? super DexEncodedMethod> predicate) {
    return Iterables.filter(virtualMethods(), predicate::test);
  }

  public void addVirtualMethods(Collection<DexEncodedMethod> methods) {
    methodCollection.addVirtualMethods(methods);
  }

  public void setVirtualMethods(DexEncodedMethod[] methods) {
    methodCollection.setVirtualMethods(methods);
  }

  private boolean verifyNoAbstractMethodsOnNonAbstractClasses(
      Iterable<DexEncodedMethod> methods, InternalOptions options) {
    if (options.canHaveDalvikAbstractMethodOnNonAbstractClassVerificationBug() && !isAbstract()) {
      for (DexEncodedMethod method : methods) {
        assert !method.isAbstract()
            : "Non-abstract method on abstract class: `" + method.method.toSourceString() + "`";
      }
    }
    return true;
  }

  public void forEachMethod(Consumer<DexEncodedMethod> consumer) {
    methodCollection.forEachMethod(consumer);
  }

  public List<DexEncodedMethod> allMethodsSorted() {
    return methodCollection.allMethodsSorted();
  }

  public void virtualizeMethods(Set<DexEncodedMethod> privateInstanceMethods) {
    methodCollection.virtualizeMethods(privateInstanceMethods);
  }

  /**
   * For all annotations on the class and all annotations on its methods and fields apply the
   * specified consumer.
   */
  public void forEachAnnotation(Consumer<DexAnnotation> consumer) {
    annotations().forEach(consumer);
    for (DexEncodedMethod method : methods()) {
      method.annotations().forEach(consumer);
      method.parameterAnnotationsList.forEachAnnotation(consumer);
    }
    for (DexEncodedField field : fields()) {
      field.annotations().forEach(consumer);
    }
  }

  public void forEachField(Consumer<DexEncodedField> consumer) {
    for (DexEncodedField field : staticFields()) {
      consumer.accept(field);
    }
    for (DexEncodedField field : instanceFields()) {
      consumer.accept(field);
    }
  }

  public TraversalContinuation traverseFields(Function<DexEncodedField, TraversalContinuation> fn) {
    for (DexEncodedField field : fields()) {
      if (fn.apply(field).shouldBreak()) {
        return TraversalContinuation.BREAK;
      }
    }
    return TraversalContinuation.CONTINUE;
  }

  public List<DexEncodedField> staticFields() {
    assert staticFields != null;
    if (InternalOptions.assertionsEnabled()) {
      return Collections.unmodifiableList(Arrays.asList(staticFields));
    }
    return Arrays.asList(staticFields);
  }

  public void appendStaticField(DexEncodedField field) {
    DexEncodedField[] newFields = new DexEncodedField[staticFields.length + 1];
    System.arraycopy(staticFields, 0, newFields, 0, staticFields.length);
    newFields[staticFields.length] = field;
    staticFields = newFields;
    assert verifyCorrectnessOfFieldHolder(field);
    assert verifyNoDuplicateFields();
  }

  public void appendStaticFields(Collection<DexEncodedField> fields) {
    DexEncodedField[] newFields = new DexEncodedField[staticFields.length + fields.size()];
    System.arraycopy(staticFields, 0, newFields, 0, staticFields.length);
    int i = staticFields.length;
    for (DexEncodedField field : fields) {
      newFields[i] = field;
      i++;
    }
    staticFields = newFields;
    assert verifyCorrectnessOfFieldHolders(fields);
    assert verifyNoDuplicateFields();
  }

  public void removeStaticField(int index) {
    DexEncodedField[] newFields = new DexEncodedField[staticFields.length - 1];
    System.arraycopy(staticFields, 0, newFields, 0, index);
    System.arraycopy(staticFields, index + 1, newFields, index, staticFields.length - index - 1);
    staticFields = newFields;
  }

  public void setStaticField(int index, DexEncodedField field) {
    staticFields[index] = field;
    assert verifyCorrectnessOfFieldHolder(field);
    assert verifyNoDuplicateFields();
  }

  public void setStaticFields(DexEncodedField[] fields) {
    staticFields = MoreObjects.firstNonNull(fields, DexEncodedField.EMPTY_ARRAY);
    assert verifyCorrectnessOfFieldHolders(staticFields());
    assert verifyNoDuplicateFields();
  }

  public boolean definesStaticField(DexField field) {
    for (DexEncodedField encodedField : staticFields()) {
      if (encodedField.field == field) {
        return true;
      }
    }
    return false;
  }

  public List<DexEncodedField> instanceFields() {
    assert instanceFields != null;
    if (InternalOptions.assertionsEnabled()) {
      return Collections.unmodifiableList(Arrays.asList(instanceFields));
    }
    return Arrays.asList(instanceFields);
  }

  public void appendInstanceField(DexEncodedField field) {
    DexEncodedField[] newFields = new DexEncodedField[instanceFields.length + 1];
    System.arraycopy(instanceFields, 0, newFields, 0, instanceFields.length);
    newFields[instanceFields.length] = field;
    instanceFields = newFields;
    assert verifyCorrectnessOfFieldHolder(field);
    assert verifyNoDuplicateFields();
  }

  public void appendInstanceFields(Collection<DexEncodedField> fields) {
    DexEncodedField[] newFields = new DexEncodedField[instanceFields.length + fields.size()];
    System.arraycopy(instanceFields, 0, newFields, 0, instanceFields.length);
    int i = instanceFields.length;
    for (DexEncodedField field : fields) {
      newFields[i] = field;
      i++;
    }
    instanceFields = newFields;
    assert verifyCorrectnessOfFieldHolders(fields);
    assert verifyNoDuplicateFields();
  }

  public void removeInstanceField(int index) {
    DexEncodedField[] newFields = new DexEncodedField[instanceFields.length - 1];
    System.arraycopy(instanceFields, 0, newFields, 0, index);
    System.arraycopy(
        instanceFields, index + 1, newFields, index, instanceFields.length - index - 1);
    instanceFields = newFields;
  }

  public void setInstanceField(int index, DexEncodedField field) {
    instanceFields[index] = field;
    assert verifyCorrectnessOfFieldHolder(field);
    assert verifyNoDuplicateFields();
  }

  public void setInstanceFields(DexEncodedField[] fields) {
    instanceFields = MoreObjects.firstNonNull(fields, DexEncodedField.EMPTY_ARRAY);
    assert verifyCorrectnessOfFieldHolders(instanceFields());
    assert verifyNoDuplicateFields();
  }

  private boolean verifyCorrectnessOfFieldHolder(DexEncodedField field) {
    assert field.holder() == type
        : "Expected field `"
            + field.field.toSourceString()
            + "` to have holder `"
            + type.toSourceString()
            + "`";
    return true;
  }

  private boolean verifyCorrectnessOfFieldHolders(Iterable<DexEncodedField> fields) {
    for (DexEncodedField field : fields) {
      assert verifyCorrectnessOfFieldHolder(field);
    }
    return true;
  }

  private boolean verifyNoDuplicateFields() {
    Set<DexField> unique = Sets.newIdentityHashSet();
    for (DexEncodedField field : fields()) {
      boolean changed = unique.add(field.field);
      assert changed : "Duplicate field `" + field.field.toSourceString() + "`";
    }
    return true;
  }

  /** Find static field in this class matching {@param field}. */
  public DexEncodedField lookupStaticField(DexField field) {
    return lookupTarget(staticFields, field);
  }

  /** Find instance field in this class matching {@param field}. */
  public DexEncodedField lookupInstanceField(DexField field) {
    return lookupTarget(instanceFields, field);
  }

  public DexField lookupUniqueInstanceFieldWithName(DexString name) {
    DexField field = null;
    for (DexEncodedField encodedField : instanceFields()) {
      if (encodedField.field.name == name) {
        if (field != null) {
          return null;
        }
        field = encodedField.field;
      }
    }
    return field;
  }

  /** Find field in this class matching {@param field}. */
  public DexEncodedField lookupField(DexField field) {
    DexEncodedField result = lookupInstanceField(field);
    return result == null ? lookupStaticField(field) : result;
  }

  /** Find direct method in this class matching {@param method}. */
  public DexEncodedMethod lookupDirectMethod(DexMethod method) {
    return methodCollection.getDirectMethod(method);
  }

  /** Find direct method in this class matching {@param predicate}. */
  public DexEncodedMethod lookupDirectMethod(Predicate<DexEncodedMethod> predicate) {
    return methodCollection.getDirectMethod(predicate);
  }

  /** Find virtual method in this class matching {@param method}. */
  public DexEncodedMethod lookupVirtualMethod(DexMethod method) {
    return methodCollection.getVirtualMethod(method);
  }

  /** Find virtual method in this class matching {@param predicate}. */
  public DexEncodedMethod lookupVirtualMethod(Predicate<DexEncodedMethod> predicate) {
    return methodCollection.getVirtualMethod(predicate);
  }

  /** Find member in this class matching {@param member}. */
  @SuppressWarnings("unchecked")
  public <D extends DexEncodedMember<D, R>, R extends DexMember<D, R>> D lookupMember(
      DexMember<D, R> member) {
    DexEncodedMember<?, ?> definition =
        member.isDexField() ? lookupField(member.asDexField()) : lookupMethod(member.asDexMethod());
    return (D) definition;
  }

  /** Find method in this class matching {@param method}. */
  public DexEncodedMethod lookupMethod(DexMethod method) {
    return methodCollection.getMethod(method);
  }

  /** Find method in this class matching {@param method}. */
  public DexEncodedMethod lookupMethod(Predicate<DexEncodedMethod> predicate) {
    return methodCollection.getMethod(predicate);
  }

  public DexEncodedMethod lookupSignaturePolymorphicMethod(
      DexString methodName, DexItemFactory factory) {
    if (type != factory.methodHandleType && type != factory.varHandleType) {
      return null;
    }
    DexEncodedMethod matchingName = null;
    DexEncodedMethod signaturePolymorphicMethod = null;
    for (DexEncodedMethod method : virtualMethods()) {
      if (method.method.name == methodName) {
        if (matchingName != null) {
          // The jvm spec, section 5.4.3.3 details that there must be exactly one method with the
          // given name only.
          return null;
        }
        matchingName = method;
        if (isSignaturePolymorphicMethod(method, factory)) {
          signaturePolymorphicMethod = method;
        }
      }
    }
    return signaturePolymorphicMethod;
  }

  private boolean isSignaturePolymorphicMethod(DexEncodedMethod method, DexItemFactory factory) {
    assert method.holder() == factory.methodHandleType || method.holder() == factory.varHandleType;
    return method.accessFlags.isVarargs()
        && method.accessFlags.isNative()
        && method.method.proto.parameters.size() == 1
        && method.method.proto.parameters.values[0] != factory.objectArrayType;
  }

  private <D extends DexEncodedMember<D, R>, R extends DexMember<D, R>> D lookupTarget(
      D[] items, R descriptor) {
    for (D entry : items) {
      if (descriptor.match(entry)) {
        return entry;
      }
    }
    return null;
  }

  public boolean isAbstract() {
    return accessFlags.isAbstract();
  }

  public boolean isAnnotation() {
    return accessFlags.isAnnotation();
  }

  public boolean isFinal() {
    return accessFlags.isFinal();
  }

  public boolean isEffectivelyFinal(AppView<?> appView) {
    return isFinal();
  }

  public boolean isInterface() {
    return accessFlags.isInterface();
  }

  public boolean isEnum() {
    return accessFlags.isEnum();
  }

  public abstract void addDependencies(MixedSectionCollection collector);

  @Override
  public DexReference getReference() {
    return getType();
  }

  @Override
  public boolean isDexClass() {
    return true;
  }

  @Override
  public DexClass asDexClass() {
    return this;
  }

  public boolean isClasspathClass() {
    return false;
  }

  public DexClasspathClass asClasspathClass() {
    return null;
  }

  public abstract boolean isNotProgramClass();

  public boolean isLibraryClass() {
    return false;
  }

  public DexLibraryClass asLibraryClass() {
    return null;
  }

  public boolean isPrivate() {
    return accessFlags.isPrivate();
  }

  public boolean isPublic() {
    return accessFlags.isPublic();
  }

  @Override
  public boolean isStatic() {
    return accessFlags.isStatic();
  }

  @Override
  public boolean isStaticMember() {
    return false;
  }

  public DexEncodedMethod getClassInitializer() {
    DexEncodedMethod classInitializer = methodCollection.getClassInitializer();
    assert classInitializer != DexEncodedMethod.SENTINEL;
    return classInitializer;
  }

  public Origin getOrigin() {
    return this.origin;
  }

  public DexType getType() {
    return type;
  }

  public DexType getSuperType() {
    return superType;
  }

  public boolean hasClassInitializer() {
    return getClassInitializer() != null;
  }

  public boolean hasDefaultInitializer() {
    return getDefaultInitializer() != null;
  }

  public DexEncodedMethod getInitializer(DexType[] parameters) {
    for (DexEncodedMethod method : directMethods()) {
      if (method.isInstanceInitializer()
          && Arrays.equals(method.method.proto.parameters.values, parameters)) {
        return method;
      }
    }
    return null;
  }

  public DexEncodedMethod getDefaultInitializer() {
    return getInitializer(DexType.EMPTY_ARRAY);
  }

  public boolean hasMissingSuperType(AppInfoWithClassHierarchy appInfo) {
    if (superType != null && appInfo.isMissingOrHasMissingSuperType(superType)) {
      return true;
    }
    for (DexType interfaceType : interfaces.values) {
      if (appInfo.isMissingOrHasMissingSuperType(interfaceType)) {
        return true;
      }
    }
    return false;
  }

  public boolean isResolvable(AppView<?> appView) {
    if (isResolvable.isUnknown()) {
      boolean resolvable;
      if (!isProgramClass()) {
        resolvable = appView.dexItemFactory().libraryTypesAssumedToBePresent.contains(type);
      } else {
        resolvable = true;
        for (DexType supertype : allImmediateSupertypes()) {
          resolvable &= supertype.isResolvable(appView);
          if (!resolvable) {
            break;
          }
        }
      }
      isResolvable = OptionalBool.of(resolvable);
    }
    assert !isResolvable.isUnknown();
    return isResolvable.isTrue();
  }

  public boolean isSerializable(AppView<? extends AppInfoWithClassHierarchy> appView) {
    return appView.appInfo().isSerializable(type);
  }

  public boolean isExternalizable(AppView<? extends AppInfoWithClassHierarchy> appView) {
    return appView.appInfo().isExternalizable(type);
  }

  public boolean classInitializationMayHaveSideEffects(AppView<?> appView) {
    return classInitializationMayHaveSideEffects(
        appView, Predicates.alwaysFalse(), Sets.newIdentityHashSet());
  }

  public boolean classInitializationMayHaveSideEffectsInContext(
      AppView<AppInfoWithLiveness> appView, ProgramDefinition context) {
    return classInitializationMayHaveSideEffects(
        appView,
        // Types that are a super type of the current context are guaranteed to be initialized
        // already.
        type -> appView.appInfo().isSubtype(context.getContextType(), type),
        Sets.newIdentityHashSet());
  }

  public boolean classInitializationMayHaveSideEffects(
      AppView<?> appView, Predicate<DexType> ignore, Set<DexType> seen) {
    if (!seen.add(type)) {
      return false;
    }
    if (ignore.test(type)) {
      return false;
    }
    if (isLibraryClass()) {
      if (isInterface()) {
        return appView.options().libraryInterfacesMayHaveStaticInitialization;
      }
      if (appView.dexItemFactory().libraryClassesWithoutStaticInitialization.contains(type)) {
        return false;
      }
    }
    if (hasClassInitializerThatCannotBePostponed()) {
      return true;
    }
    if (defaultValuesForStaticFieldsMayTriggerAllocation()) {
      return true;
    }
    return initializationOfParentTypesMayHaveSideEffects(appView, ignore, seen);
  }

  private boolean hasClassInitializerThatCannotBePostponed() {
    if (isLibraryClass()) {
      // We don't know for library classes in general but assume that java.lang.Object is safe.
      return superType != null;
    }
    DexEncodedMethod clinit = getClassInitializer();
    if (clinit == null || clinit.getCode() == null) {
      return false;
    }
    return !clinit.getOptimizationInfo().classInitializerMayBePostponed();
  }

  public void forEachImmediateSupertype(Consumer<DexType> fn) {
    if (superType != null) {
      fn.accept(superType);
    }
    for (DexType iface : interfaces.values) {
      fn.accept(iface);
    }
  }

  public Iterable<DexType> allImmediateSupertypes() {
    Iterator<DexType> iterator =
        superType != null
            ? Iterators.concat(
                Iterators.singletonIterator(superType), Iterators.forArray(interfaces.values))
            : Iterators.forArray(interfaces.values);
    return () -> iterator;
  }

  public boolean initializationOfParentTypesMayHaveSideEffects(AppView<?> appView) {
    return initializationOfParentTypesMayHaveSideEffects(
        appView, Predicates.alwaysFalse(), Sets.newIdentityHashSet());
  }

  public boolean initializationOfParentTypesMayHaveSideEffects(
      AppView<?> appView, Predicate<DexType> ignore, Set<DexType> seen) {
    for (DexType iface : interfaces.values) {
      if (iface.classInitializationMayHaveSideEffects(appView, ignore, seen)) {
        return true;
      }
    }
    if (superType != null
        && superType.classInitializationMayHaveSideEffects(appView, ignore, seen)) {
      return true;
    }
    return false;
  }

  public boolean definesFinalizer(DexItemFactory factory) {
    return lookupVirtualMethod(factory.objectMembers.finalize) != null;
  }

  public boolean defaultValuesForStaticFieldsMayTriggerAllocation() {
    return staticFields().stream()
        .anyMatch(field -> field.getStaticValue().mayHaveSideEffects());
  }

  public List<InnerClassAttribute> getInnerClasses() {
    return innerClasses;
  }

  public void setInnerClasses(List<InnerClassAttribute> innerClasses) {
    this.innerClasses = innerClasses;
  }

  public EnclosingMethodAttribute getEnclosingMethodAttribute() {
    return enclosingMethod;
  }

  public void setEnclosingMethodAttribute(EnclosingMethodAttribute enclosingMethod) {
    this.enclosingMethod = enclosingMethod;
  }

  public void clearEnclosingMethodAttribute() {
    enclosingMethod = null;
  }

  public void removeEnclosingMethodAttribute(Predicate<EnclosingMethodAttribute> predicate) {
    if (enclosingMethod != null && predicate.test(enclosingMethod)) {
      enclosingMethod = null;
    }
  }

  public void clearInnerClasses() {
    innerClasses.clear();
  }

  public void clearClassSignature() {
    classSignature = ClassSignature.noSignature();
  }

  public void removeInnerClasses(Predicate<InnerClassAttribute> predicate) {
    innerClasses.removeIf(predicate::test);
  }

  public InnerClassAttribute getInnerClassAttributeForThisClass() {
    for (InnerClassAttribute innerClassAttribute : getInnerClasses()) {
      if (type == innerClassAttribute.getInner()) {
        return innerClassAttribute;
      }
    }
    return null;
  }

  public void replaceInnerClassAttributeForThisClass(InnerClassAttribute newInnerClassAttribute) {
    ListIterator<InnerClassAttribute> iterator = getInnerClasses().listIterator();
    while (iterator.hasNext()) {
      InnerClassAttribute innerClassAttribute = iterator.next();
      if (type == innerClassAttribute.getInner()) {
        iterator.set(newInnerClassAttribute);
        return;
      }
    }
    throw new Unreachable();
  }

  public ClassSignature getClassSignature() {
    return classSignature;
  }

  public void setClassSignature(ClassSignature classSignature) {
    this.classSignature = classSignature;
  }

  public boolean isLocalClass() {
    InnerClassAttribute innerClass = getInnerClassAttributeForThisClass();
    // The corresponding enclosing-method attribute might be not available, e.g., CF version 50.
    return innerClass != null && innerClass.getOuter() == null && innerClass.isNamed();
  }

  public boolean isMemberClass() {
    InnerClassAttribute innerClass = getInnerClassAttributeForThisClass();
    boolean isMember = innerClass != null && innerClass.getOuter() != null && innerClass.isNamed();
    assert !isMember || getEnclosingMethodAttribute() == null;
    return isMember;
  }

  public boolean isAnonymousClass() {
    InnerClassAttribute innerClass = getInnerClassAttributeForThisClass();
    // The corresponding enclosing-method attribute might be not available, e.g., CF version 50.
    // We can't rely on outer type either because it's not null prior to 51 and null since 51.
    return innerClass != null && innerClass.isAnonymous();
  }

  public boolean isInANest() {
    return isNestHost() || isNestMember();
  }

  public void clearNestHost() {
    nestHost = null;
  }

  public void setNestHost(DexType type) {
    assert type != null;
    this.nestHost = new NestHostClassAttribute(type);
  }

  public boolean isNestHost() {
    return !nestMembers.isEmpty();
  }

  public boolean isNestMember() {
    return nestHost != null;
  }

  public DexType getNestHost() {
    if (isNestMember()) {
      return nestHost.getNestHost();
    }
    if (isNestHost()) {
      return type;
    }
    return null;
  }

  public void forEachNestMember(Consumer<DexType> consumer) {
    assert isNestHost();
    getNestMembersClassAttributes().forEach(member -> consumer.accept(member.getNestMember()));
  }

  public NestHostClassAttribute getNestHostClassAttribute() {
    return nestHost;
  }

  public List<NestMemberClassAttribute> getNestMembersClassAttributes() {
    return nestMembers;
  }

  /** Returns kotlin class info if the class is synthesized by kotlin compiler. */
  public abstract KotlinClassLevelInfo getKotlinInfo();

  public boolean hasInstanceFields() {
    return instanceFields.length > 0;
  }

  public boolean hasInstanceFieldsDirectlyOrIndirectly(AppView<?> appView) {
    if (superType == null || type == appView.dexItemFactory().objectType) {
      return false;
    }
    if (hasInstanceFields()) {
      return true;
    }
    DexClass superClass = appView.definitionFor(superType);
    return superClass == null || superClass.hasInstanceFieldsDirectlyOrIndirectly(appView);
  }

  public List<DexEncodedField> getDirectAndIndirectInstanceFields(AppView<?> appView) {
    List<DexEncodedField> result = new ArrayList<>();
    DexClass current = this;
    while (current != null && current.type != appView.dexItemFactory().objectType) {
      result.addAll(current.instanceFields());
      current = appView.definitionFor(current.superType);
    }
    return result;
  }

  public boolean isValid(InternalOptions options) {
    assert verifyNoAbstractMethodsOnNonAbstractClasses(virtualMethods(), options);
    assert !isInterface() || !getMethodCollection().hasVirtualMethods(DexEncodedMethod::isFinal);
    assert verifyCorrectnessOfFieldHolders(fields());
    assert verifyNoDuplicateFields();
    assert methodCollection.verify();
    return true;
  }

  public boolean hasStaticSynchronizedMethods() {
    for (DexEncodedMethod encodedMethod : directMethods()) {
      if (encodedMethod.isStatic() && encodedMethod.isSynchronized()) {
        return true;
      }
    }
    return false;
  }
}
