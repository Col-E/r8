// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.ir.optimize.CodeRewriter.removeOrReplaceByDebugLocalWrite;
import static com.android.tools.r8.naming.dexitembasedstring.ClassNameComputationInfo.ClassNameMapping.CANONICAL_NAME;
import static com.android.tools.r8.naming.dexitembasedstring.ClassNameComputationInfo.ClassNameMapping.NAME;
import static com.android.tools.r8.naming.dexitembasedstring.ClassNameComputationInfo.ClassNameMapping.SIMPLE_NAME;
import static com.android.tools.r8.utils.DescriptorUtils.INNER_CLASS_SEPARATOR;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.escape.EscapeAnalysis;
import com.android.tools.r8.ir.analysis.escape.EscapeAnalysisConfiguration;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.DexItemBasedConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.passes.CodeRewriterPass;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.naming.dexitembasedstring.ClassNameComputationInfo;
import java.io.UTFDataFormatException;
import java.util.function.BiFunction;
import java.util.function.Function;

public class StringOptimizer extends CodeRewriterPass<AppInfo> {

  public StringOptimizer(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getTimingId() {
    return "StringOptimizer";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code) {
    return !isDebugMode(code.context());
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    boolean hasChanged = false;
    if (options.enableNameReflectionOptimization
        || options.testing.forceNameReflectionOptimization) {
      hasChanged |= rewriteClassGetName(code);
    }
    if (code.metadata().mayHaveConstString()) {
      hasChanged |= computeTrivialOperationsOnConstString(code);
    }
    hasChanged |= removeTrivialConversions(code);
    return CodeRewriterResult.hasChanged(hasChanged);
  }

  // boolean String#isEmpty()
  // boolean String#startsWith(String)
  // boolean String#endsWith(String)
  // boolean String#contains(String)
  // boolean String#equals(String)
  // boolean String#equalsIgnoreCase(String)
  // boolean String#contentEquals(String)
  // int String#hashCode()
  // int String#length()
  // int String#indexOf(String)
  // int String#indexOf(int)
  // int String#lastIndexOf(String)
  // int String#lastIndexOf(int)
  // int String#compareTo(String)
  // int String#compareToIgnoreCase(String)
  // String String#substring(int)
  // String String#substring(int, int)
  // String String#trim()
  private boolean computeTrivialOperationsOnConstString(IRCode code) {
    boolean hasChanged = false;
    AffectedValues affectedValues = new AffectedValues();
    InstructionListIterator it = code.instructionListIterator();
    while (it.hasNext()) {
      Instruction instr = it.next();
      if (!instr.isInvokeVirtual()) {
        continue;
      }
      InvokeVirtual invoke = instr.asInvokeVirtual();
      if (!invoke.hasOutValue()) {
        continue;
      }
      DexMethod invokedMethod = invoke.getInvokedMethod();
      if (invokedMethod.getHolderType() != dexItemFactory.stringType) {
        continue;
      }
      if (invokedMethod.getName() == dexItemFactory.substringName) {
        assert invoke.inValues().size() == 2 || invoke.inValues().size() == 3;
        Value rcv = invoke.getReceiver().getAliasedValue();
        if (rcv.definition == null
            || !rcv.definition.isConstString()
            || rcv.hasLocalInfo()) {
          continue;
        }
        Value beginIndex = invoke.inValues().get(1).getAliasedValue();
        if (beginIndex.definition == null
            || !beginIndex.definition.isConstNumber()
            || beginIndex.hasLocalInfo()) {
          continue;
        }
        int beginIndexValue = beginIndex.definition.asConstNumber().getIntValue();
        Value endIndex = null;
        if (invoke.inValues().size() == 3) {
          endIndex = invoke.inValues().get(2).getAliasedValue();
          if (endIndex.definition == null
              || !endIndex.definition.isConstNumber()
              || endIndex.hasLocalInfo()) {
            continue;
          }
        }
        String rcvString = rcv.definition.asConstString().getValue().toString();
        int endIndexValue =
            endIndex == null
                ? rcvString.length()
                : endIndex.definition.asConstNumber().getIntValue();
        if (beginIndexValue < 0
            || endIndexValue > rcvString.length()
            || beginIndexValue > endIndexValue) {
          // This will raise StringIndexOutOfBoundsException.
          continue;
        }
        String sub = rcvString.substring(beginIndexValue, endIndexValue);
        Value stringValue =
            code.createValue(
                TypeElement.stringClassType(appView, definitelyNotNull()), invoke.getLocalInfo());
        affectedValues.addAll(invoke.outValue().affectedValues());
        it.replaceCurrentInstruction(
            new ConstString(stringValue, dexItemFactory.createString(sub)));
        continue;
      }

      if (invokedMethod == dexItemFactory.stringMembers.trim) {
        Value receiver = invoke.getReceiver().getAliasedValue();
        if (receiver.hasLocalInfo() || receiver.isPhi() || !receiver.definition.isConstString()) {
          continue;
        }
        DexString resultString =
            dexItemFactory.createString(
                receiver.definition.asConstString().getValue().toString().trim());
        Value newOutValue =
            code.createValue(
                TypeElement.stringClassType(appView, definitelyNotNull()), invoke.getLocalInfo());
        it.replaceCurrentInstruction(new ConstString(newOutValue, resultString), affectedValues);
        continue;
      }

      Function<DexString, Integer> operatorWithNoArg = null;
      BiFunction<DexString, DexString, Integer> operatorWithString = null;
      BiFunction<DexString, Integer, Integer> operatorWithInt = null;
      if (invokedMethod == dexItemFactory.stringMembers.hashCode) {
        operatorWithNoArg = rcv -> {
          try {
            return rcv.decodedHashCode();
          } catch (UTFDataFormatException e) {
            // It is already guaranteed that the string does not throw.
            throw new Unreachable();
          }
        };
      } else if (invokedMethod == dexItemFactory.stringMembers.length) {
        operatorWithNoArg = rcv -> rcv.size;
      } else if (invokedMethod == dexItemFactory.stringMembers.isEmpty) {
        operatorWithNoArg = rcv -> rcv.size == 0 ? 1 : 0;
      } else if (invokedMethod == dexItemFactory.stringMembers.contains) {
        operatorWithString = (rcv, arg) -> rcv.toString().contains(arg.toString()) ? 1 : 0;
      } else if (invokedMethod == dexItemFactory.stringMembers.startsWith) {
        operatorWithString = (rcv, arg) -> rcv.startsWith(arg) ? 1 : 0;
      } else if (invokedMethod == dexItemFactory.stringMembers.endsWith) {
        operatorWithString = (rcv, arg) -> rcv.endsWith(arg) ? 1 : 0;
      } else if (invokedMethod == dexItemFactory.stringMembers.equals) {
        operatorWithString = (rcv, arg) -> rcv.equals(arg) ? 1 : 0;
      } else if (invokedMethod == dexItemFactory.stringMembers.equalsIgnoreCase) {
        operatorWithString = (rcv, arg) -> rcv.toString().equalsIgnoreCase(arg.toString()) ? 1 : 0;
      } else if (invokedMethod == dexItemFactory.stringMembers.contentEqualsCharSequence) {
        operatorWithString = (rcv, arg) -> rcv.toString().contentEquals(arg.toString()) ? 1 : 0;
      } else if (invokedMethod == dexItemFactory.stringMembers.indexOfInt) {
        operatorWithInt = (rcv, idx) -> rcv.toString().indexOf(idx);
      } else if (invokedMethod == dexItemFactory.stringMembers.indexOfString) {
        operatorWithString = (rcv, arg) -> rcv.toString().indexOf(arg.toString());
      } else if (invokedMethod == dexItemFactory.stringMembers.lastIndexOfInt) {
        operatorWithInt = (rcv, idx) -> rcv.toString().lastIndexOf(idx);
      } else if (invokedMethod == dexItemFactory.stringMembers.lastIndexOfString) {
        operatorWithString = (rcv, arg) -> rcv.toString().lastIndexOf(arg.toString());
      } else if (invokedMethod == dexItemFactory.stringMembers.compareTo) {
        operatorWithString = (rcv, arg) -> rcv.toString().compareTo(arg.toString());
      } else if (invokedMethod == dexItemFactory.stringMembers.compareToIgnoreCase) {
        operatorWithString = (rcv, arg) -> rcv.toString().compareToIgnoreCase(arg.toString());
      } else {
        continue;
      }
      Value rcv = invoke.getReceiver().getAliasedValue();
      if (rcv.definition == null
          || !rcv.definition.isConstString()
          || rcv.definition.asConstString().instructionInstanceCanThrow(appView, code.context())
          || rcv.hasLocalInfo()) {
        continue;
      }
      DexString rcvString = rcv.definition.asConstString().getValue();

      ConstNumber constNumber;
      if (operatorWithNoArg != null) {
        assert invoke.inValues().size() == 1;
        int v = operatorWithNoArg.apply(rcvString);
        constNumber = code.createIntConstant(v);
      } else if (operatorWithString != null) {
        assert invoke.inValues().size() == 2;
        Value arg = invoke.inValues().get(1).getAliasedValue();
        if (arg.definition == null
            || !arg.definition.isConstString()
            || arg.hasLocalInfo()) {
          continue;
        }
        int v = operatorWithString.apply(rcvString, arg.definition.asConstString().getValue());
        constNumber = code.createIntConstant(v);
      } else {
        assert operatorWithInt != null;
        assert invoke.inValues().size() == 2;
        Value arg = invoke.inValues().get(1).getAliasedValue();
        if (arg.definition == null
            || !arg.definition.isConstNumber()
            || arg.hasLocalInfo()) {
          continue;
        }
        int v = operatorWithInt.apply(rcvString, arg.definition.asConstNumber().getIntValue());
        constNumber = code.createIntConstant(v);
      }

      hasChanged = true;
      it.replaceCurrentInstruction(constNumber);
    }
    // Computed substring is not null, and thus propagate that information.
    affectedValues.narrowingWithAssumeRemoval(appView, code);
    assert code.isConsistentSSA(appView);
    return hasChanged;
  }

  // Find Class#get*Name() with a constant-class and replace it with a const-string if possible.
  private boolean rewriteClassGetName(IRCode code) {
    boolean hasChanged = false;
    AffectedValues affectedValues = new AffectedValues();
    InstructionListIterator it = code.instructionListIterator();
    while (it.hasNext()) {
      Instruction instr = it.next();
      if (!instr.isInvokeVirtual()) {
        continue;
      }
      InvokeVirtual invoke = instr.asInvokeVirtual();
      DexMethod invokedMethod = invoke.getInvokedMethod();
      if (!dexItemFactory.classMethods.isReflectiveNameLookup(invokedMethod)) {
        continue;
      }

      Value out = invoke.outValue();
      // Skip the call if the computed name is already discarded or not used anywhere.
      if (out == null || !out.hasAnyUsers()) {
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
          || in.hasLocalInfo()) {
        continue;
      }

      ConstClass constClass = in.definition.asConstClass();
      DexType type = constClass.getValue();
      int arrayDepth = type.getNumberOfLeadingSquareBrackets();
      DexType baseType = type.toBaseType(dexItemFactory);
      // Make sure base type is a class type.
      if (!baseType.isClassType()) {
        continue;
      }
      DexClass holder = appView.definitionFor(baseType);
      if (holder == null) {
        continue;
      }
      boolean mayBeRenamed =
          appView.enableWholeProgramOptimizations()
              && appView.withLiveness().appInfo().isMinificationAllowed(holder);
      // b/120138731: Filter out escaping uses. In such case, the result of this optimization will
      // be stored somewhere, which can lead to a regression if the corresponding class is in a deep
      // package hierarchy. For local cases, it is likely a one-time computation, but make sure the
      // result is used reasonably, such as library calls. For example, if a class may be minified
      // while its name is used to compute hash code, which won't be optimized, it's better not to
      // compute the name.
      if (!appView.options().testing.forceNameReflectionOptimization) {
        if (mayBeRenamed) {
          continue;
        }
        if (invokedMethod != dexItemFactory.classMethods.getSimpleName) {
          EscapeAnalysis escapeAnalysis =
              new EscapeAnalysis(appView, StringOptimizerEscapeAnalysisConfiguration.getInstance());
          if (escapeAnalysis.isEscaping(code, out)) {
            continue;
          }
        }
      }

      String descriptor = baseType.toDescriptorString();
      boolean assumeTopLevel = descriptor.indexOf(INNER_CLASS_SEPARATOR) < 0;
      DexItemBasedConstString deferred = null;
      DexString name = null;
      if (invokedMethod == dexItemFactory.classMethods.getName) {
        if (mayBeRenamed) {
          Value stringValue =
              code.createValue(
                  TypeElement.stringClassType(appView, definitelyNotNull()), invoke.getLocalInfo());
          deferred =
              new DexItemBasedConstString(
                  stringValue, baseType, ClassNameComputationInfo.create(NAME, arrayDepth));
        } else {
          name = NAME.map(descriptor, holder, dexItemFactory, arrayDepth);
        }
      } else if (invokedMethod == dexItemFactory.classMethods.getTypeName) {
        // TODO(b/119426668): desugar Type#getTypeName
        continue;
      } else if (invokedMethod == dexItemFactory.classMethods.getCanonicalName) {
        // Always returns null if the target type is local or anonymous class.
        if (holder.isLocalClass() || holder.isAnonymousClass()) {
          ConstNumber constNull = code.createConstNull();
          it.replaceCurrentInstruction(constNull, affectedValues);
        } else {
          // b/119471127: If an outer class is shrunk, we may compute a wrong canonical name.
          // Leave it as-is so that the class's canonical name is consistent across the app.
          if (!assumeTopLevel) {
            continue;
          }
          if (mayBeRenamed) {
            Value stringValue =
                code.createValue(
                    TypeElement.stringClassType(appView, definitelyNotNull()),
                    invoke.getLocalInfo());
            deferred =
                new DexItemBasedConstString(
                    stringValue,
                    baseType,
                    ClassNameComputationInfo.create(CANONICAL_NAME, arrayDepth));
          } else {
            name = CANONICAL_NAME.map(descriptor, holder, dexItemFactory, arrayDepth);
          }
        }
      } else if (invokedMethod == dexItemFactory.classMethods.getSimpleName) {
        // Always returns an empty string if the target type is an anonymous class.
        if (holder.isAnonymousClass()) {
          name = dexItemFactory.createString("");
        } else {
          // b/120130435: If an outer class is shrunk, we may compute a wrong simple name.
          // Leave it as-is so that the class's simple name is consistent across the app.
          if (!assumeTopLevel) {
            continue;
          }
          if (mayBeRenamed) {
            Value stringValue =
                code.createValue(
                    TypeElement.stringClassType(appView, definitelyNotNull()),
                    invoke.getLocalInfo());
            deferred =
                new DexItemBasedConstString(
                    stringValue,
                    baseType,
                    ClassNameComputationInfo.create(SIMPLE_NAME, arrayDepth));
          } else {
            name = SIMPLE_NAME.map(descriptor, holder, dexItemFactory, arrayDepth);
          }
        }
      }
      if (name != null) {
        Value stringValue =
            code.createValue(
                TypeElement.stringClassType(appView, definitelyNotNull()), invoke.getLocalInfo());
        ConstString constString = new ConstString(stringValue, name);
        it.replaceCurrentInstruction(constString, affectedValues);
        hasChanged = true;
      } else if (deferred != null) {
        it.replaceCurrentInstruction(deferred, affectedValues);
        hasChanged = true;
      }
    }
    // Computed name is not null or literally null (for canonical name of local/anonymous class).
    // In either way, that is narrower information, and thus propagate that.
    affectedValues.narrowingWithAssumeRemoval(appView, code);
    assert code.isConsistentSSA(appView);
    return hasChanged;
  }

  // String#valueOf(null) -> "null"
  // String#valueOf(String s) -> s
  // str.toString() -> str
  private boolean removeTrivialConversions(IRCode code) {
    boolean hasChanged = false;
    AffectedValues affectedValues = new AffectedValues();
    InstructionListIterator it = code.instructionListIterator();
    while (it.hasNext()) {
      Instruction instr = it.next();
      if (instr.isInvokeStatic()) {
        InvokeStatic invoke = instr.asInvokeStatic();
        DexMethod invokedMethod = invoke.getInvokedMethod();
        if (invokedMethod != dexItemFactory.stringMembers.valueOf) {
          continue;
        }
        assert invoke.inValues().size() == 1;
        Value in = invoke.inValues().get(0);
        if (in.hasLocalInfo()) {
          continue;
        }
        Value out = invoke.outValue();
        TypeElement inType = in.getType();
        if (out != null && in.isAlwaysNull(appView)) {
          affectedValues.addAll(out.affectedValues());
          it.replaceCurrentInstructionWithConstString(
              appView, code, dexItemFactory.createString("null"));
        } else if (inType.nullability().isDefinitelyNotNull()
            && inType.isClassType()
            && inType.asClassType().getClassType().equals(dexItemFactory.stringType)) {
          if (out != null) {
            affectedValues.addAll(out.affectedValues());
            removeOrReplaceByDebugLocalWrite(invoke, it, in, out);
          } else {
            it.removeOrReplaceByDebugLocalRead();
          }
        }
        hasChanged = true;
      } else if (instr.isInvokeVirtual()) {
        InvokeVirtual invoke = instr.asInvokeVirtual();
        DexMethod invokedMethod = invoke.getInvokedMethod();
        if (invokedMethod != dexItemFactory.stringMembers.toString) {
          continue;
        }
        assert invoke.inValues().size() == 1;
        Value in = invoke.getReceiver();
        TypeElement inType = in.getType();
        if (inType.nullability().isDefinitelyNotNull()
            && inType.isClassType()
            && inType.asClassType().getClassType().equals(dexItemFactory.stringType)) {
          Value out = invoke.outValue();
          if (out != null) {
            affectedValues.addAll(out.affectedValues());
            removeOrReplaceByDebugLocalWrite(invoke, it, in, out);
          } else {
            it.removeOrReplaceByDebugLocalRead();
          }
        }
        hasChanged = true;
      }
    }
    // Newly added "null" string is not null, and thus propagate that information.
    affectedValues.narrowingWithAssumeRemoval(appView, code);
    code.removeRedundantBlocks();
    assert code.isConsistentSSA(appView);
    return hasChanged;
  }

  static class StringOptimizerEscapeAnalysisConfiguration
      implements EscapeAnalysisConfiguration {

    private static final StringOptimizerEscapeAnalysisConfiguration INSTANCE =
        new StringOptimizerEscapeAnalysisConfiguration();

    private StringOptimizerEscapeAnalysisConfiguration() {}

    public static StringOptimizerEscapeAnalysisConfiguration getInstance() {
      return INSTANCE;
    }

    @Override
    public boolean isLegitimateEscapeRoute(
        AppView<?> appView,
        EscapeAnalysis escapeAnalysis,
        Instruction escapeRoute,
        ProgramMethod context) {
      if (escapeRoute.isReturn() || escapeRoute.isThrow() || escapeRoute.isStaticPut()) {
        return false;
      }
      if (escapeRoute.isInvokeMethod()) {
        DexMethod invokedMethod = escapeRoute.asInvokeMethod().getInvokedMethod();
        // b/120138731: Only allow known simple operations on const-string
        if (invokedMethod == appView.dexItemFactory().stringMembers.hashCode
            || invokedMethod == appView.dexItemFactory().stringMembers.isEmpty
            || invokedMethod == appView.dexItemFactory().stringMembers.length) {
          return true;
        }
        // Add more cases to filter out, if any.
        return false;
      }
      if (escapeRoute.isArrayPut()) {
        Value array = escapeRoute.asArrayPut().array().getAliasedValue();
        return !array.isPhi() && array.definition.isCreatingArray();
      }
      if (escapeRoute.isInstancePut()) {
        Value instance = escapeRoute.asInstancePut().object().getAliasedValue();
        return !instance.isPhi() && instance.definition.isNewInstance();
      }
      // All other cases are not legitimate.
      return false;
    }
  }
}
