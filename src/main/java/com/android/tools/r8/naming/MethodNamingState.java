// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.naming.Minifier.MinifierMemberNamingStrategy.EMPTY_CHAR_ARRAY;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;

class MethodNamingState<KeyType> {

  private final AppView<?> appView;
  private final MethodNamingState<KeyType> parent;
  private final Map<KeyType, InternalState> usedNames = new HashMap<>();
  private final Function<DexProto, KeyType> keyTransform;
  private final MemberNamingStrategy strategy;

  static <S> MethodNamingState<S> createRoot(
      AppView<?> appView, Function<DexProto, S> keyTransform, MemberNamingStrategy strategy) {
    return new MethodNamingState<>(null, appView, keyTransform, strategy);
  }

  private MethodNamingState(
      MethodNamingState<KeyType> parent,
      AppView<?> appView,
      Function<DexProto, KeyType> keyTransform,
      MemberNamingStrategy strategy) {
    this.appView = appView;
    this.parent = parent;
    this.keyTransform = keyTransform;
    this.strategy = strategy;
  }

  public MethodNamingState<KeyType> createChild() {
    return new MethodNamingState<>(this, appView, keyTransform, strategy);
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
      InternalState parentState = parent != null ? parent.getOrCreateInternalStateFor(key) : null;
      result = new InternalState(appView, parentState);
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

  public DexString assignNewNameFor(DexMethod source, DexString original, DexProto proto) {
    KeyType key = keyTransform.apply(proto);
    DexString result = getAssignedNameFor(original, key);
    if (result == null) {
      InternalState state = getOrCreateInternalStateFor(key);
      result = state.getNewNameFor(source);
    }
    return result;
  }

  public void reserveName(DexString name, DexProto proto) {
    KeyType key = keyTransform.apply(proto);
    InternalState state = getOrCreateInternalStateFor(key);
    state.reserveName(name);
  }

  public boolean isReserved(DexString name, DexProto proto) {
    KeyType key = keyTransform.apply(proto);
    InternalState state = findInternalStateFor(key);
    if (state == null) {
      return false;
    }
    return state.isReserved(name);
  }

  public boolean isAvailable(DexProto proto, DexString candidate) {
    KeyType key = keyTransform.apply(proto);
    InternalState state = findInternalStateFor(key);
    if (state == null) {
      return true;
    }
    return state.isAvailable(candidate);
  }

  public void addRenaming(DexString original, DexProto proto, DexString newName) {
    KeyType key = keyTransform.apply(proto);
    InternalState state = getOrCreateInternalStateFor(key);
    state.addRenaming(original, key, newName);
  }

  void printState(
      DexProto proto,
      Function<MethodNamingState<?>, DexType> stateKeyGetter,
      String indentation,
      PrintStream out) {
    KeyType key = keyTransform.apply(proto);
    InternalState state = getOrCreateInternalStateFor(key);
    out.print(indentation);
    out.print("NamingState(node=`");
    out.print(stateKeyGetter.apply(this).toSourceString());
    out.print("`, proto=`");
    out.print(proto.toSourceString());
    out.print("`, key=`");
    out.print(key.toString());
    out.println("`)");
    if (state != null) {
      state.printInternalState(this, stateKeyGetter, indentation + "  ", out);
    } else {
      out.print(indentation);
      out.println("<NO STATE>");
    }
  }

  class InternalState {

    private static final int INITIAL_NAME_COUNT = 1;

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

    private InternalState(AppView<?> appView, InternalState parentInternalState) {
      this(
          appView.dexItemFactory(),
          parentInternalState,
          appView.options().getProguardConfiguration().getObfuscationDictionary().iterator());
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

    void reserveName(DexString name) {
      if (reservedNames == null) {
        reservedNames = Sets.newIdentityHashSet();
      }
      reservedNames.add(name);
    }

    public int incrementAndGet() {
      int parentNameCount = 0;
      InternalState tmp = parentInternalState;
      while (tmp != null) {
        if (tmp.nameCount > parentNameCount) {
          parentNameCount = tmp.nameCount;
        }
        tmp = tmp.parentInternalState;
      }
      if (parentNameCount > nameCount) {
        nameCount = parentNameCount;
      }
      return nameCount++;
    }

    DexString getAssignedNameFor(DexString original, KeyType proto) {
      DexString result = null;
      if (renamings != null) {
        result = renamings.get(original, proto);
      }
      if (result == null && parentInternalState != null) {
        result = parentInternalState.getAssignedNameFor(original, proto);
      }
      return result;
    }

    private DexString getNewNameFor(DexMethod source) {
      DexString name;
      do {
        name = nextSuggestedName(source);
      } while (!isAvailable(name) && !strategy.breakOnNotAvailable(source, name));
      return name;
    }

    void addRenaming(DexString original, KeyType proto, DexString newName) {
      if (renamings == null) {
        renamings = HashBasedTable.create();
      }
      renamings.put(original, proto, newName);
    }

    DexString nextSuggestedName(DexMethod source) {
      if (!strategy.bypassDictionary() && dictionaryIterator.hasNext()) {
        return itemFactory.createString(dictionaryIterator.next());
      } else {
        return strategy.next(source, this);
      }
    }

    void printInternalState(
        MethodNamingState<?> expectedNamingState,
        Function<MethodNamingState<?>, DexType> stateKeyGetter,
        String indentation,
        PrintStream out) {
      assert expectedNamingState == MethodNamingState.this;

      DexType stateKey = stateKeyGetter.apply(expectedNamingState);
      out.print(indentation);
      out.print("InternalState(node=`");
      out.print(stateKey != null ? stateKey.toSourceString() : "<GLOBAL>");
      out.println("`)");

      printLastName(indentation + "  ", out);
      printReservedNames(indentation + "  ", out);
      printRenamings(indentation + "  ", out);

      if (parentInternalState != null) {
        parentInternalState.printInternalState(
            expectedNamingState.parent, stateKeyGetter, indentation + "  ", out);
      }
    }

    void printLastName(String indentation, PrintStream out) {
      out.print(indentation);
      out.print("Last name: ");
      if (nameCount > 1) {
        out.print(StringUtils.numberToIdentifier(EMPTY_CHAR_ARRAY, nameCount - 1, false));
        out.print(" (name count: ");
        out.print(nameCount);
        out.print(")");
      } else {
        out.print("<NONE>");
      }
      out.println();
    }

    void printReservedNames(String indentation, PrintStream out) {
      out.print(indentation);
      out.print("Reserved names:");
      if (reservedNames == null || reservedNames.isEmpty()) {
        out.print(" <NO RESERVED NAMES>");
      } else {
        for (DexString reservedName : reservedNames) {
          out.print(System.lineSeparator());
          out.print(indentation);
          out.print("  ");
          out.print(reservedName.toSourceString());
        }
      }
      out.println();
    }

    void printRenamings(String indentation, PrintStream out) {
      out.print(indentation);
      out.print("Renamings:");
      if (renamings == null || renamings.isEmpty()) {
        out.print(" <NO RENAMINGS>");
      } else {
        for (DexString original : renamings.rowKeySet()) {
          Map<KeyType, DexString> row = renamings.row(original);
          for (Entry<KeyType, DexString> entry : row.entrySet()) {
            out.print(System.lineSeparator());
            out.print(indentation);
            out.print("  ");
            out.print(original.toSourceString());
            out.print(entry.getKey().toString());
            out.print(" -> ");
            out.print(entry.getValue().toSourceString());
          }
        }
      }
      out.println();
    }
  }
}
