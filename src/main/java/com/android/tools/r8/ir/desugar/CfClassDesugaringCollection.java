// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.ir.desugar.records.RecordRewriter;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.List;

/** Interface for desugaring a class. */
public abstract class CfClassDesugaringCollection {

  public abstract void desugar(DexProgramClass clazz, CfClassDesugaringEventConsumer eventConsumer);

  /** Returns true if the given class needs desugaring. */
  public abstract boolean needsDesugaring(DexProgramClass clazz);

  public abstract boolean isEmpty();

  public static CfClassDesugaringCollection empty() {
    return EmptyCfClassDesugaringCollection.getInstance();
  }

  public static CfClassDesugaringCollection create(AppView<?> appView) {
    List<CfClassDesugaring> desugarings = new ArrayList<>();
    RecordRewriter recordRewriter = RecordRewriter.create(appView);
    if (recordRewriter != null) {
      desugarings.add(recordRewriter);
    }
    if (desugarings.isEmpty()) {
      return empty();
    }
    return new NonEmptyCfClassDesugaringCollection(desugarings);
  }

  public static class NonEmptyCfClassDesugaringCollection extends CfClassDesugaringCollection {
    private final List<CfClassDesugaring> desugarings;

    NonEmptyCfClassDesugaringCollection(List<CfClassDesugaring> desugarings) {
      this.desugarings = desugarings;
    }

    @Override
    public void desugar(DexProgramClass clazz, CfClassDesugaringEventConsumer eventConsumer) {
      for (CfClassDesugaring desugaring : desugarings) {
        desugaring.desugar(clazz, eventConsumer);
      }
    }

    @Override
    public boolean needsDesugaring(DexProgramClass clazz) {
      return Iterables.any(desugarings, desugaring -> desugaring.needsDesugaring(clazz));
    }

    @Override
    public boolean isEmpty() {
      return false;
    }
  }

  public static class EmptyCfClassDesugaringCollection extends CfClassDesugaringCollection {

    private static final EmptyCfClassDesugaringCollection INSTANCE =
        new EmptyCfClassDesugaringCollection();

    public static EmptyCfClassDesugaringCollection getInstance() {
      return INSTANCE;
    }

    @Override
    public void desugar(DexProgramClass clazz, CfClassDesugaringEventConsumer eventConsumer) {
      // Intentionally empty.
    }

    @Override
    public boolean needsDesugaring(DexProgramClass clazz) {
      return false;
    }

    @Override
    public boolean isEmpty() {
      return true;
    }
  }
}
