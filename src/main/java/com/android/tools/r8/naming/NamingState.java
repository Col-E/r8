// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.graph.CachedHashValueDexItem;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

class NamingState<ProtoType extends CachedHashValueDexItem, KeyType> {

  private final NamingState<ProtoType, KeyType> parent;
  private final Map<KeyType, InternalState<ProtoType>> usedNames = new HashMap<>();
  private final DexItemFactory itemFactory;
  private final ImmutableList<String> dictionary;
  private final Function<ProtoType, KeyType> keyTransform;
  private final boolean useUniqueMemberNames;

  static <S, T extends CachedHashValueDexItem> NamingState<T, S> createRoot(
      DexItemFactory itemFactory,
      ImmutableList<String> dictionary,
      Function<T, S> keyTransform,
      boolean useUniqueMemberNames) {
    return new NamingState<>(null, itemFactory, dictionary, keyTransform, useUniqueMemberNames);
  }

  private NamingState(
      NamingState<ProtoType, KeyType> parent,
      DexItemFactory itemFactory,
      ImmutableList<String> dictionary,
      Function<ProtoType, KeyType> keyTransform,
      boolean useUniqueMemberNames) {
    this.parent = parent;
    this.itemFactory = itemFactory;
    this.dictionary = dictionary;
    this.keyTransform = keyTransform;
    this.useUniqueMemberNames = useUniqueMemberNames;
  }

  public NamingState<ProtoType, KeyType> createChild() {
    return new NamingState<>(this, itemFactory, dictionary, keyTransform, useUniqueMemberNames);
  }

  private InternalState<ProtoType> findInternalStateFor(ProtoType proto) {
    KeyType key = keyTransform.apply(proto);
    InternalState<ProtoType> result = usedNames.get(key);
    if (result == null && parent != null) {
      result = parent.findInternalStateFor(proto);
    }
    return result;
  }

  private InternalState<ProtoType> getOrCreateInternalStateFor(ProtoType proto) {
    // TODO(herhut): Maybe allocate these sparsely and search via state chain.
    KeyType key = keyTransform.apply(proto);
    InternalState<ProtoType> result = usedNames.get(key);
    if (result == null) {
      if (parent != null) {
        InternalState<ProtoType> parentState = parent.getOrCreateInternalStateFor(proto);
        result = parentState.createChild();
      } else {
        result = new InternalState<>(itemFactory, null, dictionary);
      }
      usedNames.put(key, result);
    }
    return result;
  }

  private DexString getAssignedNameFor(DexString name, ProtoType proto) {
    InternalState<ProtoType> state = findInternalStateFor(proto);
    if (state == null) {
      return null;
    }
    return state.getAssignedNameFor(name, proto);
  }

  public DexString assignNewNameFor(DexString original, ProtoType proto, boolean markAsUsed) {
    DexString result = getAssignedNameFor(original, proto);
    if (result == null) {
      InternalState<ProtoType> state = getOrCreateInternalStateFor(proto);
      result = state.getNameFor(original, proto, markAsUsed);
    }
    return result;
  }

  public void reserveName(DexString name, ProtoType proto) {
    InternalState<ProtoType> state = getOrCreateInternalStateFor(proto);
    state.reserveName(name);
  }

  public boolean isReserved(DexString name, ProtoType proto) {
    InternalState<ProtoType> state = findInternalStateFor(proto);
    if (state == null) {
      return false;
    }
    return state.isReserved(name);
  }

  public boolean isAvailable(DexString original, ProtoType proto, DexString candidate) {
    InternalState<ProtoType> state = findInternalStateFor(proto);
    if (state == null) {
      return true;
    }
    assert state.getAssignedNameFor(original, proto) != candidate;
    return state.isAvailable(candidate);
  }

  public void addRenaming(DexString original, ProtoType proto, DexString newName) {
    InternalState<ProtoType> state = getOrCreateInternalStateFor(proto);
    state.addRenaming(original, proto, newName);
  }

  private class InternalState<InternalProtoType extends CachedHashValueDexItem> {

    private static final int INITIAL_NAME_COUNT = 1;
    private final char[] EMPTY_CHAR_ARRARY = new char[0];

    protected final DexItemFactory itemFactory;
    private final InternalState<InternalProtoType> parentInternalState;
    private Set<DexString> reservedNames = null;
    private Table<DexString, InternalProtoType, DexString> renamings = null;
    private int nameCount;
    private final Iterator<String> dictionaryIterator;

    private InternalState(
        DexItemFactory itemFactory,
        InternalState<InternalProtoType> parentInternalState,
        Iterator<String> dictionaryIterator) {
      this.itemFactory = itemFactory;
      this.parentInternalState = parentInternalState;
      this.nameCount =
          parentInternalState == null ? INITIAL_NAME_COUNT : parentInternalState.nameCount;
      this.dictionaryIterator = dictionaryIterator;
    }

    private InternalState(
        DexItemFactory itemFactory,
        InternalState<InternalProtoType> parentInternalState,
        List<String> dictionary) {
      this(itemFactory, parentInternalState, dictionary.iterator());
    }

    private boolean isReserved(DexString name) {
      return (reservedNames != null && reservedNames.contains(name))
          || (parentInternalState != null && parentInternalState.isReserved(name));
    }

    private boolean isAvailable(DexString name) {
      return !(renamings != null && renamings.containsValue(name))
          && !(reservedNames != null && reservedNames.contains(name))
          && (parentInternalState == null || parentInternalState.isAvailable(name));
    }

    InternalState<InternalProtoType> createChild() {
      return new InternalState<>(itemFactory, this, dictionaryIterator);
    }

    void reserveName(DexString name) {
      if (reservedNames == null) {
        reservedNames = Sets.newIdentityHashSet();
      }
      reservedNames.add(name);
    }

    DexString getAssignedNameFor(DexString original, InternalProtoType proto) {
      DexString result = null;
      if (renamings != null) {
        if (useUniqueMemberNames) {
          Map<InternalProtoType, DexString> row = renamings.row(original);
          if (row != null) {
            // Either not renamed yet (0) or renamed (1). If renamed, return the same renamed name
            // so that other members with the same name can be renamed to the same renamed name.
            assert row.values().size() <= 1;
            result = Iterables.getOnlyElement(row.values(), null);
          }
        } else {
          result = renamings.get(original, proto);
        }
      }
      if (result == null && parentInternalState != null) {
        result = parentInternalState.getAssignedNameFor(original, proto);
      }
      return result;
    }

    DexString getNameFor(DexString original, InternalProtoType proto, boolean markAsUsed) {
      DexString name = getAssignedNameFor(original, proto);
      if (name != null) {
        return name;
      }
      do {
        name = itemFactory.createString(nextSuggestedName());
      } while (!isAvailable(name));
      if (markAsUsed) {
        addRenaming(original, proto, name);
      }
      return name;
    }

    void addRenaming(DexString original, InternalProtoType proto, DexString newName) {
      if (renamings == null) {
        renamings = HashBasedTable.create();
      }
      renamings.put(original, proto, newName);
    }

    String nextSuggestedName() {
      if (dictionaryIterator.hasNext()) {
        return dictionaryIterator.next();
      } else {
        return StringUtils.numberToIdentifier(EMPTY_CHAR_ARRARY, nameCount++, false);
      }
    }
  }
}
