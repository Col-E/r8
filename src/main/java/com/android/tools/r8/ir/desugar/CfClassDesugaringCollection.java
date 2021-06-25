// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.ir.desugar.records.RecordRewriter;

/** Interface for desugaring a class. */
public abstract class CfClassDesugaringCollection {

  public abstract void desugar(DexProgramClass clazz, CfClassDesugaringEventConsumer eventConsumer);

  /** Returns true if the given class needs desugaring. */
  public abstract boolean needsDesugaring(DexProgramClass clazz);

  public abstract boolean isEmpty();

  public static class NonEmptyCfClassDesugaringCollection extends CfClassDesugaringCollection {
    private final RecordRewriter recordRewriter;

    NonEmptyCfClassDesugaringCollection(RecordRewriter recordRewriter) {
      this.recordRewriter = recordRewriter;
    }

    @Override
    public void desugar(DexProgramClass clazz, CfClassDesugaringEventConsumer eventConsumer) {
      recordRewriter.desugar(clazz, eventConsumer);
    }

    @Override
    public boolean needsDesugaring(DexProgramClass clazz) {
      return recordRewriter.needsDesugaring(clazz);
    }

    @Override
    public boolean isEmpty() {
      return false;
    }
  }

  public static class EmptyCfClassDesugaringCollection extends CfClassDesugaringCollection {
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
