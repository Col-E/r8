// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.synthesis.CommittedItems;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Set;

public class MissingClasses {

  private final Set<DexType> missingClasses;

  private MissingClasses(Set<DexType> missingClasses) {
    this.missingClasses = missingClasses;
  }

  public Builder builder() {
    return new Builder(missingClasses);
  }

  public static Builder builderForInitialMissingClasses() {
    return new Builder();
  }

  public static MissingClasses empty() {
    return new MissingClasses(Sets.newIdentityHashSet());
  }

  public MissingClasses commitSyntheticItems(CommittedItems committedItems) {
    return builder()
        // TODO(b/175542052): Synthetic types should not be reported as missing in the first place.
        .removeAlreadyMissingClasses(committedItems.getLegacySyntheticTypes())
        .ignoreMissingClasses();
  }

  public boolean contains(DexType type) {
    return missingClasses.contains(type);
  }

  public static class Builder {

    private final Set<DexType> alreadyMissingClasses;
    private final Set<DexType> newMissingClasses = Sets.newIdentityHashSet();

    // Set of missing types that are not to be reported as missing. This does not hide reports
    // if the same type is in newMissingClasses in which case it is reported regardless.
    private final Set<DexType> newIgnoredMissingClasses = Sets.newIdentityHashSet();

    private Builder() {
      this(Sets.newIdentityHashSet());
    }

    private Builder(Set<DexType> alreadyMissingClasses) {
      this.alreadyMissingClasses = alreadyMissingClasses;
    }

    public void addNewMissingClass(DexType type) {
      newMissingClasses.add(type);
    }

    public Builder addNewMissingClasses(Collection<DexType> types) {
      newMissingClasses.addAll(types);
      return this;
    }

    public void ignoreNewMissingClass(DexType type) {
      newIgnoredMissingClasses.add(type);
    }

    public boolean contains(DexType type) {
      return alreadyMissingClasses.contains(type) || newMissingClasses.contains(type);
    }

    Builder removeAlreadyMissingClasses(Iterable<DexType> types) {
      for (DexType type : types) {
        alreadyMissingClasses.remove(type);
      }
      return this;
    }

    @Deprecated
    public MissingClasses ignoreMissingClasses() {
      return build();
    }

    public MissingClasses reportMissingClasses(InternalOptions options) {
      Set<DexType> newMissingClassesWithoutDontWarn =
          options.getProguardConfiguration().getDontWarnPatterns().getNonMatches(newMissingClasses);
      newMissingClassesWithoutDontWarn.removeAll(alreadyMissingClasses);
      newMissingClassesWithoutDontWarn.removeAll(
          getAllowedMissingClasses(options.dexItemFactory()));
      if (!newMissingClassesWithoutDontWarn.isEmpty()) {
        MissingClassesDiagnostic diagnostic =
            new MissingClassesDiagnostic.Builder()
                .addMissingClasses(newMissingClassesWithoutDontWarn)
                .setFatal(!options.ignoreMissingClasses)
                .build();
        if (options.ignoreMissingClasses) {
          options.reporter.warning(diagnostic);
        } else {
          throw options.reporter.fatalError(diagnostic);
        }
      }
      return build();
    }

    private static Collection<DexType> getAllowedMissingClasses(DexItemFactory dexItemFactory) {
      return ImmutableList.of(
          dexItemFactory.annotationDefault,
          dexItemFactory.annotationMethodParameters,
          dexItemFactory.annotationSourceDebugExtension,
          dexItemFactory.annotationThrows,
          // TODO(b/176133674) StringConcatFactory is backported, but the class is reported as
          //  missing because the enqueuer runs prior to backporting and thus sees the non-desugared
          //  code.
          dexItemFactory.stringConcatFactoryType);
    }

    /** Intentionally private, use {@link Builder#reportMissingClasses(InternalOptions)}. */
    private MissingClasses build() {
      // Extend the newMissingClasses set with all other missing classes.
      //
      // We also add newIgnoredMissingClasses to newMissingClasses to be able to assert that we have
      // a closed world after the first round of tree shaking: we should never lookup a class that
      // was not live or missing during the first round of tree shaking.
      // See also AppInfoWithLiveness.definitionFor().
      //
      // Note: At this point, all missing classes in newMissingClasses have already been reported.
      // Thus adding newIgnoredMissingClasses to newMissingClasses will not lead to reports for the
      // classes in newIgnoredMissingClasses.
      newMissingClasses.addAll(alreadyMissingClasses);
      newMissingClasses.addAll(newIgnoredMissingClasses);
      return new MissingClasses(newMissingClasses);
    }

    public boolean wasAlreadyMissing(DexType type) {
      return alreadyMissingClasses.contains(type);
    }
  }
}
