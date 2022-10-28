package cn.edu.hitsz.compiler.parser;

import cn.edu.hitsz.compiler.NotImplementedException;
import cn.edu.hitsz.compiler.RunConfigs;
import cn.edu.hitsz.compiler.error.ErrorDescription;
import cn.edu.hitsz.compiler.ir.IRImmediate;
import cn.edu.hitsz.compiler.ir.IRValue;
import cn.edu.hitsz.compiler.ir.IRVariable;
import cn.edu.hitsz.compiler.ir.Instruction;
import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.parser.table.Production;
import cn.edu.hitsz.compiler.parser.table.Status;
import cn.edu.hitsz.compiler.parser.table.Term;
import cn.edu.hitsz.compiler.symtab.SymbolTable;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Stack;
import java.util.stream.Collectors;

// 实验三: 实现 IR 生成

/**
 *
 */
public class IRGenerator implements ActionObserver {
    public static class TokenWithInfo {
        private final Token token;
        private IRValue addr = null;

        public TokenWithInfo(Token token) {
            this.token = token;
        }

        public IRValue getAddr() {
            return addr;
        }

        public TokenWithInfo setAddr(IRValue addr) {
            this.addr = addr;
            return this;
        }

        public Token getToken() {
            return token;
        }

        @Override
        public String toString() {
            return "[%s, %s]".formatted(this.token, this.addr);
        }
    }

    class TokenShiftStack extends Stack<TokenWithInfo> {
        @Override
        public synchronized String toString() {
            return this.stream().filter(i -> !Objects.equals(i.getToken().getKindId(), "Semicolon")).map(TokenWithInfo::toString).collect(Collectors.joining(", "));
        }

        @Override
        public synchronized TokenWithInfo pop() {
            var r = super.pop();
            // System.out.printf("pop:\t%s\n", this);
            return r;
        }

        @Override
        public TokenWithInfo push(TokenWithInfo item) {
            var r = super.push(item);
            // System.out.printf("push:\t%s\n", this);
            return r;
        }
    }

    private SymbolTable symbolTable = null;
    private TokenShiftStack shiftStack = new TokenShiftStack();
    private List<Instruction> code = new LinkedList<>();

    @Override
    public void whenShift(Status currentStatus, Token currentToken) {
        shiftStack.push(new TokenWithInfo(currentToken));
    }

    @Override
    public void whenReduce(Status currentStatus, Production production) {
        // noinspection AlibabaSwitchStatement
        switch (production.index()) {
            // 简单赋值语句中间代码生成 && 算数表达式中间代码生成
            // S -> id = E;
            case 6 -> {
                var e = shiftStack.pop();
                var eq = shiftStack.pop();
                assert (Objects.equals(eq.getToken().getKind().getTermName(), "="));
                var id = shiftStack.pop();
                if (!symbolTable.has(id.getToken().getText())) {
                    throw new RuntimeException(ErrorDescription.NO_SYMBOL.formatted(id.getToken().getText()));
                }
                if (id.getAddr() == null) {
                    id.setAddr(IRVariable.named(id.getToken().getText()));
                }
                assert (id.getAddr().isIRVariable());
                if (RunConfigs.DEBUG) {
                    System.out.printf("S -> id = E | S -> %s = %s\n", id.getAddr(), e.getAddr());
                }
                var insn = Instruction.createMov((IRVariable) id.getAddr(), e.getAddr());
                code.add(insn);
            }
            // E -> E + A;
            case 8 -> {
                var a = shiftStack.pop();
                var plus = shiftStack.pop();
                assert (Objects.equals(plus.getToken().getKind().getTermName(), "+"));
                var e = shiftStack.pop();
                var result = IRVariable.temp();
                var insn = Instruction.createAdd(result, a.getAddr(), e.getAddr());
                code.add(insn);
                shiftStack.push(e.setAddr(result));
            }
            // E -> E - A;
            case 9 -> {
                var a = shiftStack.pop();
                var minus = shiftStack.pop();
                assert (Objects.equals(minus.getToken().getKind().getTermName(), "-"));
                var e = shiftStack.pop();
                var result = IRVariable.temp();
                var insn = Instruction.createSub(result, e.getAddr(), a.getAddr());
                code.add(insn);
                shiftStack.push(e.setAddr(result));
            }
            // A -> A * B;
            case 11 -> {
                var b = shiftStack.pop();
                var mul = shiftStack.pop();
                assert (Objects.equals(mul.getToken().getKind().getTermName(), "*"));
                var a = shiftStack.pop();
                var result = IRVariable.temp();
                if (b.addr.isImmediate()) {
                    // there is no muli, so create template variable to hold imm value
                    var temp = IRVariable.temp();
                    code.add(Instruction.createMov(temp, b.getAddr()));
                    var insn = Instruction.createMul(result, a.getAddr(), temp);
                    code.add(insn);
                } else {
                    var insn = Instruction.createMul(result, a.getAddr(), b.getAddr());
                    code.add(insn);
                }
                shiftStack.push(a.setAddr(result));
            }
            // A -> B; | E -> A;
            case 12, 10 -> {
                var b = shiftStack.pop();
                shiftStack.push(new TokenWithInfo(b.getToken()).setAddr(b.getAddr()));
            }
            // B -> ( E );
            case 13 -> {
                var right = shiftStack.pop();
                assert (Objects.equals(right.getToken().getKind().getTermName(), ")"));
                var e = shiftStack.pop();
                var left = shiftStack.pop();
                assert (Objects.equals(left.getToken().getKind().getTermName(), "("));
                shiftStack.push(e.setAddr(e.getAddr()));
            }
            // B -> id;
            case 14 -> {
                var id = shiftStack.pop();
                if (!symbolTable.has(id.getToken().getText())) {
                    throw new RuntimeException(ErrorDescription.NO_SYMBOL.formatted(id.getToken().getText()));
                }
                var variable = IRVariable.named(symbolTable.get(id.getToken().getText()).getText());
                shiftStack.push(new TokenWithInfo(id.getToken()).setAddr(variable));
            }
            // B -> IntConst;
            case 15 -> {
                var intConst = shiftStack.pop();
                var value = IRImmediate.of(Integer.parseInt(intConst.getToken().getText()));
                shiftStack.push(new TokenWithInfo(intConst.getToken()).setAddr(value));
            }
            // 返回
            // S -> return E;
            case 7 -> {
                var e = shiftStack.pop();
                var insn = Instruction.createRet(e.getAddr());
                code.add(insn);
            }
            default -> {
                shiftStack.pop();
            }
        }
    }


    @Override
    public void whenAccept(Status currentStatus) {
        shiftStack.clear();
    }

    @Override
    public void setSymbolTable(SymbolTable table) {
        symbolTable = table;
    }

    public List<Instruction> getIR() {
        return code;
    }

    public void dumpIR(String path) {
        FileUtils.writeLines(path, getIR().stream().map(Instruction::toString).toList());
    }
}

