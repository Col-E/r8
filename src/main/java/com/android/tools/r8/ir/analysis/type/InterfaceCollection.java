// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.Pair;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMap.Entry;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMaps;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class InterfaceCollection {

  @SuppressWarnings("ReferenceEquality")
  public static boolean isKnownToImplement(
      DexType iface, DexType implementor, InternalOptions options) {
    if (options.canHaveZipFileWithMissingCloseableBug()
        && implementor == options.dexItemFactory().zipFileType
        && iface == options.dexItemFactory().closeableType) {
      return false;
    }
    return true;
  }

  public static class Builder {
    private Reference2BooleanMap<DexType> interfaces = new Reference2BooleanOpenHashMap<>();

    private Builder() {}

    public Builder addInterface(DexType iface, DexClass implementor, InternalOptions options) {
      return addInterface(
          iface,
          !implementor.isLibraryClass()
              || isKnownToImplement(iface, implementor.getType(), options));
    }

    public Builder addInterface(DexType iface, DexType implementor, InternalOptions options) {
      return addInterface(iface, isKnownToImplement(iface, implementor, options));
    }

    public Builder addInterface(DexType type, boolean isKnown) {
      interfaces.compute(
          type,
          (existingType, existingIsKnown) ->
              // If the entry is new 'existingIsKnown == null', so we join with (null or true).
              (existingIsKnown == null || existingIsKnown) && isKnown);
      return this;
    }

    public Builder addKnownInterface(DexType type) {
      return addInterface(type, true);
    }

    public InterfaceCollection build() {
      if (interfaces.isEmpty()) {
        return InterfaceCollection.empty();
      }
      return new InterfaceCollection(interfaces);
    }
  }

  private static final InterfaceCollection EMPTY =
      new InterfaceCollection(Reference2BooleanMaps.emptyMap());

  public static InterfaceCollection empty() {
    return EMPTY;
  }

  public static InterfaceCollection singleton(DexType type) {
    return new InterfaceCollection(Reference2BooleanMaps.singleton(type, true));
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Set of interfaces mapping to an optional presence.
   *
   * <ul>
   *   <li>An unmapped type is known to not be present.
   *   <li>A type mapped to true is known to always be present.
   *   <li>A type mapped to false is not always known to be present.
   */
  private final Reference2BooleanMap<DexType> interfaces;

  private InterfaceCollection(Reference2BooleanMap<DexType> interfaces) {
    assert interfaces != null;
    this.interfaces = interfaces;
  }

  public boolean isEmpty() {
    return interfaces.isEmpty();
  }

  public int size() {
    return interfaces.size();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof InterfaceCollection)) {
      return false;
    }
    InterfaceCollection that = (InterfaceCollection) o;
    return interfaces.equals(that.interfaces);
  }

  @Override
  public int hashCode() {
    return interfaces.hashCode();
  }

  public void forEach(BiConsumer<DexType, Boolean> fn) {
    interfaces.forEach(fn);
  }

  public void forEachKnownInterface(Consumer<DexType> consumer) {
    forEach(
        (type, isKnown) -> {
          if (isKnown) {
            consumer.accept(type);
          }
        });
  }

  public boolean allKnownInterfacesMatch(Predicate<DexType> fn) {
    for (Entry<DexType> entry : interfaces.reference2BooleanEntrySet()) {
      if (entry.getBooleanValue() && !fn.test(entry.getKey())) {
        return false;
      }
    }
    return true;
  }

  public boolean anyMatch(BiPredicate<DexType, Boolean> fn) {
    for (Entry<DexType> entry : interfaces.reference2BooleanEntrySet()) {
      if (fn.test(entry.getKey(), entry.getBooleanValue())) {
        return true;
      }
    }
    return false;
  }

  public List<Pair<DexType, Boolean>> getInterfaceList() {
    List<Pair<DexType, Boolean>> list = new ArrayList<>(interfaces.size());
    interfaces.forEach((iface, isKnown) -> list.add(new Pair<>(iface, isKnown)));
    return list;
  }

  public boolean hasSingleKnownInterface() {
    DexType singleKnownInterface = getSingleKnownInterface();
    return singleKnownInterface != null;
  }

  public DexType getSingleKnownInterface() {
    if (interfaces.size() != 1) {
      return null;
    }
    DexType type = interfaces.keySet().iterator().next();
    return interfaces.getBoolean(type) ? type : null;
  }

  public OptionalBool contains(DexType type) {
    Boolean value = interfaces.get(type);
    if (value == null) {
      return OptionalBool.FALSE;
    }
    return value ? OptionalBool.TRUE : OptionalBool.unknown();
  }

  public boolean containsKnownInterface(DexType type) {
    return contains(type).isTrue();
  }
}
