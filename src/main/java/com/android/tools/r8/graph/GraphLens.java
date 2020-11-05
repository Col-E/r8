// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.horizontalclassmerging.ClassMerger;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.desugar.InterfaceProcessor.InterfaceProcessorNestedGraphLens;
import com.android.tools.r8.shaking.KeepInfoCollection;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Object2BooleanArrayMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * A GraphLens implements a virtual view on top of the graph, used to delay global rewrites until
 * later IR processing stages.
 *
 * <p>Valid remappings are limited to the following operations:
 *
 * <ul>
 *   <li>Mapping a classes type to one of the super/subtypes.
 *   <li>Renaming private methods/fields.
 *   <li>Moving methods/fields to a super/subclass.
 *   <li>Replacing method/field references by the same method/field on a super/subtype
 *   <li>Moved methods might require changed invocation type at the call site
 * </ul>
 *
 * Note that the latter two have to take visibility into account.
 */
public abstract class GraphLens {

  abstract static class MemberLookupResult<R extends DexMember<?, R>> {

    private final R reference;
    private final R reboundReference;

    private MemberLookupResult(R reference, R reboundReference) {
      this.reference = reference;
      this.reboundReference = reboundReference;
    }

    public R getReference() {
      return reference;
    }

    public R getRewrittenReference(Map<R, R> rewritings) {
      return rewritings.getOrDefault(reference, reference);
    }

    public boolean hasReboundReference() {
      return reboundReference != null;
    }

    public R getReboundReference() {
      return reboundReference;
    }

    public R getRewrittenReboundReference(Map<R, R> rewritings) {
      return rewritings.getOrDefault(reboundReference, reboundReference);
    }

    abstract static class Builder<R extends DexMember<?, R>, Self extends Builder<R, Self>> {

      R reference;
      R reboundReference;

      public Self setReference(R reference) {
        this.reference = reference;
        return self();
      }

      public Self setReboundReference(R reboundReference) {
        this.reboundReference = reboundReference;
        return self();
      }

      public abstract Self self();
    }
  }

  /**
   * Intermediate result of a field lookup that stores the actual non-rebound reference and the
   * rebound reference that points to the definition of the field.
   */
  public static class FieldLookupResult extends MemberLookupResult<DexField> {

    private FieldLookupResult(DexField reference, DexField reboundReference) {
      super(reference, reboundReference);
    }

    public static Builder builder(GraphLens lens) {
      return new Builder(lens);
    }

    public static class Builder extends MemberLookupResult.Builder<DexField, Builder> {

      private GraphLens lens;

      private Builder(GraphLens lens) {
        this.lens = lens;
      }

      @Override
      public Builder self() {
        return this;
      }

      public FieldLookupResult build() {
        // TODO(b/168282032): All non-identity graph lenses should set the rebound reference.
        return new FieldLookupResult(reference, reboundReference);
      }
    }
  }

  /**
   * Result of a method lookup in a GraphLens.
   *
   * <p>This provides the new target and invoke type to use, along with a description of the
   * prototype changes that have been made to the target method and the corresponding required
   * changes to the invoke arguments.
   */
  public static class MethodLookupResult extends MemberLookupResult<DexMethod> {

    private final Type type;
    private final RewrittenPrototypeDescription prototypeChanges;

    public MethodLookupResult(
        DexMethod reference,
        DexMethod reboundReference,
        Type type,
        RewrittenPrototypeDescription prototypeChanges) {
      super(reference, reboundReference);
      this.type = type;
      this.prototypeChanges = prototypeChanges;
    }

    public static Builder builder(GraphLens lens) {
      return new Builder(lens);
    }

    public Type getType() {
      return type;
    }

    public RewrittenPrototypeDescription getPrototypeChanges() {
      return prototypeChanges;
    }

    public static class Builder extends MemberLookupResult.Builder<DexMethod, Builder> {

      private final GraphLens lens;
      private RewrittenPrototypeDescription prototypeChanges = RewrittenPrototypeDescription.none();
      private Type type;

      private Builder(GraphLens lens) {
        this.lens = lens;
      }

      public Builder setPrototypeChanges(RewrittenPrototypeDescription prototypeChanges) {
        this.prototypeChanges = prototypeChanges;
        return this;
      }

      public Builder setType(Type type) {
        this.type = type;
        return this;
      }

      public MethodLookupResult build() {
        assert reference != null;
        // TODO(b/168282032): All non-identity graph lenses should set the rebound reference.
        return new MethodLookupResult(reference, reboundReference, type, prototypeChanges);
      }

      @Override
      public Builder self() {
        return this;
      }
    }
  }

  public static class Builder {

    protected Builder() {}

    protected final Map<DexType, DexType> typeMap = new IdentityHashMap<>();
    protected final Map<DexMethod, DexMethod> methodMap = new IdentityHashMap<>();
    protected final Map<DexField, DexField> fieldMap = new IdentityHashMap<>();

    protected final BiMap<DexField, DexField> originalFieldSignatures = HashBiMap.create();
    protected final BiMap<DexMethod, DexMethod> originalMethodSignatures = HashBiMap.create();

    public void map(DexType from, DexType to) {
      if (from == to) {
        return;
      }
      typeMap.put(from, to);
    }

    public void map(DexMethod from, DexMethod to) {
      if (from == to) {
        return;
      }
      methodMap.put(from, to);
    }

    public void map(DexField from, DexField to) {
      if (from == to) {
        return;
      }
      fieldMap.put(from, to);
    }

    public void move(DexMethod from, DexMethod to) {
      if (from == to) {
        return;
      }
      map(from, to);
      originalMethodSignatures.put(to, from);
    }

    public void move(DexField from, DexField to) {
      if (from == to) {
        return;
      }
      fieldMap.put(from, to);
      originalFieldSignatures.put(to, from);
    }

    public GraphLens build(DexItemFactory dexItemFactory) {
      return build(dexItemFactory, getIdentityLens());
    }

    public GraphLens build(DexItemFactory dexItemFactory, GraphLens previousLens) {
      if (typeMap.isEmpty() && methodMap.isEmpty() && fieldMap.isEmpty()) {
        return previousLens;
      }
      return new NestedGraphLens(
          typeMap,
          methodMap,
          fieldMap,
          originalFieldSignatures,
          originalMethodSignatures,
          previousLens,
          dexItemFactory);
    }
  }

  /**
   * Intentionally private. All graph lenses except for {@link IdentityGraphLens} should inherit
   * from {@link NonIdentityGraphLens}.
   */
  private GraphLens() {}

  public abstract DexType getOriginalType(DexType type);

  public abstract Iterable<DexType> getOriginalTypes(DexType type);

  public abstract DexField getOriginalFieldSignature(DexField field);

  public abstract DexMethod getOriginalMethodSignature(DexMethod method);

  public abstract DexField getRenamedFieldSignature(DexField originalField);

  public final DexMember<?, ?> getRenamedMemberSignature(DexMember<?, ?> originalMember) {
    return originalMember.isDexField()
        ? getRenamedFieldSignature(originalMember.asDexField())
        : getRenamedMethodSignature(originalMember.asDexMethod());
  }

  public final DexMethod getRenamedMethodSignature(DexMethod originalMethod) {
    return getRenamedMethodSignature(originalMethod, null);
  }

  public abstract DexMethod getRenamedMethodSignature(DexMethod originalMethod, GraphLens applied);

  public DexEncodedMethod mapDexEncodedMethod(
      DexEncodedMethod originalEncodedMethod, DexDefinitionSupplier definitions) {
    return mapDexEncodedMethod(originalEncodedMethod, definitions, null);
  }

  public DexEncodedMethod mapDexEncodedMethod(
      DexEncodedMethod originalEncodedMethod,
      DexDefinitionSupplier definitions,
      GraphLens applied) {
    assert originalEncodedMethod != DexEncodedMethod.SENTINEL;
    DexMethod newMethod = getRenamedMethodSignature(originalEncodedMethod.method, applied);
    // Note that:
    // * Even if `newMethod` is the same as `originalEncodedMethod.method`, we still need to look it
    //   up, since `originalEncodedMethod` may be obsolete.
    // * We can't directly use AppInfo#definitionFor(DexMethod) since definitions may not be
    //   updated either yet.
    DexClass newHolder = definitions.definitionFor(newMethod.holder);
    assert newHolder != null;
    DexEncodedMethod newEncodedMethod = newHolder.lookupMethod(newMethod);
    assert newEncodedMethod != null;
    return newEncodedMethod;
  }

  public ProgramMethod mapProgramMethod(
      ProgramMethod oldMethod, DexDefinitionSupplier definitions) {
    DexMethod newMethod = getRenamedMethodSignature(oldMethod.getReference());
    DexProgramClass holder = asProgramClassOrNull(definitions.definitionForHolder(newMethod));
    return newMethod.lookupOnProgramClass(holder);
  }

  // Predicate indicating if a rewritten type is a simple renaming, meaning the move from type to
  // rewritten is just a renaming of the type to another. In other words, the content of the
  // definition, including the definition of all of its members is the same modulo the renaming.
  public boolean isSimpleRenaming(DexType from, DexType to) {
    return false;
  }

  public abstract DexType lookupClassType(DexType type);

  public abstract DexType lookupType(DexType type);

  // This overload can be used when the graph lens is known to be context insensitive.
  public final DexMethod lookupMethod(DexMethod method) {
    assert verifyIsContextFreeForMethod(method);
    return lookupMethod(method, null, null).getReference();
  }

  /** Lookup a rebound or non-rebound method reference using the current graph lens. */
  public abstract MethodLookupResult lookupMethod(DexMethod method, DexMethod context, Type type);

  protected abstract MethodLookupResult internalLookupMethod(
      DexMethod reference, DexMethod context, Type type, LookupMethodContinuation continuation);

  interface LookupMethodContinuation {

    MethodLookupResult lookupMethod(MethodLookupResult previous);
  }

  public abstract RewrittenPrototypeDescription lookupPrototypeChangesForMethodDefinition(
      DexMethod method);

  /** Lookup a rebound or non-rebound field reference using the current graph lens. */
  public DexField lookupField(DexField field) {
    // Lookup the field using the graph lens and return the (non-rebound) reference from the lookup
    // result.
    return lookupFieldResult(field).getReference();
  }

  /** Lookup a rebound or non-rebound field reference using the current graph lens. */
  public FieldLookupResult lookupFieldResult(DexField field) {
    // Lookup the field using the graph lens and return the lookup result.
    return internalLookupField(field, x -> x);
  }

  protected abstract FieldLookupResult internalLookupField(
      DexField reference, LookupFieldContinuation continuation);

  interface LookupFieldContinuation {

    FieldLookupResult lookupField(FieldLookupResult previous);
  }

  public DexMethod lookupGetFieldForMethod(DexField field, DexMethod context) {
    return null;
  }

  public DexMethod lookupPutFieldForMethod(DexField field, DexMethod context) {
    return null;
  }

  public DexReference lookupReference(DexReference reference) {
    if (reference.isDexType()) {
      return lookupType(reference.asDexType());
    } else if (reference.isDexMethod()) {
      return lookupMethod(reference.asDexMethod());
    } else {
      assert reference.isDexField();
      return lookupField(reference.asDexField());
    }
  }

  // The method lookupMethod() maps a pair INVOKE=(method signature, invoke type) to a new pair
  // INVOKE'=(method signature', invoke type'). This mapping can be context sensitive, meaning that
  // the result INVOKE' depends on where the invocation INVOKE is in the program. This is, for
  // example, used by the vertical class merger to translate invoke-super instructions that hit
  // a method in the direct super class to invoke-direct instructions after class merging.
  //
  // This method can be used to determine if a graph lens is context sensitive. If a graph lens
  // is context insensitive, it is safe to invoke lookupMethod() without a context (or to pass null
  // as context). Trying to invoke a context sensitive graph lens without a context will lead to
  // an assertion error.
  public abstract boolean isContextFreeForMethods();

  public boolean verifyIsContextFreeForMethod(DexMethod method) {
    return isContextFreeForMethods();
  }

  public static GraphLens getIdentityLens() {
    return IdentityGraphLens.getInstance();
  }

  public boolean hasCodeRewritings() {
    return true;
  }

  public boolean isAppliedLens() {
    return false;
  }

  public abstract boolean isIdentityLens();

  public boolean isMemberRebindingLens() {
    return false;
  }

  public abstract boolean isNonIdentityLens();

  public NonIdentityGraphLens asNonIdentityLens() {
    return null;
  }

  public boolean isInterfaceProcessorLens() {
    return false;
  }

  public InterfaceProcessorNestedGraphLens asInterfaceProcessorLens() {
    return null;
  }

  public GraphLens withCodeRewritingsApplied(DexItemFactory dexItemFactory) {
    if (hasCodeRewritings()) {
      return new ClearCodeRewritingGraphLens(dexItemFactory, this);
    }
    return this;
  }

  public <T extends DexDefinition> boolean assertDefinitionsNotModified(Iterable<T> definitions) {
    for (DexDefinition definition : definitions) {
      DexReference reference = definition.getReference();
      // We allow changes to bridge methods as these get retargeted even if they are kept.
      boolean isBridge =
          definition.isDexEncodedMethod() && definition.asDexEncodedMethod().accessFlags.isBridge();
      assert isBridge || lookupReference(reference) == reference;
    }
    return true;
  }

  public <T extends DexReference> boolean assertPinnedNotModified(KeepInfoCollection keepInfo) {
    List<DexReference> pinnedItems = new ArrayList<>();
    keepInfo.forEachPinnedType(pinnedItems::add);
    keepInfo.forEachPinnedMethod(pinnedItems::add);
    keepInfo.forEachPinnedField(pinnedItems::add);
    return assertReferencesNotModified(pinnedItems);
  }

  public <T extends DexReference> boolean assertReferencesNotModified(Iterable<T> references) {
    for (DexReference reference : references) {
      if (reference.isDexField()) {
        DexField field = reference.asDexField();
        assert getRenamedFieldSignature(field) == field;
      } else if (reference.isDexMethod()) {
        DexMethod method = reference.asDexMethod();
        assert getRenamedMethodSignature(method) == method;
      } else {
        assert reference.isDexType();
        DexType type = reference.asDexType();
        assert lookupType(type) == type;
      }
    }
    return true;
  }

  public Map<DexCallSite, ProgramMethodSet> rewriteCallSites(
      Map<DexCallSite, ProgramMethodSet> callSites, DexDefinitionSupplier definitions) {
    Map<DexCallSite, ProgramMethodSet> result = new IdentityHashMap<>();
    LensCodeRewriterUtils rewriter = new LensCodeRewriterUtils(definitions, this);
    callSites.forEach(
        (callSite, contexts) -> {
          for (ProgramMethod context : contexts.rewrittenWithLens(definitions, this)) {
            DexCallSite rewrittenCallSite = rewriter.rewriteCallSite(callSite, context);
            result
                .computeIfAbsent(rewrittenCallSite, ignore -> ProgramMethodSet.create())
                .add(context);
          }
        });
    return result;
  }

  @SuppressWarnings("unchecked")
  public <T extends DexReference> T rewriteReference(T reference) {
    if (reference.isDexField()) {
      return (T) getRenamedFieldSignature(reference.asDexField());
    }
    if (reference.isDexMethod()) {
      return (T) getRenamedMethodSignature(reference.asDexMethod());
    }
    assert reference.isDexType();
    return (T) lookupType(reference.asDexType());
  }

  public Set<DexReference> rewriteReferences(Set<DexReference> references) {
    Set<DexReference> result = SetUtils.newIdentityHashSet(references.size());
    for (DexReference reference : references) {
      result.add(rewriteReference(reference));
    }
    return result;
  }

  public <R extends DexReference, T> ImmutableMap<R, T> rewriteReferenceKeys(Map<R, T> map) {
    ImmutableMap.Builder<R, T> builder = ImmutableMap.builder();
    map.forEach((reference, value) -> builder.put(rewriteReference(reference), value));
    return builder.build();
  }

  public Object2BooleanMap<DexReference> rewriteReferenceKeys(Object2BooleanMap<DexReference> map) {
    Object2BooleanMap<DexReference> result = new Object2BooleanArrayMap<>();
    for (Object2BooleanMap.Entry<DexReference> entry : map.object2BooleanEntrySet()) {
      result.put(rewriteReference(entry.getKey()), entry.getBooleanValue());
    }
    return result;
  }

  public ImmutableSet<DexMethod> rewriteMethods(Set<DexMethod> methods) {
    ImmutableSet.Builder<DexMethod> builder = ImmutableSet.builder();
    for (DexMethod method : methods) {
      builder.add(getRenamedMethodSignature(method));
    }
    return builder.build();
  }

  public ImmutableSortedSet<DexMethod> rewriteMethodsSorted(Set<DexMethod> methods) {
    ImmutableSortedSet.Builder<DexMethod> builder =
        new ImmutableSortedSet.Builder<>(PresortedComparable::slowCompare);
    for (DexMethod method : methods) {
      builder.add(getRenamedMethodSignature(method));
    }
    return builder.build();
  }

  public <T> ImmutableMap<DexField, T> rewriteFieldKeys(Map<DexField, T> map) {
    ImmutableMap.Builder<DexField, T> builder = ImmutableMap.builder();
    map.forEach((field, value) -> builder.put(getRenamedFieldSignature(field), value));
    return builder.build();
  }

  public ImmutableSet<DexType> rewriteTypes(Set<DexType> types) {
    ImmutableSortedSet.Builder<DexType> builder =
        new ImmutableSortedSet.Builder<>(PresortedComparable::slowCompare);
    for (DexType type : types) {
      builder.add(lookupType(type));
    }
    return builder.build();
  }

  public <T> ImmutableMap<DexType, T> rewriteTypeKeys(Map<DexType, T> map) {
    ImmutableMap.Builder<DexType, T> builder = ImmutableMap.builder();
    map.forEach((type, value) -> builder.put(lookupType(type), value));
    return builder.build();
  }

  public boolean verifyMappingToOriginalProgram(
      AppView<?> appView, DexApplication originalApplication) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    Iterable<DexProgramClass> classes = appView.appInfo().classesWithDeterministicOrder();
    // Collect all original fields and methods for efficient querying.
    Set<DexField> originalFields = Sets.newIdentityHashSet();
    Set<DexMethod> originalMethods = Sets.newIdentityHashSet();
    for (DexProgramClass clazz : originalApplication.classes()) {
      for (DexEncodedField field : clazz.fields()) {
        originalFields.add(field.field);
      }
      for (DexEncodedMethod method : clazz.methods()) {
        originalMethods.add(method.method);
      }
    }

    // Check that all fields and methods in the generated program can be mapped back to one of the
    // original fields or methods.
    for (DexProgramClass clazz : classes) {
      if (appView.appInfo().getSyntheticItems().isSyntheticClass(clazz)) {
        continue;
      }
      for (DexEncodedField field : clazz.fields()) {
        // The field $r8$clinitField may be synthesized by R8 in order to trigger the initialization
        // of the enclosing class. It is not present in the input, and therefore we do not require
        // that it can be mapped back to the original program.
        if (field.field.match(dexItemFactory.objectMembers.clinitField)) {
          continue;
        }

        // TODO(b/167947782): Should be a general check to see if the field is D8/R8 synthesized.
        if (field.getReference().name.toSourceString().equals(ClassMerger.CLASS_ID_FIELD_NAME)) {
          continue;
        }

        DexField originalField = getOriginalFieldSignature(field.field);
        assert originalFields.contains(originalField)
            : "Unable to map field `" + field.field.toSourceString() + "` back to original program";
      }
      for (DexEncodedMethod method : clazz.methods()) {
        if (method.isD8R8Synthesized()) {
          // Methods synthesized by D8/R8 may not be mapped.
          continue;
        }
        DexMethod originalMethod = getOriginalMethodSignature(method.method);
        assert originalMethods.contains(originalMethod);
      }
    }

    return true;
  }

  public abstract static class NonIdentityGraphLens extends GraphLens {

    private final DexItemFactory dexItemFactory;
    private GraphLens previousLens;

    private final Map<DexType, DexType> arrayTypeCache = new ConcurrentHashMap<>();

    public NonIdentityGraphLens(DexItemFactory dexItemFactory, GraphLens previousLens) {
      this.dexItemFactory = dexItemFactory;
      this.previousLens = previousLens;
    }

    public final DexItemFactory dexItemFactory() {
      return dexItemFactory;
    }

    public final GraphLens getPrevious() {
      return previousLens;
    }

    @SuppressWarnings("unchecked")
    public final <T extends GraphLens> T findPrevious(Predicate<NonIdentityGraphLens> predicate) {
      GraphLens current = getPrevious();
      while (current.isNonIdentityLens()) {
        NonIdentityGraphLens nonIdentityGraphLens = current.asNonIdentityLens();
        if (predicate.test(nonIdentityGraphLens)) {
          return (T) nonIdentityGraphLens;
        }
        current = nonIdentityGraphLens.getPrevious();
      }
      return null;
    }

    public final void withAlternativeParentLens(GraphLens lens, Action action) {
      GraphLens oldParent = getPrevious();
      previousLens = lens;
      action.execute();
      previousLens = oldParent;
    }

    @Override
    public MethodLookupResult lookupMethod(DexMethod method, DexMethod context, Type type) {
      if (method.getHolderType().isArrayType()) {
        assert lookupType(method.getReturnType()) == method.getReturnType();
        assert method.getParameters().stream()
            .allMatch(parameterType -> lookupType(parameterType) == parameterType);
        return MethodLookupResult.builder(this)
            .setReference(method.withHolder(lookupType(method.getHolderType()), dexItemFactory))
            .setType(type)
            .build();
      }
      assert method.getHolderType().isClassType();
      return internalLookupMethod(method, context, type, result -> result);
    }

    @Override
    public final DexType lookupType(DexType type) {
      if (type.isPrimitiveType() || type.isVoidType() || type.isNullValueType()) {
        return type;
      }
      if (type.isArrayType()) {
        DexType result = arrayTypeCache.get(type);
        if (result == null) {
          DexType baseType = type.toBaseType(dexItemFactory);
          DexType newType = lookupType(baseType);
          result = baseType == newType ? type : type.replaceBaseType(newType, dexItemFactory);
          arrayTypeCache.put(type, result);
        }
        return result;
      }
      return lookupClassType(type);
    }

    @Override
    public final DexType lookupClassType(DexType type) {
      assert type.isClassType() : "Expected class type, but was `" + type.toSourceString() + "`";
      return internalDescribeLookupClassType(getPrevious().lookupClassType(type));
    }

    @Override
    protected FieldLookupResult internalLookupField(
        DexField reference, LookupFieldContinuation continuation) {
      return previousLens.internalLookupField(
          reference, previous -> continuation.lookupField(internalDescribeLookupField(previous)));
    }

    @Override
    protected MethodLookupResult internalLookupMethod(
        DexMethod reference, DexMethod context, Type type, LookupMethodContinuation continuation) {
      return previousLens.internalLookupMethod(
          reference,
          internalGetPreviousMethodSignature(context),
          type,
          previous -> continuation.lookupMethod(internalDescribeLookupMethod(previous, context)));
    }

    protected abstract FieldLookupResult internalDescribeLookupField(FieldLookupResult previous);

    protected abstract MethodLookupResult internalDescribeLookupMethod(
        MethodLookupResult previous, DexMethod context);

    protected abstract DexType internalDescribeLookupClassType(DexType previous);

    protected abstract DexMethod internalGetPreviousMethodSignature(DexMethod method);

    @Override
    public final boolean isIdentityLens() {
      return false;
    }

    @Override
    public final boolean isNonIdentityLens() {
      return true;
    }

    @Override
    public final NonIdentityGraphLens asNonIdentityLens() {
      return this;
    }
  }

  private static final class IdentityGraphLens extends GraphLens {

    private static IdentityGraphLens INSTANCE = new IdentityGraphLens();

    private IdentityGraphLens() {}

    private static IdentityGraphLens getInstance() {
      return INSTANCE;
    }

    @Override
    public boolean isIdentityLens() {
      return true;
    }

    @Override
    public boolean isNonIdentityLens() {
      return false;
    }

    @Override
    public DexType getOriginalType(DexType type) {
      return type;
    }

    @Override
    public Iterable<DexType> getOriginalTypes(DexType type) {
      return IterableUtils.singleton(type);
    }

    @Override
    public DexField getOriginalFieldSignature(DexField field) {
      return field;
    }

    @Override
    public DexMethod getOriginalMethodSignature(DexMethod method) {
      return method;
    }

    @Override
    public DexField getRenamedFieldSignature(DexField originalField) {
      return originalField;
    }

    @Override
    public DexMethod getRenamedMethodSignature(DexMethod originalMethod, GraphLens applied) {
      return originalMethod;
    }

    @Override
    public DexType lookupType(DexType type) {
      return type;
    }

    @Override
    public DexType lookupClassType(DexType type) {
      assert type.isClassType();
      return type;
    }

    @Override
    public MethodLookupResult lookupMethod(DexMethod method, DexMethod context, Type type) {
      return MethodLookupResult.builder(this).setReference(method).setType(type).build();
    }

    @Override
    public RewrittenPrototypeDescription lookupPrototypeChangesForMethodDefinition(
        DexMethod method) {
      return RewrittenPrototypeDescription.none();
    }

    @Override
    protected FieldLookupResult internalLookupField(
        DexField reference, LookupFieldContinuation continuation) {
      // Passes the field reference back to the next graph lens. The identity lens intentionally
      // does not set the rebound field reference, since it does not know what that is.
      return continuation.lookupField(
          FieldLookupResult.builder(this).setReference(reference).build());
    }

    @Override
    protected MethodLookupResult internalLookupMethod(
        DexMethod reference, DexMethod context, Type type, LookupMethodContinuation continuation) {
      // Passes the method reference back to the next graph lens. The identity lens intentionally
      // does not set the rebound method reference, since it does not know what that is.
      return continuation.lookupMethod(
          MethodLookupResult.builder(this).setReference(reference).setType(type).build());
    }

    @Override
    public boolean isContextFreeForMethods() {
      return true;
    }

    @Override
    public boolean hasCodeRewritings() {
      return false;
    }
  }

  // This lens clears all code rewriting (lookup methods mimics identity lens behavior) but still
  // relies on the previous lens for names (getRenamed/Original methods).
  public static class ClearCodeRewritingGraphLens extends NonIdentityGraphLens {

    public ClearCodeRewritingGraphLens(DexItemFactory dexItemFactory, GraphLens previousLens) {
      super(dexItemFactory, previousLens);
    }

    @Override
    public DexType getOriginalType(DexType type) {
      return getPrevious().getOriginalType(type);
    }

    @Override
    public Iterable<DexType> getOriginalTypes(DexType type) {
      return getPrevious().getOriginalTypes(type);
    }

    @Override
    public DexField getOriginalFieldSignature(DexField field) {
      return getPrevious().getOriginalFieldSignature(field);
    }

    @Override
    public DexMethod getOriginalMethodSignature(DexMethod method) {
      return getPrevious().getOriginalMethodSignature(method);
    }

    @Override
    public DexField getRenamedFieldSignature(DexField originalField) {
      return getPrevious().getRenamedFieldSignature(originalField);
    }

    @Override
    public DexMethod getRenamedMethodSignature(DexMethod originalMethod, GraphLens applied) {
      return this != applied
          ? getPrevious().getRenamedMethodSignature(originalMethod, applied)
          : originalMethod;
    }

    @Override
    public RewrittenPrototypeDescription lookupPrototypeChangesForMethodDefinition(
        DexMethod method) {
      return getIdentityLens().lookupPrototypeChangesForMethodDefinition(method);
    }

    @Override
    public final DexType internalDescribeLookupClassType(DexType previous) {
      return previous;
    }

    @Override
    protected FieldLookupResult internalLookupField(
        DexField reference, LookupFieldContinuation continuation) {
      return getIdentityLens().internalLookupField(reference, continuation);
    }

    @Override
    protected FieldLookupResult internalDescribeLookupField(FieldLookupResult previous) {
      throw new Unreachable();
    }

    @Override
    protected MethodLookupResult internalLookupMethod(
        DexMethod reference, DexMethod context, Type type, LookupMethodContinuation continuation) {
      return getIdentityLens().internalLookupMethod(reference, context, type, continuation);
    }

    @Override
    public MethodLookupResult internalDescribeLookupMethod(
        MethodLookupResult previous, DexMethod context) {
      throw new Unreachable();
    }

    @Override
    protected DexMethod internalGetPreviousMethodSignature(DexMethod method) {
      return method;
    }

    @Override
    public boolean isContextFreeForMethods() {
      return getIdentityLens().isContextFreeForMethods();
    }
  }

  /**
   * GraphLens implementation with a parent lens using a simple mapping for type, method and field
   * mapping.
   *
   * <p>Subclasses can override the lookup methods.
   *
   * <p>For method mapping where invocation type can change just override {@link
   * #mapInvocationType(DexMethod, DexMethod, Type)} if the default name mapping applies, and only
   * invocation type might need to change.
   */
  public static class NestedGraphLens extends NonIdentityGraphLens {

    protected final DexItemFactory dexItemFactory;

    protected final Map<DexType, DexType> typeMap;
    protected final Map<DexMethod, DexMethod> methodMap;
    protected final Map<DexField, DexField> fieldMap;

    // Maps that store the original signature of fields and methods that have been affected, for
    // example, by vertical class merging. Needed to generate a correct Proguard map in the end.
    protected final BiMap<DexField, DexField> originalFieldSignatures;
    protected BiMap<DexMethod, DexMethod> originalMethodSignatures;

    // Overrides this if the sub type needs to be a nested lens while it doesn't have any mappings
    // at all, e.g., publicizer lens that changes invocation type only.
    protected boolean isLegitimateToHaveEmptyMappings() {
      return false;
    }

    public NestedGraphLens(
        Map<DexType, DexType> typeMap,
        Map<DexMethod, DexMethod> methodMap,
        Map<DexField, DexField> fieldMap,
        BiMap<DexField, DexField> originalFieldSignatures,
        BiMap<DexMethod, DexMethod> originalMethodSignatures,
        GraphLens previousLens,
        DexItemFactory dexItemFactory) {
      super(dexItemFactory, previousLens);
      assert !typeMap.isEmpty()
          || !methodMap.isEmpty()
          || !fieldMap.isEmpty()
          || isLegitimateToHaveEmptyMappings();
      this.typeMap = typeMap.isEmpty() ? null : typeMap;
      this.methodMap = methodMap;
      this.fieldMap = fieldMap;
      this.originalFieldSignatures = originalFieldSignatures;
      this.originalMethodSignatures = originalMethodSignatures;
      this.dexItemFactory = dexItemFactory;
    }

    public static Builder builder() {
      return new Builder();
    }

    protected DexType internalGetOriginalType(DexType previous) {
      return previous;
    }

    protected Iterable<DexType> internalGetOriginalTypes(DexType previous) {
      return IterableUtils.singleton(internalGetOriginalType(previous));
    }

    @Override
    public DexType getOriginalType(DexType type) {
      return getPrevious().getOriginalType(internalGetOriginalType(type));
    }

    @Override
    public Iterable<DexType> getOriginalTypes(DexType type) {
      return IterableUtils.flatMap(internalGetOriginalTypes(type), getPrevious()::getOriginalTypes);
    }

    @Override
    public DexField getOriginalFieldSignature(DexField field) {
      DexField originalField =
          originalFieldSignatures != null
              ? originalFieldSignatures.getOrDefault(field, field)
              : field;
      return getPrevious().getOriginalFieldSignature(originalField);
    }

    @Override
    public DexMethod getOriginalMethodSignature(DexMethod method) {
      DexMethod originalMethod = internalGetPreviousMethodSignature(method);
      return getPrevious().getOriginalMethodSignature(originalMethod);
    }

    @Override
    public DexField getRenamedFieldSignature(DexField originalField) {
      DexField renamedField = getPrevious().getRenamedFieldSignature(originalField);
      return originalFieldSignatures != null
          ? originalFieldSignatures.inverse().getOrDefault(renamedField, renamedField)
          : renamedField;
    }

    @Override
    public DexMethod getRenamedMethodSignature(DexMethod originalMethod, GraphLens applied) {
      if (this == applied) {
        return originalMethod;
      }
      DexMethod renamedMethod = getPrevious().getRenamedMethodSignature(originalMethod, applied);
      return internalGetNextMethodSignature(renamedMethod);
    }

    @Override
    protected DexType internalDescribeLookupClassType(DexType previous) {
      return typeMap != null ? typeMap.getOrDefault(previous, previous) : previous;
    }

    @Override
    protected FieldLookupResult internalDescribeLookupField(FieldLookupResult previous) {
      if (previous.hasReboundReference()) {
        // Rewrite the rebound reference and then "fixup" the non-rebound reference.
        DexField rewrittenReboundReference = previous.getRewrittenReboundReference(fieldMap);
        DexField rewrittenNonReboundReference =
            previous.getReference() == previous.getReboundReference()
                ? rewrittenReboundReference
                : rewrittenReboundReference.withHolder(
                    internalDescribeLookupClassType(previous.getReference().getHolderType()),
                    dexItemFactory);
        return FieldLookupResult.builder(this)
            .setReboundReference(rewrittenReboundReference)
            .setReference(rewrittenNonReboundReference)
            .build();
      } else {
        // TODO(b/168282032): We should always have the rebound reference, so this should become
        //  unreachable.
        DexField rewrittenReference = previous.getRewrittenReference(fieldMap);
        return FieldLookupResult.builder(this).setReference(rewrittenReference).build();
      }
    }

    @Override
    public MethodLookupResult internalDescribeLookupMethod(
        MethodLookupResult previous, DexMethod context) {
      if (previous.hasReboundReference()) {
        // TODO(sgjesse): Should we always do interface to virtual mapping? Is it a performance win
        //  that only subclasses which are known to need it actually do it?
        DexMethod rewrittenReboundReference = previous.getRewrittenReboundReference(methodMap);
        DexMethod rewrittenReference =
            previous.getReference() == previous.getReboundReference()
                ? rewrittenReboundReference
                : // This assumes that the holder will always be moved in lock-step with the method!
                rewrittenReboundReference.withHolder(
                    internalDescribeLookupClassType(previous.getReference().getHolderType()),
                    dexItemFactory);
        return MethodLookupResult.builder(this)
            .setReference(rewrittenReference)
            .setReboundReference(rewrittenReboundReference)
            .setPrototypeChanges(
                internalDescribePrototypeChanges(
                    previous.getPrototypeChanges(), rewrittenReboundReference))
            .setType(
                mapInvocationType(
                    rewrittenReboundReference, previous.getReference(), previous.getType()))
            .build();
      } else {
        // TODO(b/168282032): We should always have the rebound reference, so this should become
        //  unreachable.
        DexMethod newMethod = methodMap.get(previous.getReference());
        if (newMethod == null) {
          return previous;
        }
        // TODO(sgjesse): Should we always do interface to virtual mapping? Is it a performance win
        //  that only subclasses which are known to need it actually do it?
        return MethodLookupResult.builder(this)
            .setReference(newMethod)
            .setPrototypeChanges(
                internalDescribePrototypeChanges(previous.getPrototypeChanges(), newMethod))
            .setType(mapInvocationType(newMethod, previous.getReference(), previous.getType()))
            .build();
      }
    }

    @Override
    public RewrittenPrototypeDescription lookupPrototypeChangesForMethodDefinition(
        DexMethod method) {
      DexMethod previous = internalGetPreviousMethodSignature(method);
      RewrittenPrototypeDescription lookup =
          getPrevious().lookupPrototypeChangesForMethodDefinition(previous);
      return internalDescribePrototypeChanges(lookup, method);
    }

    protected RewrittenPrototypeDescription internalDescribePrototypeChanges(
        RewrittenPrototypeDescription prototypeChanges, DexMethod method) {
      return prototypeChanges;
    }

    @Override
    protected DexMethod internalGetPreviousMethodSignature(DexMethod method) {
      return originalMethodSignatures != null
          ? originalMethodSignatures.getOrDefault(method, method)
          : method;
    }

    protected DexMethod internalGetNextMethodSignature(DexMethod method) {
      return originalMethodSignatures != null
          ? originalMethodSignatures.inverse().getOrDefault(method, method)
          : method;
    }

    @Override
    public DexMethod lookupGetFieldForMethod(DexField field, DexMethod context) {
      return getPrevious().lookupGetFieldForMethod(field, context);
    }

    @Override
    public DexMethod lookupPutFieldForMethod(DexField field, DexMethod context) {
      return getPrevious().lookupPutFieldForMethod(field, context);
    }

    /**
     * Default invocation type mapping.
     *
     * <p>This is an identity mapping. If a subclass need invocation type mapping either override
     * this method or {@link #lookupMethod(DexMethod, DexMethod, Type)}
     */
    protected Type mapInvocationType(DexMethod newMethod, DexMethod originalMethod, Type type) {
      return type;
    }

    /**
     * Standard mapping between interface and virtual invoke type.
     *
     * <p>Handle methods moved from interface to class or class to interface.
     */
    public static Type mapVirtualInterfaceInvocationTypes(
        DexDefinitionSupplier definitions,
        DexMethod newMethod,
        DexMethod originalMethod,
        Type type) {
      if (type == Type.VIRTUAL || type == Type.INTERFACE) {
        // Get the invoke type of the actual definition.
        DexClass newTargetClass = definitions.definitionFor(newMethod.getHolderType());
        if (newTargetClass == null) {
          return type;
        }
        DexClass originalTargetClass = definitions.definitionFor(originalMethod.getHolderType());
        if (originalTargetClass != null
            && (originalTargetClass.isInterface() ^ (type == Type.INTERFACE))) {
          // The invoke was wrong to start with, so we keep it wrong. This is to ensure we get
          // the IncompatibleClassChangeError the original invoke would have triggered.
          return newTargetClass.accessFlags.isInterface() ? Type.VIRTUAL : Type.INTERFACE;
        }
        return newTargetClass.accessFlags.isInterface() ? Type.INTERFACE : Type.VIRTUAL;
      }
      return type;
    }

    @Override
    public boolean isContextFreeForMethods() {
      return getPrevious().isContextFreeForMethods();
    }

    @Override
    public boolean verifyIsContextFreeForMethod(DexMethod method) {
      assert getPrevious().verifyIsContextFreeForMethod(method);
      return true;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      if (typeMap != null) {
        for (Map.Entry<DexType, DexType> entry : typeMap.entrySet()) {
          builder.append(entry.getKey().toSourceString()).append(" -> ");
          builder.append(entry.getValue().toSourceString()).append(System.lineSeparator());
        }
      }
      for (Map.Entry<DexMethod, DexMethod> entry : methodMap.entrySet()) {
        builder.append(entry.getKey().toSourceString()).append(" -> ");
        builder.append(entry.getValue().toSourceString()).append(System.lineSeparator());
      }
      for (Map.Entry<DexField, DexField> entry : fieldMap.entrySet()) {
        builder.append(entry.getKey().toSourceString()).append(" -> ");
        builder.append(entry.getValue().toSourceString()).append(System.lineSeparator());
      }
      builder.append(getPrevious().toString());
      return builder.toString();
    }
  }
}
