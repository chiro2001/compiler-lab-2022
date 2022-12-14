package cn.edu.hitsz.compiler.asm;

import cn.edu.hitsz.compiler.RunConfigs;
import cn.edu.hitsz.compiler.error.ErrorDescription;
import cn.edu.hitsz.compiler.ir.*;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.*;
import java.util.stream.Collectors;


/**
 * 实验四: 实现汇编生成
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
    protected List<Instruction> ir = null;
    protected final List<String> assemblyCode = new LinkedList<>();
    protected final List<IRVariable> regMap = new ArrayList<>();
    protected final Map<IRValue, Integer> variableMap = new HashMap<>();
    protected final Map<InstructionKind, String> asmTemplates = Map.of(
            InstructionKind.MOV, "mv %rd, %rs1",
            InstructionKind.MUL, "mul %rd, %rs1, %rs2",
            InstructionKind.ADD, "add %rd, %rs1, %rs2",
            InstructionKind.SUB, "sub %rd, %rs1, %rs2",
            InstructionKind.ADDI, "addi %rd, %rs1, %imm",
            InstructionKind.LW, "lw %rd, %offset(%rs1)",
            InstructionKind.SW, "sw %rs2, %offset(%rs1)"
    );
    // See: https://en.wikichip.org/wiki/risc-v/registers
    static public List<String> regNames = List.of(
            "zero", "ra", "sp", "gp", "tp",
            "t0", "t1", "t2", "s0", "s1",
            "a0", "a1", "a2", "a3", "a4", "a5", "a6", "a7",
            "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9", "s10", "s11",
            "t3", "t4", "t5", "t6");
    static public Set<String> notArrangeableRegs = Set.of(
            "zero", "a0", "sp", "gp", "tp", "t6");
    static public List<String> arrangeableRegs = regNames.stream()
            .filter(name -> !notArrangeableRegs.contains(name)).collect(Collectors.toList());
    static protected final IRVariable regRet = IRVariable.named("a0");
    static protected final IRVariable regZero = IRVariable.named("zero");

    /**
     * Regs buffer base address
     */
    static protected final IRVariable regSp = IRVariable.named("sp");
    /**
     * This reg is used to index regs buffer
     */
    static protected final IRVariable regT6 = IRVariable.named("t6");

    // should not touch sp reg in TXT programs, or the arranges will fail!
    public record VariableInBuffer(IRVariable r, boolean valid) {
    }

    static class VariableBuffer extends ArrayList<VariableInBuffer> {
        @Override
        public boolean add(VariableInBuffer variableInBuffer) {
            if (has(variableInBuffer.r)) {
                if (variableInBuffer.valid) {
                    setValid(variableInBuffer.r);
                } else {
                    setInvalid(variableInBuffer.r);
                }
                return true;
            } else {
                return super.add(variableInBuffer);
            }
        }

        public boolean has(IRVariable r) {
            if (isEmpty()) {
                return false;
            }
            return this.stream().map(i -> i.r == r).reduce((a, b) -> a || b).get();
        }

        public boolean hasValid(IRVariable r) {
            if (!has(r)) {
                return false;
            }
            if (isEmpty()) {
                return false;
            }
            return this.stream()
                    .filter(i -> i.r != r)
                    .map(i -> i.valid)
                    .reduce((a, b) -> a | b)
                    .orElse(false);
        }

        public int indexOf(IRVariable r) {
            for (int i = 0; i < size(); i++) {
                if (get(i).r == r) {
                    return i;
                }
            }
            return -1;
        }

        public void setValid(IRVariable r) {
            for (int i = 0; i < size(); i++) {
                if (get(i).r == r) {
                    set(i, new VariableInBuffer(get(i).r, true));
                }
            }
        }

        public void setInvalid(IRVariable r) {
            for (int i = 0; i < size(); i++) {
                if (get(i).r == r) {
                    set(i, new VariableInBuffer(get(i).r, false));
                }
            }
        }

        public List<IRVariable> listInRegs() {
            return this.stream().filter(i -> i.valid).map(i -> i.r).collect(Collectors.toList());
        }

        public List<IRVariable> listInMems() {
            return this.stream().filter(i -> !i.valid).map(i -> i.r).collect(Collectors.toList());
        }
    }

    protected void displayDebug() {
        if (RunConfigs.DEBUG) {
            System.out.println("/=== cut here ===\\");
            var links = arrangeableRegs.stream().map(n -> "%s->%s".formatted(n, regMap.get(regNames.indexOf(n)))).collect(Collectors.toList());
            System.out.printf("links(%d): %s\n", links.size(), String.join(", ", links));
            System.out.printf("rlinks(%d): %s\n", variableMap.size(), variableMap);
            var regs = variableBuffer.stream().filter(i -> i.valid).map(i -> i.r).collect(Collectors.toSet());
            System.out.printf("in regs(%d): %s\n", regs.size(), regs);
            var mems = variableBuffer.stream().filter(i -> !i.valid).map(i -> i.r).collect(Collectors.toSet());
            System.out.printf("in mems(%d): %s\n", mems.size(), mems);
            System.out.println("\\=== cut done ===/");
        }
    }

    /**
     * mark a variable in reg+mem. valid=1 then in regs.
     */
    final protected VariableBuffer variableBuffer = new VariableBuffer();

    protected void saveVariableToStack(IRVariable r) {
        int offsetInBuf = variableBuffer.indexOf(r);
        int offset = offsetInBuf < 0 ? variableBuffer.size() : offsetInBuf;
        applySave(InstructionKind.SW, r, regSp, offset * 4);
        if (offsetInBuf < 0) {
            variableBuffer.add(new VariableInBuffer(r, true));
        } else {
            variableBuffer.setInvalid(r);
        }
        // TODO: setup variable sizes
        // sp is tail of stack
    }

    protected void loadVariableFromStack(IRVariable r) {
        int offset = variableBuffer.indexOf(r);
        assert offset >= 0;
        variableBuffer.setValid(r);
        applyLoad(InstructionKind.LW, r, regSp, offset);
    }

    protected void updateArrangeReg(IRVariable r) {
        if (RunConfigs.DEBUG) {
            System.out.printf("updateArrangeReg(%s)\n", r);
        }
        // if (!variableMap.containsKey(r)) {
        if (!variableBuffer.has(r)) {
            if (RunConfigs.DEBUG) {
                System.out.printf("%s not in reg+mem\n", r);
            }
            // add new variable in regs+mem
            if (variableBuffer.stream().filter(i -> i.valid).toList().size() >= arrangeableRegs.size()) {
                if (RunConfigs.DEBUG) {
                    System.out.println("regs used up, select one and push to sp buffer");
                }
                // simply find first not saved reg
                var foundIndex = 0;
                for (var n : arrangeableRegs) {
                    var index = regNames.indexOf(n);
                    var rNext = regMap.get(index);
                    // System.out.printf("regMap.get(%2d) = %s...", index, regMap.get(index));
                    if (variableBuffer.hasValid(rNext)) {
                        // System.out.println("in regs");
                        foundIndex = index;
                        break;
                    } else {
                        // if (variableBuffer.has(rNext)) {
                        //     System.out.println("in mem not in regs");
                        // } else {
                        //     System.out.println("not in regs+mem");
                        // }
                    }
                }
                if (foundIndex == 0) {
                    throw new RuntimeException("cannot found free regs!");
                }
                if (RunConfigs.DEBUG) {
                    System.out.printf("will bind to reg %s, kick out %s to mem\n", regNames.get(foundIndex), regMap.get(foundIndex));
                }
                displayDebug();
                var newReg = regMap.get(foundIndex);
                saveVariableToStack(newReg);
                assignVariable(foundIndex, r);
                applyIgnoreArrange(InstructionKind.MOV, newReg, r, null, null);
                displayDebug();
                System.out.printf("%s bind to %s done\n", r, regNames.get(foundIndex));
            } else {
                if (RunConfigs.DEBUG) {
                    System.out.println("have free regs");
                }
                displayDebug();
                var foundIndex = 0;
                for (var n : arrangeableRegs) {
                    var index = regNames.indexOf(n);
                    if (variableBuffer.hasValid(regMap.get(index))) {
                        foundIndex = index;
                        break;
                    }
                }
                if (RunConfigs.DEBUG) {
                    System.out.printf("will bind to reg %s\n", regNames.get(foundIndex));
                }
                assert foundIndex > 0;
                variableBuffer.add(new VariableInBuffer(r, true));
                assignVariable(foundIndex, r);
                displayDebug();
            }
            // load variable from stack
        } else {
            if (RunConfigs.DEBUG) {
                System.out.printf("%s in reg+mem\n", r);
            }
            if (!variableBuffer.hasValid(r)) {
                if (RunConfigs.DEBUG) {
                    System.out.printf("%s not in regs now, need to load from mem\n", r);
                }
                loadVariableFromStack(r);
            } else {
                if (RunConfigs.DEBUG) {
                    System.out.printf("%s in reg %s\n", r, regNames.get(variableMap.get(r)));
                }
            }
        }
    }

    protected void updateArrangeRegs(IRValue... r) {
        Arrays.stream(r).toList().stream()
                .filter(Objects::nonNull)
                .filter(IRValue::isIRVariable)
                .filter(i -> !Set.of(regZero, regT6, regSp, regRet).contains((IRVariable) i))
                .map(i -> (IRVariable) i)
                .forEach(this::updateArrangeReg);
    }

    protected String applyInstructionToAsm(InstructionKind kind, IRVariable rd, IRValue rs1, IRValue rs2, IRImmediate imm) {
        return applyInstructionToAsm(kind, rd, rs1, rs2, imm, false);
    }

    protected String applyInstructionToAsm(InstructionKind kind, IRVariable rd, IRValue rs1, IRValue rs2, IRImmediate imm, boolean ignoreArrange) {
        if (RunConfigs.DEBUG) {
            System.out.printf("applyInstructionToAsm(%s, rd=%s, rs1=%s, rs2=%s, imm=%s)\n", kind, rd, rs1, rs2, imm);
        }
        if (!ignoreArrange) {
            updateArrangeRegs(rd, rs1, rs2);
        }
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
                try {
                    asm = asm.replaceAll("%rs1", regNames.get(variableMap.get(r)));
                } catch (NullPointerException e) {
                    throw e;
                }
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

    protected void apply(InstructionKind kind, IRVariable rd, IRValue rs1, IRValue rs2, IRImmediate imm) {
        assemblyCode.add(applyInstructionToAsm(kind, rd, rs1, rs2, imm));
    }

    protected void applyIgnoreArrange(InstructionKind kind, IRVariable rd, IRValue rs1, IRValue rs2, IRImmediate imm) {
        assemblyCode.add(applyInstructionToAsm(kind, rd, rs1, rs2, imm, true));
    }

    protected void applyLoad(InstructionKind kind, IRValue rd, IRValue rs1, int offset) {
        updateArrangeRegs(rd, rs1);
        assemblyCode.add(asmTemplates.get(kind)
                .replaceAll("%rd", regNames.get(variableMap.get(rd)))
                .replaceAll("%rs1", regNames.get(variableMap.get(rs1)))
                .replaceAll("%offset", String.valueOf(offset))
        );
    }

    protected void applySave(InstructionKind kind, IRValue rs1, IRValue rs2, int offset) {
        updateArrangeRegs(rs1, rs2);
        assemblyCode.add(asmTemplates.get(kind)
                .replaceAll("%rs1", regNames.get(variableMap.get(rs1)))
                .replaceAll("%rs2", regNames.get(variableMap.get(rs2)))
                .replaceAll("%offset", String.valueOf(offset))
        );
    }

    protected void assignVariable(int index, IRVariable variable) {
        regMap.set(index, variable);
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
        for (var c : originInstructions) {
            if (RunConfigs.DEBUG) {
                System.out.println(c);
            }
        }
        ir = originInstructions;
        for (int i = 0; i < 32; i++) {
            regMap.add(null);
        }
        assignVariable(regNames.indexOf(regRet.getName()), regRet);
        assignVariable(regNames.indexOf(regZero.getName()), regZero);
        assignVariable(regNames.indexOf(regSp.getName()), regSp);
        assignVariable(regNames.indexOf(regT6.getName()), regT6);
        // pre-arrange
        List<IRVariable> variables = new HashSet<>(ir.stream().map(insn -> {
            Set<IRVariable> vars = insn.getOperators().stream()
                    .filter(IRValue::isIRVariable)
                    .map(i -> (IRVariable) i)
                    .collect(Collectors.toSet());
            if (Set.of(InstructionKind.ADD, InstructionKind.SUB, InstructionKind.MUL, InstructionKind.MOV).contains(insn.getKind())) {
                vars.add(insn.getResult());
            }
            return vars;
        }).reduce(new HashSet<>(), (a, b) -> {
            a.addAll(b);
            return a;
        })).stream().toList();
        var initRegSize = Math.min(arrangeableRegs.size(), variables.size());
        if (RunConfigs.DEBUG) {
            System.out.println("initRegSize = " + initRegSize);
        }
        for (int i = 0; i < initRegSize; i++) {
            var r = variables.get(i);
            var reg = arrangeableRegs.get(i);
            if (RunConfigs.DEBUG) {
                System.out.printf("[%2d] bind %s\t to reg %s\n", i, r, reg);
            }
            assignVariable(regNames.indexOf(reg), r);
            variableBuffer.add(new VariableInBuffer(r, true));
            assert variableMap.containsKey(r);
        }
        if (RunConfigs.DEBUG) {
            System.out.println("Load IR Done.");
        }
        // Setup regs buffer base address
        apply(InstructionKind.ADDI, regSp, regZero, null, IRImmediate.of(RunConfigs.REGS_BUFFER_BASE));
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
        for (var insn : ir) {
            if (RunConfigs.DEBUG) {
                System.out.printf("Generating: %s\n", insn);
            }
            switch (insn.getKind()) {
                case MOV -> {
                    if (insn.getFrom().isImmediate()) {
                        apply(InstructionKind.ADDI, insn.getResult(), regZero, null, (IRImmediate) insn.getFrom());
                    } else {
                        apply(insn.getKind(), insn.getResult(), insn.getFrom(), null, null);
                    }
                }
                case ADD, SUB, MUL -> {
                    var isImm = List.of(insn.getLHS().isImmediate(), insn.getRHS().isImmediate());
                    var hasImm = isImm.get(0) || isImm.get(1);
                    var rd = insn.getResult();
                    if (hasImm) {
                        var imm = (IRImmediate) (isImm.get(0) ? insn.getLHS() : insn.getRHS());
                        var r2 = (IRVariable) (isImm.get(1) ? insn.getLHS() : insn.getRHS());
                        if (insn.getKind() != InstructionKind.ADD) {
                            // SUB/MUL has not imm, generate a template to hold it
                            var temp = IRVariable.temp();
                            // var temp = IRVariable.named("tmp_imm");
                            apply(InstructionKind.ADDI, temp, regZero, null, imm);
                            if (isImm.get(0)) {
                                apply(insn.getKind(), rd, temp, insn.getRHS(), null);
                            } else {
                                apply(insn.getKind(), rd, insn.getLHS(), temp, null);
                            }
                        } else {
                            apply(InstructionKind.ADDI, rd, r2, null, imm);
                        }
                    } else {
                        apply(insn.getKind(), rd, insn.getLHS(), insn.getRHS(), null);
                    }
                }
                case LOAD -> {
                    applyLoad(insn.getKind(), insn.getResult(), insn.getLHS(), 0);
                }
                case SAVE -> {
                    applySave(insn.getKind(), insn.getLHS(), insn.getRHS(), 0);
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
        FileUtils.writeFile(path, ".text\n    " + String.join("\n    ", assemblyCode));
    }
}

