// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

class MethodNamingState<KeyType> {

  private final MethodNamingState<KeyType> parent;
  private final Map<KeyType, InternalReservationState> usedNames = new HashMap<>();
  private final Map<KeyType, InternalNewNameState> newNameStates = new HashMap<>();
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

  MethodNamingState<KeyType> createChild() {
    return new MethodNamingState<>(this, keyTransform, strategy);
  }

  private InternalReservationState findInternalReservationStateFor(KeyType key) {
    InternalReservationState result = usedNames.get(key);
    if (result == null && parent != null) {
      result = parent.findInternalReservationStateFor(key);
    }
    return result;
  }

  private InternalReservationState getOrCreateInternalReservationStateFor(KeyType key) {
    InternalReservationState result = usedNames.get(key);
    if (result == null) {
      InternalReservationState parentState =
          parent != null ? parent.getOrCreateInternalReservationStateFor(key) : null;
      result = new InternalReservationState(parentState);
      usedNames.put(key, result);
    }
    return result;
  }

  private InternalNewNameState findInternalNewNameStateFor(KeyType key) {
    InternalNewNameState result = newNameStates.get(key);
    if (result == null && parent != null) {
      result = parent.findInternalNewNameStateFor(key);
    }
    return result;
  }

  private InternalNewNameState getOrCreateNewNameStateFor(KeyType key) {
    InternalNewNameState result = newNameStates.get(key);
    if (result == null) {
      InternalReservationState reservationState = getOrCreateInternalReservationStateFor(key);
      assert reservationState != null;
      InternalNewNameState parentState =
          parent != null ? parent.getOrCreateNewNameStateFor(key) : null;
      result = new InternalNewNameState(parentState, reservationState);
      newNameStates.put(key, result);
    }
    return result;
  }

  private DexString getAssignedNameFor(DexString name, KeyType key) {
    InternalReservationState state = findInternalReservationStateFor(key);
    if (state == null) {
      return null;
    }
    return state.getAssignedNameFor(name);
  }

  DexString assignNewNameFor(DexMethod source, DexString original, DexProto proto) {
    KeyType key = keyTransform.apply(proto);
    DexString result = getAssignedNameFor(original, key);
    if (result == null) {
      InternalNewNameState state = getOrCreateNewNameStateFor(key);
      result = state.getNewNameFor(source);
    }
    return result;
  }

  void reserveName(DexString name, DexProto proto, DexString originalName) {
    KeyType key = keyTransform.apply(proto);
    InternalReservationState state = getOrCreateInternalReservationStateFor(key);
    state.reserveName(name, originalName);
  }

  boolean isReserved(DexString name, DexProto proto) {
    KeyType key = keyTransform.apply(proto);
    InternalReservationState state = findInternalReservationStateFor(key);
    if (state == null) {
      return false;
    }
    return state.isReserved(name);
  }

  DexString getReservedOriginalName(DexString name, DexProto proto) {
    KeyType key = keyTransform.apply(proto);
    InternalReservationState state = findInternalReservationStateFor(key);
    if (state == null) {
      return null;
    }
    return state.getReservedOriginalName(name);
  }

  boolean isAvailable(DexProto proto, DexString candidate) {
    KeyType key = keyTransform.apply(proto);
    InternalReservationState state = findInternalReservationStateFor(key);
    if (state == null) {
      return true;
    }
    return state.isAvailable(candidate);
  }

  void addRenaming(DexString original, DexProto proto, DexString newName) {
    KeyType key = keyTransform.apply(proto);
    InternalReservationState state = getOrCreateInternalReservationStateFor(key);
    state.addRenaming(original, newName);
  }

  void printState(
      DexProto proto,
      Function<MethodNamingState<?>, DexType> stateKeyGetter,
      String indentation,
      PrintStream out) {
    KeyType key = keyTransform.apply(proto);
    InternalNewNameState state = getOrCreateNewNameStateFor(key);
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

  class InternalReservationState {
    private final InternalReservationState parentInternalState;
    private Map<DexString, DexString> reservedNames = null;
    private Map<DexString, DexString> renamings = null;

    private InternalReservationState(InternalReservationState parentInternalState) {
      this.parentInternalState = parentInternalState;
    }

    boolean isReserved(DexString name) {
      return (reservedNames != null && reservedNames.containsKey(name))
          || (parentInternalState != null && parentInternalState.isReserved(name));
    }

    DexString getReservedOriginalName(DexString name) {
      DexString result = null;
      if (reservedNames != null) {
        result = reservedNames.get(name);
      }
      if (result == null && parentInternalState != null) {
        result = parentInternalState.getReservedOriginalName(name);
      }
      return result;
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

    private boolean isAvailable(DexString name) {
      return !(renamings != null && renamings.containsValue(name))
          && !(reservedNames != null && reservedNames.containsKey(name))
          && (parentInternalState == null || parentInternalState.isAvailable(name));
    }

    void reserveName(DexString name, DexString originalName) {
      if (reservedNames == null) {
        reservedNames = new HashMap<>();
      }
      reservedNames.put(name, originalName);
    }

    void addRenaming(DexString original, DexString newName) {
      if (renamings == null) {
        renamings = new HashMap<>();
      }
      renamings.put(original, newName);
    }

    void printReservedNames(String indentation, PrintStream out) {
      out.print(indentation);
      out.print("Reserved names:");
      if (reservedNames == null || reservedNames.isEmpty()) {
        out.print(" <NO RESERVED NAMES>");
      } else {
        for (DexString reservedName : reservedNames.keySet()) {
          out.print(System.lineSeparator());
          out.print(indentation);
          out.print("  ");
          out.print(reservedName.toSourceString() + "(by " + reservedNames.get(reservedName) + ")");
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

  class InternalNewNameState implements InternalNamingState {

    private final InternalNewNameState parentInternalState;
    private final InternalReservationState reservationState;

    private static final int INITIAL_NAME_COUNT = 1;
    private static final int INITIAL_DICTIONARY_INDEX = 0;

    private int virtualNameCount;
    private int directNameCount = 0;
    private int dictionaryIndex;

    private InternalNewNameState(
        InternalNewNameState parentInternalState, InternalReservationState reservationState) {
      this.parentInternalState = parentInternalState;
      this.reservationState = reservationState;
      this.dictionaryIndex =
          parentInternalState == null
              ? INITIAL_DICTIONARY_INDEX
              : parentInternalState.dictionaryIndex;
      this.virtualNameCount =
          parentInternalState == null ? INITIAL_NAME_COUNT : parentInternalState.virtualNameCount;
      assert reservationState != null;
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
      InternalNewNameState tmp = parentInternalState;
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

    private DexString getNewNameFor(DexMethod source) {
      DexString name;
      do {
        name = strategy.next(source, this);
      } while (!reservationState.isAvailable(name));
      return name;
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
      reservationState.printReservedNames(indentation + "  ", out);
      reservationState.printRenamings(indentation + "  ", out);

      if (parentInternalState != null) {
        parentInternalState.printInternalState(
            expectedNamingState.parent, stateKeyGetter, indentation + "  ", out);
      }
    }

    void printLastName(String indentation, PrintStream out) {
      out.print(indentation);
      out.print("public name count: ");
      out.print(virtualNameCount);
      out.print(", ");
      out.print("direct name count: ");
      out.println(directNameCount);
    }
  }
}
