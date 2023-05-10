// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.google.common.base.Predicates.alwaysFalse;
import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.GenericSignature.ClassTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FormalTypeParameter;
import com.android.tools.r8.graph.MethodCollection.MethodCollectionFactory;
import com.android.tools.r8.kotlin.KotlinClassLevelInfo;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevelUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.structural.Copyable;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
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
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public abstract class DexClass extends DexDefinition
    implements ClassDefinition, ClassResolutionResult, Copyable<DexClass> {

  public interface FieldSetter {
    void setField(int index, DexEncodedField field);
  }

  public final Origin origin;
  public final DexType type;
  public final ClassAccessFlags accessFlags;
  public DexType superType;
  public DexTypeList interfaces;
  public DexString sourceFile;

  private OptionalBool isResolvable = OptionalBool.unknown();

  /** Access has to be synchronized during concurrent collection/writing phase. */
  protected final FieldCollection fieldCollection;

  /** Access has to be synchronized during concurrent collection/writing phase. */
  protected final MethodCollection methodCollection;

  /** Enclosing context of this class if it is an inner class, null otherwise. */
  private EnclosingMethodAttribute enclosingMethod;

  /** InnerClasses table. If this class is an inner class, it will have an entry here. */
  private List<InnerClassAttribute> innerClasses;

  /**
   * Nest attributes. If this class was compiled in JDK 11 and higher, and is in a nest, one of the
   * two attributes will be set.
   */
  private NestHostClassAttribute nestHost;

  private List<NestMemberClassAttribute> nestMembers;

  private List<PermittedSubclassAttribute> permittedSubclasses;

  private List<RecordComponentInfo> recordComponents;

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
      MethodCollectionFactory methodCollectionFactory,
      NestHostClassAttribute nestHost,
      List<NestMemberClassAttribute> nestMembers,
      List<PermittedSubclassAttribute> permittedSubclasses,
      List<RecordComponentInfo> recordComponents,
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
    this.fieldCollection = FieldCollection.create(this, staticFields, instanceFields);
    this.methodCollection = methodCollectionFactory.create(this);
    this.nestHost = nestHost;
    this.nestMembers = nestMembers;
    assert nestMembers != null;
    this.permittedSubclasses = permittedSubclasses;
    this.recordComponents = recordComponents;
    assert permittedSubclasses != null;
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
  public boolean hasClassResolutionResult() {
    return true;
  }

  @Override
  public void forEachClassResolutionResult(Consumer<DexClass> consumer) {
    consumer.accept(this);
  }

  @Override
  public DexClass toSingleClassWithProgramOverLibrary() {
    return this;
  }

  @Override
  public DexClass toSingleClassWithLibraryOverProgram() {
    return this;
  }

  @Override
  public DexClass toAlternativeClass() {
    return null;
  }

  public abstract void accept(
      Consumer<DexProgramClass> programClassConsumer,
      Consumer<DexClasspathClass> classpathClassConsumer,
      Consumer<DexLibraryClass> libraryClassConsumer);

  @Override
  public void forEachClassField(Consumer<? super DexClassAndField> consumer) {
    forEachClassFieldMatching(alwaysTrue(), consumer);
  }

  public void forEachClassFieldMatching(
      Predicate<DexEncodedField> predicate, Consumer<? super DexClassAndField> consumer) {
    forEachFieldMatching(predicate, field -> consumer.accept(DexClassAndField.create(this, field)));
  }

  @Override
  public void forEachClassMethod(Consumer<? super DexClassAndMethod> consumer) {
    forEachClassMethodMatching(alwaysTrue(), consumer);
  }

  public void forEachClassMethodMatching(
      Predicate<DexEncodedMethod> predicate, Consumer<? super DexClassAndMethod> consumer) {
    methodCollection.forEachMethodMatching(
        predicate, method -> consumer.accept(DexClassAndMethod.create(this, method)));
  }

  @Override
  public ClassAccessFlags getAccessFlags() {
    return accessFlags;
  }

  public DexTypeList getInterfaces() {
    return interfaces;
  }

  public void setInterfaces(DexTypeList interfaces) {
    this.interfaces = interfaces;
  }

  public DexString getSourceFile() {
    return sourceFile;
  }

  public void setSourceFile(DexString sourceFile) {
    this.sourceFile = sourceFile;
  }

  public Iterable<DexClassAndField> classFields() {
    return Iterables.transform(fields(), field -> DexClassAndField.create(this, field));
  }

  public Iterable<DexEncodedField> fields() {
    return fields(Predicates.alwaysTrue());
  }

  public Iterable<DexEncodedField> fields(final Predicate<? super DexEncodedField> predicate) {
    return fieldCollection.fields(predicate);
  }

  public Iterable<DexEncodedMember<?, ?>> members() {
    return Iterables.concat(fields(), methods());
  }

  public Iterable<DexEncodedMember<?, ?>> members(Predicate<DexEncodedMember<?, ?>> predicate) {
    return Iterables.concat(fields(predicate), methods(predicate));
  }

  public FieldCollection getFieldCollection() {
    return fieldCollection;
  }

  @Override
  public MethodCollection getMethodCollection() {
    return methodCollection;
  }

  public Iterable<DexClassAndMethod> classMethods() {
    return Iterables.transform(methods(), method -> DexClassAndMethod.create(this, method));
  }

  public Iterable<DexEncodedMethod> methods() {
    return methodCollection.methods();
  }

  public Iterable<DexEncodedMethod> methods(Predicate<? super DexEncodedMethod> predicate) {
    return methodCollection.methods(predicate);
  }

  @Override
  protected void collectMixedSectionItems(MixedSectionCollection mixedItems) {
    throw new Unreachable();
  }

  public Iterable<DexEncodedMethod> directMethods() {
    return methodCollection.directMethods();
  }

  public Iterable<DexEncodedMethod> directMethods(Predicate<? super DexEncodedMethod> predicate) {
    return Iterables.filter(directMethods(), predicate::test);
  }

  public void addDirectMethod(DexEncodedMethod method) {
    methodCollection.addDirectMethod(method);
  }

  public void addDirectMethods(Collection<DexEncodedMethod> methods) {
    methodCollection.addDirectMethods(methods);
  }

  public DexEncodedMethod removeMethod(DexMethod method) {
    return methodCollection.removeMethod(method);
  }

  public void setDirectMethods(Collection<DexEncodedMethod> methods) {
    setDirectMethods(methods.toArray(DexEncodedMethod.EMPTY_ARRAY));
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

  public void addVirtualMethod(DexEncodedMethod method) {
    methodCollection.addVirtualMethod(method);
  }

  public void addVirtualMethods(Collection<DexEncodedMethod> methods) {
    methodCollection.addVirtualMethods(methods);
  }

  public void setVirtualMethods(List<DexEncodedMethod> methods) {
    setVirtualMethods(methods.toArray(DexEncodedMethod.EMPTY_ARRAY));
  }

  public void setVirtualMethods(DexEncodedMethod[] methods) {
    methodCollection.setVirtualMethods(methods);
  }

  private boolean verifyNoAbstractMethodsOnNonAbstractClasses(
      Iterable<DexEncodedMethod> methods, InternalOptions options) {
    if (options.canHaveDalvikAbstractMethodOnNonAbstractClassVerificationBug() && !isAbstract()) {
      for (DexEncodedMethod method : methods) {
        assert !method.isAbstract()
            : "Non-abstract method on abstract class: `"
                + method.getReference().toSourceString()
                + "`";
      }
    }
    return true;
  }

  public void forEachMethod(Consumer<DexEncodedMethod> consumer) {
    methodCollection.forEachMethod(consumer);
  }

  public List<DexEncodedField> allFieldsSorted() {
    return fieldCollection.allFieldsSorted();
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
    forEachFieldMatching(alwaysTrue(), consumer);
  }

  public void forEachFieldMatching(
      Predicate<? super DexEncodedField> predicate, Consumer<? super DexEncodedField> consumer) {
    fields(predicate).forEach(consumer);
  }

  public void forEachInstanceField(Consumer<DexEncodedField> consumer) {
    forEachInstanceFieldMatching(alwaysTrue(), consumer);
  }

  public void forEachInstanceFieldMatching(
      Predicate<DexEncodedField> predicate, Consumer<DexEncodedField> consumer) {
    instanceFields(predicate).forEach(consumer);
  }

  public void forEachStaticField(Consumer<DexEncodedField> consumer) {
    forEachStaticFieldMatching(alwaysTrue(), consumer);
  }

  public void forEachStaticFieldMatching(
      Predicate<DexEncodedField> predicate, Consumer<DexEncodedField> consumer) {
    staticFields(predicate).forEach(consumer);
  }

  public TraversalContinuation<?, ?> traverseFields(
      Function<DexEncodedField, TraversalContinuation<?, ?>> fn) {
    for (DexEncodedField field : fields()) {
      if (fn.apply(field).shouldBreak()) {
        return TraversalContinuation.doBreak();
      }
    }
    return TraversalContinuation.doContinue();
  }

  public List<DexEncodedField> staticFields() {
    return fieldCollection.staticFieldsAsList();
  }

  public Iterable<DexEncodedField> staticFields(Predicate<DexEncodedField> predicate) {
    return IterableUtils.filter(staticFields(), predicate);
  }

  public void appendStaticField(DexEncodedField field) {
    fieldCollection.appendStaticField(field);
  }

  public void appendStaticFields(Collection<DexEncodedField> fields) {
    fieldCollection.appendStaticFields(fields);
  }

  public DexEncodedField[] clearStaticFields() {
    List<DexEncodedField> previousFields = staticFields();
    fieldCollection.clearStaticFields();
    return previousFields.toArray(DexEncodedField.EMPTY_ARRAY);
  }

  public void setStaticFields(DexEncodedField[] fields) {
    fieldCollection.setStaticFields(fields);
  }

  public void setStaticFields(Collection<DexEncodedField> fields) {
    setStaticFields(fields.toArray(DexEncodedField.EMPTY_ARRAY));
  }

  public List<DexEncodedField> instanceFields() {
    return fieldCollection.instanceFieldsAsList();
  }

  public Iterable<DexEncodedField> instanceFields(Predicate<? super DexEncodedField> predicate) {
    return Iterables.filter(instanceFields(), predicate::test);
  }

  public void appendInstanceField(DexEncodedField field) {
    fieldCollection.appendInstanceField(field);
  }

  public void appendInstanceFields(Collection<DexEncodedField> fields) {
    fieldCollection.appendInstanceFields(fields);
  }

  public void setInstanceFields(DexEncodedField[] fields) {
    fieldCollection.setInstanceFields(fields);
  }

  public DexEncodedField[] clearInstanceFields() {
    List<DexEncodedField> previousFields = instanceFields();
    fieldCollection.clearInstanceFields();
    return previousFields.toArray(DexEncodedField.EMPTY_ARRAY);
  }

  /** Find method in this class matching {@param method}. */
  public DexClassAndField lookupClassField(DexField field) {
    return toClassFieldOrNull(lookupField(field));
  }

  /** Find field in this class matching {@param field}. */
  public DexEncodedField lookupField(DexField field) {
    return fieldCollection.lookupField(field);
  }

  /** Find static field in this class matching {@param field}. */
  public DexEncodedField lookupStaticField(DexField field) {
    return fieldCollection.lookupStaticField(field);
  }

  /** Find instance field in this class matching {@param field}. */
  public DexEncodedField lookupInstanceField(DexField field) {
    return fieldCollection.lookupInstanceField(field);
  }

  public DexEncodedField lookupUniqueInstanceFieldWithName(DexString name) {
    return internalLookupUniqueFieldThatMatches(field -> field.getName() == name, instanceFields());
  }

  public DexEncodedField lookupUniqueStaticFieldWithName(DexString name) {
    return internalLookupUniqueFieldThatMatches(field -> field.getName() == name, staticFields());
  }

  private static DexEncodedField internalLookupUniqueFieldThatMatches(
      Predicate<DexEncodedField> predicate, List<DexEncodedField> fields) {
    DexEncodedField result = null;
    for (DexEncodedField field : fields) {
      if (predicate.test(field)) {
        if (result != null) {
          return null;
        }
        result = field;
      }
    }
    return result;
  }

  private DexClassAndField toClassFieldOrNull(DexEncodedField field) {
    return field != null ? DexClassAndField.create(this, field) : null;
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
  public DexClassAndMethod lookupClassMethod(DexMethod method) {
    return toClassMethodOrNull(methodCollection.getMethod(method));
  }

  private DexClassAndMethod toClassMethodOrNull(DexEncodedMethod method) {
    return method != null ? DexClassAndMethod.create(this, method) : null;
  }

  /** Find method in this class matching {@param method}. */
  public DexEncodedMethod lookupMethod(DexMethod method) {
    return methodCollection.getMethod(method);
  }

  public DexEncodedMethod lookupMethod(DexProto methodProto, DexString methodName) {
    return methodCollection.getMethod(methodProto, methodName);
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
      if (method.getReference().name == methodName) {
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

  public static boolean isSignaturePolymorphicMethod(
      DexEncodedMethod method, DexItemFactory factory) {
    assert method.getHolderType() == factory.methodHandleType
        || method.getHolderType() == factory.varHandleType;
    return method.accessFlags.isVarargs()
        && method.accessFlags.isNative()
        && method.getReference().proto.parameters.size() == 1
        && method.getReference().proto.parameters.values[0] == factory.objectArrayType;
  }

  public boolean canBeInstantiatedByNewInstance() {
    return !isAbstract() && !isAnnotation() && !isInterface();
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

  @Override
  public boolean isInterface() {
    return accessFlags.isInterface();
  }

  public boolean isEnum() {
    return accessFlags.isEnum();
  }

  public boolean isRecord() {
    return accessFlags.isRecord();
  }

  public abstract void addDependencies(MixedSectionCollection collector);

  @Override
  public DexReference getReference() {
    return getType();
  }

  @Override
  public DexClass asClass() {
    return this;
  }

  @Override
  public boolean isDexClass() {
    return true;
  }

  @Override
  public DexClass asDexClass() {
    return this;
  }

  @Override
  public boolean isClasspathClass() {
    return false;
  }

  @Override
  public DexClasspathClass asClasspathClass() {
    return null;
  }

  public abstract boolean isNotProgramClass();

  @Override
  public boolean isLibraryClass() {
    return false;
  }

  @Override
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

  @Override
  public ClassReference getClassReference() {
    return Reference.classFromDescriptor(getType().toDescriptorString());
  }

  @Override
  public DexClass getContextClass() {
    return this;
  }

  @Override
  public DexClass getDefinition() {
    return this;
  }

  @Override
  public Origin getOrigin() {
    return this.origin;
  }

  @Override
  public DexType getType() {
    return type;
  }

  public boolean hasSuperType() {
    return superType != null;
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
          && Arrays.equals(method.getReference().proto.parameters.values, parameters)) {
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
      if (isLibraryClass()) {
        resolvable = AndroidApiLevelUtils.isApiSafeForReference(asLibraryClass(), appView);
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
    return classInitializationMayHaveSideEffects(appView, alwaysFalse());
  }

  public boolean classInitializationMayHaveSideEffects(
      AppView<?> appView, Predicate<DexType> ignore) {
    return internalClassOrInterfaceMayHaveInitializationSideEffects(
        appView, this, ignore, Sets.newIdentityHashSet());
  }

  public final boolean classInitializationMayHaveSideEffectsInContext(
      AppView<?> appView, Definition context) {
    // Types that are a super type of the current context are guaranteed to be initialized already.
    return classInitializationMayHaveSideEffects(
        appView, type -> appView.isSubtype(context.getContextType(), type).isTrue());
  }

  abstract boolean internalClassOrInterfaceMayHaveInitializationSideEffects(
      AppView<?> appView,
      DexClass initialAccessHolder,
      Predicate<DexType> ignore,
      Set<DexType> seen);

  public void forEachImmediateInterface(Consumer<DexType> fn) {
    for (DexType iface : interfaces.values) {
      fn.accept(iface);
    }
  }

  public void forEachImmediateSupertype(Consumer<DexType> fn) {
    if (superType != null) {
      fn.accept(superType);
    }
    forEachImmediateInterface(fn);
  }

  public void forEachImmediateSupertype(BiConsumer<DexType, Boolean> fn) {
    if (superType != null) {
      fn.accept(superType, false);
    }
    forEachImmediateInterface(iface -> fn.accept(iface, true));
  }

  public boolean validInterfaceSignatures() {
    return getClassSignature().getSuperInterfaceSignatures().isEmpty()
        || interfaces.values.length == getClassSignature().getSuperInterfaceSignatures().size();
  }

  public void forEachImmediateInterfaceWithSignature(
      BiConsumer<DexType, ClassTypeSignature> consumer) {
    assert validInterfaceSignatures();

    // If there is no generic signature information don't pass any type arguments.
    if (getClassSignature().getSuperInterfaceSignatures().isEmpty()) {
      forEachImmediateInterface(
          superInterface ->
              consumer.accept(superInterface, new ClassTypeSignature(superInterface)));
      return;
    }

    Iterator<DexType> interfaceIterator = Arrays.asList(interfaces.values).iterator();
    Iterator<ClassTypeSignature> interfaceSignatureIterator =
        getClassSignature().getSuperInterfaceSignatures().iterator();

    while (interfaceIterator.hasNext()) {
      assert interfaceSignatureIterator.hasNext();
      DexType superInterface = interfaceIterator.next();
      ClassTypeSignature superInterfaceSignatures = interfaceSignatureIterator.next();
      consumer.accept(superInterface, superInterfaceSignatures);
    }
  }

  public void forEachImmediateSupertypeWithSignature(
      DexItemFactory factory, BiConsumer<DexType, ClassTypeSignature> consumer) {
    if (superType != null) {
      consumer.accept(superType, classSignature.getSuperClassSignatureOrObject(factory));
    }
    forEachImmediateInterfaceWithSignature(consumer);
  }

  public void forEachImmediateInterfaceWithAppliedTypeArguments(
      List<FieldTypeSignature> typeArguments,
      BiConsumer<DexType, List<FieldTypeSignature>> consumer) {
    assert validInterfaceSignatures();

    // If there is no generic signature information don't pass any type arguments.
    if (getClassSignature().getSuperInterfaceSignatures().isEmpty()) {
      forEachImmediateInterface(
          superInterface -> consumer.accept(superInterface, ImmutableList.of()));
      return;
    }

    Iterator<DexType> interfaceIterator = Arrays.asList(interfaces.values).iterator();
    Iterator<ClassTypeSignature> interfaceSignatureIterator =
        getClassSignature().getSuperInterfaceSignatures().iterator();

    while (interfaceIterator.hasNext()) {
      assert interfaceSignatureIterator.hasNext();
      DexType superInterface = interfaceIterator.next();
      ClassTypeSignature superInterfaceSignatures = interfaceSignatureIterator.next();

      // With no type arguments erase the signatures.
      if (typeArguments.isEmpty() && superInterfaceSignatures.hasTypeVariableArguments()) {
        consumer.accept(superInterface, ImmutableList.of());
        continue;
      }

      consumer.accept(superInterface, applyTypeArguments(superInterfaceSignatures, typeArguments));
    }
    assert !interfaceSignatureIterator.hasNext();
  }

  public void forEachImmediateSupertypeWithAppliedTypeArguments(
      List<FieldTypeSignature> typeArguments,
      BiConsumer<DexType, List<FieldTypeSignature>> consumer) {
    if (superType != null) {
      consumer.accept(
          superType,
          applyTypeArguments(getClassSignature().getSuperClassSignatureOrNull(), typeArguments));
    }
    forEachImmediateInterfaceWithAppliedTypeArguments(typeArguments, consumer);
  }

  private List<FieldTypeSignature> applyTypeArguments(
      ClassTypeSignature superInterfaceSignatures, List<FieldTypeSignature> appliedTypeArguments) {
    if (superInterfaceSignatures == null) {
      return Collections.emptyList();
    }
    ImmutableList.Builder<FieldTypeSignature> superTypeArgumentsBuilder = ImmutableList.builder();
    superInterfaceSignatures
        .typeArguments()
        .forEach(
            typeArgument -> {
              if (typeArgument.isTypeVariableSignature()) {
                for (int i = 0; i < getClassSignature().getFormalTypeParameters().size(); i++) {
                  FormalTypeParameter formalTypeParameter =
                      getClassSignature().getFormalTypeParameters().get(i);
                  if (formalTypeParameter
                      .getName()
                      .equals(typeArgument.asTypeVariableSignature().typeVariable())) {
                    if (i >= appliedTypeArguments.size()) {
                      assert false;
                    } else {
                      superTypeArgumentsBuilder.add(appliedTypeArguments.get(i));
                    }
                  }
                }
              } else {
                superTypeArgumentsBuilder.add(typeArgument);
              }
            });
    return superTypeArgumentsBuilder.build();
  }

  @Override
  public Iterable<DexType> allImmediateSupertypes() {
    Iterator<DexType> iterator =
        superType != null
            ? Iterators.concat(
                Iterators.singletonIterator(superType), Iterators.forArray(interfaces.values))
            : Iterators.forArray(interfaces.values);
    return () -> iterator;
  }

  public boolean definesFinalizer(DexItemFactory factory) {
    return lookupVirtualMethod(factory.objectMembers.finalize) != null;
  }

  public boolean defaultValuesForStaticFieldsMayTriggerAllocation() {
    return staticFields().stream()
        .anyMatch(
            field -> field.hasExplicitStaticValue() && field.getStaticValue().mayHaveSideEffects());
  }

  public List<InnerClassAttribute> getInnerClasses() {
    return innerClasses;
  }

  public void setInnerClasses(List<InnerClassAttribute> innerClasses) {
    this.innerClasses = innerClasses;
  }

  public boolean hasEnclosingMethodAttribute() {
    return enclosingMethod != null;
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
    innerClasses.removeIf(predicate);
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

  public void clearPermittedSubclasses() {
    permittedSubclasses.clear();
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

  public void clearNestMembers() {
    nestMembers.clear();
  }

  public void setNestHost(DexType type) {
    assert type != null;
    this.nestHost = new NestHostClassAttribute(type);
  }

  public void setNestHostAttribute(NestHostClassAttribute nestHostAttribute) {
    this.nestHost = nestHostAttribute;
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

  public boolean isInSameNest(DexClass other) {
    return isInANest() && other.isInANest() && getNestHost() == other.getNestHost();
  }

  public void forEachNestMember(Consumer<DexType> consumer) {
    assert isNestHost();
    getNestMembersClassAttributes().forEach(member -> consumer.accept(member.getNestMember()));
  }

  public NestHostClassAttribute getNestHostClassAttribute() {
    return nestHost;
  }

  public boolean hasNestMemberAttributes() {
    return !nestMembers.isEmpty();
  }

  public List<NestMemberClassAttribute> getNestMembersClassAttributes() {
    return nestMembers;
  }

  public void setNestMemberAttributes(List<NestMemberClassAttribute> nestMemberAttributes) {
    this.nestMembers = nestMemberAttributes;
  }

  public void removeNestMemberAttributes(Predicate<NestMemberClassAttribute> predicate) {
    nestMembers.removeIf(predicate);
  }

  public boolean hasPermittedSubclassAttributes() {
    return !permittedSubclasses.isEmpty();
  }

  public List<PermittedSubclassAttribute> getPermittedSubclassAttributes() {
    return permittedSubclasses;
  }

  public List<RecordComponentInfo> getRecordComponents() {
    return recordComponents;
  }

  public void clearRecordComponents() {
    recordComponents.clear();
  }

  public void removeRecordComponents(Predicate<RecordComponentInfo> predicate) {
    if (!recordComponents.isEmpty()) {
      recordComponents.removeIf(predicate);
    }
  }

  /** Returns kotlin class info if the class is synthesized by kotlin compiler. */
  public abstract KotlinClassLevelInfo getKotlinInfo();

  public final String getSimpleName() {
    return getType().getSimpleName();
  }

  public final String getTypeName() {
    return getType().getTypeName();
  }

  public boolean hasStaticFields() {
    return fieldCollection.hasStaticFields();
  }

  public boolean hasInstanceFields() {
    return fieldCollection.hasInstanceFields();
  }

  public List<DexClassAndField> getDirectAndIndirectInstanceFields(AppView<?> appView) {
    List<DexClassAndField> result = new ArrayList<>();
    DexClass current = this;
    while (current != null && current.type != appView.dexItemFactory().objectType) {
      current.forEachClassFieldMatching(DexEncodedField::isInstance, result::add);
      current = appView.definitionFor(current.superType);
    }
    return result;
  }

  public boolean isValid(InternalOptions options) {
    assert verifyNoAbstractMethodsOnNonAbstractClasses(virtualMethods(), options);
    assert !isInterface() || !getMethodCollection().hasVirtualMethods(DexEncodedMethod::isFinal);
    assert fieldCollection.verify();
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


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DexClass dexClass = (DexClass) o;

    if (!Objects.equals(type, dexClass.type)) return false;
    if (!Objects.equals(accessFlags, dexClass.accessFlags)) return false;
    if (!Objects.equals(superType, dexClass.superType)) return false;
    if (!Objects.equals(interfaces, dexClass.interfaces)) return false;
    if (!Objects.equals(sourceFile, dexClass.sourceFile)) return false;
    if (!Objects.equals(fieldCollection, dexClass.fieldCollection))
      return false;
    if (!Objects.equals(methodCollection, dexClass.methodCollection))
      return false;
    if (!Objects.equals(enclosingMethod, dexClass.enclosingMethod))
      return false;
    if (!Objects.equals(innerClasses, dexClass.innerClasses))
      return false;
    if (!Objects.equals(nestHost, dexClass.nestHost)) return false;
    if (!Objects.equals(nestMembers, dexClass.nestMembers)) return false;
    if (!Objects.equals(permittedSubclasses, dexClass.permittedSubclasses))
      return false;
    if (!Objects.equals(recordComponents, dexClass.recordComponents))
      return false;
    return Objects.equals(classSignature, dexClass.classSignature);
  }

  @Override
  public int hashCode() {
    int result = type != null ? type.hashCode() : 0;
    result = 31 * result + (accessFlags != null ? accessFlags.hashCode() : 0);
    result = 31 * result + (superType != null ? superType.hashCode() : 0);
    result = 31 * result + (interfaces != null ? interfaces.hashCode() : 0);
    result = 31 * result + (sourceFile != null ? sourceFile.hashCode() : 0);
    result = 31 * result + (fieldCollection != null ? fieldCollection.hashCode() : 0);
    result = 31 * result + (methodCollection != null ? methodCollection.hashCode() : 0);
    result = 31 * result + (enclosingMethod != null ? enclosingMethod.hashCode() : 0);
    result = 31 * result + (innerClasses != null ? innerClasses.hashCode() : 0);
    result = 31 * result + (nestHost != null ? nestHost.hashCode() : 0);
    result = 31 * result + (nestMembers != null ? nestMembers.hashCode() : 0);
    result = 31 * result + (permittedSubclasses != null ? permittedSubclasses.hashCode() : 0);
    result = 31 * result + (recordComponents != null ? recordComponents.hashCode() : 0);
    result = 31 * result + (classSignature != null ? classSignature.hashCode() : 0);
    return result;
  }
}
