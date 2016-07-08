/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.graal.lir.alloc;

import java.util.List;

import com.oracle.graal.compiler.common.LIRKind;
import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.LIRInsertionBuffer;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.StandardOp;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.framemap.FrameMapBuilder;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.lir.phases.PreAllocationOptimizationPhase;
import com.oracle.graal.lir.util.RegisterMap;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterArray;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.PlatformKind;

public class SaveCalleeSaveRegisters extends PreAllocationOptimizationPhase {

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, List<? extends AbstractBlockBase<?>> codeEmittingOrder, List<? extends AbstractBlockBase<?>> linearScanOrder,
                    PreAllocationOptimizationContext context) {
        FrameMapBuilder frameMapBuilder = lirGenRes.getFrameMapBuilder();
        RegisterArray calleeSaveRegisters = frameMapBuilder.getCodeCache().getRegisterConfig().getCalleeSaveRegisters();
        if (calleeSaveRegisters == null || calleeSaveRegisters.size() == 0) {
            return;
        }
        LIR lir = lirGenRes.getLIR();
        RegisterMap<Variable> savedRegisters = saveAtEntry(lir, context.lirGen, calleeSaveRegisters, target.arch);

        for (AbstractBlockBase<?> block : lir.codeEmittingOrder()) {
            if (block == null) {
                continue;
            }
            if (block.getSuccessorCount() == 0) {
                restoreAtExit(lir, context.lirGen.getSpillMoveFactory(), savedRegisters, block);
            }
        }
    }

    private static RegisterMap<Variable> saveAtEntry(LIR lir, LIRGeneratorTool lirGen, RegisterArray calleeSaveRegisters, Architecture arch) {
        AbstractBlockBase<?> startBlock = lir.getControlFlowGraph().getStartBlock();
        List<LIRInstruction> instructions = lir.getLIRforBlock(startBlock);
        int insertionIndex = 1;
        LIRInsertionBuffer buffer = new LIRInsertionBuffer();
        buffer.init(instructions);
        StandardOp.LabelOp entry = (StandardOp.LabelOp) instructions.get(insertionIndex - 1);
        RegisterValue[] savedRegisterValues = new RegisterValue[calleeSaveRegisters.size()];
        int savedRegisterValueIndex = 0;
        RegisterMap<Variable> saveMap = new RegisterMap<>(arch);
        for (Register register : calleeSaveRegisters) {
            PlatformKind registerPlatformKind = arch.getLargestStorableKind(register.getRegisterCategory());
            LIRKind lirKind = LIRKind.value(registerPlatformKind);
            RegisterValue registerValue = register.asValue(lirKind);
            Variable saveVariable = lirGen.newVariable(lirKind);
            LIRInstruction save = lirGen.getSpillMoveFactory().createMove(saveVariable, registerValue);
            buffer.append(insertionIndex, save);
            saveMap.put(register, saveVariable);
            savedRegisterValues[savedRegisterValueIndex++] = registerValue;
        }
        entry.addIncomingValues(savedRegisterValues);
        buffer.finish();
        return saveMap;
    }

    private static void restoreAtExit(LIR lir, LIRGeneratorTool.MoveFactory moveFactory, RegisterMap<Variable> calleeSaveRegisters, AbstractBlockBase<?> block) {
        List<LIRInstruction> instructions = lir.getLIRforBlock(block);
        int insertionIndex = instructions.size() - 1;
        LIRInsertionBuffer buffer = new LIRInsertionBuffer();
        buffer.init(instructions);
        assert instructions.get(insertionIndex) instanceof StandardOp.BlockEndOp;
        calleeSaveRegisters.forEach((Register register, Variable saved) -> {
            LIRInstruction restore = moveFactory.createMove(register.asValue(saved.getValueKind()), saved);
            buffer.append(insertionIndex, restore);
        });
        buffer.finish();
    }
}
