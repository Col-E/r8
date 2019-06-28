// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.ir.analysis.proto.schema.ProtoFieldTypeFactory;
import com.android.tools.r8.ir.analysis.proto.schema.ProtoMessageInfo;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.BasicBlock.ThrowingInfo;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.DexItemBasedConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.naming.dexitembasedstring.FieldNameComputationInfo;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import java.util.ArrayList;
import java.util.List;

public class GeneratedMessageLiteShrinker {

  private final AppView<AppInfoWithLiveness> appView;
  private final RawMessageInfoDecoder decoder;
  private final RawMessageInfoEncoder encoder;
  private final ProtoReferences references;
  private final TypeLatticeElement stringType;
  private final ThrowingInfo throwingInfo;

  private final ProtoFieldTypeFactory factory = new ProtoFieldTypeFactory();

  public GeneratedMessageLiteShrinker(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.decoder = new RawMessageInfoDecoder(factory);
    this.encoder = new RawMessageInfoEncoder(appView.dexItemFactory());
    this.references = appView.protoShrinker().references;
    this.stringType = TypeLatticeElement.stringClassType(appView, Nullability.definitelyNotNull());
    this.throwingInfo = ThrowingInfo.defaultForConstString(appView.options());
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
    DexClass clazz = appView.definitionFor(method.method.holder);
    if (clazz == null || !clazz.isProgramClass()) {
      return;
    }

    InvokeStatic newMessageInfoInvoke = null;
    for (Instruction instruction : code.instructions()) {
      if (instruction.isInvokeStatic()) {
        InvokeStatic invoke = instruction.asInvokeStatic();
        if (invoke.getInvokedMethod() == references.newMessageInfoMethod) {
          newMessageInfoInvoke = invoke;
          break;
        }
      }

      // Implicitly check that the method newMessageInfo() has not been inlined. In that case,
      // we would need to rewrite the const-string instructions that flow into the constructor
      // of com.google.protobuf.RawMessageInfo.
      assert !instruction.isNewInstance()
          || instruction.asNewInstance().clazz != references.rawMessageInfoType;
    }

    if (newMessageInfoInvoke != null) {
      Value infoValue = newMessageInfoInvoke.inValues().get(1).getAliasedValue();
      Value objectsValue = newMessageInfoInvoke.inValues().get(2).getAliasedValue();

      // TODO(b/112437944): If we regenerate the arguments to newMessageInfo() entirely, then we can
      //  simply generate DexItemBasedConstString instructions at that point. That way the block
      //  below will not be needed.
      {
        List<ConstString> identifierNameStringCandidates = new ArrayList<>();
        for (Instruction user : objectsValue.uniqueUsers()) {
          if (user.isArrayPut()) {
            Value rewritingCandidate = user.asArrayPut().value().getAliasedValue();
            if (!rewritingCandidate.isPhi() && rewritingCandidate.definition.isConstString()) {
              identifierNameStringCandidates.add(rewritingCandidate.definition.asConstString());
            }
          }
        }

        boolean changed = false;
        for (ConstString rewritingCandidate : identifierNameStringCandidates) {
          DexString fieldName = rewritingCandidate.getValue();
          DexField field = uniqueInstanceFieldWithName(clazz, fieldName, code.origin);
          if (field == null) {
            continue;
          }
          Value newValue = code.createValue(stringType);
          rewritingCandidate.replace(
              new DexItemBasedConstString(
                  newValue, field, FieldNameComputationInfo.forFieldName(), throwingInfo));
          changed = true;
        }

        if (changed) {
          method.getMutableOptimizationInfo().markUseIdentifierNameString();
        }
      }

      // Decode the arguments passed to newMessageInfo().
      ProtoMessageInfo protoMessageInfo = decoder.run(infoValue, objectsValue);
      if (protoMessageInfo != null) {
        // Rewrite the arguments to newMessageInfo().
        infoValue.definition.replace(
            new ConstString(
                code.createValue(stringType), encoder.encodeInfo(protoMessageInfo), throwingInfo));
      } else {
        // We should generally be able to decode the arguments passed to newMessageInfo().
        assert false;
      }
    }
  }

  private DexField uniqueInstanceFieldWithName(DexClass clazz, DexString name, Origin origin) {
    DexField field = null;
    for (DexEncodedField encodedField : clazz.instanceFields()) {
      if (encodedField.field.name == name) {
        if (field != null) {
          Reporter reporter = appView.options().reporter;
          String errorMessage =
              "Expected to find a single instance field named \""
                  + name.toSourceString()
                  + "\" in `"
                  + clazz.type.toSourceString()
                  + "`";
          throw reporter.fatalError(new StringDiagnostic(errorMessage, origin));
        }
        field = encodedField.field;
      }
    }
    return field;
  }
}
