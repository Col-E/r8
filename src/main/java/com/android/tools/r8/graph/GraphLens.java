// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.optimize.enums.EnumUnboxingLens;
import com.android.tools.r8.optimize.MemberRebindingIdentityLens;
import com.android.tools.r8.optimize.MemberRebindingLens;
import com.android.tools.r8.shaking.KeepInfoCollection;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeHashMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalManyToOneRepresentativeMap;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Object2BooleanArrayMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
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

    public R getRewrittenReference(BidirectionalManyToOneRepresentativeMap<R, R> rewritings) {
      return rewritings.getOrDefault(reference, reference);
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

    public R getRewrittenReboundReference(
        BidirectionalManyToOneRepresentativeMap<R, R> rewritings) {
      return rewritings.getOrDefault(reboundReference, reboundReference);
    }

    public R getRewrittenReboundReference(Function<R, R> rewritings) {
      R rewrittenReboundReference = rewritings.apply(reboundReference);
      return rewrittenReboundReference != null ? rewrittenReboundReference : reboundReference;
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

    private final DexType readCastType;
    private final DexType writeCastType;

    private FieldLookupResult(
        DexField reference,
        DexField reboundReference,
        DexType readCastType,
        DexType writeCastType) {
      super(reference, reboundReference);
      this.readCastType = readCastType;
      this.writeCastType = writeCastType;
    }

    public static Builder builder(GraphLens lens) {
      return new Builder(lens);
    }

    public boolean hasReadCastType() {
      return readCastType != null;
    }

    public DexType getReadCastType() {
      return readCastType;
    }

    public DexType getRewrittenReadCastType(Function<DexType, DexType> fn) {
      return hasReadCastType() ? fn.apply(readCastType) : null;
    }

    public boolean hasWriteCastType() {
      return writeCastType != null;
    }

    public DexType getWriteCastType() {
      return writeCastType;
    }

    public DexType getRewrittenWriteCastType(Function<DexType, DexType> fn) {
      return hasWriteCastType() ? fn.apply(writeCastType) : null;
    }

    public static class Builder extends MemberLookupResult.Builder<DexField, Builder> {

      private DexType readCastType;
      private DexType writeCastType;
      private GraphLens lens;

      private Builder(GraphLens lens) {
        this.lens = lens;
      }

      public Builder setReadCastType(DexType readCastType) {
        this.readCastType = readCastType;
        return this;
      }

      public Builder setWriteCastType(DexType writeCastType) {
        this.writeCastType = writeCastType;
        return this;
      }

      @Override
      public Builder self() {
        return this;
      }

      public FieldLookupResult build() {
        // TODO(b/168282032): All non-identity graph lenses should set the rebound reference.
        return new FieldLookupResult(reference, reboundReference, readCastType, writeCastType);
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

  public abstract static class Builder {

    protected final MutableBidirectionalManyToOneRepresentativeMap<DexField, DexField> fieldMap =
        BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();
    protected final MutableBidirectionalManyToOneRepresentativeMap<DexMethod, DexMethod> methodMap =
        BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();
    protected final Map<DexType, DexType> typeMap = new IdentityHashMap<>();

    protected Builder() {}

    public void map(DexType from, DexType to) {
      if (from == to) {
        return;
      }
      typeMap.put(from, to);
    }

    public void move(DexMethod from, DexMethod to) {
      if (from == to) {
        return;
      }
      methodMap.put(from, to);
    }

    public void move(DexField from, DexField to) {
      if (from == to) {
        return;
      }
      fieldMap.put(from, to);
    }

    public abstract GraphLens build(AppView<?> appView);
  }

  /**
   * Intentionally private. All graph lenses except for {@link IdentityGraphLens} should inherit
   * from {@link NonIdentityGraphLens}.
   */
  private GraphLens() {}

  public boolean isSyntheticFinalizationGraphLens() {
    return false;
  }

  public abstract DexType getOriginalType(DexType type);

  public abstract Iterable<DexType> getOriginalTypes(DexType type);

  public abstract DexField getOriginalFieldSignature(DexField field);

  public final DexMember<?, ?> getOriginalMemberSignature(DexMember<?, ?> member) {
    return member.apply(this::getOriginalFieldSignature, this::getOriginalMethodSignature);
  }

  public final DexMethod getOriginalMethodSignature(DexMethod method) {
    return getOriginalMethodSignature(method, null);
  }

  public final DexMethod getOriginalMethodSignature(DexMethod method, GraphLens atGraphLens) {
    GraphLens current = this;
    DexMethod original = method;
    while (current.isNonIdentityLens() && current != atGraphLens) {
      NonIdentityGraphLens nonIdentityLens = current.asNonIdentityLens();
      original = nonIdentityLens.getPreviousMethodSignature(original);
      current = nonIdentityLens.getPrevious();
    }
    assert atGraphLens == null ? current.isIdentityLens() : (current == atGraphLens);
    return original;
  }

  public final DexMethod getOriginalMethodSignatureForMapping(DexMethod method) {
    GraphLens current = this;
    DexMethod original = method;
    while (current.isNonIdentityLens()) {
      NonIdentityGraphLens nonIdentityLens = current.asNonIdentityLens();
      original = nonIdentityLens.getPreviousMethodSignatureForMapping(original);
      current = nonIdentityLens.getPrevious();
    }
    assert current.isIdentityLens();
    return original;
  }

  public final DexField getRenamedFieldSignature(DexField originalField) {
    return getRenamedFieldSignature(originalField, null);
  }

  public abstract DexField getRenamedFieldSignature(DexField originalField, GraphLens codeLens);

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
    DexMethod newMethod = getRenamedMethodSignature(originalEncodedMethod.getReference(), applied);
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

  // Predicate indicating if a rewritten reference is a simple renaming, meaning the move from one
  // reference to another is simply either just a renaming or/also renaming of the references. In
  // other words, the content of the definition, including the definition of all of its members is
  // the same modulo the renaming.
  public <T extends DexReference> boolean isSimpleRenaming(T from, T to) {
    assert from != to;
    return false;
  }

  // Predicate to see if a method definition is only changed by repackaging or synthetic
  // finalization indicating that it is a simple renaming.
  public final boolean isSimpleRenaming(DexMethod method) {
    DexMethod methodToCompareAgainst = method;
    DexMethod original = method;
    GraphLens current = this;
    while (current.isNonIdentityLens()) {
      NonIdentityGraphLens nonIdentityLens = current.asNonIdentityLens();
      original = nonIdentityLens.getPreviousMethodSignature(original);
      if (current.isSimpleRenamingLens()) {
        methodToCompareAgainst = original;
      } else if (methodToCompareAgainst != original) {
        return false;
      }
      assert nonIdentityLens.getPrevious() != null;
      current = nonIdentityLens.getPrevious();
    }
    return true;
  }

  public abstract String lookupPackageName(String pkg);

  public DexType lookupClassType(DexType type) {
    return lookupClassType(type, getIdentityLens());
  }

  public abstract DexType lookupClassType(DexType type, GraphLens applied);

  public DexType lookupType(DexType type) {
    return lookupType(type, getIdentityLens());
  }

  public abstract DexType lookupType(DexType type, GraphLens applied);

  @Deprecated
  public final DexMethod lookupMethod(DexMethod method) {
    assert verifyIsContextFreeForMethod(method);
    return lookupMethod(method, null, null).getReference();
  }

  public final MethodLookupResult lookupInvokeDirect(DexMethod method, ProgramMethod context) {
    return lookupMethod(method, context.getReference(), Type.DIRECT);
  }

  public final MethodLookupResult lookupInvokeDirect(
      DexMethod method, ProgramMethod context, GraphLens codeLens) {
    return lookupMethod(method, context.getReference(), Type.DIRECT, codeLens);
  }

  public final MethodLookupResult lookupInvokeInterface(DexMethod method, ProgramMethod context) {
    return lookupMethod(method, context.getReference(), Type.INTERFACE);
  }

  public final MethodLookupResult lookupInvokeInterface(
      DexMethod method, ProgramMethod context, GraphLens codeLens) {
    return lookupMethod(method, context.getReference(), Type.INTERFACE, codeLens);
  }

  public final MethodLookupResult lookupInvokeStatic(DexMethod method, ProgramMethod context) {
    return lookupMethod(method, context.getReference(), Type.STATIC);
  }

  public final MethodLookupResult lookupInvokeStatic(
      DexMethod method, ProgramMethod context, GraphLens codeLens) {
    return lookupMethod(method, context.getReference(), Type.STATIC, codeLens);
  }

  public final MethodLookupResult lookupInvokeSuper(DexMethod method, ProgramMethod context) {
    return lookupMethod(method, context.getReference(), Type.SUPER);
  }

  public final MethodLookupResult lookupInvokeSuper(
      DexMethod method, ProgramMethod context, GraphLens codeLens) {
    return lookupMethod(method, context.getReference(), Type.SUPER, codeLens);
  }

  public final MethodLookupResult lookupInvokeVirtual(DexMethod method, ProgramMethod context) {
    return lookupMethod(method, context.getReference(), Type.VIRTUAL);
  }

  public final MethodLookupResult lookupInvokeVirtual(
      DexMethod method, ProgramMethod context, GraphLens codeLens) {
    return lookupMethod(method, context.getReference(), Type.VIRTUAL, codeLens);
  }

  public final MethodLookupResult lookupMethod(DexMethod method, DexMethod context, Type type) {
    return lookupMethod(method, context, type, null);
  }

  /**
   * Lookup a rebound or non-rebound method reference using the current graph lens.
   *
   * @param codeLens Specifies the graph lens which has already been applied to the code object. The
   *     lookup procedure will not recurse beyond this graph lens to ensure that each mapping is
   *     applied at most once.
   *     <p>Note: since the compiler currently inserts {@link ClearCodeRewritingGraphLens} it is
   *     generally valid to pass null for the {@param codeLens}. The removal of {@link
   *     ClearCodeRewritingGraphLens} is tracked by b/202368283. After this is removed, the compiler
   *     should generally use the result of calling {@link AppView#codeLens()}.
   */
  public abstract MethodLookupResult lookupMethod(
      DexMethod method, DexMethod context, Type type, GraphLens codeLens);

  protected abstract MethodLookupResult internalLookupMethod(
      DexMethod reference,
      DexMethod context,
      Type type,
      GraphLens codeLens,
      LookupMethodContinuation continuation);

  interface LookupMethodContinuation {

    MethodLookupResult lookupMethod(MethodLookupResult previous);
  }

  public final RewrittenPrototypeDescription lookupPrototypeChangesForMethodDefinition(
      DexMethod method) {
    return lookupPrototypeChangesForMethodDefinition(method, null);
  }

  public abstract RewrittenPrototypeDescription lookupPrototypeChangesForMethodDefinition(
      DexMethod method, GraphLens codeLens);

  public final DexField lookupField(DexField field) {
    return lookupField(field, null);
  }

  /** Lookup a rebound or non-rebound field reference using the current graph lens. */
  public DexField lookupField(DexField field, GraphLens codeLens) {
    // Lookup the field using the graph lens and return the (non-rebound) reference from the lookup
    // result.
    return lookupFieldResult(field, codeLens).getReference();
  }

  /** Lookup a rebound or non-rebound field reference using the current graph lens. */
  public final FieldLookupResult lookupFieldResult(DexField field) {
    // Lookup the field using the graph lens and return the lookup result.
    return lookupFieldResult(field, null);
  }

  /** Lookup a rebound or non-rebound field reference using the current graph lens. */
  public final FieldLookupResult lookupFieldResult(DexField field, GraphLens codeLens) {
    // Lookup the field using the graph lens and return the lookup result.
    return internalLookupField(field, codeLens, x -> x);
  }

  protected abstract FieldLookupResult internalLookupField(
      DexField reference, GraphLens codeLens, LookupFieldContinuation continuation);

  interface LookupFieldContinuation {

    FieldLookupResult lookupField(FieldLookupResult previous);
  }

  public DexReference lookupReference(DexReference reference) {
    return reference.apply(this::lookupType, this::lookupField, this::lookupMethod);
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

  public boolean hasCustomCodeRewritings() {
    return false;
  }

  public boolean isAppliedLens() {
    return false;
  }

  public boolean isArgumentPropagatorGraphLens() {
    return false;
  }

  public boolean isClearCodeRewritingLens() {
    return false;
  }

  public boolean isEnumUnboxerLens() {
    return false;
  }

  public EnumUnboxingLens asEnumUnboxerLens() {
    return null;
  }

  public boolean isHorizontalClassMergerGraphLens() {
    return false;
  }

  public boolean isSimpleRenamingLens() {
    return false;
  }

  public abstract boolean isIdentityLens();

  public boolean isMemberRebindingLens() {
    return false;
  }

  public MemberRebindingLens asMemberRebindingLens() {
    return null;
  }

  public boolean isMemberRebindingIdentityLens() {
    return false;
  }

  public MemberRebindingIdentityLens asMemberRebindingIdentityLens() {
    return null;
  }

  public abstract boolean isNonIdentityLens();

  public NonIdentityGraphLens asNonIdentityLens() {
    return null;
  }

  public boolean isVerticalClassMergerLens() {
    return false;
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

  public <T extends DexReference> boolean assertPinnedNotModified(
      KeepInfoCollection keepInfo, InternalOptions options) {
    List<DexReference> pinnedItems = new ArrayList<>();
    keepInfo.forEachPinnedType(pinnedItems::add, options);
    keepInfo.forEachPinnedMethod(pinnedItems::add, options);
    keepInfo.forEachPinnedField(pinnedItems::add, options);
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
    LensCodeRewriterUtils rewriter = new LensCodeRewriterUtils(definitions, this, null);
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
    return rewriteReference(reference, null);
  }

  @SuppressWarnings("unchecked")
  public <T extends DexReference> T rewriteReference(T reference, GraphLens codeLens) {
    return (T)
        reference.apply(
            type -> lookupType(type, codeLens),
            field -> getRenamedFieldSignature(field, codeLens),
            method -> getRenamedMethodSignature(method, codeLens));
  }

  public <T extends DexReference> Set<T> rewriteReferences(Set<T> references) {
    Set<T> result = SetUtils.newIdentityHashSet(references.size());
    for (T reference : references) {
      result.add(rewriteReference(reference));
    }
    return result;
  }

  public <R extends DexReference, T> Map<R, T> rewriteReferenceKeys(
      Map<R, T> map, BiFunction<R, List<T>, T> merge) {
    Map<R, T> result = new IdentityHashMap<>();
    Map<R, List<T>> needsMerge = new IdentityHashMap<>();
    map.forEach(
        (reference, value) -> {
          R rewrittenReference = rewriteReference(reference);
          List<T> unmergedValues = needsMerge.get(rewrittenReference);
          if (unmergedValues != null) {
            unmergedValues.add(value);
          } else {
            T existingValue = result.put(rewrittenReference, value);
            if (existingValue != null) {
              // Remove this for now and let the merge function decide when all colliding values are
              // known.
              needsMerge.put(rewrittenReference, ListUtils.newArrayList(existingValue, value));
              result.remove(rewrittenReference);
            }
          }
        });
    needsMerge.forEach(
        (rewrittenReference, unmergedValues) -> {
          T mergedValue = merge.apply(rewrittenReference, unmergedValues);
          if (mergedValue != null) {
            result.put(rewrittenReference, mergedValue);
          }
        });
    return result;
  }

  public <T extends DexReference> Object2BooleanMap<T> rewriteReferenceKeys(
      Object2BooleanMap<T> map) {
    Object2BooleanMap<T> result = new Object2BooleanArrayMap<>();
    for (Object2BooleanMap.Entry<T> entry : map.object2BooleanEntrySet()) {
      result.put(rewriteReference(entry.getKey()), entry.getBooleanValue());
    }
    return result;
  }

  public <T> ImmutableMap<DexField, T> rewriteFieldKeys(Map<DexField, T> map) {
    ImmutableMap.Builder<DexField, T> builder = ImmutableMap.builder();
    map.forEach((field, value) -> builder.put(getRenamedFieldSignature(field), value));
    return builder.build();
  }

  public ImmutableSet<DexType> rewriteTypes(Set<DexType> types) {
    ImmutableSet.Builder<DexType> builder = new ImmutableSet.Builder<>();
    for (DexType type : types) {
      builder.add(lookupType(type));
    }
    return builder.build();
  }

  public <T> Map<DexType, T> rewriteTypeKeys(Map<DexType, T> map, BiFunction<T, T, T> merge) {
    Map<DexType, T> newMap = new IdentityHashMap<>();
    map.forEach(
        (type, value) -> {
          DexType rewrittenType = lookupType(type);
          T previousValue = newMap.get(rewrittenType);
          newMap.put(
              rewrittenType, previousValue != null ? merge.apply(value, previousValue) : value);
        });
    return newMap;
  }

  public boolean verifyMappingToOriginalProgram(
      AppView<?> appView, DexApplication originalApplication) {
    Iterable<DexProgramClass> classes = appView.appInfo().classesWithDeterministicOrder();
    // Collect all original fields and methods for efficient querying.
    Set<DexField> originalFields = Sets.newIdentityHashSet();
    Set<DexMethod> originalMethods = Sets.newIdentityHashSet();
    for (DexProgramClass clazz : originalApplication.classes()) {
      for (DexEncodedField field : clazz.fields()) {
        originalFields.add(field.getReference());
      }
      for (DexEncodedMethod method : clazz.methods()) {
        originalMethods.add(method.getReference());
      }
    }

    // Check that all fields and methods in the generated program can be mapped back to one of the
    // original fields or methods.
    for (DexProgramClass clazz : classes) {
      if (appView.appInfo().getSyntheticItems().isSyntheticClass(clazz)) {
        continue;
      }
      for (DexEncodedField field : clazz.fields()) {
        if (field.isD8R8Synthesized()) {
          // Fields synthesized by D8/R8 may not be mapped.
          continue;
        }
        DexField originalField = getOriginalFieldSignature(field.getReference());
        assert originalFields.contains(originalField)
            : "Unable to map field `"
                + field.getReference().toSourceString()
                + "` back to original program";
      }
      for (DexEncodedMethod method : clazz.methods()) {
        if (method.isD8R8Synthesized()) {
          // Methods synthesized by D8/R8 may not be mapped.
          continue;
        }
        DexMethod originalMethod = getOriginalMethodSignature(method.getReference());
        assert originalMethods.contains(originalMethod)
            : "Method could not be mapped back: "
                + method.toSourceString()
                + ", originalMethod: "
                + originalMethod.toSourceString();
      }
    }

    return true;
  }

  public abstract static class NonIdentityGraphLens extends GraphLens {

    private final DexItemFactory dexItemFactory;
    private GraphLens previousLens;

    private final Map<DexType, DexType> arrayTypeCache = new ConcurrentHashMap<>();

    public NonIdentityGraphLens(AppView<?> appView) {
      this(appView.dexItemFactory(), appView.graphLens());
    }

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
    public final <T extends NonIdentityGraphLens> T find(
        Predicate<NonIdentityGraphLens> predicate) {
      GraphLens current = this;
      while (current.isNonIdentityLens()) {
        NonIdentityGraphLens nonIdentityGraphLens = current.asNonIdentityLens();
        if (predicate.test(nonIdentityGraphLens)) {
          return (T) nonIdentityGraphLens;
        }
        current = nonIdentityGraphLens.getPrevious();
      }
      return null;
    }

    @SuppressWarnings("unchecked")
    public final <T extends NonIdentityGraphLens> T findPrevious(
        Predicate<NonIdentityGraphLens> predicate) {
      GraphLens previous = getPrevious();
      return previous.isNonIdentityLens() ? previous.asNonIdentityLens().find(predicate) : null;
    }

    public final <T extends NonIdentityGraphLens> T findPreviousUntil(
        Predicate<NonIdentityGraphLens> predicate,
        Predicate<NonIdentityGraphLens> stoppingCriterion) {
      T found = findPrevious(predicate.or(stoppingCriterion));
      return (found == null || stoppingCriterion.test(found)) ? null : found;
    }

    public final void withAlternativeParentLens(GraphLens lens, Action action) {
      GraphLens oldParent = getPrevious();
      previousLens = lens;
      action.execute();
      previousLens = oldParent;
    }

    @Override
    public MethodLookupResult lookupMethod(
        DexMethod method, DexMethod context, Type type, GraphLens codeLens) {
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
      return internalLookupMethod(method, context, type, codeLens, result -> result);
    }

    @Override
    public String lookupPackageName(String pkg) {
      return getPrevious().lookupPackageName(pkg);
    }

    @Override
    public final DexType lookupType(DexType type, GraphLens applied) {
      if (this == applied) {
        return type;
      }
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
    public final DexType lookupClassType(DexType type, GraphLens applied) {
      assert type.isClassType() : "Expected class type, but was `" + type.toSourceString() + "`";
      if (this == applied) {
        return type;
      }
      return internalDescribeLookupClassType(getPrevious().lookupClassType(type));
    }

    @Override
    protected FieldLookupResult internalLookupField(
        DexField reference, GraphLens codeLens, LookupFieldContinuation continuation) {
      if (this == codeLens) {
        return getIdentityLens().internalLookupField(reference, codeLens, continuation);
      }
      return previousLens.internalLookupField(
          reference,
          codeLens,
          previous -> continuation.lookupField(internalDescribeLookupField(previous)));
    }

    @Override
    protected MethodLookupResult internalLookupMethod(
        DexMethod reference,
        DexMethod context,
        Type type,
        GraphLens codeLens,
        LookupMethodContinuation continuation) {
      if (this == codeLens) {
        GraphLens identityLens = getIdentityLens();
        return identityLens.internalLookupMethod(
            reference, context, type, identityLens, continuation);
      }
      return previousLens.internalLookupMethod(
          reference,
          getPreviousMethodSignature(context),
          type,
          codeLens,
          previous -> continuation.lookupMethod(internalDescribeLookupMethod(previous, context)));
    }

    protected abstract FieldLookupResult internalDescribeLookupField(FieldLookupResult previous);

    protected abstract MethodLookupResult internalDescribeLookupMethod(
        MethodLookupResult previous, DexMethod context);

    protected abstract DexType internalDescribeLookupClassType(DexType previous);

    public abstract DexMethod getPreviousMethodSignature(DexMethod method);

    /***
     * The previous mapping for a method often coincides with the previous method signature, but it
     * may not, for example for bridges inserted in vertically merged classes where the original
     * signature is used for computing invoke-super but should not be used for mapping output.
     */
    public DexMethod getPreviousMethodSignatureForMapping(DexMethod method) {
      return getPreviousMethodSignature(method);
    }

    public abstract DexMethod getNextMethodSignature(DexMethod method);

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
    public DexField getRenamedFieldSignature(DexField originalField, GraphLens codeLens) {
      return originalField;
    }

    @Override
    public DexMethod getRenamedMethodSignature(DexMethod originalMethod, GraphLens applied) {
      return originalMethod;
    }

    @Override
    public String lookupPackageName(String pkg) {
      return pkg;
    }

    @Override
    public DexType lookupType(DexType type, GraphLens applied) {
      return type;
    }

    @Override
    public DexType lookupClassType(DexType type, GraphLens applied) {
      assert type.isClassType();
      return type;
    }

    @Override
    public MethodLookupResult lookupMethod(
        DexMethod method, DexMethod context, Type type, GraphLens codeLens) {
      assert codeLens == null || codeLens.isIdentityLens();
      return MethodLookupResult.builder(this).setReference(method).setType(type).build();
    }

    @Override
    public RewrittenPrototypeDescription lookupPrototypeChangesForMethodDefinition(
        DexMethod method, GraphLens codeLens) {
      return RewrittenPrototypeDescription.none();
    }

    @Override
    protected FieldLookupResult internalLookupField(
        DexField reference, GraphLens codeLens, LookupFieldContinuation continuation) {
      // Passes the field reference back to the next graph lens. The identity lens intentionally
      // does not set the rebound field reference, since it does not know what that is.
      return continuation.lookupField(
          FieldLookupResult.builder(this).setReference(reference).build());
    }

    @Override
    protected MethodLookupResult internalLookupMethod(
        DexMethod reference,
        DexMethod context,
        Type type,
        GraphLens codeLens,
        LookupMethodContinuation continuation) {
      // Passes the method reference back to the next graph lens. The identity lens intentionally
      // does not set the rebound method reference, since it does not know what that is.
      return continuation.lookupMethod(lookupMethod(reference, context, type, codeLens));
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
    public DexField getRenamedFieldSignature(DexField originalField, GraphLens codeLens) {
      return this != codeLens
          ? getPrevious().getRenamedFieldSignature(originalField)
          : originalField;
    }

    @Override
    public DexMethod getRenamedMethodSignature(DexMethod originalMethod, GraphLens applied) {
      return this != applied
          ? getPrevious().getRenamedMethodSignature(originalMethod, applied)
          : originalMethod;
    }

    @Override
    public boolean isClearCodeRewritingLens() {
      return true;
    }

    @Override
    public RewrittenPrototypeDescription lookupPrototypeChangesForMethodDefinition(
        DexMethod method, GraphLens codeLens) {
      return getIdentityLens().lookupPrototypeChangesForMethodDefinition(method, codeLens);
    }

    @Override
    public final DexType internalDescribeLookupClassType(DexType previous) {
      return previous;
    }

    @Override
    protected FieldLookupResult internalLookupField(
        DexField reference, GraphLens codeLens, LookupFieldContinuation continuation) {
      return getIdentityLens().internalLookupField(reference, codeLens, continuation);
    }

    @Override
    protected FieldLookupResult internalDescribeLookupField(FieldLookupResult previous) {
      throw new Unreachable();
    }

    @Override
    protected MethodLookupResult internalLookupMethod(
        DexMethod reference,
        DexMethod context,
        Type type,
        GraphLens codeLens,
        LookupMethodContinuation continuation) {
      assert codeLens == null || codeLens == this;
      GraphLens identityLens = getIdentityLens();
      return identityLens.internalLookupMethod(
          reference, context, type, identityLens, continuation);
    }

    @Override
    public MethodLookupResult internalDescribeLookupMethod(
        MethodLookupResult previous, DexMethod context) {
      throw new Unreachable();
    }

    @Override
    public DexMethod getPreviousMethodSignature(DexMethod method) {
      return method;
    }

    @Override
    public DexMethod getNextMethodSignature(DexMethod method) {
      return method;
    }

    @Override
    public boolean isContextFreeForMethods() {
      return getIdentityLens().isContextFreeForMethods();
    }
  }
}
