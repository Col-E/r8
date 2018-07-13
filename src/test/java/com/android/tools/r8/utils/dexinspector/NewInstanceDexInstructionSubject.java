package com.android.tools.r8.utils.dexinspector;

import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.code.NewInstance;
import com.android.tools.r8.graph.DexType;

public class NewInstanceDexInstructionSubject extends DexInstructionSubject
    implements NewInstanceInstructionSubject {
  public NewInstanceDexInstructionSubject(Instruction instruction) {
    super(instruction);
  }

  @Override
  public DexType getType() {
    return ((NewInstance) instruction).getType();
  }
}
