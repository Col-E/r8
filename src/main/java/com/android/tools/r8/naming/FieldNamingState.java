// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static com.android.tools.r8.naming.Minifier.MinifierMemberNamingStrategy.EMPTY_CHAR_ARRAY;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.FieldNamingState.InternalState;
import com.android.tools.r8.utils.StringUtils;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

public class FieldNamingState extends FieldNamingStateBase<InternalState> implements Cloneable {

  private final ReservedFieldNamingState reservedNames;
  private final MemberNamingStrategy strategy;

  public FieldNamingState(AppView<? extends AppInfo> appView, MemberNamingStrategy strategy) {
    this(appView, strategy, new ReservedFieldNamingState(appView));
  }

  public FieldNamingState(
      AppView<? extends AppInfo> appView,
      MemberNamingStrategy strategy,
      ReservedFieldNamingState reservedNames) {
    this(appView, strategy, reservedNames, new IdentityHashMap<>());
  }

  private FieldNamingState(
      AppView<? extends AppInfo> appView,
      MemberNamingStrategy strategy,
      ReservedFieldNamingState reservedNames,
      Map<DexType, InternalState> internalStates) {
    super(appView, internalStates);
    this.reservedNames = reservedNames;
    this.strategy = strategy;
  }

  public FieldNamingState createChildState(ReservedFieldNamingState reservedNames) {
    FieldNamingState childState =
        new FieldNamingState(appView, strategy, reservedNames, internalStates);
    childState.includeReservations(this.reservedNames);
    return childState;
  }

  public DexString getOrCreateNameFor(DexField field) {
    DexEncodedField encodedField = appView.appInfo().resolveField(field);
    if (encodedField != null) {
      DexClass clazz = appView.definitionFor(encodedField.field.holder);
      if (clazz == null || clazz.isLibraryClass()) {
        return field.name;
      }
      if (!appView.options().getProguardConfiguration().hasApplyMappingFile()
          && appView.rootSet().noObfuscation.contains(encodedField.field)) {
        return field.name;
      }
    }
    return getOrCreateInternalState(field).createNewName(field);
  }

  public void includeReservations(ReservedFieldNamingState reservedNames) {
    this.reservedNames.includeReservations(reservedNames);
  }

  @Override
  public InternalState createInternalState() {
    return new InternalState();
  }

  @Override
  public FieldNamingState clone() {
    Map<DexType, InternalState> internalStatesClone = new IdentityHashMap<>();
    for (Map.Entry<DexType, InternalState> entry : internalStates.entrySet()) {
      internalStatesClone.put(entry.getKey(), entry.getValue().clone());
    }
    return new FieldNamingState(appView, strategy, reservedNames, internalStatesClone);
  }

  class InternalState implements Cloneable {

    private final Iterator<String> dictionaryIterator;
    private int nextNameIndex;

    public InternalState() {
      this(1, appView.options().getProguardConfiguration().getObfuscationDictionary().iterator());
    }

    public InternalState(int nextNameIndex, Iterator<String> dictionaryIterator) {
      this.dictionaryIterator = dictionaryIterator;
      this.nextNameIndex = nextNameIndex;
    }

    public DexString createNewName(DexField field) {
      DexString name;
      do {
        name = nextNameAccordingToStrategy(field);
      } while (reservedNames.isReserved(name, field.type)
          && !strategy.breakOnNotAvailable(field, name));
      return name;
    }

    private DexString nextNameAccordingToStrategy(DexField field) {
      if (!strategy.bypassDictionary() && dictionaryIterator.hasNext()) {
        return appView.dexItemFactory().createString(dictionaryIterator.next());
      } else {
        return strategy.next(field, this);
      }
    }

    public DexString nextNameAccordingToState() {
      return appView
          .dexItemFactory()
          .createString(StringUtils.numberToIdentifier(EMPTY_CHAR_ARRAY, nextNameIndex++, false));
    }

    @Override
    public InternalState clone() {
      return new InternalState(nextNameIndex, dictionaryIterator);
    }
  }
}
