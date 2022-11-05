package cn.edu.hitsz.compiler.asm;

import cn.edu.hitsz.compiler.RunConfigs;
import cn.edu.hitsz.compiler.ir.*;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.stream.Collectors;

public class AsmTests {
    @Test
    public void regsArrangeTest() {
        class AssemblyGeneratorTest extends AssemblyGenerator {
            public void insertVariable(IRVariable r) {
                var regs = variableBuffer.listInRegs();
                var mems = variableBuffer.listInMems();
                if (regs.size() >= arrangeableRegs.size()) {
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
            }

            @Override
            public void run() {
                loadIR(new LinkedList<>());
                for (int i = 0; i < 64; i++) {
                    // variableBuffer.add(new VariableInBuffer(IRVariable.temp(), true));
                    // displayDebug();
                    insertVariable(IRVariable.temp());
                }
            }
        }

        new AssemblyGeneratorTest().run();
    }
}

