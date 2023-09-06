// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexValue.DexValueMethodHandle;
import com.android.tools.r8.graph.DexValue.DexValueMethodType;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.lightir.LirConstant;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.StructuralItem;
import com.android.tools.r8.utils.structural.StructuralMapping;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import com.google.common.io.BaseEncoding;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;

public final class DexCallSite extends IndexedDexItem
    implements StructuralItem<DexCallSite>, LirConstant {

  public final DexString methodName;
  public final DexProto methodProto;

  public final DexMethodHandle bootstrapMethod;
  public final List<DexValue> bootstrapArgs;

  // Lazy computed encoding derived from the above immutable fields.
  private DexEncodedArray encodedArray = null;

  // Only used for sorting for deterministic output. This is the method and the instruction
  // offset where this DexCallSite ends up in the output.
  private DexMethod method;
  private int instructionOffset = -1;

  private static void specify(StructuralSpecification<DexCallSite, ?> spec) {
    spec
        // Use the possibly absent "context" info as the major key for sorting.
        // TODO(b/171867022): Investigate if this is needed now that a call-site can be sorted based
        //  on its content directly.
        .withNullableItem(c -> c.method)
        .withInt(c -> c.instructionOffset)
        // Actual call-site content.
        .withItem(c -> c.methodName)
        .withItem(c -> c.methodProto)
        .withItem(c -> c.bootstrapMethod)
        .withItemCollection(c -> c.bootstrapArgs);
  }

  DexCallSite(
      DexString methodName,
      DexProto methodProto,
      DexMethodHandle bootstrapMethod,
      List<DexValue> bootstrapArgs) {
    assert methodName != null;
    assert methodProto != null;
    assert bootstrapMethod != null;
    assert bootstrapArgs != null;

    this.methodName = methodName;
    this.methodProto = methodProto;
    this.bootstrapMethod = bootstrapMethod;
    this.bootstrapArgs = bootstrapArgs;
  }

  public static DexCallSite fromAsmInvokeDynamic(
      InvokeDynamicInsnNode insn, JarApplicationReader application, DexType clazz) {
    return fromAsmInvokeDynamic(application, clazz, insn.name, insn.desc, insn.bsm, insn.bsmArgs);
  }

  public static DexCallSite fromAsmInvokeDynamic(
      JarApplicationReader application,
      DexType clazz,
      String name,
      String desc,
      Handle bsmHandle,
      Object[] bsmArgs) {
    // Bootstrap method
    if (bsmHandle.getTag() != Opcodes.H_INVOKESTATIC
        && bsmHandle.getTag() != Opcodes.H_NEWINVOKESPECIAL) {
      // JVM9 §4.7.23 note: Tag must be InvokeStatic or NewInvokeSpecial.
      throw new CompilationError("Bootstrap handle invalid: tag == " + bsmHandle.getTag());
    }
    // Resolve the bootstrap method.
    DexMethodHandle bootstrapMethod = DexMethodHandle.fromAsmHandle(bsmHandle, application, clazz);

    // Decode static bootstrap arguments
    List<DexValue> bootstrapArgs = new ArrayList<>();
    for (Object arg : bsmArgs) {
      bootstrapArgs.add(DexValue.fromAsmBootstrapArgument(arg, application, clazz));
    }

    // Construct call site
    return application.getCallSite(name, desc, bootstrapMethod, bootstrapArgs);
  }

  public List<DexValue> getBootstrapArgs() {
    return bootstrapArgs;
  }

  public DexProto getMethodProto() {
    return methodProto;
  }

  @Override
  public DexCallSite self() {
    return this;
  }

  @Override
  public StructuralMapping<DexCallSite> getStructuralMapping() {
    return DexCallSite::specify;
  }

  public void setContext(DexMethod method, int instructionOffset) {
    assert method != null;
    assert instructionOffset >= 0;
    assert this.method == null;
    assert this.instructionOffset == -1;
    this.method = method;
    this.instructionOffset = instructionOffset;
  }

  @Override
  public int computeHashCode() {
    // Call sites are equal only when this == other, which was already computed by the caller of
    // computeEquals. Do not share call site entries, each invoke-custom must have its own
    // call site, but the content of the entry (encoded array) in the data section can be shared.
    return System.identityHashCode(this);
  }

  @Override
  public boolean computeEquals(Object other) {
    // Call sites are equal only when this == other, which was already computed by the caller of
    // computeEquals. Do not share call site entries, each invoke-custom must have its own
    // call site, but the content of the entry (encoded array) in the data section can be shared.
    return false;
  }

  @Override
  public String toString() {
    StringBuilder builder =
        new StringBuilder("CallSite: { Name: ").append(methodName.toSourceString())
            .append(", Proto: ").append(methodProto.toSourceString())
            .append(", ").append(bootstrapMethod.toSourceString());
    String sep = ", Args: ";
    for (DexItem arg : bootstrapArgs) {
      builder.append(sep).append(arg.toSourceString());
      sep = ", ";
    }
    builder.append('}');
    return builder.toString();
  }

  public void collectIndexedItems(AppView<?> appView, IndexedItemCollection indexedItems) {
    if (indexedItems.addCallSite(this)) {
      methodName.collectIndexedItems(indexedItems);
      methodProto.collectIndexedItems(appView, indexedItems);
      bootstrapMethod.collectIndexedItems(appView, indexedItems);
      for (DexValue arg : bootstrapArgs) {
        arg.collectIndexedItems(appView, indexedItems);
      }
    }
  }

  @Override
  protected void collectMixedSectionItems(MixedSectionCollection mixedItems) {
    getEncodedArray().collectMixedSectionItems(mixedItems);
  }

  @Override
  public int getOffset(ObjectToOffsetMapping mapping) {
    return mapping.getOffsetFor(this);
  }

  // TODO(mikaelpeltier): Adapt syntax when invoke-custom will be available into smali.
  @Override
  public String toSmaliString() {
    return toString();
  }

  public String getHash() {
    return new HashBuilder().build();
  }

  @Override
  public LirConstantOrder getLirConstantOrder() {
    return LirConstantOrder.CALL_SITE;
  }

  @Override
  public int internalLirConstantAcceptCompareTo(LirConstant other, CompareToVisitor visitor) {
    return acceptCompareTo((DexCallSite) other, visitor);
  }

  @Override
  public void internalLirConstantAcceptHashing(HashingVisitor visitor) {
    acceptHashing(visitor);
  }

  private final class HashBuilder {
    private ByteArrayOutputStream bytes;
    private ObjectOutputStream out;

    private void write(DexString string) throws IOException {
      out.writeInt(string.size); // To avoid same-prefix problem
      out.write(string.content);
    }

    private void write(DexType type) throws IOException {
      write(type.descriptor);
    }

    private void write(DexMethodHandle methodHandle) throws IOException {
      out.writeShort(methodHandle.type.getValue());
      if (methodHandle.isFieldHandle()) {
        write(methodHandle.asField());
      } else {
        write(methodHandle.asMethod());
      }
    }

    private void write(DexProto proto) throws IOException {
      write(proto.returnType);
      DexType[] params = proto.parameters.values;
      out.writeInt(params.length);
      for (DexType param : params) {
        write(param);
      }
    }

    private void write(DexMethod method) throws IOException {
      write(method.holder);
      write(method.proto);
      write(method.name);
    }

    private void write(DexField field) throws IOException {
      write(field.holder);
      write(field.type);
      write(field.name);
    }

    private void write(List<DexValue> args) throws IOException {
      out.writeInt(args.size());
      for (DexValue arg : args) {
        out.writeByte(arg.getValueKind().toByte());
        switch (arg.getValueKind()) {
          case DOUBLE:
            out.writeDouble(arg.asDexValueDouble().value);
            break;
          case FLOAT:
            out.writeFloat(arg.asDexValueFloat().value);
            break;
          case INT:
            out.writeInt(arg.asDexValueInt().value);
            break;
          case LONG:
            out.writeLong(arg.asDexValueLong().value);
            break;
          case METHOD_HANDLE:
            write(arg.asDexValueMethodHandle().value);
            break;
          case METHOD_TYPE:
            write(arg.asDexValueMethodType().value);
            break;
          case STRING:
            write(arg.asDexValueString().value);
            break;
          case TYPE:
            write(arg.asDexValueType().value);
            break;
          default:
            assert false;
        }
      }
    }

    String build() {
      try {
        bytes = new ByteArrayOutputStream();
        out = new ObjectOutputStream(bytes);

        // We will generate SHA-1 hash of the call site information based on call site
        // attributes used in equality comparison, such that if the two call sites are
        // different their hashes should also be different.
        write(methodName);
        write(methodProto);
        write(bootstrapMethod);
        write(bootstrapArgs);
        out.close();

        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        digest.update(bytes.toByteArray());
        return BaseEncoding.base64Url().omitPadding().encode(digest.digest());
      } catch (NoSuchAlgorithmException | IOException ex) {
        throw new Unreachable("Cannot get SHA-1 message digest");
      }
    }
  }

  public DexEncodedArray getEncodedArray() {
    if (encodedArray == null) {
      // 3 is the fixed size of the call site
      DexValue[] callSitesValues = new DexValue[3 + bootstrapArgs.size()];
      int valuesIndex = 0;
      callSitesValues[valuesIndex++] = new DexValueMethodHandle(bootstrapMethod);
      callSitesValues[valuesIndex++] = new DexValueString(methodName);
      callSitesValues[valuesIndex++] = new DexValueMethodType(methodProto);
      for (DexValue extraArgValue : bootstrapArgs) {
        callSitesValues[valuesIndex++] = extraArgValue;
      }
      encodedArray = new DexEncodedArray(callSitesValues);
    }

    return encodedArray;
  }
}
