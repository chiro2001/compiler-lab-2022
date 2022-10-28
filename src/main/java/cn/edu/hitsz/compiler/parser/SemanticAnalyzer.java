package cn.edu.hitsz.compiler.parser;

import cn.edu.hitsz.compiler.NotImplementedException;
import cn.edu.hitsz.compiler.RunConfigs;
import cn.edu.hitsz.compiler.error.ErrorDescription;
import cn.edu.hitsz.compiler.ir.Instruction;
import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.parser.table.Production;
import cn.edu.hitsz.compiler.parser.table.Status;
import cn.edu.hitsz.compiler.symtab.SourceCodeType;
import cn.edu.hitsz.compiler.symtab.SymbolTable;

import java.util.Stack;

// TODO: 实验三: 实现语义分析
public class SemanticAnalyzer implements ActionObserver {
    private SymbolTable symbolTable = null;
    private Stack<SourceCodeType> semanticTypeStack = new Stack<>();
    private Stack<Token> shiftStack = new Stack<>();

    @Override
    public void whenAccept(Status currentStatus) {
        // TODO: 该过程在遇到 Accept 时要采取的代码动作
        // throw new NotImplementedException();
        semanticTypeStack.clear();
        shiftStack.clear();
    }

    @Override
    public void whenReduce(Status currentStatus, Production production) {
        // TODO: 该过程在遇到 reduce production 时要采取的代码动作
        // throw new NotImplementedException();
        // noinspection AlibabaSwitchStatement
        switch (production.index()) {
            // S -> D id
            case 4 -> {
                var id = shiftStack.pop();
                if (symbolTable.has(id.getText())) {
                    var p = symbolTable.get(id.getText());
                    p.setType(semanticTypeStack.pop());
                    semanticTypeStack.add(SourceCodeType.None);
                    if (RunConfigs.DEBUG) {
                        System.out.printf("Set %s as type %s\n", p.getText(), p.getType());
                    }
                } else {
                    throw new RuntimeException(String.format(ErrorDescription.NO_SYMBOL, id.getText()));
                }
            }
            // D -> int
            case 5 -> {
                semanticTypeStack.add(SourceCodeType.Int);
            }
            default -> {
                semanticTypeStack.add(SourceCodeType.None);
            }
        }
    }

    @Override
    public void whenShift(Status currentStatus, Token currentToken) {
        // TODO: 该过程在遇到 shift 时要采取的代码动作
        // throw new NotImplementedException();
        shiftStack.add(currentToken);
    }

    @Override
    public void setSymbolTable(SymbolTable table) {
        // TODO: 设计你可能需要的符号表存储结构
        // 如果需要使用符号表的话, 可以将它或者它的一部分信息存起来, 比如使用一个成员变量存储
        // throw new NotImplementedException();
        symbolTable = table;
    }
}

