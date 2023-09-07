// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.ProgramMethod;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A description of the desugaring task.
 *
 * <p>The description encodes if there are any side effects that must be done as part of the
 * desugaring checks (such as issue diagnostics) as well as if a given instruction needs to be
 * desugared, and if so, how to desugar it. The process of computing a description should be
 * side-effect free. All side effects should be done either in the 'scan' or the
 * 'desugarInstruction' callbacks.
 */
public class DesugarDescription {

  private static final DesugarDescription NOTHING = new DesugarDescription();

  private DesugarDescription() {}

  public void scan() {}

  public boolean needsDesugaring() {
    return false;
  }

  public Collection<CfInstruction> desugarInstruction(
      FreshLocalProvider freshLocalProvider,
      LocalStackAllocator localStackAllocator,
      CfDesugaringInfo desugaringInfo,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext,
      CfInstructionDesugaringCollection desugaringCollection,
      DexItemFactory dexItemFactory) {
    return null;
  }

  public static DesugarDescription nothing() {
    assert NOTHING == builder().build();
    return NOTHING;
  }

  public static Builder builder() {
    return InitialBuilder.getInstance();
  }

  @FunctionalInterface
  public interface ScanCallback {
    void scan();
  }

  @FunctionalInterface
  public interface DesugarCallback {
    Collection<CfInstruction> desugarInstruction(
        FreshLocalProvider freshLocalProvider,
        LocalStackAllocator localStackAllocator,
        CfDesugaringInfo desugaringInfo,
        CfInstructionDesugaringEventConsumer eventConsumer,
        ProgramMethod context,
        MethodProcessingContext methodProcessingContext,
        CfInstructionDesugaringCollection desugaringCollection,
        DexItemFactory dexItemFactory);
  }

  public abstract static class Builder {
    public abstract DesugarDescription build();

    public abstract Builder addScanEffect(ScanCallback callback);

    public abstract Builder setDesugarRewrite(DesugarCallback callback);
  }

  /**
   * Initial builder is an empty singleton. Any actual change will result in the allocation of a
   * non-empty builder. This ensures that the trivial case has zero allocation overhead.
   */
  static class InitialBuilder extends Builder {
    static final InitialBuilder INSTANCE = new InitialBuilder();

    public static InitialBuilder getInstance() {
      return INSTANCE;
    }

    @Override
    public DesugarDescription build() {
      return NOTHING;
    }

    @Override
    public Builder addScanEffect(ScanCallback callback) {
      return new NonEmptyBuilder().addScanEffect(callback);
    }

    @Override
    public Builder setDesugarRewrite(DesugarCallback callback) {
      return new NonEmptyBuilder().setDesugarRewrite(callback);
    }
  }

  static class NonEmptyBuilder extends Builder {

    List<ScanCallback> scanEffects = new ArrayList<>();
    DesugarCallback desugarRewrite = null;

    @Override
    public Builder addScanEffect(ScanCallback callback) {
      assert callback != null;
      scanEffects.add(callback);
      return this;
    }

    @Override
    public Builder setDesugarRewrite(DesugarCallback desugarRewrite) {
      assert this.desugarRewrite == null;
      assert desugarRewrite != null;
      this.desugarRewrite = desugarRewrite;
      return this;
    }

    @Override
    public DesugarDescription build() {
      return new DesugarDescription() {
        @Override
        public void scan() {
          scanEffects.forEach(ScanCallback::scan);
        }

        @Override
        public boolean needsDesugaring() {
          return desugarRewrite != null;
        }

        @Override
        public Collection<CfInstruction> desugarInstruction(
            FreshLocalProvider freshLocalProvider,
            LocalStackAllocator localStackAllocator,
            CfDesugaringInfo desugaringInfo,
            CfInstructionDesugaringEventConsumer eventConsumer,
            ProgramMethod context,
            MethodProcessingContext methodProcessingContext,
            CfInstructionDesugaringCollection desugaringCollection,
            DexItemFactory dexItemFactory) {
          return desugarRewrite == null
              ? null
              : desugarRewrite.desugarInstruction(
                  freshLocalProvider,
                  localStackAllocator,
                  desugaringInfo,
                  eventConsumer,
                  context,
                  methodProcessingContext,
                  desugaringCollection,
                  dexItemFactory);
        }
      };
    }
  }
}
