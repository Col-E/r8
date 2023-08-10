// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.lightir;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.ir.code.IRMetadata;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.code.Opcodes;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.utils.ArrayUtils;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class SimpleLensLirRewriter<EV> extends LirParsedInstructionCallback<EV> {

  private final ProgramMethod context;
  private final DexMethod contextReference;
  private final GraphLens graphLens;
  private final GraphLens codeLens;
  private final LensCodeRewriterUtils helper;

  private int numberOfInvokeTypeChanges = 0;
  private Map<DexItem, DexItem> constantPoolMapping = null;

  public SimpleLensLirRewriter(
      LirCode<EV> code,
      ProgramMethod context,
      GraphLens graphLens,
      GraphLens codeLens,
      LensCodeRewriterUtils helper) {
    super(code);
    this.context = context;
    this.contextReference = context.getReference();
    this.graphLens = graphLens;
    this.codeLens = codeLens;
    this.helper = helper;
  }

  @Override
  public int getCurrentValueIndex() {
    // We do not need to interpret values.
    return -1;
  }

  public void onTypeReference(DexType type) {
    addRewrittenMapping(type, graphLens.lookupType(type, codeLens));
  }

  public void onFieldReference(DexField field) {
    addRewrittenMapping(field, graphLens.lookupField(field, codeLens));
  }

  public void onCallSiteReference(DexCallSite callSite) {
    addRewrittenMapping(callSite, helper.rewriteCallSite(callSite, context));
  }

  public void onProtoReference(DexProto proto) {
    addRewrittenMapping(proto, helper.rewriteProto(proto));
  }

  private void onInvoke(DexMethod method, InvokeType type) {
    MethodLookupResult result = graphLens.lookupMethod(method, contextReference, type, codeLens);
    if (result.getType() != type) {
      assert (type == InvokeType.VIRTUAL && result.getType() == InvokeType.INTERFACE)
          || (type == InvokeType.INTERFACE && result.getType() == InvokeType.VIRTUAL);
      numberOfInvokeTypeChanges++;
    } else {
      // All non-type dependent mappings are just rewritten in the content pool.
      addRewrittenMapping(method, result.getReference());
    }
  }

  private void addRewrittenMapping(DexItem item, DexItem rewrittenItem) {
    if (item == rewrittenItem) {
      return;
    }
    if (constantPoolMapping == null) {
      constantPoolMapping =
          new IdentityHashMap<>(
              // Avoid using initial capacity larger than the number of actual constants.
              Math.min(getCode().getConstantPool().length, 32));
    }
    DexItem old = constantPoolMapping.put(item, rewrittenItem);
    if (old != null && old != rewrittenItem) {
      throw new Unreachable(
          "Unexpected rewriting of item: "
              + item
              + " to two distinct items: "
              + rewrittenItem
              + " and "
              + old);
    }
  }

  @Override
  public void onInvokeDirect(DexMethod method, List<EV> arguments, boolean isInterface) {
    onInvoke(method, InvokeType.DIRECT);
  }

  @Override
  public void onInvokeSuper(DexMethod method, List<EV> arguments, boolean isInterface) {
    onInvoke(method, InvokeType.SUPER);
  }

  @Override
  public void onInvokeVirtual(DexMethod method, List<EV> arguments) {
    onInvoke(method, InvokeType.VIRTUAL);
  }

  @Override
  public void onInvokeStatic(DexMethod method, List<EV> arguments, boolean isInterface) {
    onInvoke(method, InvokeType.STATIC);
  }

  @Override
  public void onInvokeInterface(DexMethod method, List<EV> arguments) {
    onInvoke(method, InvokeType.INTERFACE);
  }

  private InvokeType getInvokeTypeThatMayChange(int opcode) {
    if (opcode == LirOpcodes.INVOKEVIRTUAL) {
      return InvokeType.VIRTUAL;
    }
    if (opcode == LirOpcodes.INVOKEINTERFACE) {
      return InvokeType.INTERFACE;
    }
    return null;
  }

  public LirCode<EV> rewrite() {
    LirCode<EV> rewritten = rewriteConstantPoolAndScanForTypeChanges(getCode());
    return rewriteInstructionsWithInvokeTypeChanges(rewritten);
  }

  private LirCode<EV> rewriteConstantPoolAndScanForTypeChanges(LirCode<EV> code) {
    // The code may need to be rewritten by the lens.
    // First pass scans just the constant pool to see if any types change or if there are any
    // fields/methods that need to be examined.
    boolean hasPotentialRewrittenMethod = false;
    for (DexItem constant : code.getConstantPool()) {
      if (constant instanceof DexType) {
        onTypeReference((DexType) constant);
      } else if (constant instanceof DexField) {
        onFieldReference((DexField) constant);
      } else if (constant instanceof DexCallSite) {
        onCallSiteReference((DexCallSite) constant);
      } else if (constant instanceof DexProto) {
        onProtoReference((DexProto) constant);
      } else if (!hasPotentialRewrittenMethod && constant instanceof DexMethod) {
        // We might be able to still fast-case this if we can guarantee the method is never
        // rewritten. Say it is an java.lang.Object reference or if the lens can fast-check it.
        hasPotentialRewrittenMethod = true;
      }
    }

    // If there are potential method rewritings then we need to iterate the instructions as the
    // rewriting is instruction-sensitive (i.e., may be dependent on the invoke type).
    if (hasPotentialRewrittenMethod) {
      for (LirInstructionView view : code) {
        view.accept(this);
      }
    }

    if (constantPoolMapping == null) {
      return code;
    }

    return code.newCodeWithRewrittenConstantPool(
        item -> constantPoolMapping.getOrDefault(item, item));
  }

  private LirCode<EV> rewriteInstructionsWithInvokeTypeChanges(LirCode<EV> code) {
    if (numberOfInvokeTypeChanges == 0) {
      return code;
    }
    // Build a small map from method refs to index in case the type-dependent methods are already
    // in the constant pool.
    Reference2IntMap<DexMethod> methodIndices = new Reference2IntOpenHashMap<>();
    DexItem[] rewrittenConstants = code.getConstantPool();
    for (int i = 0, dexItemsLength = rewrittenConstants.length; i < dexItemsLength; i++) {
      DexItem constant = rewrittenConstants[i];
      if (constant instanceof DexMethod) {
        methodIndices.put((DexMethod) constant, i);
      }
    }

    IRMetadata irMetadata = code.getMetadataForIR();
    ByteArrayWriter byteWriter = new ByteArrayWriter();
    LirWriter lirWriter = new LirWriter(byteWriter);
    List<DexItem> methodsToAppend = new ArrayList<>(numberOfInvokeTypeChanges);
    for (LirInstructionView view : code) {
      int opcode = view.getOpcode();
      // Instructions that do not have an invoke-type change are just mapped via identity.
      if (LirOpcodes.isOneByteInstruction(opcode)) {
        lirWriter.writeOneByteInstruction(opcode);
        continue;
      }
      InvokeType type = getInvokeTypeThatMayChange(opcode);
      if (type == null) {
        int size = view.getRemainingOperandSizeInBytes();
        lirWriter.writeInstruction(opcode, size);
        while (size-- > 0) {
          lirWriter.writeOperand(view.getNextU1());
        }
        continue;
      }
      // This is potentially an invoke with a type change, in such cases the method is mapped with
      // the instruction updated to the new type. The constant pool is amended with the mapped
      // method if needed.
      int constantIndex = view.getNextConstantOperand();
      DexMethod method = (DexMethod) code.getConstantItem(constantIndex);
      MethodLookupResult result =
          graphLens.lookupMethod(method, context.getReference(), type, codeLens);
      if (result.getType() != type) {
        --numberOfInvokeTypeChanges;
        if (result.getType().isVirtual()) {
          opcode = LirOpcodes.INVOKEVIRTUAL;
          irMetadata.record(Opcodes.INVOKE_VIRTUAL);
        } else if (result.getType().isInterface()) {
          opcode = LirOpcodes.INVOKEINTERFACE;
          irMetadata.record(Opcodes.INVOKE_INTERFACE);
        } else {
          throw new Unreachable(
              "Unexpected change of invoke that may need an interface bit set: "
                  + result.getType());
        }
        constantIndex =
            methodIndices.computeIfAbsent(
                result.getReference(),
                ref -> {
                  methodsToAppend.add(ref);
                  return rewrittenConstants.length + methodsToAppend.size() - 1;
                });
      }
      int constantIndexSize = ByteUtils.intEncodingSize(constantIndex);
      int remainingSize = view.getRemainingOperandSizeInBytes();
      lirWriter.writeInstruction(opcode, constantIndexSize + remainingSize);
      ByteUtils.writeEncodedInt(constantIndex, lirWriter::writeOperand);
      while (remainingSize-- > 0) {
        lirWriter.writeOperand(view.getNextU1());
      }
    }
    assert numberOfInvokeTypeChanges == 0;
    // Note that since we assume 'null' in the mapping is identity this may end up with a stale
    // reference to a no longer used method. That is not an issue as it will be pruned when
    // building IR again, it is just a small and size overhead.
    LirCode<EV> newCode =
        code.copyWithNewConstantsAndInstructions(
            irMetadata,
            ArrayUtils.appendElements(code.getConstantPool(), methodsToAppend),
            byteWriter.toByteArray());
    return newCode;
  }
}
