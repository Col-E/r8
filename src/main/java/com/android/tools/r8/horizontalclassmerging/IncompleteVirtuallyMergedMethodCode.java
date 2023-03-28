// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfInstanceFieldRead;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfSwitch;
import com.android.tools.r8.cf.code.CfSwitch.Kind;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.horizontalclassmerging.VirtualMethodMerger.SuperMethodReference;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.IterableUtils;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import it.unimi.dsi.fastutil.ints.IntBidirectionalIterator;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;

/**
 * A short-lived piece of code that will be converted into {@link CfCode} using the method {@link
 * IncompleteHorizontalClassMergerCode#toCfCode(AppView, ProgramMethod,
 * HorizontalClassMergerGraphLens)}.
 */
public class IncompleteVirtuallyMergedMethodCode extends IncompleteHorizontalClassMergerCode {

  private final DexField classIdField;
  private final Int2ReferenceSortedMap<DexMethod> mappedMethods;
  private final DexMethod originalMethod;
  private final SuperMethodReference superMethod;

  public IncompleteVirtuallyMergedMethodCode(
      DexField classIdField,
      Int2ReferenceSortedMap<DexMethod> mappedMethods,
      DexMethod originalMethod,
      SuperMethodReference superMethod) {
    this.mappedMethods = mappedMethods;
    this.classIdField = classIdField;
    this.superMethod = superMethod;
    this.originalMethod = originalMethod;
  }

  /**
   * Given a mapping from class ids to methods to invoke, this creates a piece of {@link CfCode} on
   * the following form.
   *
   * <pre>
   *   public Bar m(Foo foo) {
   *     switch (this.classId) {
   *       case 0:
   *         return this.m1(foo);
   *       case 1:
   *         return this.m2(foo);
   *       ...
   *       default:
   *         return this.mN(foo); // or super.m(foo);
   *     }
   *   }
   * </pre>
   *
   * <p>Note that the methods to invoke must be rewritten using {@param lens}, since the invoked
   * methods may be changed as a result of the horizontal class merger's fixup (e.g., if the method
   * signature refers to a horizontally merged type).
   */
  @Override
  public CfCode toCfCode(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ProgramMethod method,
      HorizontalClassMergerGraphLens lens) {
    // We store each argument in a local.
    int maxLocals = 1 + IterableUtils.sumInt(method.getParameters(), DexType::getRequiredRegisters);

    // We load all arguments on the stack and then the receiver to fetch the class id.
    int maxStack = maxLocals + 1;

    // Create instructions.
    List<CfInstruction> instructions = new ArrayList<>();

    // Setup keys and labels for switch.
    IntBidirectionalIterator classIdIterator = mappedMethods.keySet().iterator();
    int[] keys = new int[mappedMethods.size() - BooleanUtils.intValue(superMethod == null)];
    List<CfLabel> labels = new ArrayList<>();
    for (int key = 0; key < keys.length; key++) {
      keys[key] = classIdIterator.nextInt();
      labels.add(new CfLabel());
    }
    CfLabel fallthroughLabel = new CfLabel();

    // Add instructions.
    instructions.add(new CfLoad(ValueType.OBJECT, 0));
    int localIndex = 1;
    for (DexType parameter : method.getParameters()) {
      instructions.add(new CfLoad(ValueType.fromDexType(parameter), localIndex));
      localIndex += parameter.getRequiredRegisters();
    }
    instructions.add(new CfLoad(ValueType.OBJECT, 0));
    instructions.add(new CfInstanceFieldRead(classIdField));

    // Emit switch.
    instructions.add(new CfSwitch(Kind.LOOKUP, fallthroughLabel, keys, labels));
    for (int key = 0; key < keys.length; key++) {
      int classId = keys[key];
      DexMethod target = lens.getNextMethodSignature(mappedMethods.get(classId));
      instructions.add(labels.get(key));
      instructions.add(createCfFrameForSwitchCase(method, maxLocals));
      instructions.add(
          new CfInvoke(Opcodes.INVOKESPECIAL, target, method.getHolder().isInterface()));
      if (method.getReturnType().isVoidType()) {
        instructions.add(new CfReturnVoid());
      } else {
        instructions.add(new CfReturn(ValueType.fromDexType(method.getReturnType())));
      }
    }

    // Emit fallthrough.
    instructions.add(fallthroughLabel);
    instructions.add(createCfFrameForSwitchCase(method, maxLocals));

    DexMethod fallthroughTarget;
    if (superMethod == null) {
      fallthroughTarget =
          lens.getNextMethodSignature(mappedMethods.get(mappedMethods.lastIntKey()));
    } else {
      DexMethod reboundFallthroughTarget =
          lens.lookupInvokeSuper(superMethod.getReboundReference(), method).getReference();
      fallthroughTarget =
          reboundFallthroughTarget.withHolder(
              lens.lookupClassType(superMethod.getReference().getHolderType()),
              appView.dexItemFactory());
    }
    instructions.add(
        new CfInvoke(Opcodes.INVOKESPECIAL, fallthroughTarget, method.getHolder().isInterface()));

    // Emit return.
    if (method.getReturnType().isVoidType()) {
      instructions.add(new CfReturnVoid());
    } else {
      instructions.add(new CfReturn(ValueType.fromDexType(method.getReturnType())));
    }
    return new CfCode(originalMethod.getHolderType(), maxStack, maxLocals, instructions) {

      @Override
      public GraphLens getCodeLens(AppView<?> appView) {
        return lens;
      }
    };
  }

  private static CfFrame createCfFrameForSwitchCase(ProgramMethod representative, int localsSize) {
    CfFrame.Builder builder =
        CfFrame.builder().allocateStack(representative.getDefinition().getNumberOfArguments());
    for (int argumentIndex = 0;
        argumentIndex < representative.getDefinition().getNumberOfArguments();
        argumentIndex++) {
      builder.push(FrameType.initialized(representative.getArgumentType(argumentIndex)));
    }
    for (int argumentIndex = 0;
        argumentIndex < representative.getDefinition().getNumberOfArguments();
        argumentIndex++) {
      builder.appendLocal(FrameType.initialized(representative.getArgumentType(argumentIndex)));
    }
    CfFrame frame = builder.build();
    assert frame.getLocals().size() == localsSize;
    return frame;
  }

  @Override
  public String toString() {
    return "IncompleteVirtuallyMergedMethodCode";
  }
}
