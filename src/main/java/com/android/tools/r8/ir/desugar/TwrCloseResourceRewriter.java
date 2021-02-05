// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.desugar.backports.BackportedMethods;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.DesugarState;
import com.google.common.base.Suppliers;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.objectweb.asm.Opcodes;

// Try with resources close-resource desugaring.
//
// Rewrites $closeResource methods synthesized by java compilers to work on older DEX runtimes.
// All invocations to $closeResource methods are rewritten to target a compiler generated version
// that is correct on older DEX runtimes where not all types implement AutoClosable as expected.
//
// Note that we don't remove $closeResource(...) synthesized by java compiler, relying on
// tree shaking to remove them since now they should not be referenced.
// TODO(b/177401708): D8 does not tree shake so we should remove the now unused method.
public final class TwrCloseResourceRewriter {

  private final AppView<?> appView;
  private final DexProto twrCloseResourceProto;
  private final List<ProgramMethod> synthesizedMethods = new ArrayList<>();

  public static boolean enableTwrCloseResourceDesugaring(InternalOptions options) {
    return options.desugarState == DesugarState.ON
        && options.enableTryWithResourcesDesugaring()
        && !options.canUseTwrCloseResourceMethod();
  }

  public TwrCloseResourceRewriter(AppView<?> appView) {
    this.appView = appView;
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    twrCloseResourceProto =
        dexItemFactory.createProto(
            dexItemFactory.voidType, dexItemFactory.throwableType, dexItemFactory.objectType);
  }

  public int rewriteCf(
      ProgramMethod method,
      Consumer<ProgramMethod> newMethodCallback,
      MethodProcessingContext methodProcessingContext) {
    CfCode code = method.getDefinition().getCode().asCfCode();
    List<CfInstruction> instructions = code.getInstructions();
    Supplier<List<CfInstruction>> lazyNewInstructions =
        Suppliers.memoize(() -> new ArrayList<>(instructions));
    int replaced = 0;
    int newInstructionDelta = 0;
    for (int i = 0; i < instructions.size(); i++) {
      CfInvoke invoke = instructions.get(i).asInvoke();
      if (invoke == null
          || invoke.getOpcode() != Opcodes.INVOKESTATIC
          || !isTwrCloseResourceMethod(invoke.getMethod(), appView.dexItemFactory())) {
        continue;
      }
      // Synthesize a new method.
      ProgramMethod closeMethod = createSyntheticCloseResourceMethod(methodProcessingContext);
      newMethodCallback.accept(closeMethod);
      // Rewrite the invoke to the new synthetic.
      int newInstructionIndex = i + newInstructionDelta;
      lazyNewInstructions
          .get()
          .set(
              newInstructionIndex,
              new CfInvoke(Opcodes.INVOKESTATIC, closeMethod.getReference(), false));
      ++replaced;
    }
    if (replaced > 0) {
      code.setInstructions(lazyNewInstructions.get());
    }
    return replaced;
  }

  // Rewrites calls to $closeResource() method. Can be invoked concurrently.
  public void rewriteIR(IRCode code, MethodProcessingContext methodProcessingContext) {
    InstructionListIterator iterator = code.instructionListIterator();
    while (iterator.hasNext()) {
      InvokeStatic invoke = iterator.next().asInvokeStatic();
      if (invoke == null
          || !isTwrCloseResourceMethod(invoke.getInvokedMethod(), appView.dexItemFactory())) {
        continue;
      }

      // Replace with a call to a synthetic utility.
      assert invoke.outValue() == null;
      assert invoke.inValues().size() == 2;
      ProgramMethod closeResourceMethod =
          createSyntheticCloseResourceMethod(methodProcessingContext);
      InvokeStatic newInvoke =
          new InvokeStatic(closeResourceMethod.getReference(), null, invoke.inValues());
      iterator.replaceCurrentInstruction(newInvoke);
      synchronized (synthesizedMethods) {
        synthesizedMethods.add(closeResourceMethod);
      }
    }
  }

  public static boolean isTwrCloseResourceMethod(DexMethod method, DexItemFactory factory) {
    return method.name == factory.twrCloseResourceMethodName
        && method.proto == factory.twrCloseResourceMethodProto;
  }

  private ProgramMethod createSyntheticCloseResourceMethod(
      MethodProcessingContext methodProcessingContext) {
    return appView
        .getSyntheticItems()
        .createMethod(
            SyntheticKind.TWR_CLOSE_RESOURCE,
            methodProcessingContext.createUniqueContext(),
            appView.dexItemFactory(),
            methodBuilder ->
                methodBuilder
                    .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                    .setProto(twrCloseResourceProto)
                    .setCode(
                        m ->
                            BackportedMethods.CloseResourceMethod_closeResourceImpl(
                                appView.options(), m)));
  }

  public void processSynthesizedMethods(IRConverter converter) {
    synthesizedMethods.forEach(converter::optimizeSynthesizedMethod);
  }
}
