// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.ApiLevelException;
import com.android.tools.r8.errors.InvalidDebugInfoException;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.ValueNumberGenerator;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.JarSourceCode;
import com.android.tools.r8.jar.JarRegisterEffectsVisitor;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.JSRInlinerAdapter;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

public class JarCode extends Code {

  // TODO(zerny): Write via the IR.
  public void writeTo(MethodVisitor visitor) {
    node.accept(visitor);
  }

  public static class ReparseContext {

    // This will hold the content of the whole class. Once all the methods of the class are swapped
    // from this to the actual JarCode, no other references would be left and the content can be
    // GC'd.
    public byte[] classCache;
    public DexProgramClass owner;
    private final List<JarCode> codeList = new ArrayList<>();
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
  public IRCode buildIR(DexEncodedMethod encodedMethod, InternalOptions options)
      throws ApiLevelException {
    triggerDelayedParsingIfNeccessary();
    return options.debug
        ? internalBuildWithLocals(encodedMethod, options, null, null)
        : internalBuild(encodedMethod, options, null, null);
  }

  @Override
  public IRCode buildInliningIR(
      DexEncodedMethod encodedMethod,
      InternalOptions options,
      ValueNumberGenerator generator,
      Position callerPosition)
      throws ApiLevelException {
    assert generator != null;
    triggerDelayedParsingIfNeccessary();
    return options.debug
        ? internalBuildWithLocals(encodedMethod, options, generator, callerPosition)
        : internalBuild(encodedMethod, options, generator, callerPosition);
  }

  private IRCode internalBuildWithLocals(
      DexEncodedMethod encodedMethod,
      InternalOptions options,
      ValueNumberGenerator generator,
      Position callerPosition)
      throws ApiLevelException {
    try {
      return internalBuild(encodedMethod, options, generator, callerPosition);
    } catch (InvalidDebugInfoException e) {
      options.warningInvalidDebugInfo(encodedMethod, origin, e);
      node.localVariables.clear();
      return internalBuild(encodedMethod, options, generator, callerPosition);
    }
  }

  private IRCode internalBuild(
      DexEncodedMethod encodedMethod,
      InternalOptions options,
      ValueNumberGenerator generator,
      Position callerPosition)
      throws ApiLevelException {
    if (!options.debug) {
      node.localVariables.clear();
    }
    JarSourceCode source = new JarSourceCode(
        method.getHolder(), node, application, encodedMethod.method, callerPosition);
    IRBuilder builder =
        (generator == null)
            ? new IRBuilder(encodedMethod, source, options)
            : new IRBuilder(encodedMethod, source, options, generator);
    return builder.build();
  }

  @Override
  public void registerInstructionsReferences(UseRegistry registry) {
    triggerDelayedParsingIfNeccessary();
    node.instructions.accept(
        new JarRegisterEffectsVisitor(method.getHolder(), registry, application));
  }

  @Override
  public void registerCaughtTypes(Consumer<DexType> dexTypeConsumer) {
    node.tryCatchBlocks.forEach(tryCatchBlockNode ->
        dexTypeConsumer.accept(application.getTypeFromDescriptor(
            DescriptorUtils.getDescriptorFromClassBinaryName(tryCatchBlockNode.type))));
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
    if (context != null) {
      // The SecondVistor is in charge of setting the context to null.
      DexProgramClass owner = context.owner;
      int flags = ClassReader.SKIP_FRAMES;
      if (application.options.isGeneratingClassFiles()) {
        // TODO(mathiasr): Keep frames in JarCode until IR->CF construction is complete.
        // When we throw Unimplemented in IR->CF construction, the original JarCode is output
        // instead. In this case we must output frames as well, or we will fail verification.
        flags = 0;
      }
      new ClassReader(context.classCache).accept(new SecondVisitor(context, application), flags);
      assert verifyNoReparseContext(owner);
    }
  }

  /**
   * Fills the MethodNodes of all the methods in the class and removes the ReparseContext.
   */
  private static class SecondVisitor extends ClassVisitor {

    private final ReparseContext context;
    private final JarApplicationReader application;
    private int methodIndex = 0;

    public SecondVisitor(ReparseContext context, JarApplicationReader application) {
      super(Opcodes.ASM6);
      this.context = context;
      this.application = application;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
        String[] exceptions) {
      MethodNode node = new JSRInlinerAdapter(null, access, name, desc, signature, exceptions);
      JarCode code = null;
      MethodAccessFlags flags = JarClassFileReader.createMethodAccessFlags(name, access);
      if (!flags.isAbstract() && !flags.isNative()) {
        code = context.codeList.get(methodIndex++);
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
