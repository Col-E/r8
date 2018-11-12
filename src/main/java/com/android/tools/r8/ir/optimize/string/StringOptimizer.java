// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.ir.optimize.CodeRewriter.removeOrReplaceByDebugLocalWrite;
import static com.android.tools.r8.utils.DescriptorUtils.getCanonicalNameFromDescriptor;
import static com.android.tools.r8.utils.DescriptorUtils.getClassNameFromDescriptor;
import static com.android.tools.r8.utils.DescriptorUtils.getSimpleClassNameFromDescriptor;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.BasicBlock.ThrowingInfo;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.base.Strings;

public class StringOptimizer {

  final ThrowingInfo throwingInfo;

  public StringOptimizer(InternalOptions options) {
    throwingInfo = options.isGeneratingClassFiles()
        ? ThrowingInfo.NO_THROW : ThrowingInfo.CAN_THROW;
  }

  // Find String#length() with a constant string and compute the length of it at compile time.
  public void computeConstStringLength(IRCode code, DexItemFactory factory) {
    // TODO(jsjeon): is it worth having an indicator of String#length()?
    if (!code.hasConstString) {
      return;
    }
    InstructionIterator it = code.instructionIterator();
    while (it.hasNext()) {
      Instruction instr = it.next();
      if (!instr.isInvokeVirtual()) {
        continue;
      }
      InvokeVirtual invoke = instr.asInvokeVirtual();
      DexMethod invokedMethod = invoke.getInvokedMethod();
      if (invokedMethod != factory.stringMethods.length) {
        continue;
      }
      assert invoke.inValues().size() == 1;
      Value in = invoke.getReceiver().getAliasedValue();
      if (in.definition == null
          || !in.definition.isConstString()
          || !in.isConstant()) {
        continue;
      }
      ConstString constString = in.definition.asConstString();
      int length = constString.getValue().toString().length();
      ConstNumber constNumber = code.createIntConstant(length);
      it.replaceCurrentInstruction(constNumber);
    }
  }

  // Find Class#get*Name() with a constant-class and replace it with a const-string if possible.
  public void rewriteClassGetName(IRCode code, AppInfo appInfo) {
    InstructionIterator it = code.instructionIterator();
    while (it.hasNext()) {
      Instruction instr = it.next();
      if (!instr.isInvokeVirtual()) {
        continue;
      }
      InvokeVirtual invoke = instr.asInvokeVirtual();
      DexMethod invokedMethod = invoke.getInvokedMethod();
      if (!appInfo.dexItemFactory.classMethods.isReflectiveNameLookup(invokedMethod)) {
        continue;
      }
      assert invoke.inValues().size() == 1;
      // In case of handling multiple invocations over the same const-string, all the following
      // usages after the initial one will point to non-null IR (a.k.a. alias), e.g.,
      //
      //   rcv <- invoke-virtual instance, ...#getClass() // Can be rewritten to const-class
      //   x <- invoke-virtual rcv, Class#getName()
      //   non_null_rcv <- non-null rcv
      //   y <- invoke-virtual non_null_rcv, Class#getCanonicalName()
      //   z <- invoke-virtual non_null_rcv, Class#getSimpleName()
      //   ... // or some other usages of the same usage.
      //
      // In that case, we should check if the original source is (possibly rewritten) const-class.
      Value in = invoke.getReceiver().getAliasedValue();
      if (in.definition == null
          || !in.definition.isConstClass()
          || !in.isConstant()) {
        continue;
      }

      ConstClass constClass = in.definition.asConstClass();
      DexType type = constClass.getValue();
      int arrayDepth = type.getNumberOfLeadingSquareBrackets();
      DexType baseType = type.toBaseType(appInfo.dexItemFactory);
      // Make sure base type is a class type.
      if (!baseType.isClassType()) {
        continue;
      }
      DexClass holder = appInfo.definitionFor(baseType);
      if (holder == null) {
        continue;
      }

      String name = null;
      if (invokedMethod == appInfo.dexItemFactory.classMethods.getName) {
        if (code.options.enableMinification) {
          // TODO(b/118536394): Add support minification and pinning.
          //   May need store array depth on DexItemBasedConstString.
          //   May need enum on DexItemBasedConstString to distinguish name computation.
          continue;
        }
        name = getClassNameFromDescriptor(baseType.toDescriptorString());
        if (arrayDepth > 0) {
          name = Strings.repeat("[", arrayDepth) + "L" + name + ";";
        }
      } else if (invokedMethod == appInfo.dexItemFactory.classMethods.getTypeName) {
        if (code.options.enableMinification) {
          // TODO(b/118536394): Add support minification and pinning.
          continue;
        }
        name = getClassNameFromDescriptor(baseType.toDescriptorString());
        if (arrayDepth > 0) {
          name = name + Strings.repeat("[]", arrayDepth);
        }
      } else if (invokedMethod == appInfo.dexItemFactory.classMethods.getCanonicalName) {
        // TODO(b/118536394): always returns "null"?
        if (holder.isLocalClass() || holder.isAnonymousClass()) {
          continue;
        }
        if (code.options.enableMinification) {
          // TODO(b/118536394): Add support minification and pinning.
          continue;
        }
        name = getCanonicalNameFromDescriptor(baseType.toDescriptorString());
        if (arrayDepth > 0) {
          name = name + Strings.repeat("[]", arrayDepth);
        }
      } else if (invokedMethod == appInfo.dexItemFactory.classMethods.getSimpleName) {
        // TODO(b/118536394): always returns ""?
        if (holder.isLocalClass() || holder.isAnonymousClass()) {
          continue;
        }
        if (code.options.enableMinification) {
          // TODO(b/118536394): Add support minification and pinning.
          continue;
        }
        name = getSimpleClassNameFromDescriptor(baseType.toDescriptorString());
        if (arrayDepth > 0) {
          name = name + Strings.repeat("[]", arrayDepth);
        }
      }
      if (name != null) {
        Value stringValue =
            code.createValue(TypeLatticeElement.stringClassType(appInfo), invoke.getLocalInfo());
        ConstString constString =
            new ConstString(stringValue, appInfo.dexItemFactory.createString(name), throwingInfo);
        it.replaceCurrentInstruction(constString);
      }
    }
  }

  // String#valueOf(null) -> "null"
  // String#valueOf(String s) -> s
  // str.toString() -> str
  public void removeTrivialConversions(IRCode code, AppInfo appInfo) {
    InstructionIterator it = code.instructionIterator();
    while (it.hasNext()) {
      Instruction instr = it.next();
      if (instr.isInvokeStatic()) {
        InvokeStatic invoke = instr.asInvokeStatic();
        DexMethod invokedMethod = invoke.getInvokedMethod();
        if (invokedMethod != appInfo.dexItemFactory.stringMethods.valueOf) {
          continue;
        }
        assert invoke.inValues().size() == 1;
        Value in = invoke.inValues().get(0);
        if (in.hasLocalInfo()) {
          continue;
        }
        TypeLatticeElement inType = in.getTypeLattice();
        if (inType.isNull()) {
          Value nullStringValue =
              code.createValue(TypeLatticeElement.stringClassType(appInfo), invoke.getLocalInfo());
          ConstString nullString = new ConstString(
              nullStringValue, appInfo.dexItemFactory.createString("null"), throwingInfo);
          it.replaceCurrentInstruction(nullString);
        } else if (inType.isClassType()
            && inType.asClassTypeLatticeElement().getClassType()
                .equals(appInfo.dexItemFactory.stringType)) {
          Value out = invoke.outValue();
          if (out != null) {
            removeOrReplaceByDebugLocalWrite(invoke, it, in, out);
          } else {
            it.removeOrReplaceByDebugLocalRead();
          }
        }
      } else if (instr.isInvokeVirtual()) {
        InvokeVirtual invoke = instr.asInvokeVirtual();
        DexMethod invokedMethod = invoke.getInvokedMethod();
        if (invokedMethod != appInfo.dexItemFactory.stringMethods.toString) {
          continue;
        }
        assert invoke.inValues().size() == 1;
        Value in = invoke.getReceiver();
        TypeLatticeElement inType = in.getTypeLattice();
        if (inType.nullElement().isDefinitelyNotNull()
            && inType.isClassType()
            && inType.asClassTypeLatticeElement().getClassType()
                .equals(appInfo.dexItemFactory.stringType)) {
          Value out = invoke.outValue();
          if (out != null) {
            removeOrReplaceByDebugLocalWrite(invoke, it, in, out);
          } else {
            it.removeOrReplaceByDebugLocalRead();
          }
        }
      }
    }
  }

}
