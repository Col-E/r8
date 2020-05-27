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
import java.util.Collection;
import java.util.IdentityHashMap;
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

    MutableKeepInfoCollection() {
      this(new IdentityHashMap<>(), new IdentityHashMap<>(), new IdentityHashMap<>());
    }

    private MutableKeepInfoCollection(
        Map<DexType, KeepClassInfo> keepClassInfo,
        Map<DexMethod, KeepMethodInfo> keepMethodInfo,
        Map<DexField, KeepFieldInfo> keepFieldInfo) {
      this.keepClassInfo = keepClassInfo;
      this.keepMethodInfo = keepMethodInfo;
      this.keepFieldInfo = keepFieldInfo;
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
      return new MutableKeepInfoCollection(newClassInfo, newMethodInfo, newFieldInfo);
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

    public void pinClass(DexProgramClass clazz) {
      KeepClassInfo info = getClassInfo(clazz);
      if (!info.isPinned()) {
        keepClassInfo.put(clazz.type, info.builder().pin().build());
      }
    }

    public void pinMember(DexProgramClass holder, DexEncodedMember<?, ?> member) {
      if (member.isDexEncodedMethod()) {
        pinMethod(holder, member.asDexEncodedMethod());
      } else {
        assert member.isDexEncodedField();
        pinField(holder, member.asDexEncodedField());
      }
    }

    public void pinMethod(ProgramMethod programMethod) {
      pinMethod(programMethod.getHolder(), programMethod.getDefinition());
    }

    public void pinMethod(DexProgramClass holder, DexEncodedMethod method) {
      KeepMethodInfo info = getMethodInfo(method, holder);
      if (!info.isPinned()) {
        keepMethodInfo.put(method.method, info.builder().pin().build());
      }
    }

    public void unpinMethod(ProgramMethod method) {
      assert !getClassInfo(method.getHolder()).isPinned();
      unpinMethod(method.getReference());
    }

    // TODO(b/156715504): We should never need to unpin items. Rather avoid pinning to begin with.
    @Deprecated
    public void unpinMethod(DexMethod method) {
      KeepMethodInfo info = keepMethodInfo.get(method);
      if (info != null && info.isPinned()) {
        keepMethodInfo.put(method, info.builder().unpin().build());
      }
    }

    public void pinField(ProgramField programField) {
      pinField(programField.getHolder(), programField.getDefinition());
    }

    public void pinField(DexProgramClass holder, DexEncodedField field) {
      KeepFieldInfo info = getFieldInfo(field, holder);
      if (!info.isPinned()) {
        keepFieldInfo.put(field.field, info.builder().pin().build());
      }
    }

    public void unpinField(DexProgramClass holder, DexEncodedField field) {
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
