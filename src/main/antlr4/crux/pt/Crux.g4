grammar Crux;
program
 : declList EOF
 ;

declList
 : decl*
 ;

decl
 : varDecl
 | arrayDecl
 | functionDefn
 ;

varDecl
 : type Identifier ';'
 ;

type
 : Identifier
 ;

literal
 : Integer
 | True
 | False
 ;

SemiColon: ';';

Integer
 : '0'
 | [1-9] [0-9]*
 ;

True: 'true';
False: 'false';

Identifier
 : [a-zA-Z] [a-zA-Z0-9_]*
 ;

WhiteSpaces
 : [ \t\r\n]+ -> skip
 ;

Comment
 : '//' ~[\r\n]* -> skip
 ;

designator
 : Identifier ('[' expr0 ']')?
 ;

op0
 : '>=' | '<=' | '!=' | '==' | '>' | '<'
 ;

op1
 : '+' | '-' | '||'
 ;

op2
 : '*' | '/' | '&&'
 ;

expr0
 : expr1 (op0 expr1)?
 ;

expr1
 : expr2 | expr1 op1 expr2
 ;

expr2
 : expr3 | expr2 op2 expr3
 ;

expr3
 : '!' expr3
 | '(' expr0 ')'
 | designator
 | callExpr
 | literal
 ;

callExpr
 : Identifier '(' exprList ')'
 ;

exprList
 : ( expr0 (',' expr0)* )?
 ;

param
 : type Identifier
 ;

paramList
 : ( param (',' param)* )?
 ;

arrayDecl
 : type Identifier '[' Integer ']' ';'
 ;

functionDefn
 : type Identifier '(' paramList ')' stmtBlock
 ;

assignStmt
 : designator '=' expr0 ';'
 ;

callStmt
 : callExpr ';'
 ;

ifStmt
 : 'if' expr0 stmtBlock ('else' stmtBlock)?
 ;

loopStmt
 : 'loop' stmtBlock
 ;

breakStmt
  : 'break' ';'
  ;

continueStmt
 : 'continue' ';'
 ;

returnStmt
 : 'return' expr0 ';'
 ;

stmt
 : varDecl
 | callStmt
 | assignStmt
 | ifStmt
 | loopStmt
 | breakStmt
 | continueStmt
 | returnStmt
 ;

stmtList
 : stmt*
 ;

stmtBlock
 : '{' stmtList '}'
 ;