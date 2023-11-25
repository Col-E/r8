// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup.diagnostic;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.profile.startup.profile.StartupProfileClassRule;
import com.android.tools.r8.profile.startup.profile.StartupProfileMethodRule;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@KeepForApi
public class MissingStartupProfileItemsDiagnostic implements Diagnostic {

  private final List<DexReference> missingStartupItems;
  private final Origin origin;

  MissingStartupProfileItemsDiagnostic(List<DexReference> missingStartupItems, Origin origin) {
    assert !missingStartupItems.isEmpty();
    this.missingStartupItems = missingStartupItems;
    this.origin = origin;
  }

  @Override
  public Origin getOrigin() {
    return origin;
  }

  @Override
  public Position getPosition() {
    return Position.UNKNOWN;
  }

  @Override
  public String getDiagnosticMessage() {
    StringBuilder builder = new StringBuilder();
    Iterator<DexReference> missingStartupItemIterator = missingStartupItems.iterator();

    // Write first missing startup item.
    writeMissingStartupItem(builder, missingStartupItemIterator.next());

    // Write remaining missing startup items with line separator before.
    while (missingStartupItemIterator.hasNext()) {
      writeMissingStartupItem(
          builder.append(System.lineSeparator()), missingStartupItemIterator.next());
    }

    return builder.toString();
  }

  private void writeMissingStartupItem(StringBuilder builder, DexReference missingStartupItem) {
    missingStartupItem.apply(
        missingStartupClass -> builder.append("Startup class not found: "),
        missingStartupField -> builder.append("Startup field not found: "),
        missingStartupMethod -> builder.append("Startup method not found: "));
    builder.append(missingStartupItem.toSourceString());
  }

  public static class Builder {

    private final DexDefinitionSupplier definitions;
    private final Set<DexReference> missingStartupItems = Sets.newIdentityHashSet();

    private Origin origin;

    public Builder(DexDefinitionSupplier definitions) {
      this.definitions = definitions;
    }

    public static Builder nop() {
      return new Builder(null);
    }

    public boolean hasMissingStartupItems() {
      return !missingStartupItems.isEmpty();
    }

    public boolean registerStartupClass(StartupProfileClassRule startupClass) {
      if (definitions != null && !definitions.hasDefinitionFor(startupClass.getReference())) {
        addMissingStartupItem(startupClass.getReference());
        return true;
      }
      return false;
    }

    public boolean registerStartupMethod(StartupProfileMethodRule startupMethod) {
      if (definitions != null && !definitions.hasDefinitionFor(startupMethod.getReference())) {
        addMissingStartupItem(startupMethod.getReference());
        return true;
      }
      return false;
    }

    private void addMissingStartupItem(DexReference reference) {
      DexString jDollarDescriptorPrefix = definitions.dexItemFactory().jDollarDescriptorPrefix;
      if (!reference.getContextType().getDescriptor().startsWith(jDollarDescriptorPrefix)) {
        missingStartupItems.add(reference);
      }
    }

    public Builder setOrigin(Origin origin) {
      this.origin = origin;
      return this;
    }

    public MissingStartupProfileItemsDiagnostic build() {
      assert hasMissingStartupItems();
      List<DexReference> sortedMissingStartupItems = new ArrayList<>(missingStartupItems);
      sortedMissingStartupItems.sort(DexReference::compareTo);
      return new MissingStartupProfileItemsDiagnostic(sortedMissingStartupItems, origin);
    }
  }
}
