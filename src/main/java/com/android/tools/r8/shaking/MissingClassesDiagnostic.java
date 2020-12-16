// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.Keep;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.google.common.collect.ImmutableSortedSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;

@Keep
public class MissingClassesDiagnostic implements Diagnostic {

  private final boolean fatal;
  private final SortedSet<ClassReference> missingClasses;

  private MissingClassesDiagnostic(boolean fatal, SortedSet<ClassReference> missingClasses) {
    assert !missingClasses.isEmpty();
    this.fatal = fatal;
    this.missingClasses = missingClasses;
  }

  public Set<ClassReference> getMissingClasses() {
    return missingClasses;
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

  // TODO(b/175755807): Extend diagnostic message with contextual information.
  @Override
  public String getDiagnosticMessage() {
    return fatal ? getFatalDiagnosticMessage() : getNonFatalDiagnosticMessage();
  }

  private String getFatalDiagnosticMessage() {
    if (missingClasses.size() == 1) {
      return "Compilation can't be completed because the class "
          + missingClasses.iterator().next().getTypeName()
          + " is missing.";
    }
    StringBuilder builder =
        new StringBuilder("Compilation can't be completed because the following ")
            .append(missingClasses.size())
            .append(" classes are missing:");
    for (ClassReference missingClass : missingClasses) {
      builder.append(System.lineSeparator()).append("- ").append(missingClass.getTypeName());
    }
    return builder.toString();
  }

  private String getNonFatalDiagnosticMessage() {
    StringBuilder builder = new StringBuilder();
    Iterator<ClassReference> missingClassesIterator = missingClasses.iterator();
    while (missingClassesIterator.hasNext()) {
      ClassReference missingClass = missingClassesIterator.next();
      builder.append("Missing class ").append(missingClass.getTypeName());
      if (missingClassesIterator.hasNext()) {
        builder.append(System.lineSeparator());
      }
    }
    return builder.toString();
  }

  public static class Builder {

    private boolean fatal;
    private ImmutableSortedSet.Builder<ClassReference> missingClassesBuilder =
        ImmutableSortedSet.orderedBy(Comparator.comparing(ClassReference::getDescriptor));

    public MissingClassesDiagnostic.Builder addMissingClasses(Collection<DexType> missingClasses) {
      for (DexType missingClass : missingClasses) {
        missingClassesBuilder.add(Reference.classFromDescriptor(missingClass.toDescriptorString()));
      }
      return this;
    }

    public MissingClassesDiagnostic.Builder setFatal(boolean fatal) {
      this.fatal = fatal;
      return this;
    }

    public MissingClassesDiagnostic build() {
      return new MissingClassesDiagnostic(fatal, missingClassesBuilder.build());
    }
  }
}
