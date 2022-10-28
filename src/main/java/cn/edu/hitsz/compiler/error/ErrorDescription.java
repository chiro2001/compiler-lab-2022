package cn.edu.hitsz.compiler.error;

/**
 * 描述错误信息
 *
 * @author chiro
 */
public class ErrorDescription {
    public final static String NO_SYMBOL = "No such symbol: %s";
    public final static String HAS_SYMBOL = "Duplicated symbol: %s";
    public final static String LEX_NO_STATE = "No such DFA State: %d";

    public final static String NO_INSTR = "No such instruction: %s";
}
