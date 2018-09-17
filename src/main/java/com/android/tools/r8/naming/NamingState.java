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
  private final Map<KeyType, InternalState> usedNames = new HashMap<>();
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

  private InternalState findInternalStateFor(KeyType key) {
    InternalState result = usedNames.get(key);
    if (result == null && parent != null) {
      result = parent.findInternalStateFor(key);
    }
    return result;
  }

  private InternalState getOrCreateInternalStateFor(KeyType key) {
    // TODO(herhut): Maybe allocate these sparsely and search via state chain.
    InternalState result = usedNames.get(key);
    if (result == null) {
      if (parent != null) {
        InternalState parentState = parent.getOrCreateInternalStateFor(key);
        result = parentState.createChild();
      } else {
        result = new InternalState(itemFactory, null, dictionary);
      }
      usedNames.put(key, result);
    }
    return result;
  }

  private DexString getAssignedNameFor(DexString name, KeyType key) {
    InternalState state = findInternalStateFor(key);
    if (state == null) {
      return null;
    }
    return state.getAssignedNameFor(name, key);
  }

  public DexString assignNewNameFor(DexString original, ProtoType proto, boolean markAsUsed) {
    KeyType key = keyTransform.apply(proto);
    DexString result = getAssignedNameFor(original, key);
    if (result == null) {
      InternalState state = getOrCreateInternalStateFor(key);
      result = state.getNameFor(original, key, markAsUsed);
    }
    return result;
  }

  public void reserveName(DexString name, ProtoType proto) {
    KeyType key = keyTransform.apply(proto);
    InternalState state = getOrCreateInternalStateFor(key);
    state.reserveName(name);
  }

  public boolean isReserved(DexString name, ProtoType proto) {
    KeyType key = keyTransform.apply(proto);
    InternalState state = findInternalStateFor(key);
    if (state == null) {
      return false;
    }
    return state.isReserved(name);
  }

  public boolean isAvailable(DexString original, ProtoType proto, DexString candidate) {
    KeyType key = keyTransform.apply(proto);
    InternalState state = findInternalStateFor(key);
    if (state == null) {
      return true;
    }
    assert !useUniqueMemberNames
        || isNullOrEqualTo(state.getAssignedNameFor(original, key), candidate);
    return state.isAvailable(candidate);
  }

  private static <T> boolean isNullOrEqualTo(T a, T b) {
    return a == null || a == b;
  }

  public void addRenaming(DexString original, ProtoType proto, DexString newName) {
    KeyType key = keyTransform.apply(proto);
    InternalState state = getOrCreateInternalStateFor(key);
    state.addRenaming(original, key, newName);
  }

  private class InternalState {

    private static final int INITIAL_NAME_COUNT = 1;
    private final char[] EMPTY_CHAR_ARRAY = new char[0];

    protected final DexItemFactory itemFactory;
    private final InternalState parentInternalState;
    private Set<DexString> reservedNames = null;
    private Table<DexString, KeyType, DexString> renamings = null;
    private int nameCount;
    private final Iterator<String> dictionaryIterator;

    private InternalState(
        DexItemFactory itemFactory,
        InternalState parentInternalState,
        Iterator<String> dictionaryIterator) {
      this.itemFactory = itemFactory;
      this.parentInternalState = parentInternalState;
      this.nameCount =
          parentInternalState == null ? INITIAL_NAME_COUNT : parentInternalState.nameCount;
      this.dictionaryIterator = dictionaryIterator;
    }

    private InternalState(
        DexItemFactory itemFactory, InternalState parentInternalState, List<String> dictionary) {
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

    InternalState createChild() {
      return new InternalState(itemFactory, this, dictionaryIterator);
    }

    void reserveName(DexString name) {
      if (reservedNames == null) {
        reservedNames = Sets.newIdentityHashSet();
      }
      reservedNames.add(name);
    }

    DexString getAssignedNameFor(DexString original, KeyType proto) {
      DexString result = null;
      if (renamings != null) {
        if (useUniqueMemberNames) {
          Map<KeyType, DexString> row = renamings.row(original);
          if (row != null) {
            // Either not renamed yet (0) or renamed (1). If renamed, return the same renamed name
            // so that other members with the same name can be renamed to the same renamed name.
            Set<DexString> renamedNames = Sets.newHashSet(row.values());
            assert renamedNames.size() <= 1;
            result = Iterables.getOnlyElement(renamedNames, null);
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

    DexString getNameFor(DexString original, KeyType proto, boolean markAsUsed) {
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

    void addRenaming(DexString original, KeyType proto, DexString newName) {
      if (renamings == null) {
        renamings = HashBasedTable.create();
      }
      renamings.put(original, proto, newName);
    }

    String nextSuggestedName() {
      if (dictionaryIterator.hasNext()) {
        return dictionaryIterator.next();
      } else {
        return StringUtils.numberToIdentifier(EMPTY_CHAR_ARRAY, nameCount++, false);
      }
    }
  }
}
