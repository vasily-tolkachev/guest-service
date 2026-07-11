grammar QuestDsl;

questFile
    : questDecl titleDecl nodeDecl+ EOF
    ;

questDecl
    : QUEST ID NEWLINE+
    ;

titleDecl
    : TITLE STRING NEWLINE+
    ;

nodeDecl
    : NODE ID NEWLINE+ nodeBody
    ;

nodeBody
    : textLine+ optionDecl*
    ;

textLine
    : TEXT_LINE NEWLINE*
    ;

optionDecl
    : GT TEXT_LINE NEWLINE* ARROW ID NEWLINE*
    ;

QUEST: 'quest';
TITLE: 'title';
NODE: 'node';
GT: '>';
ARROW: '->';
STRING: '"' (~["\r\n] | '\\"')* '"';
ID: [a-zA-Z_][a-zA-Z0-9_-]*;
TEXT_LINE: ~[\r\n]+;
NEWLINE: '\r'? '\n';
WS: [ \t]+ -> skip;
