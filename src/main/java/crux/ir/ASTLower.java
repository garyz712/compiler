package crux.ir;

import crux.ast.SymbolTable.Symbol;
import crux.ast.*;
import crux.ast.OpExpr.Operation;
import crux.ast.traversal.NodeVisitor;
import crux.ast.types.*;
import crux.ir.insts.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

class InstPair {
  Instruction start;
  Instruction end;
  Variable val;

  InstPair(Instruction start, Instruction end, Variable val) {
    this.start= start;
    this.end=end;
    this.val = val;
  }

  Instruction getStart() {
    return start;
  }

  Instruction getEnd() {
    return end;
  }

  Variable getVal() {
    return val;
  }

}


/**
 * Convert AST to IR and build the CFG
 */
public final class ASTLower implements NodeVisitor<InstPair> {
  private Program mCurrentProgram = null;
  private Function mCurrentFunction = null;

  private Map<Symbol, LocalVar> mCurrentLocalVarMap = null;

  private NopInst loopExit = null; //TODO is this correct?

  private NopInst loopHead = null;

  /**
   * A constructor to initialize member variables
   */
  public ASTLower() {}

  public Program lower(DeclarationList ast) {
    visit(ast);
    return mCurrentProgram;
  }

  @Override
  public InstPair visit(DeclarationList declarationList) {

    mCurrentProgram = new Program();
    for (Node n : declarationList.getChildren()){
      Declaration d = (Declaration) n;
      d.accept(this);
    }
    return null;

  }

  /**
   * This visitor should create a Function instance for the functionDefinition node, add parameters
   * to the localVarMap, add the function to the program, and init the function start Instruction.
   */
  @Override
  public InstPair visit(FunctionDefinition functionDefinition) {

    mCurrentFunction = new Function(functionDefinition.getSymbol().getName(), (FuncType) functionDefinition.getType());
    mCurrentLocalVarMap = new HashMap<Symbol, LocalVar>();
    ArrayList<LocalVar> params = new ArrayList<LocalVar>();
    // for each argument:
    // 	create LocalVar using mCurrentFunction.getTempVar() and put them in a list
    // 	put the variable (↑) to mCurrentLocalVarMap with correct symbol
    // set arguments for mCurrentFunction
    // add mCurrentFunction to the function list in mCurrentProgram
    for (Symbol arg : functionDefinition.getParameters()){
      LocalVar argV = mCurrentFunction.getTempVar(arg.getType());
      params.add(argV);
      mCurrentLocalVarMap.put(arg, argV);
    }
    mCurrentFunction.setArguments(params);
    mCurrentProgram.addFunction(mCurrentFunction);
    // visit function body
    // set the start node of mCurrentFunction
    // dump mCurrentFunction and mCurrentLocalVarMap
    // return null
    InstPair body = functionDefinition.getStatements().accept(this);
    mCurrentFunction.setStart(body.getStart());
    mCurrentLocalVarMap = null;
    mCurrentFunction = null;
    return null;


  }

  @Override
  public InstPair visit(StatementList statementList) {
    List<Node> children = statementList.getChildren();
    Instruction firstInst = null;
    Instruction lastInst = null;

    for(Node child: children) {
      InstPair statement = child.accept(this);

      if (firstInst == null) {
        firstInst = new NopInst();
        firstInst.setNext(0, statement.getStart());
      } else {
        lastInst.setNext(0, statement.getStart());
      }

      if (statement.getEnd() == null){ // if statement is break or continue, return firstInst & break exit
        return new InstPair(firstInst, statement.getStart(), null);
      }

      lastInst = statement.getEnd();
    }
    return new InstPair(firstInst, lastInst, null);

  }

  /**
   * Declarations, could be either local or Global
   */
  @Override
  public InstPair visit(VariableDeclaration variableDeclaration) {
    if (mCurrentFunction == null){
      GlobalDecl gd = new GlobalDecl(variableDeclaration.getSymbol(), IntegerConstant.get(mCurrentProgram, 1));
      mCurrentProgram.addGlobalVar(gd);
    }else{
      LocalVar v = mCurrentFunction.getTempVar(variableDeclaration.getType());
      mCurrentLocalVarMap.put(variableDeclaration.getSymbol(), v);
    }
    NopInst nop = new NopInst();
    return new InstPair(nop, nop, null);
  }

  /**
   * Create a declaration for array and connected it to the CFG
   */
  @Override
  public InstPair visit(ArrayDeclaration arrayDeclaration) {
    ArrayType at = (ArrayType) arrayDeclaration.getSymbol().getType();
    GlobalDecl gd = new GlobalDecl(arrayDeclaration.getSymbol(), IntegerConstant.get(mCurrentProgram, at.getExtent()));
    mCurrentProgram.addGlobalVar(gd);
    NopInst nop = new NopInst();
    //TODO return null; ?
    return new InstPair(nop, nop, null);
  }

  /**
   * LookUp the name in the map(s). For globals, we should do a load to get the value to load into a
   * LocalVar.
   */
  @Override
  public InstPair visit(VarAccess name) {
    Symbol symbol = name.getSymbol();
    if (mCurrentLocalVarMap.containsKey(symbol)){
      LocalVar v = mCurrentLocalVarMap.get(symbol);
      NopInst nop = new NopInst();
      return new InstPair(nop, nop, v);
    }else{
      AddressVar av = mCurrentFunction.getTempAddressVar(name.getType());
      Instruction i = new AddressAt(av, symbol);
      LocalVar v = mCurrentFunction.getTempVar(name.getType());
      LoadInst load = new LoadInst(v, av);
      i.setNext(0, load);
      //TODO which one is correct?
      return new InstPair(i, load, v);
    }


  }

  /**
   * If the location is a VarAccess to a LocalVar, copy the value to it. If the location is a
   * VarAccess to a global, store the value. If the location is ArrayAccess, store the value.
   */
  @Override
  public InstPair visit(Assignment assignment) {
    Expression rhs = assignment.getValue();
    InstPair right = rhs.accept(this);

    if (assignment.getLocation() instanceof VarAccess){
      VarAccess lhs = (VarAccess) assignment.getLocation();
      if (mCurrentLocalVarMap.containsKey(lhs.getSymbol())){
        LocalVar v = mCurrentLocalVarMap.get(lhs.getSymbol());
        CopyInst assign = new CopyInst(v, right.getVal());
        right.getEnd().setNext(0, assign);
        return new InstPair(right.getStart(), assign, null);
      }else{
        AddressVar av = mCurrentFunction.getTempAddressVar(lhs.getType());
        Instruction i = new AddressAt(av, lhs.getSymbol());
        i.setNext(0, right.getStart());
        StoreInst assign = new StoreInst((LocalVar) right.getVal(), av);
        right.getEnd().setNext(0, assign);

        return new InstPair(i, assign, null);
      }
    }else{
      ArrayAccess lhsArray = (ArrayAccess) assignment.getLocation();
      InstPair index = lhsArray.getIndex().accept(this);
      AddressVar av = mCurrentFunction.getTempAddressVar(lhsArray.getBase().getType());
      Instruction i = new AddressAt(av, lhsArray.getBase(), (LocalVar) index.getVal());
      index.getEnd().setNext(0, i);
      i.setNext(0, right.getStart());
      StoreInst assign = new StoreInst((LocalVar) right.getVal(), av);
      right.getEnd().setNext(0, assign);

      return new InstPair(index.getStart(), assign, null);
    }

  }

  /**
   * Lower a Call.
   */
  @Override
  public InstPair visit(Call call) {
    ArrayList<LocalVar> params = new ArrayList<LocalVar>();
    // If the argument list is empty
    if (call.getChildren().isEmpty()){
      FuncType ft = (FuncType) call.getCallee().getType();
      if (ft.getRet().getClass() != VoidType.class){
        LocalVar v = mCurrentFunction.getTempVar(ft.getRet());
        CallInst ci = new CallInst(v, call.getCallee(), params);
        return new InstPair(ci, ci, v);
      }else{
        CallInst ci = new CallInst(call.getCallee(), params);
        return new InstPair(ci, ci, null);
      }
    }else{ // if there are arguments
      Instruction first = null;
      Instruction last = null;
      Instruction load = null;
      for (Node arg : call.getChildren()){
        InstPair exp = arg.accept(this);
        if (first == null){
          first = exp.getStart();
        }else{
          last.setNext(0, exp.getStart());
        }
        last = exp.getEnd();

        var param = exp.getVal();
        if (param instanceof LocalVar){
          params.add((LocalVar) param);
        }else{ // TODO if call with an arrayAcess?
          AddressVar paramAV = (AddressVar) exp.getVal();
          ArrayType paramA = (ArrayType) paramAV.getType();
          System.out.println("arg is " + paramAV.getName() +" its type is " + paramAV.getType().toString());
          LocalVar v = mCurrentFunction.getTempVar(paramA.getBase());
          load = new LoadInst(v, paramAV);
          System.out.println("v is "+v.getName());
          params.add(v);
          last.setNext(0, load);
        }
      }
      // arguments added, add the call statement
      FuncType ft = (FuncType) call.getCallee().getType();
      if (ft.getRet().getClass() != VoidType.class){
        LocalVar v = mCurrentFunction.getTempVar(ft.getRet());

        CallInst ci = new CallInst(v, call.getCallee(), params);
        if (load == null){
          last.setNext(0, ci);
        }else{ //TODO is this correct?
          load.setNext(0, ci);
        }
        return new InstPair(first, ci, v);
      }else{
        CallInst ci = new CallInst(call.getCallee(), params);
        if (load == null){
          last.setNext(0, ci);
        }else{ //TODO is this correct?
          load.setNext(0, ci);
        }
        //last.setNext(0, ci);
        return new InstPair(first, ci, null);
      }
    }



  }

  public BinaryOperator.Op toOp(Operation op){
    if (op == Operation.ADD){
      return BinaryOperator.Op.Add;
    }else if (op == Operation.SUB){
      return BinaryOperator.Op.Sub;
    }else if (op == Operation.MULT){
      return BinaryOperator.Op.Mul;
    }else if (op == Operation.DIV){
      return BinaryOperator.Op.Div;
    }else{
      throw new AssertionError("invalid op type");
    }
  }

  public CompareInst.Predicate toPredicate(Operation op){
    if (op == Operation.GT){
      return CompareInst.Predicate.GT;
    }else if (op == Operation.LT){
      return CompareInst.Predicate.LT;
    }else if (op == Operation.GE){
      return CompareInst.Predicate.GE;
    }else if (op == Operation.LE){
      return CompareInst.Predicate.LE;
    }else if (op == Operation.EQ){
      return CompareInst.Predicate.EQ;
    }else if (op == Operation.NE){
      return CompareInst.Predicate.NE;
    }else{
      throw new AssertionError("invalid op type");
    }
  }

  /**
   * Handle operations like arithmetics and comparisons. Also handle logical operations (and,
   * or, not).
   */
  @Override
  public InstPair visit(OpExpr operation) {
    List<Node> children = operation.getChildren();
    InstPair left = children.get(0).accept(this);
    Operation op = operation.getOp();
    LocalVar v = mCurrentFunction.getTempVar(operation.getType());
    if (op == Operation.ADD || op == Operation.SUB || op == Operation.MULT || op == Operation.DIV){
      InstPair right = children.get(1).accept(this);
      left.getEnd().setNext(0, right.getStart());
      BinaryOperator i = new BinaryOperator(toOp(op), v, (LocalVar) left.getVal(), (LocalVar) right.getVal());
      right.getEnd().setNext(0, i);
      return new InstPair(left.getStart(), i, v);
    }else if (op == Operation.GT || op == Operation.LT || op == Operation.GE || op == Operation.LE || op == Operation.EQ || op == Operation.NE){
      InstPair right = children.get(1).accept(this);
      left.getEnd().setNext(0, right.getStart());
      CompareInst i = new CompareInst(v, toPredicate(op), (LocalVar) left.getVal(), (LocalVar) right.getVal());
      right.getEnd().setNext(0, i);
      return new InstPair(left.getStart(), i, v);
    }else if (op == Operation.LOGIC_OR){
      InstPair right = children.get(1).accept(this);
      left.getEnd().setNext(0, right.getStart());
      JumpInst ji = new JumpInst((LocalVar) left.getVal()); //TODO is this correct?
      left.getEnd().setNext(0, ji);
      ji.setNext(0, right.getStart());
      LocalVar v3 = mCurrentFunction.getTempVar(new BoolType());
      CopyInst ci0 = new CopyInst(v3, right.getVal());
      right.getEnd().setNext(0, ci0);
      NopInst nop = new NopInst();
      ci0.setNext(0, nop);
      //true
      CopyInst ci1 = new CopyInst(v3, BooleanConstant.get(mCurrentProgram, true));
      ji.setNext(1, ci1);
      ci1.setNext(0, nop);
      return new InstPair(left.getStart(), nop, v3);
    }else if (op == Operation.LOGIC_AND) {
      InstPair right = children.get(1).accept(this);
      left.getEnd().setNext(0, right.getStart());
      JumpInst ji = new JumpInst((LocalVar) left.getVal());
      left.getEnd().setNext(0, ji);
      ji.setNext(1, right.getStart());
      LocalVar v3 = mCurrentFunction.getTempVar(new BoolType());
      CopyInst ci0 = new CopyInst(v3, right.getVal());
      right.getEnd().setNext(0, ci0);
      NopInst nop = new NopInst();
      ci0.setNext(0, nop);
      //false
      CopyInst ci1 = new CopyInst(v3, BooleanConstant.get(mCurrentProgram, false));
      ji.setNext(0, ci1);
      ci1.setNext(0, nop);
      return new InstPair(left.getStart(), nop, v3);
    }else{
      UnaryNotInst i = new UnaryNotInst(v, (LocalVar) left.getVal());
      left.getEnd().setNext(0, i);
      return new InstPair(left.getStart(), i, v);
    }

  }

  private InstPair visit(Expression expression) {
    return null;
  } //Deleted

  /**
   * It should compute the address into the array, do the load, and return the value in a LocalVar.
   */
  @Override
  public InstPair visit(ArrayAccess access) {
    InstPair index = access.getIndex().accept(this);
    ArrayType at = (ArrayType) access.getBase().getType();
    AddressVar av = mCurrentFunction.getTempAddressVar(at.getBase());
    LocalVar v = mCurrentFunction.getTempVar(av.getType());
    Instruction i = new AddressAt(av, access.getBase(), (LocalVar) index.getVal());
    index.getEnd().setNext(0, i);
    //System.out.println("visiting ArrayAccess" + av.getName());
    LoadInst li = new LoadInst(v, av);
    i.setNext(0 ,li);
    return new InstPair(index.getStart(), li, v);
  }

  /**
   * Copy the literal into a tempVar
   */
  @Override
  public InstPair visit(LiteralBool literalBool) {
    BooleanConstant c = BooleanConstant.get(mCurrentProgram, literalBool.getValue());
    LocalVar v = mCurrentFunction.getTempVar(literalBool.getType());
    CopyInst i = new CopyInst(v,c);
    return new InstPair(i, i, v);

  }

  /**
   * Copy the literal into a tempVar
   */
  @Override
  public InstPair visit(LiteralInt literalInt) {
    IntegerConstant c = IntegerConstant.get(mCurrentProgram, literalInt.getValue());
    LocalVar v = mCurrentFunction.getTempVar(literalInt.getType());
    CopyInst i = new CopyInst(v, c);
    return new InstPair(i, i, v);

  }

  /**
   * Lower a Return.
   */
  @Override
  public InstPair visit(Return ret) {
    InstPair exp = ret.getValue().accept(this);

    ReturnInst ri = new ReturnInst((LocalVar) exp.getVal());
    exp.getEnd().setNext(0, ri);
    return new InstPair(exp.getStart(), ri, null);
  }

  /**
   * Break Node
   */
  @Override
  public InstPair visit(Break brk) {
    NopInst nop = new NopInst();
    return new InstPair(loopExit, nop, null);
  }

  /**
   * Continue Node
   */
  @Override
  public InstPair visit(Continue cont) {
    NopInst nop = new NopInst();
    return new InstPair(loopHead, nop, null);
  }

  /**
   * Implement If Then Else statements.
   */
  @Override
  public InstPair visit(IfElseBranch ifElseBranch) {

    InstPair condition = ifElseBranch.getCondition().accept(this);
    InstPair thenBlock = ifElseBranch.getThenBlock().accept(this);

    JumpInst ji = new JumpInst((LocalVar) condition.getVal());
    NopInst nop = new NopInst();

    condition.getEnd().setNext(0, ji);
    ji.setNext(1, thenBlock.getStart());
    thenBlock.getEnd().setNext(0, nop);

    if (! ifElseBranch.getElseBlock().getChildren().isEmpty()){
      InstPair elseBlock = ifElseBranch.getElseBlock().accept(this);
      ji.setNext(0, elseBlock.getStart());
      elseBlock.getEnd().setNext(0, nop);
    }else{
      ji.setNext(0, nop);
    }// TODO is the else implementation correct?


    return new InstPair(condition.getStart(), nop, null);

  }

  /**
   * Implement loops.
   */
  @Override
  public InstPair visit(Loop loop) {
    NopInst head = new NopInst();
    NopInst exit = new NopInst();
    NopInst outerExit = loopExit;
    NopInst outerHead = loopHead;
    loopExit = exit;
    loopHead = head;
    InstPair body = loop.getBody().accept(this);
    if (body.getStart() != null){
      head.setNext(0, body.getStart());
    }
    if (body.getEnd() != exit){
      body.getEnd().setNext(0, head);
    }

    loopExit = outerExit;
    loopHead = outerHead;
// loop while (){
//  loop while (){
//    break
//    exit
//  }
//  break
//  exit
// }
    return new InstPair(head, exit,null);
  }
}
