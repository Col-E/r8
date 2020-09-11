// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.function.Function;

/**
 * Represents an application with a fully "committed" addition of synthetic items.
 *
 * <p>The committed application is used to rebuild application info (see AppInfo and its
 * derivatives) with the associated additional information for the program at well defined points on
 * the main thread.
 *
 * <p>A committed application will have no pending synthetics that are not defined in the program
 * classes collection, and it must also satisfy that all synthetic types are indeed contained in the
 * applications program class collection.
 */
public class CommittedItems implements SyntheticDefinitionsProvider {

  // Immutable package accessible fields to allow SyntheticItems creation.
  final DexApplication application;
  final int nextSyntheticId;
  final ImmutableSet<DexType> legacySyntheticTypes;
  final ImmutableMap<DexType, SyntheticReference> syntheticItems;
  final ImmutableList<DexType> committedTypes;

  CommittedItems(
      int nextSyntheticId,
      DexApplication application,
      ImmutableSet<DexType> legacySyntheticTypes,
      ImmutableMap<DexType, SyntheticReference> syntheticItems,
      ImmutableList<DexType> committedTypes) {
    assert verifyTypesAreInApp(application, legacySyntheticTypes);
    assert verifyTypesAreInApp(application, syntheticItems.keySet());
    this.nextSyntheticId = nextSyntheticId;
    this.application = application;
    this.legacySyntheticTypes = legacySyntheticTypes;
    this.syntheticItems = syntheticItems;
    this.committedTypes = committedTypes;
  }

  // Conversion to a mutable synthetic items collection. Should only be used in AppInfo creation.
  public SyntheticItems toSyntheticItems() {
    return new SyntheticItems(this);
  }

  public DexApplication getApplication() {
    return application;
  }

  public Collection<DexType> getCommittedTypes() {
    return committedTypes;
  }

  @Override
  public DexClass definitionFor(DexType type, Function<DexType, DexClass> baseDefinitionFor) {
    // All synthetic types are committed to the application so lookup is just the base lookup.
    return baseDefinitionFor.apply(type);
  }

  private static boolean verifyTypesAreInApp(DexApplication app, Collection<DexType> types) {
    for (DexType type : types) {
      assert app.programDefinitionFor(type) != null : "Missing synthetic: " + type;
    }
    return true;
  }
}
