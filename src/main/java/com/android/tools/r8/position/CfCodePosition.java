package com.android.tools.r8.position;

import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.ProgramMethod;

import javax.annotation.Nonnull;

public class CfCodePosition implements Position {
	private final ProgramMethod method;
	private final CfCode code;
	private final int instructionIndex;

	public CfCodePosition(@Nonnull ProgramMethod method, @Nonnull CfCode code, int instructionIndex) {
		this.method = method;
		this.code = code;
		this.instructionIndex = instructionIndex;
	}

	@Nonnull
	public ProgramMethod getMethod() {
		return method;
	}

	@Nonnull
	public CfCode getCode() {
		return code;
	}

	public int getInstructionIndex() {
		return instructionIndex;
	}

	@Override
	public String getDescription() {
		return method.toSourceString() + " - @Code: " + instructionIndex;
	}
}
