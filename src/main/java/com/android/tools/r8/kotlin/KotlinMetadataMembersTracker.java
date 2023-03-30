// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.IterableUtils;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import java.util.Set;

public class KotlinMetadataMembersTracker {

  private int count;
  // Only used for asserting equality during local testing.
  private final Set<DexMember<?, ?>> references;

  public KotlinMetadataMembersTracker(AppView<?> appView) {
    references = appView.options().testing.enableTestAssertions ? Sets.newIdentityHashSet() : null;
  }

  public void add(DexMember<?, ?> reference) {
    count += 1;
    if (references != null) {
      references.add(reference);
    }
  }

  public boolean isEqual(KotlinMetadataMembersTracker tracker, AppView<?> appView) {
    if (count != tracker.count) {
      return false;
    }
    if (references != null) {
      assert tracker.references != null;
      assert references.size() == tracker.references.size();
      SetView<DexMember<?, ?>> diffComparedToRewritten =
          Sets.difference(references, tracker.references);
      if (!diffComparedToRewritten.isEmpty()) {
        SetView<DexMember<?, ?>> diffComparedToOriginal =
            Sets.difference(tracker.references, references);
        // Known kotlin types may not exist directly in the way they are annotated in the metadata.
        // As an example kotlin.Function2 exists in the metadata but the concrete type is
        // kotlin.jvm.functions.Function2. As a result we may not rewrite metadata but the
        // underlying types are changed.
        diffComparedToRewritten.forEach(
            diff -> {
              DexMember<?, ?> rewrittenReference =
                  appView
                      .graphLens()
                      .getRenamedMemberSignature(diff, appView.getKotlinMetadataLens());
              assert diffComparedToOriginal.contains(rewrittenReference);
              assert IterableUtils.findOrDefault(
                      diff.getReferencedTypes(), type -> isKotlinJvmType(appView, type), null)
                  != null;
            });
      }
    }
    return true;
  }

  private boolean isKotlinJvmType(AppView<?> appView, DexType type) {
    return type.descriptor.startsWith(appView.dexItemFactory().kotlin.kotlinJvmTypePrefix);
  }
}
