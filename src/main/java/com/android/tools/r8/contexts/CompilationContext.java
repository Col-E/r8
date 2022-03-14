// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.contexts;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaring;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.structural.HasherWrapper;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class CompilationContext {

  // Internal contract to compute a unique suffix for synthetics.
  private abstract static class ContextDescriptorProvider {

    // Method to construct a fully qualified description of the context.
    // This is used to ensure that all contexts are unique during compilation.
    abstract StringBuilder buildContextDescriptorForTesting(StringBuilder builder);

    // Build a suffix to append to synthetic definitions.
    abstract StringBuilder buildSyntheticSuffix(StringBuilder builder);
  }

  /**
   * Create the initial compilation context.
   *
   * <p>This context should be a singleton for a given compilation allocated by AppView.
   */
  public static CompilationContext createInitialContext(InternalOptions options) {
    return new CompilationContext(options);
  }

  private final Consumer<String> testingConsumer;
  private final Thread mainThread;
  private final Map<String, String> seenSetForTesting = new ConcurrentHashMap<>();
  private int nextProcessorId = 0;

  private CompilationContext(InternalOptions options) {
    testingConsumer = options.testing.processingContextsConsumer;
    mainThread = options.mainThread;
  }

  private boolean verifyContext(ContextDescriptorProvider context) {
    String descriptor = context.buildContextDescriptorForTesting(new StringBuilder()).toString();
    String suffix = context.buildSyntheticSuffix(new StringBuilder()).toString();
    assert descriptor.endsWith(suffix);
    if (testingConsumer != null) {
      testingConsumer.accept(descriptor);
    }
    assert seenSetForTesting.put(descriptor, descriptor) == null
        : "Duplicated use of context descriptor: " + descriptor;
    return true;
  }

  /**
   * Creates the context for a "processor".
   *
   * <p>A "processor" is just a compilation task but which is deterministically ordered as part of
   * the full compilation pipeline. Thus, this method should only be called on the main-thread
   * ensuring that the assigned ids are deterministic. The id itself has not particular meaning.
   */
  public ProcessorContext createProcessorContext() {
    ProcessorContext processorContext = new ProcessorContext(this, nextProcessorId++);
    assert verifyContext(processorContext);
    assert mainThread == Thread.currentThread() : "Invoked on another thread than main";
    return processorContext;
  }

  public static class ProcessorContext extends ContextDescriptorProvider {
    private final CompilationContext parent;
    private final int processorId;

    private ProcessorContext(CompilationContext parent, int processorId) {
      this.parent = parent;
      this.processorId = processorId;
    }

    private boolean verifyContext(ContextDescriptorProvider context) {
      assert parent.verifyContext(context);
      return true;
    }

    /**
     * Create processing context for a single method.
     *
     * <p>There should only ever be a single allocation of the particular method-processing context.
     * This is generally ensured by, eg, a MethodProcessor, having private access to the processing
     * context and ensuring a safe single allocation of the individual method-processing contexts.
     */
    public MethodProcessingContext createMethodProcessingContext(ProgramMethod method) {
      MethodProcessingContext methodProcessingContext = new MethodProcessingContext(this, method);
      assert verifyContext(methodProcessingContext);
      return methodProcessingContext;
    }

    public MainThreadContext createMainThreadContext() {
      MainThreadContext singleThreadContext = new MainThreadContext(this, parent.mainThread);
      assert verifyContext(singleThreadContext);
      return singleThreadContext;
    }

    public ClassSynthesisDesugaringContext createClassSynthesisDesugaringContext(
        CfClassSynthesizerDesugaring desugaring) {
      ClassSynthesisDesugaringContext classSynthesisDesugaringContext =
          new ClassSynthesisDesugaringContext(this, desugaring);
      assert verifyContext(classSynthesisDesugaringContext);
      return classSynthesisDesugaringContext;
    }

    private StringBuilder buildSuffix(StringBuilder builder) {
      return builder.append('$').append(processorId);
    }

    @Override
    StringBuilder buildContextDescriptorForTesting(StringBuilder builder) {
      return buildSuffix(builder);
    }

    @Override
    StringBuilder buildSyntheticSuffix(StringBuilder builder) {
      return buildSuffix(builder);
    }
  }

  /** The processor context is responsible in creating a task context per thread. */
  public abstract static class ThreadTaskContext extends ContextDescriptorProvider {
    private final ProcessorContext parent;
    private int nextId = 0;

    private ThreadTaskContext(ProcessorContext parent) {
      this.parent = parent;
    }

    public ProcessorContext getParent() {
      return parent;
    }

    int incrementAndGetNextId() {
      return nextId++;
    }
  }

  /**
   * If no threading is used, the MainThreadContext allows one to generate UniqueContext instances
   * without a thread specific identifier.
   */
  public static class MainThreadContext extends ThreadTaskContext {

    private static final String IDENTIFIER = "main";

    private final Thread mainThread;

    private MainThreadContext(ProcessorContext parent, Thread mainThread) {
      super(parent);
      this.mainThread = mainThread;
    }

    @Override
    StringBuilder buildContextDescriptorForTesting(StringBuilder builder) {
      return getParent().buildContextDescriptorForTesting(builder).append(IDENTIFIER);
    }

    @Override
    StringBuilder buildSyntheticSuffix(StringBuilder builder) {
      return getParent().buildSyntheticSuffix(builder).append(IDENTIFIER);
    }

    public UniqueContext createUniqueContext(DexProgramClass context) {
      assert mainThread == Thread.currentThread() : "Invoked on another thread than main";
      UniqueContext uniqueContext = new UniqueContext(this, context, incrementAndGetNextId());
      assert getParent().verifyContext(uniqueContext);
      return uniqueContext;
    }
  }

  public static class ClassSynthesisDesugaringContext extends ThreadTaskContext {
    private final CfClassSynthesizerDesugaring desugaring;

    private ClassSynthesisDesugaringContext(
        ProcessorContext parent, CfClassSynthesizerDesugaring desugaring) {
      super(parent);
      this.desugaring = desugaring;
    }

    @Override
    StringBuilder buildContextDescriptorForTesting(StringBuilder builder) {
      return getParent()
          .buildContextDescriptorForTesting(builder)
          .append(desugaring.uniqueIdentifier());
    }

    @Override
    StringBuilder buildSyntheticSuffix(StringBuilder builder) {
      return getParent().buildSyntheticSuffix(builder).append(desugaring.uniqueIdentifier());
    }

    public UniqueContext createUniqueContext(DexProgramClass context) {
      UniqueContext uniqueContext = new UniqueContext(this, context, incrementAndGetNextId());
      assert getParent().verifyContext(uniqueContext);
      return uniqueContext;
    }
  }

  /** Description of the method context from which to synthesize. */
  public static class MethodProcessingContext extends ThreadTaskContext {
    private final ProgramMethod method;

    private MethodProcessingContext(ProcessorContext parent, ProgramMethod method) {
      super(parent);
      this.method = method;
    }

    /**
     * Create a unique processing context.
     *
     * <p>The uniqueness of the context requires that the parent, eg, method-context, is unique and
     * that the processing of that entity is such that the calls to this method happen in a
     * deterministic order, eg, by the processing of method instructions being single threaded.
     */
    public UniqueContext createUniqueContext() {
      UniqueContext uniqueContext =
          new UniqueContext(this, getClassContext(), incrementAndGetNextId());
      assert getParent().verifyContext(uniqueContext);
      return uniqueContext;
    }

    private DexProgramClass getClassContext() {
      return method.getHolder();
    }

    public ProgramMethod getMethodContext() {
      return method;
    }

    private StringBuilder buildSuffix(StringBuilder builder) {
      // TODO(b/172194101): Sanitize the method descriptor instead of hashing.
      HasherWrapper hasher = HasherWrapper.sha256Hasher();
      method.getReference().hash(hasher);
      return builder.append('$').append(hasher.hashCodeAsString());
    }

    @Override
    StringBuilder buildContextDescriptorForTesting(StringBuilder builder) {
      // Put the type first in the context descriptor.
      builder.append(getClassContext().getType().toDescriptorString());
      return buildSuffix(getParent().buildContextDescriptorForTesting(builder));
    }

    @Override
    StringBuilder buildSyntheticSuffix(StringBuilder builder) {
      return buildSuffix(getParent().buildSyntheticSuffix(builder));
    }
  }

  public static class UniqueContext extends ContextDescriptorProvider {
    private final ThreadTaskContext parent;
    // The class context is required to:
    // - determine in which feature the method should be put (feature split),
    // - put the synthetic in the correct package.
    private final DexProgramClass classContext;
    private final int positionId;

    private UniqueContext(ThreadTaskContext parent, DexProgramClass classContext, int positionId) {
      this.parent = parent;
      this.classContext = classContext;
      this.positionId = positionId;
    }

    private StringBuilder buildSuffix(StringBuilder builder) {
      return builder.append('$').append(positionId);
    }

    @Override
    StringBuilder buildContextDescriptorForTesting(StringBuilder builder) {
      return buildSuffix(parent.buildContextDescriptorForTesting(builder));
    }

    @Override
    StringBuilder buildSyntheticSuffix(StringBuilder builder) {
      return buildSuffix(parent.buildSyntheticSuffix(builder));
    }

    public DexProgramClass getClassContext() {
      return classContext;
    }

    public String getSyntheticSuffix() {
      return buildSyntheticSuffix(new StringBuilder()).toString();
    }
  }
}
