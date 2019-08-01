// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.ir.analysis.proto.schema.ProtoFieldTypeFactory;
import com.android.tools.r8.ir.analysis.proto.schema.ProtoMessageInfo;
import com.android.tools.r8.ir.analysis.proto.schema.ProtoObject;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlock.ThrowingInfo;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.List;

public class GeneratedMessageLiteShrinker {

  private final AppView<AppInfoWithLiveness> appView;
  private final RawMessageInfoDecoder decoder;
  private final RawMessageInfoEncoder encoder;
  private final ProtoReferences references;
  private final ThrowingInfo throwingInfo;

  private final TypeLatticeElement objectArrayType;
  private final TypeLatticeElement stringType;

  private final ProtoFieldTypeFactory factory = new ProtoFieldTypeFactory();

  public GeneratedMessageLiteShrinker(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.decoder = new RawMessageInfoDecoder(factory);
    this.encoder = new RawMessageInfoEncoder(appView.dexItemFactory());
    this.references = appView.protoShrinker().references;
    this.throwingInfo = ThrowingInfo.defaultForConstString(appView.options());

    // Types.
    this.objectArrayType =
        TypeLatticeElement.fromDexType(
            appView.dexItemFactory().objectArrayType, Nullability.definitelyNotNull(), appView);
    this.stringType = TypeLatticeElement.stringClassType(appView, Nullability.definitelyNotNull());
  }

  public void run(DexEncodedMethod method, IRCode code) {
    if (appView.options().isMinifying() && references.isDynamicMethod(method.method)) {
      rewriteDynamicMethod(method, code);
    }
  }

  /**
   * Finds all const-string instructions in the code that flows into GeneratedMessageLite.
   * newMessageInfo(), and rewrites them into a dex-item-based-const-string if the string value
   * corresponds to the name of an instance field of the enclosing class.
   *
   * <p>NOTE: This is work in progress. Understanding the full semantics of the arguments passed to
   * newMessageInfo is still pending.
   */
  private void rewriteDynamicMethod(DexEncodedMethod method, IRCode code) {
    DexClass context = appView.definitionFor(method.method.holder);
    if (context == null || !context.isProgramClass()) {
      return;
    }

    InvokeMethod newMessageInfoInvoke = null;
    for (Instruction instruction : code.instructions()) {
      if (instruction.isInvokeStatic()) {
        InvokeStatic invoke = instruction.asInvokeStatic();
        if (invoke.getInvokedMethod() == references.newMessageInfoMethod
            || invoke.getInvokedMethod() == references.rawMessageInfoConstructor) {
          newMessageInfoInvoke = invoke;
          break;
        }
      }
    }

    if (newMessageInfoInvoke != null) {
      Value infoValue = newMessageInfoInvoke.inValues().get(1).getAliasedValue();
      Value objectsValue = newMessageInfoInvoke.inValues().get(2).getAliasedValue();

      // Decode the arguments passed to newMessageInfo().
      ProtoMessageInfo protoMessageInfo = decoder.run(infoValue, objectsValue, context);
      if (protoMessageInfo != null) {
        // Rewrite the arguments to newMessageInfo().
        rewriteArgumentsToNewMessageInfo(
            method, code, newMessageInfoInvoke, infoValue, protoMessageInfo);

        // TODO(b/112437944): Need to ensure that the definition of the original `objects` value is
        //  removed by dead code elimination.
      } else {
        // We should generally be able to decode the arguments passed to newMessageInfo().
        assert false;
      }
    }
  }

  private void rewriteArgumentsToNewMessageInfo(
      DexEncodedMethod method,
      IRCode code,
      InvokeMethod newMessageInfoInvoke,
      Value infoValue,
      ProtoMessageInfo protoMessageInfo) {
    rewriteInfoArgumentToNewMessageInfo(code, infoValue, protoMessageInfo);
    rewriteObjectsArgumentToNewMessageInfo(method, code, newMessageInfoInvoke, protoMessageInfo);
  }

  private void rewriteInfoArgumentToNewMessageInfo(
      IRCode code, Value infoValue, ProtoMessageInfo protoMessageInfo) {
    infoValue.definition.replace(
        new ConstString(
            code.createValue(stringType), encoder.encodeInfo(protoMessageInfo), throwingInfo),
        code);
  }

  private void rewriteObjectsArgumentToNewMessageInfo(
      DexEncodedMethod method,
      IRCode code,
      InvokeMethod newMessageInfoInvoke,
      ProtoMessageInfo protoMessageInfo) {
    // Position iterator immediately before the call to newMessageInfo().
    BasicBlock block = newMessageInfoInvoke.getBlock();
    InstructionListIterator instructionIterator = block.listIterator(code, newMessageInfoInvoke);
    Instruction previous = instructionIterator.previous();
    instructionIterator.setInsertionPosition(newMessageInfoInvoke.getPosition());
    assert previous == newMessageInfoInvoke;

    // Create the `objects` array.
    List<ProtoObject> objects = encoder.encodeObjects(protoMessageInfo);
    Value sizeValue =
        instructionIterator.insertConstIntInstruction(code, appView.options(), objects.size());
    Value newObjectsValue = code.createValue(objectArrayType);
    instructionIterator.add(
        new NewArrayEmpty(newObjectsValue, sizeValue, appView.dexItemFactory().objectArrayType));

    // Populate the `objects` array.
    boolean hasIntroducedIdentifierNameString = false;
    for (int i = 0; i < objects.size(); i++) {
      Value indexValue = instructionIterator.insertConstIntInstruction(code, appView.options(), i);
      Instruction materializingInstruction = objects.get(i).buildIR(appView, code);
      instructionIterator.add(materializingInstruction);
      instructionIterator.add(
          new ArrayPut(
              MemberType.OBJECT, newObjectsValue, indexValue, materializingInstruction.outValue()));

      if (materializingInstruction.isDexItemBasedConstString()) {
        hasIntroducedIdentifierNameString = true;
      }
    }

    // Pass the newly created `objects` array to newMessageInfo().
    newMessageInfoInvoke.replaceValue(2, newObjectsValue);

    if (hasIntroducedIdentifierNameString) {
      method.getMutableOptimizationInfo().markUseIdentifierNameString();
    }
  }
}
