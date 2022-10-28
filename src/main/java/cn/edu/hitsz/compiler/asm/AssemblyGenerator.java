package cn.edu.hitsz.compiler.asm;

import cn.edu.hitsz.compiler.error.ErrorDescription;
import cn.edu.hitsz.compiler.ir.*;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.*;
import java.util.stream.Collectors;


/**
 * TODO: 实验四: 实现汇编生成
 * <br>
 * 在编译器的整体框架中, 代码生成可以称作后端, 而前面的所有工作都可称为前端.
 * <br>
 * 在前端完成的所有工作中, 都是与目标平台无关的, 而后端的工作为将前端生成的目标平台无关信息
 * 根据目标平台生成汇编代码. 前后端的分离有利于实现编译器面向不同平台生成汇编代码. 由于前后
 * 端分离的原因, 有可能前端生成的中间代码并不符合目标平台的汇编代码特点. 具体到本项目你可以
 * 尝试加入一个方法将中间代码调整为更接近 risc-v 汇编的形式, 这样会有利于汇编代码的生成.
 * <br>
 * 为保证实现上的自由, 框架中并未对后端提供基建, 在具体实现时可自行设计相关数据结构.
 *
 * @see AssemblyGenerator#run() 代码生成与寄存器分配
 */
public class AssemblyGenerator {
    private List<Instruction> ir = null;
    private List<String> assemblyCode = new LinkedList<>();
    private List<IRValue> regMap = new ArrayList<>();
    private Map<IRVariable, Integer> variableMap = new HashMap<>();
    private Map<InstructionKind, String> asmTemplates = Map.of(
            InstructionKind.MOV, "mv %rd, %rs1",
            InstructionKind.MUL, "mul %rd, %rs1, %rs2",
            InstructionKind.ADD, "add %rd, %rs1, %rs2",
            InstructionKind.SUB, "sub %rd, %rs1, %rs2",
            InstructionKind.ADDI, "addi %rd, %rs1, %imm",
            InstructionKind.SUBI, "sub %rd, %rs1, %imm"
    );
    // See: https://en.wikichip.org/wiki/risc-v/registers
    static public List<String> regNames = List.of(
            "zero", "ra", "sp", "gp", "tp",
            "t0", "t1", "t2", "s0", "s1",
            "a0", "a1", "a2", "a3", "a4", "a5", "a6", "a7",
            "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9", "s10", "s11",
            "t3", "t4", "t5", "t6");
    static private IRVariable regRet = IRVariable.named("a0");
    static private IRVariable regZero = IRVariable.named("zero");

    private String applyInstructionToAsm(InstructionKind kind, IRVariable rd, IRValue rs1, IRValue rs2, IRImmediate imm) {
        var asm = asmTemplates.get(kind);
        // TODO: split too big imm
        if (imm != null) {
            asm = asm.replaceAll("%imm", "" + imm.getValue());
        }
        if (rd != null) {
            asm = asm.replaceAll("%rd", regNames.get(variableMap.get(rd)));
        }
        if (rs1 != null) {
            if (rs1 instanceof IRVariable r) {
                asm = asm.replaceAll("%rs1", regNames.get(variableMap.get(r)));
            } else {
                throw new RuntimeException();
            }
        }
        if (rs2 != null) {
            if (rs2 instanceof IRVariable r) {
                asm = asm.replaceAll("%rs2", regNames.get(variableMap.get(r)));
            } else {
                throw new RuntimeException();
            }
        }
        return asm;
    }
    
    private void apply(InstructionKind kind, IRVariable rd, IRValue rs1, IRValue rs2, IRImmediate imm) {
        assemblyCode.add(applyInstructionToAsm(kind, rd, rs1, rs2, imm));
    }

    private void assignVariable(int index, IRVariable variable) {
        assert(regMap.get(index) == null);
        regMap.set(index, variable);
        assert(!variableMap.containsKey(variable) && !variableMap.containsValue(index));
        variableMap.put(variable, index);
    }

    /**
     * 加载前端提供的中间代码
     * <br>
     * 视具体实现而定, 在加载中或加载后会生成一些在代码生成中会用到的信息. 如变量的引用
     * 信息. 这些信息可以通过简单的映射维护, 或者自行增加记录信息的数据结构.
     *
     * @param originInstructions 前端提供的中间代码
     */
    public void loadIR(List<Instruction> originInstructions) {
        // TODO: 读入前端提供的中间代码并生成所需要的信息
        // throw new NotImplementedException();
        for (int i = 0; i < 32; i++) {
            regMap.add(null);
        }
        assignVariable(regNames.indexOf(regRet.getName()), regRet);
        assignVariable(regNames.indexOf(regZero.getName()), regZero);
        ir = originInstructions;
    }


    /**
     * 执行代码生成.
     * <br>
     * 根据理论课的做法, 在代码生成时同时完成寄存器分配的工作. 若你觉得这样的做法不好,
     * 也可以将寄存器分配和代码生成分开进行.
     * <br>
     * 提示: 寄存器分配中需要的信息较多, 关于全局的与代码生成过程无关的信息建议在代码生
     * 成前完成建立, 与代码生成的过程相关的信息可自行设计数据结构进行记录并动态维护.
     */
    public void run() {
        // TODO: 执行寄存器分配与代码生成
        // throw new NotImplementedException();
        for (var insn : ir) {
            switch (insn.getKind()) {
                case MOV -> {
                    if (insn.getFrom().isImmediate()) {
                        apply(InstructionKind.ADDI, insn.getResult(), null, null, (IRImmediate) insn.getFrom());
                    }
                    apply(insn.getKind(), insn.getResult(), insn.getFrom(), null, null);
                }
                case ADD, SUB, MUL -> {
                    if (insn.getRHS() instanceof IRImmediate imm) {
                        var immKind = Map.of(
                                InstructionKind.ADD, InstructionKind.ADDI,
                                InstructionKind.SUB, InstructionKind.SUBI);
                        apply(immKind.get(insn.getKind()), insn.getResult(), insn.getLHS(), null, imm);
                    } else {
                        apply(insn.getKind(), insn.getResult(), insn.getLHS(), insn.getRHS(), null);
                    }
                }
                case RET -> {
                    apply(InstructionKind.MOV, regRet, insn.getReturnValue(), null, null);
                }
                default -> {
                    throw new RuntimeException(ErrorDescription.NO_INSTR.formatted(insn));
                }
            }
        }
    }


    /**
     * 输出汇编代码到文件
     *
     * @param path 输出文件路径
     */
    public void dump(String path) {
        FileUtils.writeFile(path, String.join("", assemblyCode));
    }
}

