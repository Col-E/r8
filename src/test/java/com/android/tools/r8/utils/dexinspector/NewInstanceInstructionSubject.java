package com.android.tools.r8.utils.dexinspector;

import com.android.tools.r8.graph.DexType;

public interface NewInstanceInstructionSubject extends InstructionSubject {
  DexType getType();
}
