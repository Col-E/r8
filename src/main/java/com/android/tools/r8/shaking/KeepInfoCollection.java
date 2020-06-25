// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense.NestedGraphLense;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.KeepFieldInfo.Joiner;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

// Non-mutable collection of keep information pertaining to a program.
public abstract class KeepInfoCollection {

  // TODO(b/157538235): This should not be bottom.
  private static KeepClassInfo keepInfoForNonProgramClass() {
    return KeepClassInfo.bottom();
  }

  // TODO(b/157538235): This should not be bottom.
  private static KeepMethodInfo keepInfoForNonProgramMethod() {
    return KeepMethodInfo.bottom();
  }

  // TODO(b/157538235): This should not be bottom.
  private static KeepFieldInfo keepInfoForNonProgramField() {
    return KeepFieldInfo.bottom();
  }

  abstract Map<DexReference, List<Consumer<KeepInfo.Joiner<?, ?, ?>>>> getRuleInstances();

  /**
   * Base accessor for keep info on a class.
   *
   * <p>Access may never be granted directly on DexType as the "keep info" for any non-program type
   * is not the same as the default keep info for a program type. By typing the interface at program
   * item we can eliminate errors where a reference to a non-program item results in optimizations
   * assuming aspects of it can be changed when in fact they can not.
   */
  public abstract KeepClassInfo getClassInfo(DexProgramClass clazz);

  /**
   * Base accessor for keep info on a method.
   *
   * <p>See comment on class access for why this is typed at program method.
   */
  public abstract KeepMethodInfo getMethodInfo(DexEncodedMethod method, DexProgramClass holder);

  /**
   * Base accessor for keep info on a field.
   *
   * <p>See comment on class access for why this is typed at program field.
   */
  public abstract KeepFieldInfo getFieldInfo(DexEncodedField field, DexProgramClass holder);

  public final KeepClassInfo getClassInfo(DexType type, DexDefinitionSupplier definitions) {
    DexProgramClass clazz = asProgramClassOrNull(definitions.definitionFor(type));
    return clazz == null ? keepInfoForNonProgramClass() : getClassInfo(clazz);
  }

  public final KeepMethodInfo getMethodInfo(ProgramMethod method) {
    return getMethodInfo(method.getDefinition(), method.getHolder());
  }

  public final KeepMethodInfo getMethodInfo(DexMethod method, DexDefinitionSupplier definitions) {
    DexProgramClass holder = asProgramClassOrNull(definitions.definitionFor(method.holder));
    if (holder == null) {
      return keepInfoForNonProgramMethod();
    }
    DexEncodedMethod definition = holder.lookupMethod(method);
    return definition == null ? KeepMethodInfo.bottom() : getMethodInfo(definition, holder);
  }

  public final KeepFieldInfo getFieldInfo(ProgramField field) {
    return getFieldInfo(field.getDefinition(), field.getHolder());
  }

  public final KeepFieldInfo getFieldInfo(DexField field, DexDefinitionSupplier definitions) {
    DexProgramClass holder = asProgramClassOrNull(definitions.definitionFor(field.holder));
    if (holder == null) {
      return keepInfoForNonProgramField();
    }
    DexEncodedField definition = holder.lookupField(field);
    return definition == null ? KeepFieldInfo.bottom() : getFieldInfo(definition, holder);
  }

  public final KeepInfo getInfo(DexReference reference, DexDefinitionSupplier definitions) {
    if (reference.isDexType()) {
      return getClassInfo(reference.asDexType(), definitions);
    }
    if (reference.isDexMethod()) {
      return getMethodInfo(reference.asDexMethod(), definitions);
    }
    if (reference.isDexField()) {
      return getFieldInfo(reference.asDexField(), definitions);
    }
    throw new Unreachable();
  }

  public final boolean isPinned(DexReference reference, DexDefinitionSupplier definitions) {
    return getInfo(reference, definitions).isPinned();
  }

  public final boolean isPinned(DexType type, DexDefinitionSupplier definitions) {
    return getClassInfo(type, definitions).isPinned();
  }

  public final boolean isPinned(DexMethod method, DexDefinitionSupplier definitions) {
    return getMethodInfo(method, definitions).isPinned();
  }

  public final boolean isPinned(DexField field, DexDefinitionSupplier definitions) {
    return getFieldInfo(field, definitions).isPinned();
  }

  public final boolean isMinificationAllowed(
      DexReference reference,
      DexDefinitionSupplier definitions,
      GlobalKeepInfoConfiguration configuration) {
    return configuration.isMinificationEnabled()
        && getInfo(reference, definitions).isMinificationAllowed(configuration);
  }

  public final boolean verifyNoneArePinned(Collection<DexType> types, AppInfo appInfo) {
    for (DexType type : types) {
      DexProgramClass clazz =
          asProgramClassOrNull(appInfo.definitionForWithoutExistenceAssert(type));
      assert clazz == null || !getClassInfo(clazz).isPinned();
    }
    return true;
  }

  // TODO(b/156715504): We should try to avoid the need for iterating pinned items.
  @Deprecated
  public abstract void forEachPinnedType(Consumer<DexType> consumer);

  // TODO(b/156715504): We should try to avoid the need for iterating pinned items.
  @Deprecated
  public abstract void forEachPinnedMethod(Consumer<DexMethod> consumer);

  // TODO(b/156715504): We should try to avoid the need for iterating pinned items.
  @Deprecated
  public abstract void forEachPinnedField(Consumer<DexField> consumer);

  public abstract KeepInfoCollection rewrite(NestedGraphLense lens);

  public abstract KeepInfoCollection mutate(Consumer<MutableKeepInfoCollection> mutator);

  // Mutation interface for building up the keep info.
  public static class MutableKeepInfoCollection extends KeepInfoCollection {

    // These are typed at signatures but the interface should make sure never to allow access
    // directly with a signature. See the comment in KeepInfoCollection.
    private final Map<DexType, KeepClassInfo> keepClassInfo;
    private final Map<DexMethod, KeepMethodInfo> keepMethodInfo;
    private final Map<DexField, KeepFieldInfo> keepFieldInfo;

    // Map of applied rules for which keys may need to be mutated.
    private final Map<DexReference, List<Consumer<KeepInfo.Joiner<?, ?, ?>>>> ruleInstances;

    MutableKeepInfoCollection() {
      this(
          new IdentityHashMap<>(),
          new IdentityHashMap<>(),
          new IdentityHashMap<>(),
          new IdentityHashMap<>());
    }

    private MutableKeepInfoCollection(
        Map<DexType, KeepClassInfo> keepClassInfo,
        Map<DexMethod, KeepMethodInfo> keepMethodInfo,
        Map<DexField, KeepFieldInfo> keepFieldInfo,
        Map<DexReference, List<Consumer<KeepInfo.Joiner<?, ?, ?>>>> ruleInstances) {
      this.keepClassInfo = keepClassInfo;
      this.keepMethodInfo = keepMethodInfo;
      this.keepFieldInfo = keepFieldInfo;
      this.ruleInstances = ruleInstances;
    }

    @Override
    public KeepInfoCollection rewrite(NestedGraphLense lens) {
      Map<DexType, KeepClassInfo> newClassInfo = new IdentityHashMap<>(keepClassInfo.size());
      keepClassInfo.forEach(
          (type, info) -> {
            DexType newType = lens.lookupType(type);
            assert !info.isPinned() || type == newType;
            newClassInfo.put(newType, info);
          });
      Map<DexMethod, KeepMethodInfo> newMethodInfo = new IdentityHashMap<>(keepMethodInfo.size());
      keepMethodInfo.forEach(
          (method, info) -> {
            DexMethod newMethod = lens.getRenamedMethodSignature(method);
            assert !info.isPinned() || method == newMethod;
            newMethodInfo.put(newMethod, info);
          });
      Map<DexField, KeepFieldInfo> newFieldInfo = new IdentityHashMap<>(keepFieldInfo.size());
      keepFieldInfo.forEach(
          (field, info) -> {
            DexField newField = lens.getRenamedFieldSignature(field);
            assert !info.isPinned() || field == newField;
            newFieldInfo.put(newField, info);
          });
      Map<DexReference, List<Consumer<KeepInfo.Joiner<?, ?, ?>>>> newRuleInstances =
          new IdentityHashMap<>(ruleInstances.size());
      ruleInstances.forEach(
          (reference, consumers) -> {
            DexReference newReference;
            if (reference.isDexType()) {
              DexType newType = lens.lookupType(reference.asDexType());
              if (!newType.isClassType()) {
                assert newType.isIntType() : "Expected only enum unboxing type changes.";
                return;
              }
              newReference = newType;
            } else if (reference.isDexMethod()) {
              newReference = lens.getRenamedMethodSignature(reference.asDexMethod());
            } else {
              assert reference.isDexField();
              newReference = lens.getRenamedFieldSignature(reference.asDexField());
            }
            newRuleInstances.put(newReference, consumers);
          });
      return new MutableKeepInfoCollection(
          newClassInfo, newMethodInfo, newFieldInfo, newRuleInstances);
    }

    @Override
    Map<DexReference, List<Consumer<KeepInfo.Joiner<?, ?, ?>>>> getRuleInstances() {
      return ruleInstances;
    }

    void evaluateRule(
        DexReference reference,
        DexDefinitionSupplier definitions,
        Consumer<KeepInfo.Joiner<?, ?, ?>> fn) {
      joinInfo(reference, definitions, fn);
      if (!getInfo(reference, definitions).isBottom()) {
        ruleInstances.computeIfAbsent(reference, k -> new ArrayList<>()).add(fn);
      }
    }

    @Override
    public KeepClassInfo getClassInfo(DexProgramClass clazz) {
      return keepClassInfo.getOrDefault(clazz.type, KeepClassInfo.bottom());
    }

    @Override
    public KeepMethodInfo getMethodInfo(DexEncodedMethod method, DexProgramClass holder) {
      assert method.holder() == holder.type;
      return keepMethodInfo.getOrDefault(method.method, KeepMethodInfo.bottom());
    }

    @Override
    public KeepFieldInfo getFieldInfo(DexEncodedField field, DexProgramClass holder) {
      assert field.holder() == holder.type;
      return keepFieldInfo.getOrDefault(field.field, KeepFieldInfo.bottom());
    }

    public void joinClass(DexProgramClass clazz, Consumer<KeepClassInfo.Joiner> fn) {
      KeepClassInfo info = getClassInfo(clazz);
      if (info.isTop()) {
        return;
      }
      KeepClassInfo.Joiner joiner = info.joiner();
      fn.accept(joiner);
      KeepClassInfo joined = joiner.join();
      if (!info.equals(joined)) {
        keepClassInfo.put(clazz.type, joined);
      }
    }

    public void joinInfo(
        DexReference reference,
        DexDefinitionSupplier definitions,
        Consumer<KeepInfo.Joiner<?, ?, ?>> fn) {
      if (reference.isDexType()) {
        DexType type = reference.asDexType();
        DexProgramClass clazz = asProgramClassOrNull(definitions.definitionFor(type));
        if (clazz != null) {
          joinClass(clazz, fn::accept);
        }
      } else if (reference.isDexMethod()) {
        DexMethod method = reference.asDexMethod();
        DexProgramClass clazz = asProgramClassOrNull(definitions.definitionFor(method.holder));
        DexEncodedMethod definition = method.lookupOnClass(clazz);
        if (definition != null) {
          joinMethod(clazz, definition, fn::accept);
        }
      } else {
        assert reference.isDexField();
        DexField field = reference.asDexField();
        DexProgramClass clazz = asProgramClassOrNull(definitions.definitionFor(field.holder));
        DexEncodedField definition = field.lookupOnClass(clazz);
        if (definition != null) {
          joinField(clazz, definition, fn::accept);
        }
      }
    }

    public void keepClass(DexProgramClass clazz) {
      joinClass(clazz, KeepInfo.Joiner::top);
    }

    public void pinClass(DexProgramClass clazz) {
      joinClass(clazz, KeepInfo.Joiner::pin);
    }

    public void joinMethod(
        DexProgramClass holder, DexEncodedMethod method, Consumer<KeepMethodInfo.Joiner> fn) {
      KeepMethodInfo info = getMethodInfo(method, holder);
      if (info == KeepMethodInfo.top()) {
        return;
      }
      KeepMethodInfo.Joiner joiner = info.joiner();
      fn.accept(joiner);
      KeepMethodInfo joined = joiner.join();
      if (!info.equals(joined)) {
        keepMethodInfo.put(method.method, joined);
      }
    }

    public void joinMethod(ProgramMethod programMethod, Consumer<KeepMethodInfo.Joiner> fn) {
      joinMethod(programMethod.getHolder(), programMethod.getDefinition(), fn);
    }

    public void keepMethod(ProgramMethod programMethod) {
      keepMethod(programMethod.getHolder(), programMethod.getDefinition());
    }

    public void keepMethod(DexProgramClass holder, DexEncodedMethod method) {
      joinMethod(holder, method, KeepInfo.Joiner::top);
    }

    public void pinMethod(DexProgramClass holder, DexEncodedMethod method) {
      joinMethod(holder, method, KeepInfo.Joiner::pin);
    }

    // Unpinning a method represents a non-monotonic change to the keep info of that item.
    // This is generally unsound as it requires additional analysis to determine that a method that
    // was pinned no longer is. A known sound example is the enum analysis that will identify
    // non-escaping enums on enum types that are not pinned, thus their methods do not need to be
    // retained even if a rule has marked them as conditionally pinned.
    public void unsafeUnpinMethod(ProgramMethod method) {
      // This asserts that the holder is not pinned as some analysis must have established that the
      // type is not "present" and thus the method need not be pinned.
      assert !getClassInfo(method.getHolder()).isPinned();
      unsafeUnpinMethod(method.getReference());
    }

    // TODO(b/157700141): Avoid pinning/unpinning references.
    @Deprecated
    public void unsafeUnpinMethod(DexMethod method) {
      KeepMethodInfo info = keepMethodInfo.get(method);
      if (info != null && info.isPinned()) {
        keepMethodInfo.put(method, info.builder().unpin().build());
      }
    }

    public void joinField(
        DexProgramClass holder, DexEncodedField field, Consumer<KeepFieldInfo.Joiner> fn) {
      KeepFieldInfo info = getFieldInfo(field, holder);
      if (info.isTop()) {
        return;
      }
      Joiner joiner = info.joiner();
      fn.accept(joiner);
      KeepFieldInfo joined = joiner.join();
      if (!info.equals(joined)) {
        keepFieldInfo.put(field.field, joined);
      }
    }

    public void keepField(ProgramField programField) {
      keepField(programField.getHolder(), programField.getDefinition());
    }

    public void keepField(DexProgramClass holder, DexEncodedField field) {
      joinField(holder, field, KeepInfo.Joiner::top);
    }

    public void pinField(DexProgramClass holder, DexEncodedField field) {
      joinField(holder, field, KeepInfo.Joiner::pin);
    }

    public void keepMember(DexProgramClass holder, DexEncodedMember<?, ?> member) {
      if (member.isDexEncodedMethod()) {
        keepMethod(holder, member.asDexEncodedMethod());
      } else {
        assert member.isDexEncodedField();
        keepField(holder, member.asDexEncodedField());
      }
    }

    public void unsafeUnpinField(DexProgramClass holder, DexEncodedField field) {
      assert holder.type == field.holder();
      assert !getClassInfo(holder).isPinned();
      KeepFieldInfo info = this.keepFieldInfo.get(field.toReference());
      if (info != null && info.isPinned()) {
        keepFieldInfo.put(field.toReference(), info.builder().unpin().build());
      }
    }

    @Override
    public KeepInfoCollection mutate(Consumer<MutableKeepInfoCollection> mutator) {
      mutator.accept(this);
      return this;
    }

    @Override
    public void forEachPinnedType(Consumer<DexType> consumer) {
      keepClassInfo.forEach(
          (type, info) -> {
            if (info.isPinned()) {
              consumer.accept(type);
            }
          });
    }

    @Override
    public void forEachPinnedMethod(Consumer<DexMethod> consumer) {
      keepMethodInfo.forEach(
          (method, info) -> {
            if (info.isPinned()) {
              consumer.accept(method);
            }
          });
    }

    @Override
    public void forEachPinnedField(Consumer<DexField> consumer) {
      keepFieldInfo.forEach(
          (field, info) -> {
            if (info.isPinned()) {
              consumer.accept(field);
            }
          });
    }
  }
}
