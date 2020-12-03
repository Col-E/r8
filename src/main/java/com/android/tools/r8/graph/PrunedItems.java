// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Set;

public class PrunedItems {

  private final DexApplication prunedApp;
  private final Set<DexReference> additionalPinnedItems;
  private final Set<DexType> noLongerSyntheticItems;
  private final Set<DexType> removedClasses;

  private PrunedItems(
      DexApplication prunedApp,
      Set<DexReference> additionalPinnedItems,
      Set<DexType> noLongerSyntheticItems,
      Set<DexType> removedClasses) {
    this.prunedApp = prunedApp;
    this.additionalPinnedItems = additionalPinnedItems;
    this.noLongerSyntheticItems = noLongerSyntheticItems;
    this.removedClasses = removedClasses;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static PrunedItems empty(DexApplication application) {
    return new Builder().setPrunedApp(application).build();
  }

  public boolean isEmpty() {
    return removedClasses.isEmpty() && additionalPinnedItems.isEmpty();
  }

  public DexApplication getPrunedApp() {
    return prunedApp;
  }

  public Set<? extends DexReference> getAdditionalPinnedItems() {
    return additionalPinnedItems;
  }

  public Set<DexType> getNoLongerSyntheticItems() {
    return noLongerSyntheticItems;
  }

  public boolean hasRemovedClasses() {
    return !removedClasses.isEmpty();
  }

  public Set<DexType> getRemovedClasses() {
    return removedClasses;
  }

  public static class Builder {

    private DexApplication prunedApp;

    private final Set<DexReference> additionalPinnedItems = Sets.newIdentityHashSet();
    private final Set<DexType> noLongerSyntheticItems = Sets.newIdentityHashSet();
    private final Set<DexType> removedClasses = Sets.newIdentityHashSet();

    public Builder setPrunedApp(DexApplication prunedApp) {
      this.prunedApp = prunedApp;
      return this;
    }

    public Builder addAdditionalPinnedItems(
        Collection<? extends DexReference> additionalPinnedItems) {
      this.additionalPinnedItems.addAll(additionalPinnedItems);
      return this;
    }

    public Builder addNoLongerSyntheticItems(Set<DexType> noLongerSyntheticItems) {
      this.noLongerSyntheticItems.addAll(noLongerSyntheticItems);
      return this;
    }

    public Builder addRemovedClasses(Set<DexType> removedClasses) {
      this.noLongerSyntheticItems.addAll(removedClasses);
      this.removedClasses.addAll(removedClasses);
      return this;
    }

    public PrunedItems build() {
      return new PrunedItems(
          prunedApp, additionalPinnedItems, noLongerSyntheticItems, removedClasses);
    }
  }
}
