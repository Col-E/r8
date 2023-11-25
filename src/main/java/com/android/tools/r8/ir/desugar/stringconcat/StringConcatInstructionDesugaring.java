// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.stringconcat;

import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfInvokeDynamic;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexItemFactory.StringBuildingMethods;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.DesugarDescription;
import com.android.tools.r8.ir.desugar.FreshLocalProvider;
import com.android.tools.r8.ir.desugar.LocalStackAllocator;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.IteratorUtils;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.Opcodes;

/** String concatenation desugaring rewriter. */
public class StringConcatInstructionDesugaring implements CfInstructionDesugaring {

  private final DexItemFactory factory;
  private final StringBuildingMethods stringBuilderMethods;

  private final Map<DexType, DexMethod> paramTypeToAppendMethod = new IdentityHashMap<>();

  public StringConcatInstructionDesugaring(AppView<?> appView) {
    this.factory = appView.dexItemFactory();
    this.stringBuilderMethods = factory.stringBuilderMethods;

    // Mapping of type parameters to methods of StringBuilder.
    paramTypeToAppendMethod.put(factory.booleanType, stringBuilderMethods.appendBoolean);
    paramTypeToAppendMethod.put(factory.charType, stringBuilderMethods.appendChar);
    paramTypeToAppendMethod.put(factory.byteType, stringBuilderMethods.appendInt);
    paramTypeToAppendMethod.put(factory.shortType, stringBuilderMethods.appendInt);
    paramTypeToAppendMethod.put(factory.intType, stringBuilderMethods.appendInt);
    paramTypeToAppendMethod.put(factory.longType, stringBuilderMethods.appendLong);
    paramTypeToAppendMethod.put(factory.floatType, stringBuilderMethods.appendFloat);
    paramTypeToAppendMethod.put(factory.doubleType, stringBuilderMethods.appendDouble);
    paramTypeToAppendMethod.put(factory.stringType, stringBuilderMethods.appendString);
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public DesugarDescription compute(CfInstruction instruction, ProgramMethod context) {
    if (instruction.isInvokeDynamic()) {
      // We are interested in bootstrap methods StringConcatFactory::makeConcat
      // and StringConcatFactory::makeConcatWthConstants, both are static.
      CfInvokeDynamic invoke = instruction.asInvokeDynamic();
      DexCallSite callSite = invoke.getCallSite();
      if (callSite.bootstrapMethod.type.isInvokeStatic()) {
        DexMethod bootstrapMethod = callSite.bootstrapMethod.asMethod();
        if (bootstrapMethod == factory.stringConcatFactoryMembers.makeConcat) {
          return desugarMakeConcat(invoke);
        }
        if (bootstrapMethod == factory.stringConcatFactoryMembers.makeConcatWithConstants) {
          return desugarMakeConcatWithConstants(invoke);
        }
      }
    }
    return DesugarDescription.nothing();
  }

  private DesugarDescription desugarMakeConcat(CfInvokeDynamic invoke) {
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (freshLocalProvider,
                localStackAllocator,
                desugaringInfo,
                eventConsumer,
                context,
                methodProcessingContext,
                desugaringCollection,
                dexItemFactory) ->
                desugarMakeConcatInstructions(invoke, freshLocalProvider, localStackAllocator))
        .build();
  }

  private Collection<CfInstruction> desugarMakeConcatInstructions(
      CfInvokeDynamic invoke,
      FreshLocalProvider freshLocalProvider,
      LocalStackAllocator localStackAllocator) {
    DexProto proto = invoke.getCallSite().methodProto;
    DexType[] parameters = proto.parameters.values;

    // Collect chunks.
    ConcatBuilder builder = new ConcatBuilder();
    for (DexType parameter : parameters) {
      ValueType valueType = ValueType.fromDexType(parameter);
      builder.addChunk(
          new ArgumentChunk(
              paramTypeToAppendMethod.getOrDefault(parameter, stringBuilderMethods.appendObject),
              freshLocalProvider.getFreshLocal(valueType.requiredRegisters())));
    }

    // Desugar the instruction.
    return builder.desugar(localStackAllocator);
  }

  private DesugarDescription desugarMakeConcatWithConstants(CfInvokeDynamic invoke) {
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (freshLocalProvider,
                localStackAllocator,
                desugaringInfo,
                eventConsumer,
                context,
                methodProcessingContext,
                desugaringCollection,
                dexItemFactory) ->
                desugarMakeConcatWithConstantsInstructions(
                    invoke, freshLocalProvider, localStackAllocator, context))
        .build();
  }

  private Collection<CfInstruction> desugarMakeConcatWithConstantsInstructions(
      CfInvokeDynamic invoke,
      FreshLocalProvider freshLocalProvider,
      LocalStackAllocator localStackAllocator,
      ProgramMethod context) {
    DexCallSite callSite = invoke.getCallSite();
    DexProto proto = callSite.methodProto;
    DexTypeList parameters = proto.getParameters();
    List<DexValue> bootstrapArgs = callSite.bootstrapArgs;

    // Get `recipe` string.
    if (bootstrapArgs.isEmpty()) {
      throw error(context, "bootstrap method misses `recipe` argument");
    }

    // Extract recipe.
    DexValueString recipeValue = bootstrapArgs.get(0).asDexValueString();
    if (recipeValue == null) {
      throw error(context, "bootstrap method argument `recipe` must be a string");
    }
    String recipe = recipeValue.getValue().toString();

    // Constant arguments to `recipe`.
    List<DexValue> constantArguments = new ArrayList<>();
    for (int i = 1; i < bootstrapArgs.size(); i++) {
      constantArguments.add(bootstrapArgs.get(i));
    }

    // Collect chunks and patch the instruction.
    ConcatBuilder builder = new ConcatBuilder();
    StringBuilder acc = new StringBuilder();
    int length = recipe.length();
    Iterator<DexValue> constantArgumentsIterator = constantArguments.iterator();
    Iterator<DexType> parameterIterator = parameters.iterator();
    for (int i = 0; i < length; i++) {
      char c = recipe.charAt(i);
      if (c == '\u0001') {
        // Reference to an argument, so we need to flush the accumulated string.
        if (acc.length() > 0) {
          DexString stringConstant = factory.createString(acc.toString());
          builder.addChunk(
              new ConstantChunk(paramTypeToAppendMethod.get(factory.stringType), stringConstant));
          acc.setLength(0);
        }
        if (!parameterIterator.hasNext()) {
          throw error(context, "too many argument references in `recipe`");
        }
        DexType parameter = parameterIterator.next();
        ValueType valueType = ValueType.fromDexType(parameter);
        builder.addChunk(
            new ArgumentChunk(
                paramTypeToAppendMethod.getOrDefault(parameter, stringBuilderMethods.appendObject),
                freshLocalProvider.getFreshLocal(valueType.requiredRegisters())));
      } else if (c == '\u0002') {
        // Reference to a constant. Since it's a constant we just convert it to string and append to
        // `acc`, this way we will avoid calling toString() on every call.
        if (!constantArgumentsIterator.hasNext()) {
          throw error(context, "too many constant references in `recipe`");
        }
        acc.append(convertToString(constantArgumentsIterator.next(), context));
      } else {
        acc.append(c);
      }
    }

    if (parameterIterator.hasNext()) {
      throw error(
          context,
          "too few argument references in `recipe`, "
              + "expected "
              + parameters.size()
              + ", referenced: "
              + (parameters.size() - IteratorUtils.countRemaining(parameterIterator)));
    }

    if (constantArgumentsIterator.hasNext()) {
      throw error(
          context,
          "too few constant references in `recipe`, "
              + "expected "
              + constantArguments.size()
              + ", referenced: "
              + (constantArguments.size()
                  - IteratorUtils.countRemaining(constantArgumentsIterator)));
    }

    // Final part.
    if (acc.length() > 0) {
      DexString stringConstant = factory.createString(acc.toString());
      builder.addChunk(
          new ConstantChunk(paramTypeToAppendMethod.get(factory.stringType), stringConstant));
    }

    // Desugar the instruction.
    return builder.desugar(localStackAllocator);
  }

  private static String convertToString(DexValue value, ProgramMethod context) {
    if (value.isDexValueString()) {
      return value.asDexValueString().getValue().toString();
    }
    throw error(
        context,
        "const arg referenced from `recipe` is not supported: " + value.getClass().getName());
  }

  private final class ConcatBuilder {

    private final List<Chunk> chunks = new ArrayList<>();

    private ArgumentChunk biggestArgumentChunk = null;
    private ConstantChunk firstConstantChunk = null;
    private int argumentChunksStackSize = 0;

    ConcatBuilder() {}

    void addChunk(ArgumentChunk chunk) {
      chunks.add(chunk);
      argumentChunksStackSize += chunk.getValueType().requiredRegisters();
      if (biggestArgumentChunk == null
          || chunk.getValueType().requiredRegisters()
              > biggestArgumentChunk.getValueType().requiredRegisters()) {
        biggestArgumentChunk = chunk;
      }
    }

    void addChunk(ConstantChunk chunk) {
      chunks.add(chunk);
      if (firstConstantChunk == null) {
        firstConstantChunk = chunk;
      }
    }

    /**
     * Patch current `invoke-custom` instruction with:
     *
     * <pre>
     *   prologue:
     *      |   new-instance v0, StringBuilder
     *      |   invoke-direct {v0}, void StringBuilder.<init>()
     *
     *   populate each chunk:
     *      |   (optional) load the constant, e.g.: const-string v1, ""
     *      |   invoke-virtual {v0, v1}, StringBuilder StringBuilder.append([type])
     *
     *   epilogue:
     *      |   invoke-virtual {v0}, String StringBuilder.toString()
     *
     * </pre>
     */
    final Collection<CfInstruction> desugar(LocalStackAllocator localStackAllocator) {
      Deque<CfInstruction> replacement = new ArrayDeque<>();
      for (Chunk chunk : chunks) {
        if (chunk.isArgumentChunk()) {
          ArgumentChunk argumentChunk = chunk.asArgumentChunk();
          replacement.addFirst(
              CfStore.store(argumentChunk.getValueType(), argumentChunk.getVariableIndex()));
        }
      }
      replacement.add(new CfNew(factory.stringBuilderType));
      replacement.add(CfStackInstruction.DUP);
      replacement.add(
          new CfInvoke(Opcodes.INVOKESPECIAL, stringBuilderMethods.defaultConstructor, false));
      for (Chunk chunk : chunks) {
        if (chunk.isArgumentChunk()) {
          ArgumentChunk argumentChunk = chunk.asArgumentChunk();
          replacement.add(
              CfLoad.load(argumentChunk.getValueType(), argumentChunk.getVariableIndex()));
        } else {
          assert chunk.isConstantChunk();
          replacement.add(new CfConstString(chunk.asConstantChunk().getStringConstant()));
        }
        replacement.add(new CfInvoke(Opcodes.INVOKEVIRTUAL, chunk.method, false));
      }
      replacement.add(new CfInvoke(Opcodes.INVOKEVIRTUAL, stringBuilderMethods.toString, false));

      // Coming into the original invoke-dynamic instruction, we have N arguments on the stack. We
      // then pop the N arguments from the stack, allocate a new-instance on the stack, and dup it,
      // to initialize the instance. We then one-by-one load the arguments and call append(). We
      // therefore need a local stack of size 3 if there is a wide argument, and otherwise a local
      // stack of size 2.
      int maxLocalStackSizeAfterStores =
          2
              + BooleanUtils.intValue(
                  biggestArgumentChunk != null
                      && biggestArgumentChunk.getValueType().requiredRegisters() == 2);
      if (maxLocalStackSizeAfterStores > argumentChunksStackSize) {
        localStackAllocator.allocateLocalStack(
            maxLocalStackSizeAfterStores - argumentChunksStackSize);
      }
      return replacement;
    }
  }

  private abstract static class Chunk {

    private final DexMethod method;

    Chunk(DexMethod method) {
      this.method = method;
    }

    public ValueType getValueType() {
      assert method.getProto().getArity() == 1;
      return ValueType.fromDexType(method.getParameter(0));
    }

    public boolean isArgumentChunk() {
      return false;
    }

    public ArgumentChunk asArgumentChunk() {
      return null;
    }

    public boolean isConstantChunk() {
      return false;
    }

    public ConstantChunk asConstantChunk() {
      return null;
    }
  }

  private static final class ArgumentChunk extends Chunk {

    private final int variableIndex;

    ArgumentChunk(DexMethod method, int variableIndex) {
      super(method);
      this.variableIndex = variableIndex;
    }

    public int getVariableIndex() {
      return variableIndex;
    }

    @Override
    public boolean isArgumentChunk() {
      return true;
    }

    @Override
    public ArgumentChunk asArgumentChunk() {
      return this;
    }
  }

  private static final class ConstantChunk extends Chunk {

    private final DexString stringConstant;

    ConstantChunk(DexMethod method, DexString stringConstant) {
      super(method);
      this.stringConstant = stringConstant;
    }

    public DexString getStringConstant() {
      return stringConstant;
    }

    @Override
    public boolean isConstantChunk() {
      return true;
    }

    @Override
    public ConstantChunk asConstantChunk() {
      return this;
    }
  }

  private static CompilationError error(ProgramMethod context, String message) {
    return new CompilationError(
        "String concatenation desugaring error (method: "
            + context.toSourceString()
            + "): "
            + message);
  }
}
