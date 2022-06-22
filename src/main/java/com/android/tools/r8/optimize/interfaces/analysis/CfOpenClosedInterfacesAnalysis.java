// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.interfaces.analysis;

import com.android.tools.r8.cf.code.CfArrayStore;
import com.android.tools.r8.cf.code.CfAssignability;
import com.android.tools.r8.cf.code.CfInstanceFieldWrite;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfStaticFieldWrite;
import com.android.tools.r8.cf.code.CfSubtypingAssignability;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.AbstractTransferFunction;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.DataflowAnalysisResult;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.TransferFunctionResult;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.cf.CfBlock;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.cf.CfControlFlowGraph;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.cf.CfIntraproceduralDataflowAnalysis;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.InterfaceCollection;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.optimize.interfaces.collection.NonEmptyOpenClosedInterfacesCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.OpenClosedInterfacesOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.UnverifiableCfCodeDiagnostic;
import com.android.tools.r8.utils.WorkList;
import com.android.tools.r8.utils.collections.ProgramMethodMap;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CfOpenClosedInterfacesAnalysis {

  private final AppView<AppInfoWithLiveness> appView;
  private final CfAssignability assignability;
  private final DexItemFactory dexItemFactory;
  private final InternalOptions options;

  private final Set<DexClass> openInterfaces = Sets.newConcurrentHashSet();

  private final ProgramMethodMap<UnverifiableCfCodeDiagnostic> unverifiableCodeDiagnostics =
      ProgramMethodMap.createConcurrent();

  public CfOpenClosedInterfacesAnalysis(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.assignability = new CfSubtypingAssignability(appView);
    this.dexItemFactory = appView.dexItemFactory();
    this.options = appView.options();
  }

  public boolean run(ExecutorService executorService) throws ExecutionException {
    processClasses(executorService);
    setClosedInterfaces();
    reportUnverifiableCodeDiagnostics();
    return true;
  }

  private void processClasses(ExecutorService executorService) throws ExecutionException {
    ThreadUtils.processItems(appView.appInfo().classes(), this::processClass, executorService);
  }

  private void processClass(DexProgramClass clazz) {
    clazz.forEachProgramMethodMatching(
        DexEncodedMethod::hasCode, method -> openInterfaces.addAll(processMethod(method)));
  }

  private Set<DexClass> processMethod(ProgramMethod method) {
    Code code = method.getDefinition().getCode();
    if (!code.isCfCode()) {
      assert code.isDefaultInstanceInitializerCode() || code.isDexCode() || code.isThrowNullCode();
      return Collections.emptySet();
    }

    CfCode cfCode = code.asCfCode();
    CfControlFlowGraph cfg = CfControlFlowGraph.create(cfCode, options);
    TransferFunction transfer = new TransferFunction(method);
    CfIntraproceduralDataflowAnalysis<CfFrameState> analysis =
        new CfIntraproceduralDataflowAnalysis<>(appView, CfFrameState.bottom(), cfg, transfer);
    DataflowAnalysisResult result = analysis.run(cfg.getEntryBlock());
    assert result.isSuccessfulAnalysisResult();

    Set<DexClass> openInterfacesForMethod = Sets.newIdentityHashSet();
    for (CfBlock block : cfg.getBlocks()) {
      if (analysis.isIntermediateBlock(block)) {
        continue;
      }
      CfFrameState state = analysis.computeBlockEntryState(block);
      if (state.isError()) {
        return registerUnverifiableCode(method, 0, state.asError());
      }
      do {
        for (int instructionIndex = block.getFirstInstructionIndex();
            instructionIndex <= block.getLastInstructionIndex();
            instructionIndex++) {
          CfInstruction instruction = cfCode.getInstruction(instructionIndex);
          processInstruction(method, instruction, state, openInterfacesForMethod::add);
          state = transfer.apply(instruction, state).asAbstractState();
          if (state.isError()) {
            return registerUnverifiableCode(method, instructionIndex, state.asError());
          }
        }
        if (analysis.isBlockWithIntermediateSuccessorBlock(block)) {
          block = cfg.getUniqueSuccessor(block);
        } else {
          block = null;
        }
      } while (block != null);
    }
    return openInterfacesForMethod;
  }

  private void processInstruction(
      ProgramMethod method,
      CfInstruction instruction,
      CfFrameState state,
      Consumer<DexClass> openInterfaceConsumer) {
    assert !state.isError();
    if (state.isBottom()) {
      // Represents that this instruction is unreachable from the method entry point.
      return;
    }
    assert state.isConcrete();
    ConcreteCfFrameState concreteState = state.asConcrete();
    if (instruction.isArrayStore()) {
      processArrayStore(instruction.asArrayStore(), concreteState, openInterfaceConsumer);
    } else if (instruction.isInstanceFieldPut()) {
      processInstanceFieldPut(
          instruction.asInstanceFieldPut(), concreteState, openInterfaceConsumer);
    } else if (instruction.isInvoke()) {
      processInvoke(instruction.asInvoke(), concreteState, openInterfaceConsumer);
    } else if (instruction.isReturn() && !instruction.isReturnVoid()) {
      processReturn(instruction.asReturn(), method, concreteState, openInterfaceConsumer);
    } else if (instruction.isStaticFieldPut()) {
      processStaticFieldPut(instruction.asStaticFieldPut(), concreteState, openInterfaceConsumer);
    }
  }

  private void processArrayStore(
      CfArrayStore arrayStore,
      ConcreteCfFrameState state,
      Consumer<DexClass> openInterfaceConsumer) {
    if (!arrayStore.getType().isObject()) {
      return;
    }
    state.peekStackElements(
        3,
        stack -> {
          FrameType array = stack.peekFirst();
          FrameType value = stack.peekLast();
          if (array.isInitializedReferenceType()) {
            DexType arrayType = array.asInitializedReferenceType().getInitializedType();
            if (arrayType.isArrayType()) {
              processAssignment(
                  value, arrayType.toArrayElementType(dexItemFactory), openInterfaceConsumer);
            } else {
              assert arrayType.isNullValueType();
            }
          } else {
            assert false;
          }
        });
  }

  private void processInstanceFieldPut(
      CfInstanceFieldWrite instanceFieldPut,
      ConcreteCfFrameState state,
      Consumer<DexClass> openInterfaceConsumer) {
    state.peekStackElement(
        head ->
            processAssignment(head, instanceFieldPut.getField().getType(), openInterfaceConsumer),
        options);
  }

  private void processInvoke(
      CfInvoke invoke, ConcreteCfFrameState state, Consumer<DexClass> openInterfaceConsumer) {
    DexMethod invokedMethod = invoke.getMethod();
    state.peekStackElements(
        invokedMethod.getNumberOfArguments(invoke.isInvokeStatic()),
        arguments -> {
          int argumentIndex = 0;
          for (FrameType argument : arguments) {
            DexType parameter =
                invokedMethod.getArgumentType(argumentIndex, invoke.isInvokeStatic());
            processAssignment(argument, parameter, openInterfaceConsumer);
            argumentIndex++;
          }
        },
        options);
  }

  private void processReturn(
      CfReturn returnInstruction,
      ProgramMethod context,
      ConcreteCfFrameState state,
      Consumer<DexClass> openInterfaceConsumer) {
    state.peekStackElement(
        head -> processAssignment(head, context.getReturnType(), openInterfaceConsumer), options);
  }

  private void processStaticFieldPut(
      CfStaticFieldWrite staticFieldPut,
      ConcreteCfFrameState state,
      Consumer<DexClass> openInterfaceConsumer) {
    state.peekStackElement(
        head -> processAssignment(head, staticFieldPut.getField().getType(), openInterfaceConsumer),
        options);
  }

  private void processAssignment(
      FrameType fromType, DexType toType, Consumer<DexClass> openInterfaceConsumer) {
    if (fromType.isInitializedReferenceType() && !fromType.isNullType()) {
      processAssignment(
          fromType.asInitializedReferenceType().getInitializedType(),
          toType,
          openInterfaceConsumer);
    }
  }

  private void processAssignment(
      DexType fromType, DexType toType, Consumer<DexClass> openInterfaceConsumer) {
    processAssignment(
        fromType.toTypeElement(appView), toType.toTypeElement(appView), openInterfaceConsumer);
  }

  private void processAssignment(
      TypeElement fromType, TypeElement toType, Consumer<DexClass> openInterfaceConsumer) {
    // If the type is an interface type, then check that the assigned value is a subtype of the
    // interface type, or mark the interface as open.
    if (!toType.isClassType()) {
      return;
    }
    ClassTypeElement toClassType = toType.asClassType();
    if (toClassType.getClassType() != dexItemFactory.objectType) {
      return;
    }
    InterfaceCollection interfaceCollection = toClassType.getInterfaces();
    interfaceCollection.forEachKnownInterface(
        knownInterfaceType -> {
          DexClass knownInterface = appView.definitionFor(knownInterfaceType);
          if (knownInterface == null) {
            return;
          }
          assert knownInterface.isInterface();
          if (fromType.lessThanOrEqualUpToNullability(toType, appView)) {
            return;
          }
          assert verifyOpenInterfaceWitnessIsSuppressed(fromType, knownInterface);
          openInterfaceConsumer.accept(knownInterface);
        });
  }

  private void setClosedInterfaces() {
    // If open interfaces are not allowed and there are one or more suppressions, we should find at
    // least one open interface.
    OpenClosedInterfacesOptions openClosedInterfacesOptions =
        options.getOpenClosedInterfacesOptions();
    assert openClosedInterfacesOptions.isOpenInterfacesAllowed()
            || !openClosedInterfacesOptions.hasSuppressions()
            || !openInterfaces.isEmpty()
        : "Expected to find at least one open interface";

    includeParentOpenInterfaces();

    appView.setOpenClosedInterfacesCollection(
        new NonEmptyOpenClosedInterfacesCollection(
            openInterfaces.stream()
                .map(DexClass::getType)
                .collect(
                    Collectors.toCollection(
                        () -> SetUtils.newIdentityHashSet(openInterfaces.size())))));
  }

  private void includeParentOpenInterfaces() {
    // This includes all parent interfaces of each open interface in the set of open interfaces,
    // by using the open interfaces as the seen set.
    WorkList<DexClass> worklist = WorkList.newWorkList(openInterfaces);
    worklist.addAllIgnoringSeenSet(openInterfaces);
    while (worklist.hasNext()) {
      DexClass openInterface = worklist.next();
      for (DexType indirectOpenInterfaceType : openInterface.getInterfaces()) {
        DexClass indirectOpenInterfaceDefinition = appView.definitionFor(indirectOpenInterfaceType);
        if (indirectOpenInterfaceDefinition != null) {
          worklist.addIfNotSeen(indirectOpenInterfaceDefinition);
        }
      }
    }
  }

  private Set<DexClass> registerUnverifiableCode(
      ProgramMethod method, int instructionIndex, ErroneousCfFrameState state) {
    if (options.getCfCodeAnalysisOptions().isUnverifiableCodeReportingEnabled()) {
      unverifiableCodeDiagnostics.put(
          method,
          new UnverifiableCfCodeDiagnostic(
              method.getMethodReference(),
              instructionIndex,
              state.getMessage(),
              method.getOrigin()));
    }
    return Collections.emptySet();
  }

  private void reportUnverifiableCodeDiagnostics() {
    Reporter reporter = appView.reporter();
    List<ProgramMethod> methods = new ArrayList<>(unverifiableCodeDiagnostics.size());
    unverifiableCodeDiagnostics.forEach((method, diagnostic) -> methods.add(method));
    methods.sort(Comparator.comparing(DexClassAndMember::getReference));
    methods.forEach(method -> reporter.warning(unverifiableCodeDiagnostics.get(method)));
  }

  private boolean verifyOpenInterfaceWitnessIsSuppressed(
      TypeElement valueType, DexClass openInterface) {
    assert options.getOpenClosedInterfacesOptions().isSuppressed(appView, valueType, openInterface)
        : "Unexpected open interface "
            + openInterface.getTypeName()
            + " (assignment: "
            + valueType
            + ")";
    return true;
  }

  private class TransferFunction
      implements AbstractTransferFunction<CfBlock, CfInstruction, CfFrameState> {

    private final CfCode code;
    private final CfAnalysisConfig config;
    private final ProgramMethod context;

    TransferFunction(ProgramMethod context) {
      CfCode code = context.getDefinition().getCode().asCfCode();
      int maxLocals = code.getMaxLocals();
      int maxStack = code.getMaxStack();
      this.code = code;
      this.config =
          new CfAnalysisConfig() {

            @Override
            public CfAssignability getAssignability() {
              return assignability;
            }

            @Override
            public DexMethod getCurrentContext() {
              return context.getReference();
            }

            @Override
            public int getMaxLocals() {
              return maxLocals;
            }

            @Override
            public int getMaxStack() {
              return maxStack;
            }

            @Override
            public boolean isImmediateSuperClassOfCurrentContext(DexType type) {
              return type == context.getHolder().getSuperType();
            }

            @Override
            public boolean isStrengthenFramesEnabled() {
              return true;
            }
          };
      this.context = context;
    }

    @Override
    public TransferFunctionResult<CfFrameState> apply(
        CfInstruction instruction, CfFrameState state) {
      return instruction.evaluate(state, appView, config);
    }

    @Override
    public CfFrameState computeInitialState(CfBlock entryBlock, CfFrameState bottom) {
      CfFrameState initialState = new ConcreteCfFrameState();
      int localIndex = 0;
      if (context.getDefinition().isInstance()) {
        if (context.getDefinition().isInstanceInitializer()) {
          initialState = initialState.storeLocal(localIndex, FrameType.uninitializedThis(), config);
        } else {
          initialState =
              initialState.storeLocal(
                  localIndex, FrameType.initialized(context.getHolderType()), config);
        }
        localIndex++;
      }
      for (DexType parameter : context.getParameters()) {
        initialState =
            initialState.storeLocal(localIndex, FrameType.initialized(parameter), config);
        localIndex += parameter.getRequiredRegisters();
      }
      return initialState;
    }

    @Override
    public CfFrameState computeBlockEntryState(
        CfBlock block, CfBlock predecessor, CfFrameState predecessorExitState) {
      return predecessorExitState;
    }

    @Override
    public CfFrameState computeExceptionalBlockEntryState(
        CfBlock block,
        DexType guard,
        CfBlock throwBlock,
        CfInstruction throwInstruction,
        CfFrameState throwState) {
      return throwState.pushException(config, guard);
    }
  }
}
