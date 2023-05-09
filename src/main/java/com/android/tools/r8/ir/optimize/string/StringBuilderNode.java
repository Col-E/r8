// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.string;

import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Value;
import com.google.common.collect.Sets;
import java.util.Set;

/**
 * StringBuilderNode defines a single point where a string builder operation occur or some abstract
 * state changes. The node can be assembled into a graph by defining predecessors and successors.
 */
class StringBuilderNode {

  interface StringBuilderInstruction {

    Instruction getInstruction();

    boolean isStringBuilderInstructionNode();

    StringBuilderInstruction asStringBuilderInstructionNode();
  }

  interface InitOrAppendNode extends StringBuilderInstruction {

    boolean isInitNode();

    boolean isAppendNode();

    InitNode asInitNode();

    AppendNode asAppendNode();

    boolean hasConstantArgument();

    String getConstantArgument();

    void setConstantArgument(String constantArgument);

    void setImplicitToStringNode(ImplicitToStringNode node);

    ImplicitToStringNode getImplicitToStringNode();

    boolean hasNonConstantArgument();

    void setNonConstantArgument(Value value);

    Value getNonConstantArgument();

    default boolean hasConstantOrNonConstantArgument() {
      return hasConstantArgument() || hasNonConstantArgument();
    }

    boolean hasSingleSuccessor();

    boolean hasSinglePredecessor();

    StringBuilderNode getSingleSuccessor();

    StringBuilderNode getSinglePredecessor();
  }

  private final Set<StringBuilderNode> successors = Sets.newLinkedHashSet();
  private final Set<StringBuilderNode> predecessors = Sets.newLinkedHashSet();

  // Field uses to ensure that munching will not operate on the same value multiple times. If all
  // peep holes would look in the same direction, this field could be removed.
  private boolean isDead = false;

  private StringBuilderNode() {}

  boolean isEscapeNode() {
    return false;
  }

  boolean isMutateNode() {
    return false;
  }

  boolean isSplitReferenceNode() {
    return false;
  }

  boolean isLoopNode() {
    return false;
  }

  boolean isNewInstanceNode() {
    return false;
  }

  public boolean isInitNode() {
    return false;
  }

  public boolean isAppendNode() {
    return false;
  }

  boolean isInitOrAppend() {
    return false;
  }

  boolean isToStringNode() {
    return false;
  }

  boolean isInspectingNode() {
    return false;
  }

  boolean isOtherStringBuilderNode() {
    return false;
  }

  boolean isImplicitToStringNode() {
    return false;
  }

  boolean isStringBuilderInstructionNode() {
    return false;
  }

  NewInstanceNode asNewInstanceNode() {
    return null;
  }

  public InitNode asInitNode() {
    return null;
  }

  public AppendNode asAppendNode() {
    return null;
  }

  InitOrAppendNode asInitOrAppend() {
    return null;
  }

  ToStringNode asToStringNode() {
    return null;
  }

  InspectingNode asInspectingNode() {
    return null;
  }

  OtherStringBuilderNode asOtherStringBuilderNode() {
    return null;
  }

  ImplicitToStringNode asImplicitToStringNode() {
    return null;
  }

  StringBuilderInstruction asStringBuilderInstructionNode() {
    return null;
  }

  boolean isDead() {
    return isDead;
  }

  public boolean hasSingleSuccessor() {
    return successors.size() == 1;
  }

  public StringBuilderNode getSingleSuccessor() {
    assert hasSingleSuccessor();
    return successors.iterator().next();
  }

  void addSuccessor(StringBuilderNode successor) {
    successors.add(successor);
    successor.predecessors.add(this);
  }

  Set<StringBuilderNode> getSuccessors() {
    return successors;
  }

  public boolean hasSinglePredecessor() {
    return predecessors.size() == 1;
  }

  public StringBuilderNode getSinglePredecessor() {
    assert hasSinglePredecessor();
    return predecessors.iterator().next();
  }

  Set<StringBuilderNode> getPredecessors() {
    return predecessors;
  }

  void addPredecessor(StringBuilderNode predecessor) {
    predecessors.add(predecessor);
  }

  void removeNode() {
    for (StringBuilderNode successor : this.getSuccessors()) {
      successor.getPredecessors().remove(this);
      successor.getPredecessors().addAll(this.getPredecessors());
    }
    for (StringBuilderNode predecessor : this.getPredecessors()) {
      predecessor.getSuccessors().remove(this);
      predecessor.getSuccessors().addAll(this.getSuccessors());
    }
    isDead = true;
  }

  /** EscapeNode is used to explicitly define an escape point for a StringBuilder. */
  static class EscapeNode extends StringBuilderNode {

    @Override
    boolean isEscapeNode() {
      return true;
    }
  }

  /**
   * MutateNode defines if a string builder could be possibly mutated or inspected. An example could
   * be:
   *
   * <pre>
   * sb = new StringBuilder();
   * escape(sb);
   * sb.append("foo");
   * canMutate(); <-- This is represented by a MutateNode.
   * sb.append("bar");
   * </pre>
   */
  static class MutateNode extends StringBuilderNode {

    @Override
    boolean isMutateNode() {
      return true;
    }
  }

  /**
   * SplitReferenceNodes are synthetic nodes inserted when a string builder is used in multiple
   * successor blocks.
   */
  static class SplitReferenceNode extends StringBuilderNode {

    @Override
    boolean isSplitReferenceNode() {
      return true;
    }
  }

  /**
   * LoopNode is a node indicating that all successor paths are part of a loop. LoopNode's are only
   * inserted once ensuring there are no loops in a StringBuilderGraph.
   */
  static class LoopNode extends StringBuilderNode {

    @Override
    boolean isLoopNode() {
      return true;
    }
  }

  /** A NewInstanceNode is a new instance of either StringBuilder or StringBuffer. */
  static class NewInstanceNode extends StringBuilderNode implements StringBuilderInstruction {

    private final NewInstance instruction;

    private NewInstanceNode(NewInstance instruction) {
      this.instruction = instruction;
    }

    @Override
    boolean isNewInstanceNode() {
      return true;
    }

    @Override
    NewInstanceNode asNewInstanceNode() {
      return this;
    }

    @Override
    public Instruction getInstruction() {
      return instruction;
    }

    @Override
    public boolean isStringBuilderInstructionNode() {
      return true;
    }

    @Override
    public StringBuilderInstruction asStringBuilderInstructionNode() {
      return this;
    }
  }

  /** An initNode is where a StringBuilder/StringBuffer is initialized. */
  static class InitNode extends StringBuilderNode
      implements InitOrAppendNode, StringBuilderInstruction {

    private final InvokeDirect instruction;
    private ImplicitToStringNode implicitToStringNode;
    private String constantArgument;
    private Value nonConstantArgument;

    private InitNode(InvokeDirect instruction) {
      this.instruction = instruction;
    }

    @Override
    public boolean isInitNode() {
      return true;
    }

    @Override
    boolean isInitOrAppend() {
      return true;
    }

    @Override
    public InitNode asInitNode() {
      return this;
    }

    @Override
    InitOrAppendNode asInitOrAppend() {
      return this;
    }

    @Override
    public Instruction getInstruction() {
      return instruction;
    }

    @Override
    public boolean isStringBuilderInstructionNode() {
      return true;
    }

    @Override
    public StringBuilderInstruction asStringBuilderInstructionNode() {
      return this;
    }

    @Override
    public void setConstantArgument(String constantArgument) {
      this.constantArgument = constantArgument;
    }

    @Override
    public String getConstantArgument() {
      return constantArgument;
    }

    @Override
    public boolean hasConstantArgument() {
      return constantArgument != null;
    }

    @Override
    public void setNonConstantArgument(Value value) {
      this.nonConstantArgument = value;
    }

    @Override
    public void setImplicitToStringNode(ImplicitToStringNode node) {
      implicitToStringNode = node;
    }

    @Override
    public ImplicitToStringNode getImplicitToStringNode() {
      return implicitToStringNode;
    }

    @Override
    public boolean hasNonConstantArgument() {
      return nonConstantArgument != null;
    }

    @Override
    public Value getNonConstantArgument() {
      assert nonConstantArgument != null;
      return nonConstantArgument;
    }

    boolean isConstructorInvokeSideEffectFree(StringBuilderOracle oracle) {
      return oracle.isConstructorInvokeSideEffectFree(instruction);
    }
  }

  /** AppendNodes are StringBuilder.append or StringBuffer.append. */
  static class AppendNode extends StringBuilderNode
      implements InitOrAppendNode, StringBuilderInstruction {

    private final InvokeVirtual instruction;
    private ImplicitToStringNode implicitToStringNode;
    private String constantArgument;
    private Value nonConstantArgument;

    private AppendNode(InvokeVirtual instruction) {
      this.instruction = instruction;
    }

    @Override
    public boolean isAppendNode() {
      return true;
    }

    @Override
    boolean isInitOrAppend() {
      return true;
    }

    @Override
    public AppendNode asAppendNode() {
      return this;
    }

    @Override
    InitOrAppendNode asInitOrAppend() {
      return this;
    }

    @Override
    public Instruction getInstruction() {
      return instruction;
    }

    @Override
    public boolean isStringBuilderInstructionNode() {
      return true;
    }

    @Override
    public StringBuilderInstruction asStringBuilderInstructionNode() {
      return this;
    }

    @Override
    public void setConstantArgument(String constantArgument) {
      this.constantArgument = constantArgument;
    }

    @Override
    public void setImplicitToStringNode(ImplicitToStringNode node) {
      implicitToStringNode = node;
    }

    @Override
    public ImplicitToStringNode getImplicitToStringNode() {
      return implicitToStringNode;
    }

    @Override
    public boolean hasNonConstantArgument() {
      return nonConstantArgument != null;
    }

    @Override
    public void setNonConstantArgument(Value value) {
      this.nonConstantArgument = value;
    }

    @Override
    public Value getNonConstantArgument() {
      return nonConstantArgument;
    }

    @Override
    public String getConstantArgument() {
      return constantArgument;
    }

    @Override
    public boolean hasConstantArgument() {
      return constantArgument != null;
    }
  }

  /**
   * ToStringNodes marked a point where a StringBuilder/StringBuffer is materialized to a string.
   */
  static class ToStringNode extends StringBuilderNode implements StringBuilderInstruction {

    private final InvokeMethod instruction;

    private ToStringNode(InvokeMethod instruction) {
      this.instruction = instruction;
    }

    @Override
    boolean isToStringNode() {
      return true;
    }

    @Override
    ToStringNode asToStringNode() {
      return this;
    }

    @Override
    public Instruction getInstruction() {
      return instruction;
    }

    @Override
    public boolean isStringBuilderInstructionNode() {
      return true;
    }

    @Override
    public StringBuilderInstruction asStringBuilderInstructionNode() {
      return this;
    }
  }

  /**
   * InspectingNode is inserted if there is inspection of the capacity of a
   * StringBuilder/StringBuffer. Special care needs to be taken since we try to ensure that capacity
   * will be the same after optimizations.
   */
  static class InspectingNode extends StringBuilderNode implements StringBuilderInstruction {

    private final Instruction instruction;

    private InspectingNode(Instruction instruction) {
      this.instruction = instruction;
    }

    @Override
    boolean isInspectingNode() {
      return true;
    }

    @Override
    InspectingNode asInspectingNode() {
      return this;
    }

    @Override
    public Instruction getInstruction() {
      return instruction;
    }

    @Override
    public boolean isStringBuilderInstructionNode() {
      return true;
    }

    @Override
    public StringBuilderInstruction asStringBuilderInstructionNode() {
      return this;
    }
  }

  /** OtherStringBuilderNode marks operations on string builders we do not model. */
  static class OtherStringBuilderNode extends StringBuilderNode
      implements StringBuilderInstruction {

    private final Instruction instruction;

    private OtherStringBuilderNode(Instruction instruction) {
      this.instruction = instruction;
    }

    @Override
    boolean isOtherStringBuilderNode() {
      return true;
    }

    @Override
    OtherStringBuilderNode asOtherStringBuilderNode() {
      return this;
    }

    @Override
    public Instruction getInstruction() {
      return instruction;
    }

    @Override
    public boolean isStringBuilderInstructionNode() {
      return true;
    }

    @Override
    public StringBuilderInstruction asStringBuilderInstructionNode() {
      return this;
    }
  }

  /**
   * ImplicitToStringNode are placed when StringBuilder/StringBuffer is appended to another
   * StringBuilder/StringBuffer.
   */
  static class ImplicitToStringNode extends StringBuilderNode {

    private final InitOrAppendNode initOrAppend;

    ImplicitToStringNode(InitOrAppendNode initOrAppend) {
      this.initOrAppend = initOrAppend;
    }

    public InitOrAppendNode getInitOrAppend() {
      return initOrAppend;
    }

    @Override
    boolean isImplicitToStringNode() {
      return true;
    }

    @Override
    ImplicitToStringNode asImplicitToStringNode() {
      return this;
    }
  }

  static EscapeNode createEscapeNode() {
    return new EscapeNode();
  }

  static MutateNode createMutateNode() {
    return new MutateNode();
  }

  static SplitReferenceNode createSplitReferenceNode() {
    return new SplitReferenceNode();
  }

  static LoopNode createLoopNode() {
    return new LoopNode();
  }

  static NewInstanceNode createNewInstanceNode(NewInstance instruction) {
    return new NewInstanceNode(instruction);
  }

  static InitNode createInitNode(InvokeDirect instruction) {
    return new InitNode(instruction);
  }

  static AppendNode createAppendNode(InvokeVirtual instruction) {
    return new AppendNode(instruction);
  }

  static ToStringNode createToStringNode(InvokeMethod instruction) {
    return new ToStringNode(instruction);
  }

  static InspectingNode createInspectionNode(Instruction instruction) {
    return new InspectingNode(instruction);
  }

  static OtherStringBuilderNode createOtherStringBuilderNode(Instruction instruction) {
    return new OtherStringBuilderNode(instruction);
  }

  static ImplicitToStringNode createImplicitToStringNode(InitOrAppendNode otherNode) {
    return new ImplicitToStringNode(otherNode);
  }
}
