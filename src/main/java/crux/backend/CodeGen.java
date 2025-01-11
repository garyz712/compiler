package crux.backend;

import crux.ast.OpExpr;
import crux.ast.SymbolTable.Symbol;
import crux.ir.*;
import crux.ir.insts.*;
import crux.printing.IRValueFormatter;

import java.util.*;

/**
 * Convert the CFG into Assembly Instructions
 */
public final class CodeGen extends InstVisitor {
  private final Program p;
  private final CodePrinter out;

  HashMap<Instruction, String> flmap;

  private HashMap<Variable, Integer> varIndexMap =  new HashMap<Variable, Integer>();;
  private int varIndex = 0;
  Integer getStackSlot(Variable v) {
    if (!varIndexMap.containsKey(v)) {
      varIndex++;
      varIndexMap.put(v, varIndex);
    }

    return varIndexMap.get(v);
  }

  public CodeGen(Program p) {
    this.p = p;
    // Do not change the file name that is outputted or it will
    // break the grader!

    out = new CodePrinter("a.s");
  }

  /**
   * It should allocate space for globals call genCode for each Function
   */
  public void genCode() {
    //TODO
    for (Iterator<GlobalDecl> glob_it = p.getGlobals(); glob_it.hasNext(); ) {
      GlobalDecl g = glob_it.next();
      String name = g.getSymbol().getName();
      long size = g.getNumElement().getValue()*8;
      out.printCode(".comm " + name + ", " + size + ", 8");
    }

    int count[] = new int[1];
    for (Iterator<Function> func_it = p.getFunctions(); func_it.hasNext(); ) {
      Function f = func_it.next();
      genCode(f, count);
    }

    out.close();

  }

  private void genCode(Function f, int count[]){
    //flmap.putAll(f.assignLabels(count)); TODO which one?
    flmap = f.assignLabels(count);
    out.printCode(".globl " + f.getName());
    out.printLabel(f.getName() + ":");
    int numSlots = f.getNumTempVars() + f.getNumTempAddressVars();
    numSlots = (numSlots + 1) & ~1; //round up to nearest even number
    out.printCode("enter $(8 * "+ numSlots+"), $0");
    varIndex = 0; //TODO verify is this correct?
    for (int i =1; i <= f.getArguments().size(); i++){
      int argslot = getStackSlot(f.getArguments().get(i-1));
      int argoffset = - argslot * 8; //TODO is this correct to create a new stack?
      if (i==1){
        out.printCode("movq %rdi, "+argoffset+"(%rbp)");
      }else if (i==2){
        out.printCode("movq %rsi, "+argoffset+"(%rbp)");
      }else if (i ==3){
        out.printCode("movq %rdx, "+argoffset+"(%rbp)");
      }else if (i==4){
        out.printCode("movq %rcx, "+argoffset+"(%rbp)");
      }else if (i==5){
        out.printCode("movq %r8, "+argoffset+"(%rbp)");
      }else if(i==6){
        out.printCode("movq %r9, "+argoffset+"(%rbp)");
      }else{
        out.printCode("movq "+8*(i-5)+"(%rbp), %r10");
        out.printCode("movq %r10, "+argoffset + "(%rbp)");
      }
    }


    Stack<Instruction> tovisit = new Stack<>();
    HashSet<Instruction> discovered = new HashSet<>();
    if (f.getStart() != null)
      tovisit.push(f.getStart());
    while (!tovisit.isEmpty()) {
      Instruction inst = tovisit.pop();
      if (discovered.contains(inst)){
        out.printCode("jmp "+flmap.get(inst));
      }else{
        if (flmap.containsKey(inst)){
          out.printCode(flmap.get(inst)+":");
        }
        inst.accept(this);
        discovered.add(inst);
        if (inst.numNext()>0){
          for (int childIdx = inst.numNext()-1; childIdx >= 0; childIdx--) {
            Instruction child = inst.getNext(childIdx);
            tovisit.push(child);
          }
        }else{

            out.printCode("leave");
            out.printCode("ret");
          }
        }
      }
    }



  public void visit(AddressAt i) {
    var VarName = i.getBase().getName();
    out.printCode("movq "+VarName+"@GOTPCREL(%rip), %r11");

    if (i.getOffset() != null){
      int idxslot = getStackSlot(i.getOffset());
      int idxoffset = - idxslot * 8;
      out.printCode("movq "+ idxoffset +"(%rbp), %r10");
      out.printCode("imulq $8, %r10");
      out.printCode("addq %r10, %r11");
    }
    int dstslot = getStackSlot(i.getDst());
    int dstoffset = - dstslot * 8;
    out.printCode("movq %r11, "+dstoffset+"(%rbp)");


  }

  public void visit(BinaryOperator i) {
    Variable dst = i.getDst();
    Variable lhs = i.getLeftOperand();
    Variable rhs = i.getRightOperand();
    int dstslot = getStackSlot(dst);
    int lhsslot = getStackSlot(lhs);
    int rhsslot = getStackSlot(rhs);
    int dstoffset = -dstslot * 8;
    int lhsoffset = -lhsslot * 8;
    int rhsoffset = -rhsslot * 8;

    switch(i.getOperator()) {
      case Add:
        out.printCode("movq "+lhsoffset+"(%rbp), %r10");
        out.printCode("addq "+rhsoffset+"(%rbp), %r10");
        out.printCode("movq %r10, "+ dstoffset+"(%rbp)");
        break;
      case Sub:
        out.printCode("movq "+lhsoffset+"(%rbp), %r10");
        out.printCode("subq "+rhsoffset+"(%rbp), %r10");
        out.printCode("movq %r10, "+ dstoffset+"(%rbp)");
        break;
      case Mul:
        out.printCode("movq "+lhsoffset+"(%rbp), %r10");
        out.printCode("imulq "+rhsoffset+"(%rbp), %r10");
        out.printCode("movq %r10, "+ dstoffset+"(%rbp)");
        break;
      case Div:
        out.printCode("movq "+lhsoffset+"(%rbp), %rax");
        out.printCode("cqto");
        out.printCode("idivq " + rhsoffset + "(%rbp)");
        out.printCode("movq %rax, "+ dstoffset+"(%rbp)");
        break;
    }
  }

  public void visit(CompareInst i) {
    Variable dst = i.getDst();
    Variable lhs = i.getLeftOperand();
    Variable rhs = i.getRightOperand();
    int dstslot = getStackSlot(dst);
    int lhsslot = getStackSlot(lhs);
    int rhsslot = getStackSlot(rhs);
    int dstoffset = -dstslot * 8;
    int lhsoffset = -lhsslot * 8;
    int rhsoffset = -rhsslot * 8;

    switch(i.getPredicate()) {
      case GE:
        out.printCode("movq $0, %rax");
        out.printCode("movq $1, %r10");
        out.printCode("movq "+lhsoffset+"(%rbp), %r11");
        out.printCode("cmp "+rhsoffset+"(%rbp), %r11");
        out.printCode("cmovge %r10, %rax");
        out.printCode("movq %rax, "+ dstoffset+"(%rbp)");
        break;
      case GT:
        out.printCode("movq $0, %rax");
        out.printCode("movq $1, %r10");
        out.printCode("movq "+lhsoffset+"(%rbp), %r11");
        out.printCode("cmp "+rhsoffset+"(%rbp), %r11");
        out.printCode("cmovg %r10, %rax");
        out.printCode("movq %rax, "+ dstoffset+"(%rbp)");
        break;
      case LE:
        out.printCode("movq $0, %rax");
        out.printCode("movq $1, %r10");
        out.printCode("movq "+lhsoffset+"(%rbp), %r11");
        out.printCode("cmp "+rhsoffset+"(%rbp), %r11");
        out.printCode("cmovle %r10, %rax");
        out.printCode("movq %rax, "+ dstoffset+"(%rbp)");
        break;
      case LT:
        out.printCode("movq $0, %rax");
        out.printCode("movq $1, %r10");
        out.printCode("movq "+lhsoffset+"(%rbp), %r11");
        out.printCode("cmp "+rhsoffset+"(%rbp), %r11");
        out.printCode("cmovl %r10, %rax");
        out.printCode("movq %rax, "+ dstoffset+"(%rbp)");
        break;
      case EQ:
        out.printCode("movq $0, %rax");
        out.printCode("movq $1, %r10");
        out.printCode("movq "+lhsoffset+"(%rbp), %r11");
        out.printCode("cmp "+rhsoffset+"(%rbp), %r11");
        out.printCode("cmove %r10, %rax");
        out.printCode("movq %rax, "+ dstoffset+"(%rbp)");
        break;
      case NE:
        out.printCode("movq $0, %rax");
        out.printCode("movq $1, %r10");
        out.printCode("movq "+lhsoffset+"(%rbp), %r11");
        out.printCode("cmp "+rhsoffset+"(%rbp), %r11");
        out.printCode("cmovne %r10, %rax");
        out.printCode("movq %rax, "+ dstoffset+"(%rbp)");
        break;
    }
  }

  public void visit(CopyInst i) {
    LocalVar dst = i.getDstVar();
    Value src = i.getSrcValue();
    int dstslot = getStackSlot(dst);
    int dstoffset = -dstslot * 8;
    if (src instanceof BooleanConstant){
      BooleanConstant bc = (BooleanConstant) src;
      if (bc.getValue()){
        out.printCode("movq $1, "+dstoffset+"(%rbp)");
      }else{
        out.printCode("movq $0, "+dstoffset+"(%rbp)");
      }
    }else if (src instanceof IntegerConstant){
      IntegerConstant ic = (IntegerConstant) src;
      out.printCode("movq $"+ic.getValue()+", "+dstoffset+"(%rbp)");
    }else{
      LocalVar srcv = (LocalVar) src;
      int srcslot = getStackSlot(srcv);
      int srcoffset = -srcslot * 8;
      out.printCode("movq "+srcoffset+"(%rbp), %r10");
      out.printCode("movq %r10, "+dstoffset+"(%rbp)");
    }
  }

  public void visit(JumpInst i) {
    LocalVar pred = i.getPredicate();
    int predslot = getStackSlot(pred);
    int predoffset = -predslot * 8;
    out.printCode("movq "+predoffset+"(%rbp), %r10");
    out.printCode("cmp $1, %r10");
    out.printCode("je "+flmap.get(i.getNext(1)));
  }

  public void visit(LoadInst i) {
    LocalVar dst = i.getDst();
    AddressVar src = i.getSrcAddress();
    int dstslot = getStackSlot(dst);
    int dstoffset = -dstslot * 8;
    int srcslot = getStackSlot(src);
    int srcoffset = -srcslot * 8;
    out.printCode("movq "+srcoffset+"(%rbp), %r10"); //src: Address -> r10
    out.printCode("movq 0(%r10), %r11");
    out.printCode("movq %r11, "+dstoffset+"(%rbp)"); // Address -> var
  }

  public void visit(NopInst i) {
    out.printCode("// NOP");
  }

  public void visit(StoreInst i) {
    AddressVar dst = i.getDestAddress();
    LocalVar src = i.getSrcValue();
    int dstslot = getStackSlot(dst);
    int dstoffset = -dstslot * 8;
    int srcslot = getStackSlot(src);
    int srcoffset = -srcslot * 8;
    out.printCode("movq "+srcoffset+"(%rbp), %r10");// src: var -> r10
    out.printCode("movq "+dstoffset+"(%rbp), %r11");// dst: Address -> dst TODO is it correct?
    out.printCode("movq %r10, 0(%r11)");// var -> Address
  }

  public void visit(ReturnInst i) {
    if (i.getReturnValue() != null){
      LocalVar v = i.getReturnValue();
      int vslot = getStackSlot(v);
      int voffset = -vslot * 8;
      out.printCode("movq "+voffset+"(%rbp), %rax");
    }
    out.printCode("leave");
    out.printCode("ret");
  }

  public void visit(CallInst i) {
    String calleeName = i.getCallee().getName();
    int counter =1;

    for (LocalVar para: i.getParams()){
      int paraslot = getStackSlot(para);
      int paraoffset = -paraslot * 8;
      if (counter==1){
        out.printCode("movq " +paraoffset+"(%rbp), %rdi");
      }else if (counter==2){
        out.printCode("movq " +paraoffset+"(%rbp), %rsi");
      }else if (counter==3){
        out.printCode("movq " +paraoffset+"(%rbp), %rdx");
      }else if (counter==4){
        out.printCode("movq " +paraoffset+"(%rbp), %rcx");
      }else if (counter==5){
        out.printCode("movq " +paraoffset+"(%rbp), %r8");
      }else if (counter==6){
        out.printCode("movq " +paraoffset+"(%rbp), %r9");
      }else{

      }
      counter++;
    }


    if (counter > 7){
      if (counter%2 == 0){ //if there are odd number of arguments
        out.printCode("subq $8, %rsp");
      }
      for (int a = counter-7; a>0; a--){
        int paraslot = getStackSlot(i.getParams().get(a+5));
        int paraoffset = -paraslot * 8;
        out.printCode("movq "+paraoffset+"(%rbp), %r10");
        out.printCode("pushq %r10");
      }
    }

    out.printCode("call " + calleeName);
    if (i.getDst()!=null){
      LocalVar dst = i.getDst();
      int dstslot = getStackSlot(dst);
      int dstoffset = -dstslot * 8;
      out.printCode("movq %rax, " +dstoffset+"(%rbp)");

    }

    if (counter > 7){
      if (counter%2 == 1){ //if there are even number of arguments
        out.printCode("addq $"+8*(counter -7)+", %rsp");
      }else{
        out.printCode("addq $"+8*(counter -6)+", %rsp");
      }
    }
  }

  public void visit(UnaryNotInst i) {
    Variable dst = i.getDst();
    Variable lhs = i.getInner();
    int dstslot = getStackSlot(dst);
    int lhsslot = getStackSlot(lhs);
    int dstoffset = -dstslot * 8;
    int lhsoffset = -lhsslot * 8;

    out.printCode("movq $1, %r10");
    out.printCode("subq "+lhsoffset+"(%rbp), %r10");
    out.printCode("movq %r10, "+ dstoffset+"(%rbp)");


  }
}
