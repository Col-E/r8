package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import java.util.*;

public class TrivialCatchBlockMerger extends CodeRewriterPass<AppInfo> {
	public TrivialCatchBlockMerger(AppView<?> appView) {
		super(appView);
	}

	@Override
	protected String getTimingId() {
		return "DuplicateCatchBlockMerger";
	}

	@Override
	protected boolean shouldRewriteCode(IRCode code) {
		return appView.options().isGeneratingClassFiles();
	}

	@Override
	protected boolean isAcceptingSSA() {
		return false;
	}

	@Override
	protected boolean isProducingSSA() {
		return false;
	}

	@Override
	protected CodeRewriterResult rewriteCode(IRCode code) {
		// Given the following circumstances:
		//
		// block A1: when exception --> B1
		// block A2: when exception --> B2
		// block B1: goto C
		// block B2: goto C
		// block C: ...
		//
		// We want to make a map that holds:
		//  C: [B1, B2]
		Map<BasicBlock, SortedSet<BasicBlock>> destinationBlockToDelegateBlock = new IdentityHashMap<>();
		for (BasicBlock block : code.getBlocks()) {
			if (block.isTrivialGoto() && block.isEventuallySuccessorOfUnmovedException()) {
				// We are a block with just a 'goto' and are a catch handler block.
				BasicBlock destination = block.getUniqueSuccessor();
				destinationBlockToDelegateBlock.computeIfAbsent(destination, d -> new TreeSet<>()).add(block);
			}
		}

		// For all blocks with catch-handlers, if they're trivial, collapse references to a single block.
		Set<BasicBlock> removedBlocks = Sets.newIdentityHashSet();
		for (BasicBlock block : code.getBlocks()) {
			if (!block.hasCatchHandlers())
				continue;

			CatchHandlers<BasicBlock> handlers = block.getCatchHandlers();
			for (CatchHandlers.CatchHandler<BasicBlock> catchHandler : ImmutableList.copyOf(handlers)) {
				BasicBlock catchTarget = catchHandler.getTarget();
				BasicBlock replacement = getDestinationOfDelegateHandler(destinationBlockToDelegateBlock, catchTarget);
				if (catchTarget != replacement) {
					block.replaceSuccessor(catchTarget, replacement);

					// Add to pending removal queue
					removedBlocks.add(catchTarget);

					// For the block removed, tell its successors that its no longer a predecessor as it will be removed.
					for (BasicBlock successor : catchTarget.getSuccessors())
						successor.getMutablePredecessors().remove(catchTarget);
				}
			}
		}
		code.removeBlocks(removedBlocks);

		return CodeRewriterResult.NONE;
	}

	private BasicBlock getDestinationOfDelegateHandler(Map<BasicBlock, SortedSet<BasicBlock>> destinationBlockToDelegateBlock,
													   BasicBlock target) {
		for (Map.Entry<BasicBlock, SortedSet<BasicBlock>> entry : destinationBlockToDelegateBlock.entrySet()) {
			SortedSet<BasicBlock> delegates = entry.getValue();
			if (delegates.contains(target))
				return delegates.first();
		}
		return target;
	}
}
