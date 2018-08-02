// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.errors.InvalidDebugInfoException;
import com.android.tools.r8.graph.JarClassFileReader.ReparseContext;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.ValueNumberGenerator;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.JarSourceCode;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.jar.InliningConstraintVisitor;
import com.android.tools.r8.jar.JarRegisterEffectsVisitor;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.JSRInlinerAdapter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

public class JarCode extends Code {

  // TODO(zerny): Write via the IR.
  public void writeTo(MethodVisitor visitor) {
    node.accept(visitor);
  }

  private final DexMethod method;
  private final Origin origin;
  private MethodNode node;
  private ReparseContext context;

  private final JarApplicationReader application;

  public JarCode(
      DexMethod method, Origin origin, ReparseContext context, JarApplicationReader application) {
    this.method = method;
    this.origin = origin;
    this.context = context;
    this.application = application;
    context.codeList.add(this);
  }

  public MethodNode getNode() {
    triggerDelayedParsingIfNeccessary();
    return node;
  }

  @Override
  public boolean isJarCode() {
    return true;
  }

  @Override
  public JarCode asJarCode() {
    return this;
  }

  @Override
  protected int computeHashCode() {
    triggerDelayedParsingIfNeccessary();
    return node.hashCode();
  }

  @Override
  protected boolean computeEquals(Object other) {
    triggerDelayedParsingIfNeccessary();
    if (this == other) {
      return true;
    }
    if (other instanceof JarCode) {
      JarCode o = (JarCode) other;
      o.triggerDelayedParsingIfNeccessary();
      // TODO(zerny): This amounts to object equality.
      return node.equals(o.node);
    }
    return false;
  }

  @Override
  public boolean isEmptyVoidMethod() {
    for (Iterator<AbstractInsnNode> it = getNode().instructions.iterator(); it.hasNext(); ) {
      AbstractInsnNode insn = it.next();
      if (insn.getType() != Opcodes.RETURN
          && !(insn instanceof LabelNode)
          && !(insn instanceof LineNumberNode)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public IRCode buildIR(
      DexEncodedMethod encodedMethod,
      AppInfo appInfo,
      GraphLense graphLense,
      InternalOptions options,
      Origin origin) {
    assert getOwner() == encodedMethod;
    triggerDelayedParsingIfNeccessary();
    return options.debug
        ? internalBuildWithLocals(encodedMethod, appInfo, graphLense, options, null, null)
        : internalBuild(encodedMethod, appInfo, graphLense, options, null, null);
  }

  @Override
  public IRCode buildInliningIR(
      DexEncodedMethod encodedMethod,
      AppInfo appInfo,
      GraphLense graphLense,
      InternalOptions options,
      ValueNumberGenerator generator,
      Position callerPosition,
      Origin origin) {
    assert getOwner() == encodedMethod;
    assert generator != null;
    triggerDelayedParsingIfNeccessary();
    return options.debug
        ? internalBuildWithLocals(
            encodedMethod, appInfo, graphLense, options, generator, callerPosition)
        : internalBuild(encodedMethod, appInfo, graphLense, options, generator, callerPosition);
  }

  private IRCode internalBuildWithLocals(
      DexEncodedMethod encodedMethod,
      AppInfo appInfo,
      GraphLense graphLense,
      InternalOptions options,
      ValueNumberGenerator generator,
      Position callerPosition) {
    try {
      return internalBuild(encodedMethod, appInfo, graphLense, options, generator, callerPosition);
    } catch (InvalidDebugInfoException e) {
      options.warningInvalidDebugInfo(encodedMethod, origin, e);
      node.localVariables.clear();
      return internalBuild(encodedMethod, appInfo, graphLense, options, generator, callerPosition);
    }
  }

  private IRCode internalBuild(
      DexEncodedMethod encodedMethod,
      AppInfo appInfo,
      GraphLense graphLense,
      InternalOptions options,
      ValueNumberGenerator generator,
      Position callerPosition) {
    if (!options.debug) {
      node.localVariables.clear();
    }
    JarSourceCode source =
        new JarSourceCode(
            method.getHolder(),
            node,
            application,
            graphLense.getOriginalMethodSignature(encodedMethod.method),
            callerPosition);
    return new IRBuilder(encodedMethod, appInfo, source, options, generator).build();
  }

  @Override
  public void registerCodeReferences(UseRegistry registry) {
    triggerDelayedParsingIfNeccessary();
    node.instructions.accept(
        new JarRegisterEffectsVisitor(method.getHolder(), registry, application));
    node.tryCatchBlocks.forEach(tryCatchBlockNode ->
        registry.registerTypeReference(application.getTypeFromDescriptor(
            DescriptorUtils.getDescriptorFromClassBinaryName(tryCatchBlockNode.type))));
  }

  public ConstraintWithTarget computeInliningConstraint(
      DexEncodedMethod encodedMethod,
      AppInfoWithLiveness appInfo,
      GraphLense graphLense,
      DexType invocationContext) {
    InliningConstraintVisitor visitor =
        new InliningConstraintVisitor(
            application, appInfo, graphLense, encodedMethod, invocationContext);
    AbstractInsnNode insn = node.instructions.getFirst();
    while (insn != null) {
      insn.accept(visitor);
      if (visitor.isFinished()) {
        return visitor.getConstraint();
      }
      insn = insn.getNext();
    }
    for (TryCatchBlockNode block : node.tryCatchBlocks) {
      visitor.accept(block);
      if (visitor.isFinished()) {
        return visitor.getConstraint();
      }
    }
    return visitor.getConstraint();
  }

  @Override
  public String toString() {
    triggerDelayedParsingIfNeccessary();
    TraceMethodVisitor visitor = new TraceMethodVisitor(new Textifier());
    node.accept(visitor);
    StringWriter writer = new StringWriter();
    visitor.p.print(new PrintWriter(writer));
    return writer.toString();
  }

  @Override
  public String toString(DexEncodedMethod method, ClassNameMapper naming) {
    return toString();
  }

  private void triggerDelayedParsingIfNeccessary() {
    if (this.context != null) {
      // The SecondVisitor is in charge of setting this.context to null.
      ReparseContext context = this.context;
      parseCode(context, false);
      if (hasJsr(context)) {
        System.out.println("JarCode: JSR encountered; reparse using JSRInlinerAdapter");
        parseCode(context, true);
        assert !hasJsr(context);
      }
      assert verifyNoReparseContext(context.owner);
    }
  }

  private void parseCode(ReparseContext context, boolean useJsrInliner) {
    SecondVisitor classVisitor = new SecondVisitor(context, application, useJsrInliner);
    new ClassReader(context.classCache).accept(classVisitor, ClassReader.SKIP_FRAMES);
  }

  private boolean hasJsr(ReparseContext context) {
    for (Code code : context.codeList) {
      if (hasJsr(code.asJarCode().node)) {
        return true;
      }
    }
    return false;
  }

  private boolean hasJsr(MethodNode node) {
    Iterator<AbstractInsnNode> it = node.instructions.iterator();
    while (it.hasNext()) {
      int opcode = it.next().getOpcode();
      if (opcode == Opcodes.JSR || opcode == Opcodes.RET) {
        return true;
      }
    }
    return false;
  }

  /**
   * Fills the MethodNodes of all the methods in the class and removes the ReparseContext.
   */
  private static class SecondVisitor extends ClassVisitor {

    private final ReparseContext context;
    private final JarApplicationReader application;
    private final boolean useJsrInliner;
    private int methodIndex = 0;

    public SecondVisitor(
        ReparseContext context, JarApplicationReader application, boolean useJsrInliner) {
      super(Opcodes.ASM6);
      this.context = context;
      this.application = application;
      this.useJsrInliner = useJsrInliner;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
        String[] exceptions) {
      MethodNode node =
          useJsrInliner
              ? new JSRInlinerAdapter(null, access, name, desc, signature, exceptions)
              : new MethodNode(Opcodes.ASM6, access, name, desc, signature, exceptions);
      JarCode code = null;
      MethodAccessFlags flags = JarClassFileReader.createMethodAccessFlags(name, access);
      if (!flags.isAbstract() && !flags.isNative()) {
        code = context.codeList.get(methodIndex++).asJarCode();
        assert code.method == application.getMethod(context.owner.type, name, desc);
      }
      if (code != null) {
        code.context = null;
        code.node = node;
        return node;
      }
      return null;
    }
  }

  private static boolean verifyNoReparseContext(DexProgramClass owner) {
    for (DexEncodedMethod method : owner.virtualMethods()) {
      Code code = method.getCode();
      if (code != null && code.isJarCode()) {
        if (code.asJarCode().context != null) {
          return false;
        }
      }
    }

    for (DexEncodedMethod method : owner.directMethods()) {
      Code code = method.getCode();
      if (code != null && code.isJarCode()) {
        if (code.asJarCode().context != null) {
          return false;
        }
      }
    }
    return true;
  }
}
