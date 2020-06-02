// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.NO_KOTLIN_INFO;
import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.kotlin.KotlinClassLevelInfo;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.TraversalContinuation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class DexProgramClass extends DexClass implements Supplier<DexProgramClass> {

  @FunctionalInterface
  public interface ChecksumSupplier {
    long getChecksum(DexProgramClass programClass);
  }

  public static final DexProgramClass[] EMPTY_ARRAY = {};

  private static final DexEncodedArray SENTINEL_NOT_YET_COMPUTED =
      new DexEncodedArray(DexValue.EMPTY_ARRAY);

  private final ProgramResource.Kind originKind;
  private final Collection<DexProgramClass> synthesizedFrom;
  private int initialClassFileVersion = -1;
  private KotlinClassLevelInfo kotlinInfo = NO_KOTLIN_INFO;

  private final ChecksumSupplier checksumSupplier;

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
      EnclosingMethodAttribute enclosingMember,
      List<InnerClassAttribute> innerClasses,
      DexAnnotationSet classAnnotations,
      DexEncodedField[] staticFields,
      DexEncodedField[] instanceFields,
      DexEncodedMethod[] directMethods,
      DexEncodedMethod[] virtualMethods,
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
        enclosingMember,
        innerClasses,
        classAnnotations,
        staticFields,
        instanceFields,
        directMethods,
        virtualMethods,
        skipNameValidationForTesting,
        checksumSupplier,
        Collections.emptyList());
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
      EnclosingMethodAttribute enclosingMember,
      List<InnerClassAttribute> innerClasses,
      DexAnnotationSet classAnnotations,
      DexEncodedField[] staticFields,
      DexEncodedField[] instanceFields,
      DexEncodedMethod[] directMethods,
      DexEncodedMethod[] virtualMethods,
      boolean skipNameValidationForTesting,
      ChecksumSupplier checksumSupplier,
      Collection<DexProgramClass> synthesizedDirectlyFrom) {
    super(
        sourceFile,
        interfaces,
        accessFlags,
        superType,
        type,
        staticFields,
        instanceFields,
        directMethods,
        virtualMethods,
        nestHost,
        nestMembers,
        enclosingMember,
        innerClasses,
        classAnnotations,
        origin,
        skipNameValidationForTesting);
    assert checksumSupplier != null;
    assert classAnnotations != null;
    this.originKind = originKind;
    this.checksumSupplier = checksumSupplier;
    this.synthesizedFrom = new HashSet<>();
    synthesizedDirectlyFrom.forEach(this::addSynthesizedFrom);
  }

  public void forEachProgramField(Consumer<ProgramField> consumer) {
    forEachField(field -> consumer.accept(new ProgramField(this, field)));
  }

  public void forEachProgramMethod(Consumer<ProgramMethod> consumer) {
    forEachProgramMethodMatching(alwaysTrue(), consumer);
  }

  public void forEachProgramMethodMatching(
      Predicate<DexEncodedMethod> predicate, Consumer<ProgramMethod> consumer) {
    methodCollection.forEachMethodMatching(
        predicate, method -> consumer.accept(new ProgramMethod(this, method)));
  }

  public void forEachProgramDirectMethod(Consumer<ProgramMethod> consumer) {
    forEachProgramDirectMethodMatching(alwaysTrue(), consumer);
  }

  public void forEachProgramDirectMethodMatching(
      Predicate<DexEncodedMethod> predicate, Consumer<ProgramMethod> consumer) {
    methodCollection.forEachDirectMethodMatching(
        predicate, method -> consumer.accept(new ProgramMethod(this, method)));
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

  public ProgramMethod getProgramDefaultInitializer() {
    return getProgramInitializer(DexType.EMPTY_ARRAY);
  }

  public ProgramMethod getProgramInitializer(DexType[] types) {
    return toProgramMethodOrNull(getInitializer(types));
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

  public TraversalContinuation traverseProgramMethods(
      Function<ProgramMethod, TraversalContinuation> fn) {
    return getMethodCollection().traverse(method -> fn.apply(new ProgramMethod(this, method)));
  }

  public TraversalContinuation traverseProgramInstanceInitializers(
      Function<ProgramMethod, TraversalContinuation> fn) {
    return traverseProgramMethods(fn, DexEncodedMethod::isInstanceInitializer);
  }

  public TraversalContinuation traverseProgramMethods(
      Function<ProgramMethod, TraversalContinuation> fn, Predicate<DexEncodedMethod> predicate) {
    return getMethodCollection()
        .traverse(
            method ->
                predicate.test(method)
                    ? fn.apply(new ProgramMethod(this, method))
                    : TraversalContinuation.CONTINUE);
  }

  public boolean originatesFromDexResource() {
    return originKind == Kind.DEX;
  }

  public boolean originatesFromClassResource() {
    return originKind == Kind.CF;
  }

  @Override
  public void collectIndexedItems(IndexedItemCollection indexedItems,
      DexMethod method, int instructionOffset) {
    if (indexedItems.addClass(this)) {
      type.collectIndexedItems(indexedItems, method, instructionOffset);
      if (superType != null) {
        superType.collectIndexedItems(indexedItems, method, instructionOffset);
      } else {
        assert type.toDescriptorString().equals("Ljava/lang/Object;");
      }
      if (sourceFile != null) {
        sourceFile.collectIndexedItems(indexedItems, method, instructionOffset);
      }
      annotations().collectIndexedItems(indexedItems, method, instructionOffset);
      if (interfaces != null) {
        interfaces.collectIndexedItems(indexedItems, method, instructionOffset);
      }
      if (getEnclosingMethodAttribute() != null) {
        getEnclosingMethodAttribute().collectIndexedItems(indexedItems);
      }
      for (InnerClassAttribute attribute : getInnerClasses()) {
        attribute.collectIndexedItems(indexedItems);
      }
      synchronizedCollectAll(indexedItems, staticFields);
      synchronizedCollectAll(indexedItems, instanceFields);
      synchronized (methodCollection) {
        methodCollection.forEachMethod(m -> m.collectIndexedItems(indexedItems));
      }
    }
  }

  private static <T extends DexItem> void synchronizedCollectAll(IndexedItemCollection collection,
      T[] items) {
    synchronized (items) {
      collectAll(collection, items);
    }
  }

  public Collection<DexProgramClass> getSynthesizedFrom() {
    return synthesizedFrom;
  }

  @Override
  void collectMixedSectionItems(MixedSectionCollection mixedItems) {
    assert getEnclosingMethodAttribute() == null;
    assert getInnerClasses().isEmpty();
    if (hasAnnotations()) {
      mixedItems.setAnnotationsDirectoryForClass(this, new DexAnnotationDirectory(this));
    }
  }

  @Override
  public void addDependencies(MixedSectionCollection collector) {
    assert getEnclosingMethodAttribute() == null;
    assert getInnerClasses().isEmpty();
    // We only have a class data item if there are methods or fields.
    if (hasMethodsOrFields()) {
      collector.add(this);
      methodCollection.forEachMethod(m -> m.collectMixedSectionItems(collector));
      collectAll(collector, staticFields);
      collectAll(collector, instanceFields);
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
    if (appView.enableWholeProgramOptimizations()) {
      assert appView.appInfo().hasLiveness();
      AppInfoWithLiveness appInfo = appView.appInfo().withLiveness();
      if (appInfo.isPinned(type)) {
        return false;
      }
      return !appInfo.isInstantiatedIndirectly(this);
    }
    return false;
  }

  @Override
  public boolean isProgramClass() {
    return true;
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
    assert this.kotlinInfo == NO_KOTLIN_INFO || kotlinInfo == NO_KOTLIN_INFO;
    this.kotlinInfo = kotlinInfo;
  }

  public boolean hasFields() {
    return instanceFields.length + staticFields.length > 0;
  }

  public boolean hasMethods() {
    return methodCollection.size() > 0;
  }

  public boolean hasMethodsOrFields() {
    return hasMethods() || hasFields();
  }

  public boolean hasAnnotations() {
    return !annotations().isEmpty()
        || hasAnnotations(methodCollection)
        || hasAnnotations(staticFields)
        || hasAnnotations(instanceFields);
  }

  boolean hasOnlyInternalizableAnnotations() {
    return !hasAnnotations(methodCollection)
        && !hasAnnotations(staticFields)
        && !hasAnnotations(instanceFields);
  }

  private boolean hasAnnotations(DexEncodedField[] fields) {
    synchronized (fields) {
      return Arrays.stream(fields).anyMatch(DexEncodedField::hasAnnotation);
    }
  }

  private boolean hasAnnotations(MethodCollection methods) {
    synchronized (methods) {
      return methods.hasAnnotations();
    }
  }

  public void addSynthesizedFrom(DexProgramClass clazz) {
    if (clazz.synthesizedFrom.isEmpty()) {
      synthesizedFrom.add(clazz);
    } else {
      clazz.synthesizedFrom.forEach(this::addSynthesizedFrom);
    }
  }

  public DexEncodedArray computeStaticValuesArray(NamingLens namingLens) {
    // Fast path to avoid sorting and collection allocation when no non-default values exist.
    if (!hasNonDefaultStaticFieldValues()) {
      return null;
    }
    DexEncodedField[] fields = staticFields;
    Arrays.sort(fields, (a, b) -> a.field.slowCompareTo(b.field, namingLens));
    int length = 0;
    List<DexValue> values = new ArrayList<>(fields.length);
    for (int i = 0; i < fields.length; i++) {
      DexEncodedField field = fields[i];
      DexValue staticValue = field.getStaticValue();
      assert staticValue != null;
      values.add(staticValue);
      if (!staticValue.isDefault(field.field.type)) {
        length = i + 1;
      }
    }
    return length > 0
        ? new DexEncodedArray(values.subList(0, length).toArray(DexValue.EMPTY_ARRAY))
        : null;
  }

  private boolean hasNonDefaultStaticFieldValues() {
    for (DexEncodedField field : staticFields) {
      DexValue value = field.getStaticValue();
      if (value != null && !value.isDefault(field.field.type)) {
        return true;
      }
    }
    return false;
  }

  public void addMethod(DexEncodedMethod method) {
    methodCollection.addMethod(method);
  }

  public void addVirtualMethod(DexEncodedMethod virtualMethod) {
    methodCollection.addVirtualMethod(virtualMethod);
  }

  public void addDirectMethod(DexEncodedMethod directMethod) {
    methodCollection.addDirectMethod(directMethod);
  }

  @Override
  public DexProgramClass get() {
    return this;
  }

  public void setInitialClassFileVersion(int initialClassFileVersion) {
    assert this.initialClassFileVersion == -1 && initialClassFileVersion > 0;
    this.initialClassFileVersion = initialClassFileVersion;
  }

  public boolean hasClassFileVersion() {
    return initialClassFileVersion > -1;
  }

  public int getInitialClassFileVersion() {
    assert initialClassFileVersion > -1;
    return initialClassFileVersion;
  }

  /**
   * Is the class reachability sensitive.
   *
   * <p>A class is reachability sensitive if the
   * dalvik.annotation.optimization.ReachabilitySensitive annotation is on any field or method. When
   * that is the case, dead reference elimination is disabled and locals are kept alive for their
   * entire scope.
   */
  public boolean hasReachabilitySensitiveAnnotation(DexItemFactory factory) {
    for (DexEncodedMember<?, ?> member : members()) {
      for (DexAnnotation annotation : member.annotations().annotations) {
        if (annotation.annotation.type == factory.annotationReachabilitySensitive) {
          return true;
        }
      }
    }
    return false;
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
              DexClass clazz = definitions.definitionFor(next);
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
}
