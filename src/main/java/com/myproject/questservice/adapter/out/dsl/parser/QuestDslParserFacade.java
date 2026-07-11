package com.myproject.questservice.adapter.out.dsl.parser;

import com.myproject.questservice.adapter.out.dsl.ast.NodeAst;
import com.myproject.questservice.adapter.out.dsl.ast.OptionAst;
import com.myproject.questservice.adapter.out.dsl.ast.QuestAst;
import com.myproject.questservice.adapter.out.dsl.error.DslError;
import com.myproject.questservice.adapter.out.dsl.error.DslProcessingException;
import com.myproject.questservice.dsl.QuestDslBaseVisitor;
import com.myproject.questservice.dsl.QuestDslLexer;
import com.myproject.questservice.dsl.QuestDslParser;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class QuestDslParserFacade {

    public QuestAst parse(String dslText) {
        QuestDslLexer lexer = new QuestDslLexer(CharStreams.fromString(dslText));
        QuestDslParser parser = new QuestDslParser(new CommonTokenStream(lexer));

        BaseErrorListener errorListener = new BaseErrorListener() {
            @Override
            public void syntaxError(
                    Recognizer<?, ?> recognizer,
                    Object offendingSymbol,
                    int line,
                    int charPositionInLine,
                    String msg,
                    RecognitionException e
            ) {
                throw new DslProcessingException(new DslError(
                        "DSL_SYNTAX_ERROR",
                        msg,
                        line,
                        charPositionInLine + 1
                ));
            }
        };

        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        lexer.addErrorListener(errorListener);
        parser.addErrorListener(errorListener);

        QuestDslParser.QuestFileContext questFileContext = parser.questFile();
        return new AstVisitor().visitQuestFile(questFileContext);
    }

    private static class AstVisitor extends QuestDslBaseVisitor<QuestAst> {

        @Override
        public QuestAst visitQuestFile(QuestDslParser.QuestFileContext ctx) {
            String questId = ctx.questDecl().ID().getText();
            String title = stripQuotes(ctx.titleDecl().STRING().getText());
            List<NodeAst> nodes = new ArrayList<>();
            for (QuestDslParser.NodeDeclContext nodeContext : ctx.nodeDecl()) {
                nodes.add(toNodeAst(nodeContext));
            }
            return new QuestAst(questId, title, nodes);
        }

        private NodeAst toNodeAst(QuestDslParser.NodeDeclContext nodeContext) {
            Token nodeIdToken = nodeContext.ID().getSymbol();
            String nodeId = nodeContext.ID().getText();

            StringBuilder textBuilder = new StringBuilder();
            List<QuestDslParser.TextLineContext> textLines = nodeContext.nodeBody().textLine();
            for (int i = 0; i < textLines.size(); i++) {
                if (i > 0) {
                    textBuilder.append("\n");
                }
                textBuilder.append(toLineText(textLines.get(i).textLineContent()));
            }

            List<OptionAst> options = new ArrayList<>();
            for (QuestDslParser.OptionDeclContext optionContext : nodeContext.nodeBody().optionDecl()) {
                Token optionToken = optionContext.GT().getSymbol();
                String optionText = toLineText(optionContext.textLineContent());
                String targetNodeId = optionContext.ID().getText();
                options.add(new OptionAst(
                        optionText,
                        targetNodeId,
                        optionToken.getLine(),
                        optionToken.getCharPositionInLine() + 1
                ));
            }

            return new NodeAst(
                    nodeId,
                    textBuilder.toString(),
                    options,
                    nodeIdToken.getLine(),
                    nodeIdToken.getCharPositionInLine() + 1
            );
        }

        private String toLineText(QuestDslParser.TextLineContentContext context) {
            List<String> atoms = new ArrayList<>();
            for (QuestDslParser.TextAtomContext atom : context.textAtom()) {
                if (atom.STRING() != null) {
                    atoms.add(stripQuotes(atom.STRING().getText()));
                } else {
                    atoms.add(atom.getText());
                }
            }
            return String.join(" ", atoms).trim();
        }

        private String stripQuotes(String value) {
            if (value.length() < 2) {
                return value;
            }
            return value.substring(1, value.length() - 1).replace("\\\"", "\"");
        }
    }
}
