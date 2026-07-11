grammar QuestDsl;

questFile
    : blankLines* questDecl blankLines* titleDecl (blankLines* nodeDecl)+ blankLines* EOF
    ;

questDecl
    : QUEST WS+ ID endOfLine
    ;

titleDecl
    : TITLE WS+ STRING endOfLine
    ;

nodeDecl
    : NODE WS+ ID endOfLine nodeBody
    ;

nodeBody
    : (blankLines* nodeTitleDecl)? (blankLines* textLine)+ (blankLines* optionDecl)*
    ;

textLine
    : textLineContent endOfLine
    ;

nodeTitleDecl
    : TITLE WS+ STRING endOfLine
    ;

optionDecl
    : GT WS* textLineContent endOfLine optionDirective* ARROW WS* ID endOfLine
    ;

optionDirective
    : IF_DIRECTIVE WS+ functionCall endOfLine
    | EFFECT_DIRECTIVE WS+ functionCall endOfLine
    ;

functionCall
    : ID WS* LPAREN WS* functionArgs? WS* RPAREN
    ;

functionArgs
    : functionArg (WS* COMMA WS* functionArg)*
    ;

functionArg
    : ID
    | STRING
    | NUMBER
    ;

textLineContent
    : textAtom (WS* textAtom)*
    ;

textAtom
    : ID
    | STRING
    | WORD
    | NUMBER
    | COMMA
    | LPAREN
    | RPAREN
    ;

endOfLine
    : WS* (NEWLINE+ | EOF)
    ;

blankLines
    : WS* NEWLINE+
    ;

QUEST: 'quest';
TITLE: 'title';
NODE: 'node';
GT: '>';
ARROW: '->';
IF_DIRECTIVE: '@if';
EFFECT_DIRECTIVE: '@effect';
LPAREN: '(';
RPAREN: ')';
COMMA: ',';
STRING: '"' (~["\r\n] | '\\"')* '"';
ID: [a-zA-Z_][a-zA-Z0-9_-]*;
NUMBER: '-'? [0-9]+;
WORD: ~[ \t\r\n(),]+;
WS: [ \t]+;
NEWLINE: '\r'? '\n';
