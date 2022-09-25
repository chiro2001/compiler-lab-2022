package cn.edu.hitsz.compiler.lexer;

import cn.edu.hitsz.compiler.error.ErrorDescription;
import cn.edu.hitsz.compiler.symtab.SymbolTable;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.StreamSupport;

/**
 * TODO: 实验一: 实现词法分析
 * <br>
 * 你可能需要参考的框架代码如下:
 *
 * @author chiro
 * @see Token 词法单元的实现
 * @see TokenKind 词法单元类型的实现
 */
public class LexicalAnalyzer {
    private final SymbolTable symbolTable;
    private FileCharacterIterator iterator = null;
    private final List<Token> tokens = new LinkedList<>();

    public LexicalAnalyzer(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
    }


    /**
     * 从给予的路径中读取并加载文件内容
     *
     * @param path 路径
     */
    public void loadFile(String path) {
        // 词法分析前的缓冲区实现
        // 可自由实现各类缓冲区
        // 或直接采用完整读入方法
        try {
            iterator = FileCharacterIterator.build(path, 2);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 执行词法分析, 准备好用于返回的 token 列表 <br>
     * 需要维护实验一所需的符号表条目, 而得在语法分析中才能确定的符号表条目的成员可以先设置为 null
     */
    public void run() {
        // 自动机实现的词法分析过程
        int state = 0;
        final var accepts = new HashSet<>(Arrays.asList(15, 17, 19, 20, 22, 23, 25, 26, 27, 28, 29, 30, 31, 32));
        final var keyWords = new HashSet<>(Arrays.asList(
                "int",
                "return"
        ));
        StringBuilder idCode = new StringBuilder();
        StringBuilder number = new StringBuilder();
        while (iterator.hasNext()) {
            while (!accepts.contains(state) && iterator.hasNext()) {
                final var c = iterator.current();
                 System.out.printf("[%2d] read: %s, buffer: %s\n", state, c == '\n' ? "\\n" : c, iterator.getBuffer().stream().map(a -> a == '\n' ? ' ' : a).map(Object::toString).reduce((a, b) -> a + b).orElseThrow());
                boolean blank = c == ' ' || c == '\t' || c == '\n';
                boolean digital = '0' <= c && c <= '9';
                boolean letter = ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z') || c == '_';
                boolean semicolon = c == ';';
                int nextState = switch (state) {
                    case 0 -> switch (c) {
                        case '*' -> 18;
                        case '=' -> 21;
                        case '\"' -> 24;
                        case '(' -> 26;
                        case ')' -> 27;
                        case ';' -> 28;
                        case '+' -> 29;
                        case '-' -> 30;
                        case '/' -> 31;
                        case ',' -> 32;
                        default -> {
                            if (blank) {
                                yield 0;
                            }
                            if (digital) {
                                yield 16;
                            }
                            if (letter) {
                                yield 14;
                            }
                            yield -1;
                        }
                    };
                    case 14 -> (letter || digital) ? 14 : 15;
                    case 16 -> digital ? 16 : 17;
                    case 18 -> c == '*' ? 19 : 20;
                    case 21 -> c == '=' ? 22 : 23;
                    case 24 -> (letter || digital) ? 24 : c == '\"' ? 25 : -1;
                    default -> accepts.contains(state) ? 0 : -1;
                };
                switch (state) {
                    case 0:
                        if (letter) {
                            idCode.append(c);
                        }
                        if (digital) {
                            number.append(c);
                        }
                        break;
                    case 14:
                        if (nextState == 14) {
                            idCode.append(c);
                        }
                    default:
                        break;
                }
                if (accepts.contains(nextState)) {
                    System.out.printf("accept [%d] id=%s, num=%s\n", nextState, idCode, number);
                    tokens.add(switch (nextState) {
                        case 15 -> {
                            final var s = idCode.toString();
                            idCode.setLength(0);
                            if (!keyWords.contains(s) && !symbolTable.has(s)) {
                                symbolTable.add(s);
                            }
                            yield keyWords.contains(s) ? Token.simple(s) : Token.normal("id", s);
                        }
                        case 17 -> {
                            final var token = Token.normal("IntConst", number.toString());
                            number.setLength(0);
                            yield token;
                        }
                        case 19 -> Token.simple("**");
                        case 20 -> Token.simple("*");
                        case 22 -> Token.simple("==");
                        case 23 -> Token.simple("=");
                        case 26 -> Token.simple("(");
                        case 27 -> Token.simple(")");
                        case 28 -> Token.simple("Semicolon");
                        case 29 -> Token.simple("+");
                        case 30 -> Token.simple("-");
                        case 31 -> Token.simple("/");
                        case 32 -> Token.simple(",");
                        default -> throw new RuntimeException(String.format(ErrorDescription.LEX_NO_STATE, state));
                    });
                }
                if (nextState != 28 && semicolon) {
                    System.out.printf("accept [%d] Semicolon\n", nextState);
                    tokens.add(Token.simple("Semicolon"));
                }
                iterator.next();
                state = nextState;
                assert state > 0;
            }
            state = 0;
        }
        System.out.println("accept [$] Done");
        tokens.add(Token.eof());
    }

    /**
     * 获得词法分析的结果, 保证在调用了 run 方法之后调用
     *
     * @return Token 列表
     */
    public Iterable<Token> getTokens() {
        // 从词法分析过程中获取 Token 列表
        // 词法分析过程可以使用 Stream 或 Iterator 实现按需分析
        // 亦可以直接分析完整个文件
        // 总之实现过程能转化为一列表即可
        // throw new NotImplementedException();
        return tokens;
    }

    public void dumpTokens(String path) {
        FileUtils.writeLines(
                path,
                StreamSupport.stream(getTokens().spliterator(), false).map(Token::toString).toList()
        );
    }


}
