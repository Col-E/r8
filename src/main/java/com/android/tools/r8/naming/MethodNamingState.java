// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.MemberNamingStrategy.MemberNamingInternalState;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.Sets;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

class MethodNamingState<KeyType> {

  private final MethodNamingState<KeyType> parent;
  private final Map<KeyType, InternalState> usedNames = new HashMap<>();
  private final Function<DexProto, KeyType> keyTransform;
  private final MemberNamingStrategy strategy;

  static <S> MethodNamingState<S> createRoot(
      Function<DexProto, S> keyTransform, MemberNamingStrategy strategy) {
    return new MethodNamingState<>(null, keyTransform, strategy);
  }

  private MethodNamingState(
      MethodNamingState<KeyType> parent,
      Function<DexProto, KeyType> keyTransform,
      MemberNamingStrategy strategy) {
    this.parent = parent;
    this.keyTransform = keyTransform;
    this.strategy = strategy;
  }

  public MethodNamingState<KeyType> createChild() {
    return new MethodNamingState<>(this, keyTransform, strategy);
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
      result = new InternalState(parentState);
      usedNames.put(key, result);
    }
    return result;
  }

  private DexString getAssignedNameFor(DexString name, KeyType key) {
    InternalState state = findInternalStateFor(key);
    if (state == null) {
      return null;
    }
    return state.getAssignedNameFor(name);
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
    state.addRenaming(original, newName);
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

  class InternalState implements MemberNamingInternalState {

    private static final int INITIAL_NAME_COUNT = 1;
    private static final int INITIAL_DICTIONARY_INDEX = 0;

    private final InternalState parentInternalState;
    private Set<DexString> reservedNames = null;
    private Map<DexString, DexString> renamings = null;
    private int virtualNameCount;
    private int directNameCount = 0;
    private int dictionaryIndex;

    private InternalState(InternalState parentInternalState) {
      this.parentInternalState = parentInternalState;
      this.dictionaryIndex =
          parentInternalState == null
              ? INITIAL_DICTIONARY_INDEX
              : parentInternalState.dictionaryIndex;
      this.virtualNameCount =
          parentInternalState == null ? INITIAL_NAME_COUNT : parentInternalState.virtualNameCount;
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

    @Override
    public int getDictionaryIndex() {
      return dictionaryIndex;
    }

    @Override
    public int incrementDictionaryIndex() {
      return dictionaryIndex++;
    }

    private boolean checkParentPublicNameCountIsLessThanOrEqual() {
      int maxParentCount = 0;
      InternalState tmp = parentInternalState;
      while (tmp != null) {
        maxParentCount = Math.max(tmp.virtualNameCount, maxParentCount);
        tmp = tmp.parentInternalState;
      }
      assert maxParentCount <= virtualNameCount;
      return true;
    }

    @Override
    public int incrementNameIndex(boolean isDirectMethodCall) {
      assert checkParentPublicNameCountIsLessThanOrEqual();
      if (isDirectMethodCall) {
        return virtualNameCount + directNameCount++;
      } else {
        assert directNameCount == 0;
        return virtualNameCount++;
      }
    }

    DexString getAssignedNameFor(DexString original) {
      DexString result = null;
      if (renamings != null) {
        result = renamings.get(original);
      }
      if (result == null && parentInternalState != null) {
        result = parentInternalState.getAssignedNameFor(original);
      }
      return result;
    }

    private DexString getNewNameFor(DexMethod source) {
      DexString name;
      do {
        name = strategy.next(source, this);
      } while (!isAvailable(name) && !strategy.breakOnNotAvailable(source, name));
      return name;
    }

    void addRenaming(DexString original, DexString newName) {
      if (renamings == null) {
        renamings = new HashMap<>();
      }
      renamings.put(original, newName);
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
      int index = virtualNameCount + directNameCount;
      if (index > 1) {
        out.print(StringUtils.numberToIdentifier(index - 1));
        out.print(" (public name count: ");
        out.print(virtualNameCount);
        out.print(")");
        out.print(" (direct name count: ");
        out.print(directNameCount);
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
        for (DexString original : renamings.keySet()) {
          out.print(System.lineSeparator());
          out.print(indentation);
          out.print("  ");
          out.print(original.toSourceString());
          out.print(" -> ");
          out.print(renamings.get(original).toSourceString());
        }
      }
      out.println();
    }
  }
}
