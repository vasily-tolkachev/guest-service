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
    : (blankLines* textLine)+ (blankLines* optionDecl)*
    ;

textLine
    : textLineContent endOfLine
    ;

optionDecl
    : GT WS* textLineContent endOfLine ARROW WS* ID endOfLine
    ;

textLineContent
    : textAtom (WS+ textAtom)*
    ;

textAtom
    : ID
    | STRING
    | WORD
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
STRING: '"' (~["\r\n] | '\\"')* '"';
ID: [a-zA-Z_][a-zA-Z0-9_-]*;
WORD: ~[ \t\r\n]+;
WS: [ \t]+;
NEWLINE: '\r'? '\n';
