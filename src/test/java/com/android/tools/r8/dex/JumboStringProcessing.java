// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.code.Const4;
import com.android.tools.r8.code.ConstString;
import com.android.tools.r8.code.ConstStringJumbo;
import com.android.tools.r8.code.Goto32;
import com.android.tools.r8.code.IfEq;
import com.android.tools.r8.code.IfEqz;
import com.android.tools.r8.code.IfNe;
import com.android.tools.r8.code.IfNez;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.code.ReturnVoid;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexCode.Try;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.dexinspector.DexInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class JumboStringProcessing extends TestBase {

  @Test
  public void branching() {
    DexItemFactory factory = new DexItemFactory();
    DexString string = factory.createString("turn into jumbo");
    factory.sort(NamingLens.getIdentityLens());
    Instruction[] instructions = buildInstructions(string, false);
    DexCode code = jumboStringProcess(factory, string, instructions);
    Instruction[] rewrittenInstructions = code.instructions;
    assert rewrittenInstructions[1] instanceof IfEq;
    IfEq condition = (IfEq) rewrittenInstructions[1];
    assert condition.getOffset() + condition.CCCC == rewrittenInstructions[3].getOffset();
    assert rewrittenInstructions[2] instanceof Goto32;
    Goto32 jump = (Goto32) rewrittenInstructions[2];
    Instruction lastInstruction = rewrittenInstructions[rewrittenInstructions.length - 1];
    assert jump.getOffset() + jump.AAAAAAAA == lastInstruction.getOffset();
  }

  @Test
  public void branching2() {
    DexItemFactory factory = new DexItemFactory();
    DexString string = factory.createString("turn into jumbo");
    factory.sort(NamingLens.getIdentityLens());
    Instruction[] instructions = buildInstructions(string, true);
    DexCode code = jumboStringProcess(factory, string, instructions);
    Instruction[] rewrittenInstructions = code.instructions;
    assert rewrittenInstructions[1] instanceof IfEqz;
    IfEqz condition = (IfEqz) rewrittenInstructions[1];
    assert condition.getOffset() + condition.BBBB == rewrittenInstructions[3].getOffset();
    assert rewrittenInstructions[2] instanceof Goto32;
    Goto32 jump = (Goto32) rewrittenInstructions[2];
    Instruction lastInstruction = rewrittenInstructions[rewrittenInstructions.length - 1];
    assert jump.getOffset() + jump.AAAAAAAA == lastInstruction.getOffset();
  }

  private Instruction[] buildInstructions(DexString string, boolean zeroCondition) {
    List<Instruction> instructions = new ArrayList<>();
    int offset = 0;
    Instruction instr = new Const4(0, 0);
    instr.setOffset(offset);
    instructions.add(instr);
    offset += instr.getSize();
    int lastInstructionOffset = 15000 * 2 + 2 + offset;
    if (zeroCondition) {
      instr = new IfNez(0, lastInstructionOffset - offset);
    } else {
      instr = new IfNe(0, 0, lastInstructionOffset - offset);
    }
    instr.setOffset(offset);
    instructions.add(instr);
    offset += instr.getSize();
    for (int i = 0; i < 15000; i++) {
      instr = new ConstString(0, string);
      instr.setOffset(offset);
      instructions.add(instr);
      offset += instr.getSize();
    }
    instr = new ReturnVoid();
    instr.setOffset(offset);
    instructions.add(instr);
    assert instr.getOffset() == lastInstructionOffset;
    return instructions.toArray(new Instruction[instructions.size()]);
  }

  private int countJumboStrings(Instruction[] instructions) {
    int count = 0;
    for (Instruction instruction : instructions) {
      count += instruction instanceof ConstStringJumbo ? 1 : 0;
    }
    return count;
  }

  private int countSimpleNops(Instruction[] instructions) {
    int count = 0;
    for (Instruction instruction : instructions) {
      count += instruction.isSimpleNop() ? 1 : 0;
    }
    return count;
  }

  @Test
  public void regress78072750() throws Exception {
    // This dex file have the baksmali output from the failing class from b/78072750, with all
    // const-string/jumbo replaced with const-string. Also one of the nops before the first
    // payload has been removed to make it valid dex file (correct alignment of the payload
    // instruction).
    Path originalDexFile =
        Paths.get(ToolHelper.SMALI_BUILD_DIR, "regression/78072750/78072750.dex");
    AndroidApp application = AndroidApp.builder()
        .addDexProgramData(Files.toByteArray(originalDexFile.toFile()), Origin.unknown())
        .build();
    DexInspector inspector = new DexInspector(application);
    DexEncodedMethod method = getMethod(
        inspector,
        "android.databinding.DataBinderMapperImpl",
        "android.databinding.ViewDataBinding",
        "getDataBinder",
        ImmutableList.of("android.databinding.DataBindingComponent", "android.view.View", "int"));
    Instruction[] instructions = method.getCode().asDexCode().instructions;
    assertEquals(0, countJumboStrings(instructions));
    assertEquals(1, countSimpleNops(instructions));

    DexItemFactory factory = inspector.getFactory();
    DexString string = factory.createString("view must have a tag");
    factory.sort(NamingLens.getIdentityLens());
    DexCode code = jumboStringProcess(factory, string, instructions);
    Instruction[] rewrittenInstructions = code.instructions;
    assertEquals(289, countJumboStrings(rewrittenInstructions));
    assertEquals(0, countSimpleNops(rewrittenInstructions));
  }

  private DexCode jumboStringProcess(
      DexItemFactory factory, DexString string, Instruction[] instructions) {
    DexCode code = new DexCode(
        1,
        0,
        0,
        instructions,
        new Try[0],
        null,
        null,
        null);
    MethodAccessFlags flags = MethodAccessFlags.fromSharedAccessFlags(Constants.ACC_PUBLIC, false);
    DexEncodedMethod method = new DexEncodedMethod(null, flags, null, null, code);
    new JumboStringRewriter(method, string, factory).rewrite();
    return method.getCode().asDexCode();
  }
}
