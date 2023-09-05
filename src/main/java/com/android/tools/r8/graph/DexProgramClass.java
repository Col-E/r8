// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.getNoKotlinInfo;
import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.GenericSignature.ClassTypeSignature;
import com.android.tools.r8.graph.MethodCollection.MethodCollectionFactory;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.kotlin.KotlinClassLevelInfo;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.synthesis.SyntheticMarker;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.structural.Ordered;
import com.android.tools.r8.utils.structural.StructuralItem;
import com.android.tools.r8.utils.structural.StructuralMapping;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class DexProgramClass extends DexClass
    implements ProgramClass, Supplier<DexProgramClass>, StructuralItem<DexProgramClass> {

  @FunctionalInterface
  public interface ChecksumSupplier {
    long getChecksum(DexProgramClass programClass);
  }

  public static final DexProgramClass[] EMPTY_ARRAY = {};

  private final ProgramResource.Kind originKind;
  private CfVersion initialClassFileVersion = null;
  private boolean deprecated = false;
  private KotlinClassLevelInfo kotlinInfo = getNoKotlinInfo();
  private OptionalBool reachabilitySensitive = OptionalBool.unknown();

  private final ChecksumSupplier checksumSupplier;

  private SyntheticMarker syntheticMarker;

  public DexProgramClass(
      DexType type,
      Kind originKind,
      Origin origin,
      ClassAccessFlags accessFlags,
      DexType superType,
      DexTypeList interfaces,
      DexString sourceFile,
      NestHostClassAttribute nestHost,
      List<NestMemberClassAttribute> nestMembers,
      List<PermittedSubclassAttribute> permittedSubclasses,
      List<RecordComponentInfo> recordComponents,
      EnclosingMethodAttribute enclosingMember,
      List<InnerClassAttribute> innerClasses,
      ClassSignature classSignature,
      DexAnnotationSet classAnnotations,
      DexEncodedField[] staticFields,
      DexEncodedField[] instanceFields,
      MethodCollectionFactory methodCollectionFactory,
      boolean skipNameValidationForTesting,
      ChecksumSupplier checksumSupplier,
      SyntheticMarker syntheticMarker) {
    super(
        sourceFile,
        interfaces,
        accessFlags,
        superType,
        type,
        staticFields,
        instanceFields,
        methodCollectionFactory,
        nestHost,
        nestMembers,
        permittedSubclasses,
        recordComponents,
        enclosingMember,
        innerClasses,
        classSignature,
        classAnnotations,
        origin,
        skipNameValidationForTesting);
    assert checksumSupplier != null;
    assert classAnnotations != null;
    this.originKind = originKind;
    this.checksumSupplier = checksumSupplier;
    this.syntheticMarker = syntheticMarker;
  }

  public DexProgramClass(
      DexType type,
      Kind originKind,
      Origin origin,
      ClassAccessFlags accessFlags,
      DexType superType,
      DexTypeList interfaces,
      DexString sourceFile,
      NestHostClassAttribute nestHost,
      List<NestMemberClassAttribute> nestMembers,
      List<PermittedSubclassAttribute> permittedSubclasses,
      List<RecordComponentInfo> recordComponents,
      EnclosingMethodAttribute enclosingMember,
      List<InnerClassAttribute> innerClasses,
      ClassSignature classSignature,
      DexAnnotationSet classAnnotations,
      DexEncodedField[] staticFields,
      DexEncodedField[] instanceFields,
      MethodCollectionFactory methodCollectionFactory,
      boolean skipNameValidationForTesting,
      ChecksumSupplier checksumSupplier) {
    this(
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
        enclosingMember,
        innerClasses,
        classSignature,
        classAnnotations,
        staticFields,
        instanceFields,
        methodCollectionFactory,
        skipNameValidationForTesting,
        checksumSupplier,
        null);
  }

  public static DexProgramClass createMockClassForTesting(DexItemFactory dexItemFactory) {
    return new DexProgramClass(
        dexItemFactory.createType("LMock;"),
        null,
        Origin.unknown(),
        ClassAccessFlags.fromSharedAccessFlags(0),
        dexItemFactory.objectType,
        DexTypeList.empty(),
        null,
        null,
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        null,
        Collections.emptyList(),
        ClassSignature.noSignature(),
        DexAnnotationSet.empty(),
        DexEncodedField.EMPTY_ARRAY,
        DexEncodedField.EMPTY_ARRAY,
        MethodCollectionFactory.empty(),
        false,
        DexProgramClass::invalidChecksumRequest);
  }

  @Override
  public void accept(
      Consumer<DexProgramClass> programClassConsumer,
      Consumer<DexClasspathClass> classpathClassConsumer,
      Consumer<DexLibraryClass> libraryClassConsumer) {
    programClassConsumer.accept(this);
  }

  @Override
  public DexProgramClass self() {
    return this;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public DexProgramClass getContext() {
    return this;
  }

  /**
   * Is the class reachability sensitive.
   *
   * <p>A class is reachability sensitive if the
   * dalvik.annotation.optimization.ReachabilitySensitive annotation is on any field or method. When
   * that is the case, dead reference elimination is disabled and locals are kept alive for their
   * entire scope.
   */
  public boolean getOrComputeReachabilitySensitive(AppView<?> appView) {
    if (reachabilitySensitive.isUnknown()) {
      reachabilitySensitive = OptionalBool.of(internalComputeReachabilitySensitive(appView));
    }
    return reachabilitySensitive.isTrue();
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean internalComputeReachabilitySensitive(AppView<?> appView) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    for (DexEncodedMember<?, ?> member : members()) {
      for (DexAnnotation annotation : member.annotations().annotations) {
        if (annotation.annotation.type == dexItemFactory.annotationReachabilitySensitive) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public StructuralMapping<DexProgramClass> getStructuralMapping() {
    return DexProgramClass::specify;
  }

  public SyntheticMarker stripSyntheticInputMarker() {
    SyntheticMarker marker = syntheticMarker;
    // The synthetic input marker is "read once". It is stored only for identifying the input as
    // synthetic and amending it to the SyntheticItems collection. After identification this field
    // should not be used.
    syntheticMarker = null;
    return marker;
  }

  private static void specify(StructuralSpecification<DexProgramClass, ?> spec) {
    spec.withItem(c -> c.type)
        .withItem(c -> c.superType)
        .withItem(c -> c.interfaces)
        .withItem(c -> c.accessFlags)
        .withNullableItem(c -> c.sourceFile)
        .withNullableItem(c -> c.initialClassFileVersion)
        .withBool(c -> c.deprecated)
        .withNullableItem(DexClass::getNestHostClassAttribute)
        .withItemCollection(DexClass::getNestMembersClassAttributes)
        .withItem(DexDefinition::annotations)
        // TODO(b/158159959): Make signatures structural.
        .withAssert(c -> c.classSignature == ClassSignature.noSignature())
        .withItemCollection(DexClass::allFieldsSorted)
        .withItemCollection(DexClass::allMethodsSorted);
  }

  public void forEachProgramField(Consumer<? super ProgramField> consumer) {
    forEachProgramFieldMatching(alwaysTrue(), consumer);
  }

  public void forEachProgramFieldMatching(
      Predicate<? super DexEncodedField> predicate, Consumer<? super ProgramField> consumer) {
    forEachFieldMatching(predicate, field -> consumer.accept(new ProgramField(this, field)));
  }

  public void forEachProgramInstanceField(Consumer<? super ProgramField> consumer) {
    forEachInstanceField(field -> consumer.accept(new ProgramField(this, field)));
  }

  public void forEachProgramStaticField(Consumer<? super ProgramField> consumer) {
    forEachStaticField(field -> consumer.accept(new ProgramField(this, field)));
  }

  public void forEachProgramStaticMethod(Consumer<? super ProgramMethod> consumer) {
    forEachProgramDirectMethodMatching(DexEncodedMethod::isStatic, consumer);
  }

  public void forEachProgramMember(Consumer<? super ProgramMember<?, ?>> consumer) {
    forEachProgramField(consumer);
    forEachProgramMethod(consumer);
  }

  public void forEachProgramMethod(Consumer<? super ProgramMethod> consumer) {
    forEachProgramMethodMatching(alwaysTrue(), consumer);
  }

  public void forEachProgramMethodMatching(
      Predicate<DexEncodedMethod> predicate, Consumer<? super ProgramMethod> consumer) {
    methodCollection.forEachMethodMatching(
        predicate, method -> consumer.accept(new ProgramMethod(this, method)));
  }

  public Iterable<ProgramMethod> programMethods() {
    return Iterables.concat(directProgramMethods(), virtualProgramMethods());
  }

  public Iterable<ProgramMethod> directProgramMethods() {
    return Iterables.transform(directMethods(), method -> new ProgramMethod(this, method));
  }

  public Iterable<ProgramMethod> directProgramMethods(Predicate<DexEncodedMethod> predicate) {
    return Iterables.transform(directMethods(predicate), method -> new ProgramMethod(this, method));
  }

  public Iterable<ProgramMethod> virtualProgramMethods() {
    return Iterables.transform(virtualMethods(), method -> new ProgramMethod(this, method));
  }

  public Iterable<ProgramMethod> virtualProgramMethods(Predicate<DexEncodedMethod> predicate) {
    return Iterables.transform(
        virtualMethods(predicate), method -> new ProgramMethod(this, method));
  }

  public Iterable<ProgramMethod> programInstanceInitializers() {
    return directProgramMethods(DexEncodedMethod::isInstanceInitializer);
  }

  public void forEachProgramDirectMethod(Consumer<ProgramMethod> consumer) {
    forEachProgramDirectMethodMatching(alwaysTrue(), consumer);
  }

  public void forEachProgramDirectMethodMatching(
      Predicate<DexEncodedMethod> predicate, Consumer<? super ProgramMethod> consumer) {
    methodCollection.forEachDirectMethodMatching(
        predicate, method -> consumer.accept(new ProgramMethod(this, method)));
  }

  public void forEachProgramInstanceInitializer(Consumer<ProgramMethod> consumer) {
    forEachProgramInstanceInitializerMatching(alwaysTrue(), consumer);
  }

  public void forEachProgramInstanceInitializerMatching(
      Predicate<DexEncodedMethod> predicate, Consumer<ProgramMethod> consumer) {
    forEachProgramDirectMethodMatching(
        method -> method.isInstanceInitializer() && predicate.test(method), consumer);
  }

  public void forEachProgramVirtualMethod(Consumer<ProgramMethod> consumer) {
    forEachProgramVirtualMethodMatching(alwaysTrue(), consumer);
  }

  public void forEachProgramVirtualMethodMatching(
      Predicate<DexEncodedMethod> predicate, Consumer<ProgramMethod> consumer) {
    methodCollection.forEachVirtualMethodMatching(
        predicate, method -> consumer.accept(new ProgramMethod(this, method)));
  }

  public ProgramMethod getProgramClassInitializer() {
    return toProgramMethodOrNull(getClassInitializer());
  }

  public void acceptProgramClassInitializer(Consumer<ProgramMethod> consumer) {
    if (hasClassInitializer()) {
      consumer.accept(getProgramClassInitializer());
    }
  }

  public ProgramMethod getProgramDefaultInitializer() {
    return getProgramInitializer(DexType.EMPTY_ARRAY);
  }

  public ProgramMethod getProgramInitializer(DexType[] types) {
    return toProgramMethodOrNull(getInitializer(types));
  }

  /** Find member in this class matching {@param member}. */
  @SuppressWarnings("unchecked")
  public <D extends DexEncodedMember<D, R>, R extends DexMember<D, R>>
      ProgramMember<D, R> lookupProgramMember(DexMember<D, R> member) {
    ProgramMember<?, ?> definition =
        member.isDexField()
            ? lookupProgramField(member.asDexField())
            : lookupProgramMethod(member.asDexMethod());
    return (ProgramMember<D, R>) definition;
  }

  public ProgramField lookupProgramField(DexField reference) {
    return toProgramFieldOrNull(lookupField(reference));
  }

  public ProgramMethod lookupProgramMethod(DexMethod reference) {
    return toProgramMethodOrNull(getMethodCollection().getMethod(reference));
  }

  private ProgramField toProgramFieldOrNull(DexEncodedField field) {
    if (field != null) {
      return new ProgramField(this, field);
    }
    return null;
  }

  private ProgramMethod toProgramMethodOrNull(DexEncodedMethod method) {
    if (method != null) {
      return new ProgramMethod(this, method);
    }
    return null;
  }

  public TraversalContinuation<?, ?> traverseProgramMembers(
      Function<ProgramMember<?, ?>, TraversalContinuation<?, ?>> fn) {
    TraversalContinuation<?, ?> continuation = traverseProgramFields(fn);
    if (continuation.shouldContinue()) {
      return traverseProgramMethods(fn);
    }
    return TraversalContinuation.doBreak();
  }

  public TraversalContinuation<?, ?> traverseProgramFields(
      Function<? super ProgramField, TraversalContinuation<?, ?>> fn) {
    return getFieldCollection().traverse(field -> fn.apply(field.asProgramField()));
  }

  public <BT, CT> TraversalContinuation<BT, CT> traverseProgramFields(
      BiFunction<? super ProgramField, CT, TraversalContinuation<BT, CT>> fn, CT initialValue) {
    return getFieldCollection()
        .traverse((field, value) -> fn.apply(field.asProgramField(), value), initialValue);
  }

  public TraversalContinuation<?, ?> traverseProgramInstanceFields(
      Function<? super ProgramField, TraversalContinuation<?, ?>> fn) {
    return getFieldCollection().traverseInstanceFields(field -> fn.apply(field.asProgramField()));
  }

  public <BT, CT> TraversalContinuation<BT, CT> traverseProgramInstanceFields(
      BiFunction<? super ProgramField, CT, TraversalContinuation<BT, CT>> fn, CT initialValue) {
    return getFieldCollection()
        .traverseInstanceFields(
            (field, value) -> fn.apply(field.asProgramField(), value), initialValue);
  }

  public TraversalContinuation<?, ?> traverseProgramMethods(
      Function<? super ProgramMethod, TraversalContinuation<?, ?>> fn) {
    return getMethodCollection().traverse(method -> fn.apply(new ProgramMethod(this, method)));
  }

  public TraversalContinuation<?, ?> traverseProgramInstanceInitializers(
      Function<ProgramMethod, TraversalContinuation<?, ?>> fn) {
    return traverseProgramMethods(fn, DexEncodedMethod::isInstanceInitializer);
  }

  public TraversalContinuation<?, ?> traverseProgramMethods(
      Function<ProgramMethod, TraversalContinuation<?, ?>> fn,
      Predicate<DexEncodedMethod> predicate) {
    return getMethodCollection()
        .traverse(
            method ->
                predicate.test(method)
                    ? fn.apply(new ProgramMethod(this, method))
                    : TraversalContinuation.doContinue());
  }

  public Kind getOriginKind() {
    return originKind;
  }

  public boolean originatesFromDexResource() {
    return originKind == Kind.DEX;
  }

  public boolean originatesFromClassResource() {
    return originKind == Kind.CF;
  }

  public void collectIndexedItems(
      AppView<?> appView, IndexedItemCollection indexedItems, LensCodeRewriterUtils rewriter) {
    if (indexedItems.addClass(this)) {
      type.collectIndexedItems(appView, indexedItems);
      if (superType != null) {
        superType.collectIndexedItems(appView, indexedItems);
      } else {
        assert type.toDescriptorString().equals("Ljava/lang/Object;");
      }
      if (sourceFile != null) {
        sourceFile.collectIndexedItems(indexedItems);
      }
      annotations().collectIndexedItems(appView, indexedItems);
      if (interfaces != null) {
        interfaces.collectIndexedItems(appView, indexedItems);
      }
      if (getEnclosingMethodAttribute() != null) {
        getEnclosingMethodAttribute().collectIndexedItems(appView, indexedItems);
      }
      for (InnerClassAttribute attribute : getInnerClasses()) {
        attribute.collectIndexedItems(appView, indexedItems);
      }
      // We are explicitly not adding items referenced in signatures.
      forEachProgramField(field -> field.collectIndexedItems(appView, indexedItems));
      forEachProgramMethod(method -> method.collectIndexedItems(appView, indexedItems, rewriter));
    }
  }

  @Override
  protected void collectMixedSectionItems(MixedSectionCollection mixedItems) {
    assert getEnclosingMethodAttribute() == null;
    assert getInnerClasses().isEmpty();
    assert !classSignature.hasSignature();
    if (hasClassOrMemberAnnotations()) {
      mixedItems.setAnnotationsDirectoryForClass(this, new DexAnnotationDirectory(this));
    }
  }

  @Override
  public void addDependencies(MixedSectionCollection collector) {
    assert getEnclosingMethodAttribute() == null;
    assert getInnerClasses().isEmpty();
    assert !classSignature.hasSignature();
    // We only have a class data item if there are methods or fields.
    if (hasMethodsOrFields()) {
      collector.add(this);
      methodCollection.forEachMethod(m -> m.collectMixedSectionItems(collector));
      fieldCollection.forEachField(f -> f.getDefinition().collectMixedSectionItems(collector));
    }
    annotations().collectMixedSectionItems(collector);
    if (interfaces != null) {
      interfaces.collectMixedSectionItems(collector);
    }
  }

  @Override
  public String toString() {
    return type.toString();
  }

  @Override
  public String toSourceString() {
    return type.toSourceString();
  }

  /**
   * Returns true if this class is final, or it is a non-pinned program class with no instantiated
   * subtypes.
   */
  @Override
  public boolean isEffectivelyFinal(AppView<?> appView) {
    if (isFinal()) {
      return true;
    }
    if (appView.hasLiveness()) {
      assert appView.enableWholeProgramOptimizations();
      InternalOptions options = appView.options();
      return !appView.getKeepInfo(this).isPinned(options)
          && !appView.appInfoWithLiveness().isInstantiatedIndirectly(this);
    }
    return false;
  }

  @Override
  public boolean isProgramClass() {
    return true;
  }

  @Override
  public DexProgramClass asClass() {
    return this;
  }

  @Override
  public DexProgramClass asProgramClass() {
    return this;
  }

  public static DexProgramClass asProgramClassOrNull(DexClass clazz) {
    return clazz != null ? clazz.asProgramClass() : null;
  }

  @Override
  public boolean isNotProgramClass() {
    return false;
  }

  @Override
  public KotlinClassLevelInfo getKotlinInfo() {
    return kotlinInfo;
  }

  public void setKotlinInfo(KotlinClassLevelInfo kotlinInfo) {
    assert kotlinInfo != null;
    assert this.kotlinInfo == getNoKotlinInfo();
    this.kotlinInfo = kotlinInfo;
  }

  public void clearKotlinInfo() {
    this.kotlinInfo = getNoKotlinInfo();
  }

  @Override
  boolean internalClassOrInterfaceMayHaveInitializationSideEffects(
      AppView<?> appView,
      DexClass initialAccessHolder,
      Predicate<DexType> ignore,
      Set<DexType> seen) {
    if (!seen.add(getType()) || ignore.test(getType())) {
      return false;
    }
    return isInterface()
        ? internalInterfaceMayHaveInitializationSideEffects(
            appView, initialAccessHolder, ignore, seen)
        : internalClassMayHaveInitializationSideEffects(appView, initialAccessHolder, ignore, seen);
  }

  private boolean internalClassMayHaveInitializationSideEffects(
      AppView<?> appView,
      DexClass initialAccessHolder,
      Predicate<DexType> ignore,
      Set<DexType> seen) {
    assert !isInterface();
    assert seen.contains(getType());
    assert !ignore.test(getType());
    if (hasClassInitializer()
        && !getClassInitializer().getOptimizationInfo().classInitializerMayBePostponed()) {
      return true;
    }
    return defaultValuesForStaticFieldsMayTriggerAllocation()
        || initializationOfParentTypesMayHaveSideEffects(
            appView, initialAccessHolder, ignore, seen);
  }

  /**
   * Interface initialization is described the JVM Specification, section 5.5 Initialization (Java
   * SE 11 Edition).
   *
   * <p>A class or interface C may be initialized only as a result of:
   *
   * <ul>
   *   <li>The execution of any one of the Java Virtual Machine instructions new, getstatic,
   *       putstatic, or invokestatic that references C.
   *   <li>...
   *   <li>If C is an interface that declares a non-abstract, non-static method, the initialization
   *       of a class that implements C directly or indirectly.
   * </ul>
   */
  private boolean internalInterfaceMayHaveInitializationSideEffects(
      AppView<?> appView,
      DexClass initialAccessHolder,
      Predicate<DexType> ignore,
      Set<DexType> seen) {
    assert isInterface();
    assert seen.contains(getType());
    assert !ignore.test(getType());

    // If there is a direct access to the interface, then this has side effects if its clinit has
    // side effects. Parent types are not initialized and thus don't need to be considered.
    if (this == initialAccessHolder) {
      if (hasClassInitializer()
          && !getClassInitializer().getOptimizationInfo().classInitializerMayBePostponed()) {
        return true;
      }
      return defaultValuesForStaticFieldsMayTriggerAllocation();
    }

    // Otherwise, this interface has side effects if its clinit has side effects and it has at least
    // one default interface method, or if one of its parent types have observable side effects.
    if (hasClassInitializer()
        && !getClassInitializer().getOptimizationInfo().classInitializerMayBePostponed()
        && getMethodCollection().hasVirtualMethods(DexEncodedMethod::isDefaultMethod)) {
      return true;
    }

    return initializationOfParentTypesMayHaveSideEffects(
        appView, initialAccessHolder, ignore, seen);
  }

  private boolean initializationOfParentTypesMayHaveSideEffects(
      AppView<?> appView,
      DexClass initialAccessHolder,
      Predicate<DexType> ignore,
      Set<DexType> seen) {
    if (superType != null
        && superType.internalClassOrInterfaceMayHaveInitializationSideEffects(
            appView, initialAccessHolder, ignore, seen)) {
      return true;
    }
    for (DexType iface : interfaces) {
      if (iface.internalClassOrInterfaceMayHaveInitializationSideEffects(
          appView, initialAccessHolder, ignore, seen)) {
        return true;
      }
    }
    return false;
  }

  public boolean hasFields() {
    return fieldCollection.size() > 0;
  }

  public boolean hasMethods() {
    return methodCollection.size() > 0;
  }

  public boolean hasMethodsOrFields() {
    return hasMethods() || hasFields();
  }

  /** Determine if the class or any of its methods/fields has any attributes. */
  public boolean hasClassOrMemberAnnotations() {
    return !annotations().isEmpty()
        || hasAnnotations(methodCollection)
        || hasAnnotations(fieldCollection);
  }

  boolean hasOnlyInternalizableAnnotations() {
    return !hasAnnotations(methodCollection) && !hasAnnotations(fieldCollection);
  }

  private boolean hasAnnotations(FieldCollection fields) {
    synchronized (fields) {
      return fields.hasAnnotations();
    }
  }

  private boolean hasAnnotations(MethodCollection methods) {
    synchronized (methods) {
      return methods.hasAnnotations();
    }
  }

  public DexEncodedArray computeStaticValuesArray(NamingLens namingLens) {
    // Fast path to avoid sorting and collection allocation when no non-default values exist.
    if (!hasNonDefaultStaticFieldValues()) {
      return null;
    }
    List<DexEncodedField> fields = new ArrayList<>(staticFields());
    fields.sort((a, b) -> a.getReference().compareToWithNamingLens(b.getReference(), namingLens));
    int length = 0;
    List<DexValue> values = new ArrayList<>(fields.size());
    for (int i = 0; i < fields.size(); i++) {
      DexEncodedField field = fields.get(i);
      DexValue staticValue = field.getStaticValue();
      assert staticValue != null;
      values.add(staticValue);
      if (!staticValue.isDefault(field.getReference().type)) {
        length = i + 1;
      }
    }
    return length > 0
        ? new DexEncodedArray(values.subList(0, length).toArray(DexValue.EMPTY_ARRAY))
        : null;
  }

  private boolean hasNonDefaultStaticFieldValues() {
    for (DexEncodedField field : staticFields()) {
      DexValue value = field.getStaticValue();
      if (value != null && !value.isDefault(field.getReference().type)) {
        return true;
      }
    }
    return false;
  }

  public void addMethod(DexEncodedMethod method) {
    methodCollection.addMethod(method);
  }

  public void replaceVirtualMethod(
      DexMethod virtualMethod, Function<DexEncodedMethod, DexEncodedMethod> replacement) {
    methodCollection.replaceVirtualMethod(virtualMethod, replacement);
  }

  public void addExtraInterfaces(List<ClassTypeSignature> extraInterfaces, DexItemFactory factory) {
    if (extraInterfaces.isEmpty()) {
      return;
    }
    addExtraInterfacesToInterfacesArray(extraInterfaces);
    addExtraInterfacesToSignatureIfPresent(extraInterfaces, factory);
  }

  private void addExtraInterfacesToInterfacesArray(List<ClassTypeSignature> extraInterfaces) {
    DexType[] newInterfaces =
        Arrays.copyOf(interfaces.values, interfaces.size() + extraInterfaces.size());
    for (int i = interfaces.size(); i < newInterfaces.length; i++) {
      newInterfaces[i] = extraInterfaces.get(i - interfaces.size()).type();
    }
    interfaces = new DexTypeList(newInterfaces);
  }

  private void addExtraInterfacesToSignatureIfPresent(
      List<ClassTypeSignature> extraInterfaces, DexItemFactory factory) {
    assert !extraInterfaces.isEmpty();
    // We introduce the extra interfaces to the generic signature.
    if (classSignature.hasNoSignature()) {
      return;
    }
    classSignature =
        ClassSignature.builder()
            .addSuperInterfaceSignatures(classSignature.getSuperInterfaceSignatures())
            .addSuperInterfaceSignatures(extraInterfaces)
            .setSuperClassSignature(classSignature.getSuperClassSignatureOrNull())
            .addFormalTypeParameters(classSignature.getFormalTypeParameters())
            .build(factory);
  }

  @Override
  public DexProgramClass get() {
    return this;
  }

  @Override
  public DexProgramClass getContextClass() {
    return this;
  }

  @Override
  public DexType getContextType() {
    return getType();
  }

  @Override
  public DexProgramClass getDefinition() {
    return this;
  }

  public void setInitialClassFileVersion(CfVersion initialClassFileVersion) {
    assert this.initialClassFileVersion == null;
    assert initialClassFileVersion != null;
    this.initialClassFileVersion = initialClassFileVersion;
  }

  public void downgradeInitialClassFileVersion(CfVersion version) {
    assert version != null;
    this.initialClassFileVersion = Ordered.minIgnoreNull(this.initialClassFileVersion, version);
  }

  public boolean hasClassFileVersion() {
    return initialClassFileVersion != null;
  }

  public CfVersion getInitialClassFileVersion() {
    return initialClassFileVersion;
  }

  public void setDeprecated() {
    deprecated = true;
  }

  public boolean isDeprecated() {
    return deprecated;
  }

  public static Iterable<DexProgramClass> asProgramClasses(
      Iterable<DexType> types, DexDefinitionSupplier definitions) {
    return () ->
        new Iterator<DexProgramClass>() {

          private final Iterator<DexType> iterator = types.iterator();

          private DexProgramClass next = findNext();

          @Override
          public boolean hasNext() {
            return next != null;
          }

          @Override
          public DexProgramClass next() {
            DexProgramClass current = next;
            next = findNext();
            return current;
          }

          private DexProgramClass findNext() {
            while (iterator.hasNext()) {
              DexType next = iterator.next();
              DexClass clazz = definitions.contextIndependentDefinitionFor(next);
              if (clazz != null && clazz.isProgramClass()) {
                return clazz.asProgramClass();
              }
            }
            return null;
          }
        };
  }

  public static long invalidChecksumRequest(DexProgramClass clazz) {
    throw new CompilationError(
        clazz + " has no checksum information while checksum encoding is requested", clazz.origin);
  }

  public static long checksumFromType(DexProgramClass clazz) {
    return clazz.type.hashCode();
  }

  public long getChecksum() {
    return checksumSupplier.getChecksum(this);
  }

  public ChecksumSupplier getChecksumSupplier() {
    return checksumSupplier;
  }

}
