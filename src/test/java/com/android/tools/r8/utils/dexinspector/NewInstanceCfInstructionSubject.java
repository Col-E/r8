package com.android.tools.r8.utils.dexinspector;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.graph.DexType;

public class NewInstanceCfInstructionSubject extends CfInstructionSubject
    implements NewInstanceInstructionSubject {
  public NewInstanceCfInstructionSubject(CfInstruction instruction) {
    super(instruction);
  }

  @Override
  public DexType getType() {
    return ((CfNew) instruction).getType();
  }
}
