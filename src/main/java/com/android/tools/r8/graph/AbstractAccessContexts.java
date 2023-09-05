// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.utils.MapUtils;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * For a concrete field, stores the contexts in which the field is accessed.
 *
 * <p>If the concrete field does not have any accesses, then {@link EmptyAccessContexts}.
 *
 * <p>If nothing is nothing about the accesses to the concrete field, then {@link
 * UnknownAccessContexts}.
 *
 * <p>Otherwise, the concrete contexts in which the field is accessed is maintained by {@link
 * ConcreteAccessContexts}. The access contexts are qualified by the field reference they access.
 *
 * <p>Example: If a field `int Foo.field` is accessed directly in `void Main.direct()` and
 * indirectly via a non-rebound reference `int FooSub.field` in `void Main.indirect()`, then the
 * collection is:
 *
 * <pre>
 *   ConcreteAccessContexts {
 *     `int Foo.field` -> { `void Main.direct()` }
 *     `int FooSub.field` -> { `void Main.indirect()` }
 *   }
 * </pre>
 */
public abstract class AbstractAccessContexts {

  abstract void flattenAccessContexts(DexField field);

  abstract void forEachAccessContext(Consumer<ProgramMethod> consumer);

  /**
   * Returns true if this field is written by a method for which {@param predicate} returns true.
   */
  abstract boolean isAccessedInMethodSatisfying(Predicate<ProgramMethod> predicate);

  /**
   * Returns true if this field is only written by methods for which {@param predicate} returns
   * true.
   */
  abstract boolean isAccessedOnlyInMethodSatisfying(Predicate<ProgramMethod> predicate);

  /**
   * Returns true if this field is written by a method in the program other than {@param method}.
   */
  abstract boolean isAccessedOutside(DexEncodedMethod method);

  abstract int getNumberOfAccessContexts();

  public final boolean hasAccesses() {
    return !isEmpty();
  }

  public boolean isBottom() {
    return false;
  }

  public boolean isConcrete() {
    return false;
  }

  public abstract boolean isEmpty();

  public ConcreteAccessContexts asConcrete() {
    return null;
  }

  public boolean isTop() {
    return false;
  }

  abstract AbstractAccessContexts rewrittenWithLens(
      DexDefinitionSupplier definitions, GraphLens lens);

  public static EmptyAccessContexts empty() {
    return EmptyAccessContexts.getInstance();
  }

  public static UnknownAccessContexts unknown() {
    return UnknownAccessContexts.getInstance();
  }

  public abstract AbstractAccessContexts join(AbstractAccessContexts contexts);

  public static class EmptyAccessContexts extends AbstractAccessContexts {

    public static EmptyAccessContexts INSTANCE = new EmptyAccessContexts();

    private EmptyAccessContexts() {}

    public static EmptyAccessContexts getInstance() {
      return INSTANCE;
    }

    @Override
    void flattenAccessContexts(DexField field) {
      // Intentionally empty.
    }

    @Override
    void forEachAccessContext(Consumer<ProgramMethod> consumer) {
      // Intentionally empty.
    }

    @Override
    boolean isAccessedInMethodSatisfying(Predicate<ProgramMethod> predicate) {
      return false;
    }

    @Override
    boolean isAccessedOnlyInMethodSatisfying(Predicate<ProgramMethod> predicate) {
      return true;
    }

    @Override
    boolean isAccessedOutside(DexEncodedMethod method) {
      return false;
    }

    @Override
    int getNumberOfAccessContexts() {
      return 0;
    }

    @Override
    public boolean isBottom() {
      return true;
    }

    @Override
    public boolean isEmpty() {
      return true;
    }

    @Override
    AbstractAccessContexts rewrittenWithLens(DexDefinitionSupplier definitions, GraphLens lens) {
      return this;
    }

    @Override
    public AbstractAccessContexts join(AbstractAccessContexts contexts) {
      return contexts;
    }
  }

  public static class ConcreteAccessContexts extends AbstractAccessContexts {

    private final Map<DexField, ProgramMethodSet> accessesWithContexts;

    public ConcreteAccessContexts() {
      this(new IdentityHashMap<>());
    }

    public ConcreteAccessContexts(Map<DexField, ProgramMethodSet> accessesWithContexts) {
      this.accessesWithContexts = accessesWithContexts;
    }

    void forEachAccess(Consumer<DexField> consumer, Predicate<DexField> predicate) {
      if (accessesWithContexts != null) {
        accessesWithContexts.forEach(
            (access, contexts) -> {
              if (predicate.test(access)) {
                consumer.accept(access);
              }
            });
      }
    }

    @Override
    void forEachAccessContext(Consumer<ProgramMethod> consumer) {
      // There can be indirect reads and writes of the same field reference, so we need to keep
      // track
      // of the previously-seen indirect accesses to avoid reporting duplicates.
      ProgramMethodSet visited = ProgramMethodSet.create();
      if (accessesWithContexts != null) {
        for (ProgramMethodSet encodedAccessContexts : accessesWithContexts.values()) {
          for (ProgramMethod encodedAccessContext : encodedAccessContexts) {
            if (visited.add(encodedAccessContext)) {
              consumer.accept(encodedAccessContext);
            }
          }
        }
      }
    }

    public Map<DexField, ProgramMethodSet> getAccessesWithContexts() {
      return accessesWithContexts;
    }

    @Override
    int getNumberOfAccessContexts() {
      if (accessesWithContexts.size() == 1) {
        return accessesWithContexts.values().iterator().next().size();
      }
      throw new Unreachable(
          "Should only be querying the number of access contexts after flattening");
    }

    ProgramMethod getUniqueAccessContext() {
      if (accessesWithContexts != null && accessesWithContexts.size() == 1) {
        ProgramMethodSet contexts = accessesWithContexts.values().iterator().next();
        if (contexts.size() == 1) {
          return contexts.iterator().next();
        }
      }
      return null;
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    void flattenAccessContexts(DexField field) {
      if (accessesWithContexts != null) {
        ProgramMethodSet flattenedAccessContexts =
            accessesWithContexts.computeIfAbsent(field, ignore -> ProgramMethodSet.create());
        accessesWithContexts.forEach(
            (access, contexts) -> {
              if (access != field) {
                flattenedAccessContexts.addAll(contexts);
              }
            });
        accessesWithContexts.clear();
        if (!flattenedAccessContexts.isEmpty()) {
          accessesWithContexts.put(field, flattenedAccessContexts);
        }
        assert accessesWithContexts.size() <= 1;
      }
    }

    /**
     * Returns true if this field is written by a method for which {@param predicate} returns true.
     */
    @Override
    public boolean isAccessedInMethodSatisfying(Predicate<ProgramMethod> predicate) {
      for (ProgramMethodSet encodedWriteContexts : accessesWithContexts.values()) {
        for (ProgramMethod encodedWriteContext : encodedWriteContexts) {
          if (predicate.test(encodedWriteContext)) {
            return true;
          }
        }
      }
      return false;
    }

    /**
     * Returns true if this field is only written by methods for which {@param predicate} returns
     * true.
     */
    @Override
    public boolean isAccessedOnlyInMethodSatisfying(Predicate<ProgramMethod> predicate) {
      for (ProgramMethodSet encodedWriteContexts : accessesWithContexts.values()) {
        for (ProgramMethod encodedWriteContext : encodedWriteContexts) {
          if (!predicate.test(encodedWriteContext)) {
            return false;
          }
        }
      }
      return true;
    }

    /**
     * Returns true if this field is written by a method in the program other than {@param method}.
     */
    @Override
    @SuppressWarnings("ReferenceEquality")
    public boolean isAccessedOutside(DexEncodedMethod method) {
      for (ProgramMethodSet encodedWriteContexts : accessesWithContexts.values()) {
        for (ProgramMethod encodedWriteContext : encodedWriteContexts) {
          if (encodedWriteContext.getDefinition() != method) {
            return true;
          }
        }
      }
      return false;
    }

    @Override
    public boolean isConcrete() {
      return true;
    }

    @Override
    public ConcreteAccessContexts asConcrete() {
      return this;
    }

    @Override
    public boolean isEmpty() {
      return accessesWithContexts.isEmpty();
    }

    public boolean recordAccess(DexField access, ProgramMethod context) {
      return accessesWithContexts
          .computeIfAbsent(access, ignore -> ProgramMethodSet.create())
          .add(context);
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    ConcreteAccessContexts rewrittenWithLens(DexDefinitionSupplier definitions, GraphLens lens) {
      Map<DexField, ProgramMethodSet> rewrittenAccessesWithContexts = null;
      for (Entry<DexField, ProgramMethodSet> entry : accessesWithContexts.entrySet()) {
        DexField field = entry.getKey();
        DexField rewrittenField = lens.lookupField(field);

        ProgramMethodSet contexts = entry.getValue();
        ProgramMethodSet rewrittenContexts = contexts.rewrittenWithLens(definitions, lens);

        if (rewrittenField == field && rewrittenContexts == contexts) {
          if (rewrittenAccessesWithContexts == null) {
            continue;
          }
        } else {
          if (rewrittenAccessesWithContexts == null) {
            rewrittenAccessesWithContexts = new IdentityHashMap<>(accessesWithContexts.size());
            MapUtils.forEachUntilExclusive(
                accessesWithContexts, rewrittenAccessesWithContexts::put, field);
          }
        }
        merge(rewrittenAccessesWithContexts, rewrittenField, rewrittenContexts);
      }
      if (rewrittenAccessesWithContexts != null) {
        rewrittenAccessesWithContexts =
            MapUtils.trimCapacityOfIdentityHashMapIfSizeLessThan(
                rewrittenAccessesWithContexts, accessesWithContexts.size());
        return new ConcreteAccessContexts(rewrittenAccessesWithContexts);
      } else {
        return this;
      }
    }

    private static void merge(
        Map<DexField, ProgramMethodSet> accessesWithContexts,
        DexField field,
        ProgramMethodSet contexts) {
      ProgramMethodSet existingContexts = accessesWithContexts.put(field, contexts);
      if (existingContexts != null) {
        if (existingContexts.size() <= contexts.size()) {
          contexts.addAll(existingContexts);
        } else {
          accessesWithContexts.put(field, existingContexts);
          existingContexts.addAll(contexts);
        }
      }
    }

    @Override
    public AbstractAccessContexts join(AbstractAccessContexts contexts) {
      if (contexts.isEmpty()) {
        return this;
      }
      if (contexts.isTop()) {
        return contexts;
      }
      Map<DexField, ProgramMethodSet> newAccessesWithContexts = new IdentityHashMap<>();
      accessesWithContexts.forEach(
          (field, methodSet) ->
              newAccessesWithContexts.put(field, ProgramMethodSet.create(methodSet)));

      BiConsumer<DexField, ProgramMethodSet> addAllMethods =
          (field, methodSet) ->
              newAccessesWithContexts
                  .computeIfAbsent(field, ignore -> ProgramMethodSet.create())
                  .addAll(methodSet);
      contexts.asConcrete().accessesWithContexts.forEach(addAllMethods);
      return new ConcreteAccessContexts(newAccessesWithContexts);
    }
  }

  public static class UnknownAccessContexts extends AbstractAccessContexts {

    public static UnknownAccessContexts INSTANCE = new UnknownAccessContexts();

    private UnknownAccessContexts() {}

    public static UnknownAccessContexts getInstance() {
      return INSTANCE;
    }

    @Override
    void flattenAccessContexts(DexField field) {
      // Intentionally empty.
    }

    @Override
    void forEachAccessContext(Consumer<ProgramMethod> consumer) {
      throw new Unreachable("Should never be iterating the access contexts when they are unknown");
    }

    @Override
    boolean isAccessedInMethodSatisfying(Predicate<ProgramMethod> predicate) {
      return true;
    }

    @Override
    boolean isAccessedOnlyInMethodSatisfying(Predicate<ProgramMethod> predicate) {
      return false;
    }

    @Override
    boolean isAccessedOutside(DexEncodedMethod method) {
      return true;
    }

    @Override
    int getNumberOfAccessContexts() {
      throw new Unreachable(
          "Should never be querying the number of access contexts when they are unknown");
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public boolean isTop() {
      return true;
    }

    @Override
    AbstractAccessContexts rewrittenWithLens(DexDefinitionSupplier definitions, GraphLens lens) {
      return this;
    }

    @Override
    public AbstractAccessContexts join(AbstractAccessContexts contexts) {
      return this;
    }
  }
}
