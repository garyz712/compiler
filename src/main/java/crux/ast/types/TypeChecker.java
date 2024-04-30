package crux.ast.types;

import crux.ast.SymbolTable.Symbol;
import crux.ast.*;
import crux.ast.traversal.NullNodeVisitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class will associate types with the AST nodes from Stage 2
 */
public final class TypeChecker {
  boolean lastStatementReturns = false;
  boolean hasBreak = false;
  Symbol currentFunctionSymbol;

  Type currentFunctionReturnType;



  private final ArrayList<String> errors = new ArrayList<>();

  public ArrayList<String> getErrors() {
    return errors;
  }

  public void check(DeclarationList ast) {
    var inferenceVisitor = new TypeInferenceVisitor();
    inferenceVisitor.visit(ast);
  }

  /**
   * Helper function, should be used to add error into the errors array
   */
  private void addTypeError(Node n, String message) {
    errors.add(String.format("TypeError%s[%s]", n.getPosition(), message));
  }

  /**
   * Helper function, should be used to record Types if the Type is an ErrorType then it will call
   * addTypeError
   */
  private void setNodeType(Node n, Type ty) {
    ((BaseNode) n).setType(ty);
    if (ty.getClass() == ErrorType.class) {
      var error = (ErrorType) ty;
      addTypeError(n, error.getMessage());
    }
  }

  /**
   * Helper to retrieve Type from the map
   */
  public Type getType(Node n) {
    return ((BaseNode) n).getType();
  }


  /**
   * This calls will visit each AST node and try to resolve it's type with the help of the
   * symbolTable.
   */
  private final class TypeInferenceVisitor extends NullNodeVisitor<Void> {

    @Override
    public Void visit(VarAccess vaccess) {
      Type nt = vaccess.getSymbol().getType();
      if (nt.getClass() == IntType.class || nt.getClass() == BoolType.class){
        setNodeType(vaccess, nt);
      }else{
        throw new AssertionError("That varaccess symbol is not a valid type");
      }
      return null;
    }

    @Override
    public Void visit(ArrayDeclaration arrayDeclaration) {
      ArrayType at = (ArrayType) arrayDeclaration.getSymbol().getType();
      if (at.getBase().getClass() == IntType.class || at.getBase().getClass() == BoolType.class){
        lastStatementReturns = false;
      }else{
        addTypeError(arrayDeclaration, "That base of array is not a valid type");
      }
      return null;
    }

    @Override
    public Void visit(Assignment assignment) {
      assignment.getValue().accept(this);
      assignment.getLocation().accept(this);
      Type ty = getType(assignment.getLocation()).assign(getType(assignment.getValue()));
      setNodeType(assignment, ty);
      lastStatementReturns = false;
      return null;
    }

    @Override
    public Void visit(Break brk) {
      hasBreak = true;
      lastStatementReturns = false;
      return null;
    }

    @Override
    public Void visit(Call call) {
      TypeList tl = new TypeList();
      for (Node n: call.getChildren()){
        n.accept(this);
        tl.append(getType(n));
      }
      //System.out.println("call");
      setNodeType(call, call.getCallee().getType().call(tl));
      lastStatementReturns = false;
      return null;
    }

    @Override
    public Void visit(Continue cont) {
      lastStatementReturns = false;
      return null;
    }
    
    @Override
    public Void visit(DeclarationList declarationList) {
      //System.out.println("declaration list");
      for (Node n : declarationList.getChildren()){
        n.accept(this);
      }
      return null;
    }

    @Override
    public Void visit(FunctionDefinition functionDefinition) {
      //System.out.println("func def");
      currentFunctionSymbol = functionDefinition.getSymbol();
      FuncType ft= (FuncType) currentFunctionSymbol.getType(); //TODO is it the return type? check the function
      currentFunctionReturnType = ft.getRet();
      if (currentFunctionSymbol.getName().equals("main")){
        if (currentFunctionReturnType.getClass() != VoidType.class || !functionDefinition.getParameters().isEmpty()){
          addTypeError(functionDefinition, "main function definition error");
        }
        functionDefinition.getStatements().accept(this);
      }else{
        // check parameters
        for (Symbol p: functionDefinition.getParameters()){
          if (p.getType().getClass() != IntType.class && p.getType().getClass() != BoolType.class){
            addTypeError(functionDefinition, "function parameter type error");
          }
        }
        //check return statement exist when return type is not void
        functionDefinition.getStatements().accept(this);
        if (currentFunctionReturnType.getClass() != VoidType.class && !lastStatementReturns){
          addTypeError(functionDefinition, "function return statement missing");
        }

      }
      return null;
    }

    @Override
    public Void visit(IfElseBranch ifElseBranch) {
      ifElseBranch.getCondition().accept(this);
      if (getType(ifElseBranch.getCondition()).getClass() != BoolType.class){
        addTypeError(ifElseBranch, "if condition not bool type");
      }
      ifElseBranch.getThenBlock().accept(this);
      boolean thenRt = lastStatementReturns;
      if (ifElseBranch.getElseBlock() != null){
        ifElseBranch.getElseBlock().accept(this);
        boolean elseRt = lastStatementReturns;
        if (!thenRt && elseRt){
          lastStatementReturns = false;
        }
      }

      return null;
    }

    @Override
    public Void visit(ArrayAccess access) {
      access.getIndex().accept(this);
      setNodeType(access, access.getBase().getType().index(getType(access.getIndex())));
      return null;
    }

    @Override
    public Void visit(LiteralBool literalBool) {
      setNodeType(literalBool, new BoolType());
      return null;
    }

    @Override
    public Void visit(LiteralInt literalInt) {
      setNodeType(literalInt, new IntType());
      return null;
    }

    @Override
    public Void visit(Loop loop) {
      //hasBreak = false;
      var oldBreak = hasBreak;
      loop.getBody().accept(this); //TODO is it how to handle nested loop?
      if (!lastStatementReturns && !hasBreak){
        addTypeError(loop, "infinite loop!");
      }
      hasBreak = oldBreak;
      return null;
    }
// loop while (){
//  loop while (){
//    break
//  }
//  break
// }
    @Override
    public Void visit(OpExpr op) {
      Type ret;
      op.getLeft().accept(this);
      Type lt = getType(op.getLeft());
      //System.out.println("opexpr");
      if (op.getOp().toString().equals("!")){
        ret = lt.not();
        setNodeType(op, ret);
        return null;
      }

      op.getRight().accept(this);
      Type rt = getType(op.getRight());

      switch (op.getOp()){
        case ADD:
          ret = lt.add(rt);
          break;
        case SUB:
          ret = lt.sub(rt);
          break;
        case MULT:
          ret = lt.mul(rt);
          break;
        case DIV:
          ret = lt.div(rt);
          break;
        case LOGIC_AND:
          ret = lt.and(rt);
          break;
        case LOGIC_OR:
          ret = lt.or(rt);
          break;
        case GE: case LE: case NE: case EQ: case GT: case LT:
          ret = lt.compare(rt);
          break;
        default:
          throw new AssertionError("Invalid operation");
      }

      setNodeType(op, ret);

      return null;
    }

    @Override
    public Void visit(Return ret) {
      ret.getValue().accept(this);
      if (getType(ret.getValue()).getClass() != currentFunctionReturnType.getClass()){
        addTypeError(ret, "return type does not match currentFunctionReturnType");
      }
      lastStatementReturns = true;
      return null;
    }

    @Override
    public Void visit(StatementList statementList) {
      //System.out.println("statement list");
      int  i =0;
      lastStatementReturns = false;
      for (Node n : statementList.getChildren()){
        n.accept(this);
        if (lastStatementReturns && i < (statementList.getChildren().size()-1)){
          throw new AssertionError("Unreachable statement found");
        }
        i++;
      }
      return null;
    }

    @Override
    public Void visit(VariableDeclaration variableDeclaration) {
      Type vt = variableDeclaration.getSymbol().getType();
      if (vt.getClass() == IntType.class || vt.getClass() == BoolType.class){
        lastStatementReturns = false;
      }else{
        addTypeError(variableDeclaration, "That base of array is not a valid type");
      }
      return null;
    }
  }
}
