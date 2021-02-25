// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnostic.internal;

import com.android.tools.r8.diagnostic.MissingDefinitionInfo;
import com.android.tools.r8.diagnostic.MissingDefinitionsDiagnostic;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class MissingDefinitionsDiagnosticImpl implements MissingDefinitionsDiagnostic {

  private final Collection<MissingDefinitionInfo> missingDefinitions;

  private MissingDefinitionsDiagnosticImpl(Collection<MissingDefinitionInfo> missingDefinitions) {
    assert !missingDefinitions.isEmpty();
    this.missingDefinitions = missingDefinitions;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public Collection<MissingDefinitionInfo> getMissingDefinitions() {
    return getMissingDefinitionsWithDeterministicOrder();
  }

  private Collection<MissingDefinitionInfo> getMissingDefinitionsWithDeterministicOrder() {
    List<MissingDefinitionInfo> missingDefinitionsWithDeterministicOrder =
        new ArrayList<>(missingDefinitions);
    missingDefinitionsWithDeterministicOrder.sort(MissingDefinitionInfoUtils.getComparator());
    return missingDefinitionsWithDeterministicOrder;
  }

  /** A missing class(es) failure can generally not be attributed to a single origin. */
  @Override
  public Origin getOrigin() {
    return Origin.unknown();
  }

  /** A missing class(es) failure can generally not be attributed to a single position. */
  @Override
  public Position getPosition() {
    return Position.UNKNOWN;
  }

  @Override
  public String getDiagnosticMessage() {
    StringBuilder builder = new StringBuilder();
    Iterator<MissingDefinitionInfo> missingDefinitionsIterator =
        getMissingDefinitionsWithDeterministicOrder().iterator();

    // The diagnostic is always non-empty.
    assert missingDefinitionsIterator.hasNext();

    // Write first line.
    MissingDefinitionInfoUtils.writeDiagnosticMessage(builder, missingDefinitionsIterator.next());

    // Write remaining lines with line separator before.
    missingDefinitionsIterator.forEachRemaining(
        missingDefinition ->
            MissingDefinitionInfoUtils.writeDiagnosticMessage(
                builder.append(System.lineSeparator()), missingDefinition));

    return builder.toString();
  }

  public static class Builder {

    private ImmutableList.Builder<MissingDefinitionInfo> missingDefinitionsBuilder =
        ImmutableList.builder();

    private Builder() {}

    public Builder addMissingDefinitionInfo(MissingDefinitionInfo missingDefinition) {
      missingDefinitionsBuilder.add(missingDefinition);
      return this;
    }

    public MissingDefinitionsDiagnostic build() {
      return new MissingDefinitionsDiagnosticImpl(missingDefinitionsBuilder.build());
    }
  }
}
