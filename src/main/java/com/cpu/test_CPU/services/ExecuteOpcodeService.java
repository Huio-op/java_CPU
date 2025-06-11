package com.cpu.test_CPU.services;

import com.cpu.test_CPU.model.JumpPoint;
import com.cpu.test_CPU.model.Opcodes;
import com.cpu.test_CPU.model.Registers;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.function.BiFunction;

@Service
public class ExecuteOpcodeService {

  public boolean execute(Opcodes op,
                         ArrayList<String> args,
                         StringBuilder response,
                         String[][] memoryRef,
                         Map<String, JumpPoint> jumpMap,
                         BiFunction<Integer, Integer, Void> jumpFunction,
                         JumpPoint currentReturnPoint
  ) {
    switch (op) {
      case NOOP: {
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "NOOP Opcode not implemented");
      }
      case MOV: {
        this.doMov(args.get(0), args.get(1));
        break;
      }
      case LOAD: {
        this.doLoad(args.get(0), args.get(1), memoryRef);
        break;
      }
      case SAVE: {
        this.doSave(args.get(0), args.get(1), memoryRef);
        break;
      }
      case INP: {
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "INP Opcode not implemented");
      }
      case OUT: {
        this.doOut(args.get(0), response);
        break;
      }
      case ADD: {
        this.doAdd(args.get(0), args.get(1));
        break;
      }
      case SUB: {
        this.doSub(args.get(0), args.get(1));
        break;
      }
      case MUL: {
        this.doMul(args.get(0), args.get(1));
        break;
      }
      case DIV: {
        this.doDiv(args.get(0), args.get(1));
        break;
      }
      case JGT: {
        return this.doJgt(args.get(0), args.get(1), args.get(2), jumpMap, jumpFunction);
      }
      case JLT: {
        return this.doJlt(args.get(0), args.get(1), args.get(2), jumpMap, jumpFunction);
      }
      case JGE: {
        return this.doJge(args.get(0), args.get(1), args.get(2), jumpMap, jumpFunction);
      }
      case JLE: {
        return this.doJle(args.get(0), args.get(1), args.get(2), jumpMap, jumpFunction);
      }
      case JEQ: {
        return this.doJeq(args.get(0), args.get(1), args.get(2), jumpMap, jumpFunction);
      }
      case JMP: {
        this.doJmp(args.get(0), jumpMap, jumpFunction);
        break;
      }
      case CPY: {
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, op.name() + " Opcode not implemented");
      }
      case DEF: {
        // Don't need to do anything when defining function
        break;
      }
      case RET: {
        this.doRet(currentReturnPoint, jumpFunction);
        break;
      }
      case HALT: {
        // Don't need to do anything when halting
        break;
      }
      default: {
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, op.name() + " Opcode not implemented");
      }
    }
    return true;
  }

  private void doMov(String value, String registerCode) {
    final Registers register = this.getRegisterByCode(registerCode);
    // Substring to remove de 0x in the start
    final int intValue = value.startsWith("0x")
      ? Integer.parseInt(value.substring(2), 16)
      : Integer.parseInt(value);

    register.getStack().push(String.valueOf(intValue));
  }

  private void doLoad(String memoryAddress, String registerCode, String[][] memoryRef) {
    final Registers register = getRegisterByCode(registerCode);

    final int[] memoryAddresses = getAddressesFromHex(memoryAddress);

    final String memoryValue = memoryRef[memoryAddresses[0]][memoryAddresses[1]];
    register.getStack().push(memoryValue);
  }

  private void doSave(String registerCode, String memoryAddress, String[][] memoryRef) {
    final Registers register = getRegisterByCode(registerCode);

    final int[] memoryAddresses = getAddressesFromHex(memoryAddress);

    memoryRef[memoryAddresses[0]][memoryAddresses[1]] = register.getStack().pop();
  }

  private void doOut(String var, StringBuilder response) {
    final Registers register = getRegisterByCode(var);
    response.append(register.getStack().pop());
    response.append("\n");
  }

  private void doAdd(String registerCode1, String registerCode2) {
    this.executeOperation(registerCode1, registerCode2, (int1, int2) -> String.valueOf(int1 + int2));
  }

  private void doSub(String registerCode1, String registerCode2) {
    this.executeOperation(registerCode1, registerCode2, (int1, int2) -> String.valueOf(int1 - int2));
  }

  private void doMul(String registerCode1, String registerCode2) {
    this.executeOperation(registerCode1, registerCode2, (int1, int2) -> String.valueOf(int1 * int2));
  }

  private void doDiv(String registerCode1, String registerCode2) {
    // TODO: Doesnt work with decimal numbers
    this.executeOperation(registerCode1, registerCode2, (int1, int2) -> String.valueOf(int1 / int2));
  }

  private boolean doJgt(String registerCode1, String registerCode2, String jumpPointHex, Map<String, JumpPoint> jumpMap, BiFunction<Integer, Integer, Void> jumpFunction) {
    return this.executeComparison(jumpPointHex, jumpMap, jumpFunction, registerCode1, registerCode2, (int1, int2) -> int1 > int2);
  }

  private boolean doJlt(String registerCode1, String registerCode2, String jumpPointHex, Map<String, JumpPoint> jumpMap, BiFunction<Integer, Integer, Void> jumpFunction) {
    return this.executeComparison(jumpPointHex, jumpMap, jumpFunction, registerCode1, registerCode2, (int1, int2) -> int1 < int2);
  }

  private boolean doJge(String registerCode1, String registerCode2, String jumpPointHex, Map<String, JumpPoint> jumpMap, BiFunction<Integer, Integer, Void> jumpFunction) {
    return this.executeComparison(jumpPointHex, jumpMap, jumpFunction, registerCode1, registerCode2, (int1, int2) -> int1 >= int2);
  }

  private boolean doJle(String registerCode1, String registerCode2, String jumpPointHex, Map<String, JumpPoint> jumpMap, BiFunction<Integer, Integer, Void> jumpFunction) {
    return this.executeComparison(jumpPointHex, jumpMap, jumpFunction, registerCode1, registerCode2, (int1, int2) -> int1 <= int2);
  }

  private boolean doJeq(String registerCode1, String registerCode2, String jumpPointHex, Map<String, JumpPoint> jumpMap, BiFunction<Integer, Integer, Void> jumpFunction) {
    return this.executeComparison(jumpPointHex, jumpMap, jumpFunction, registerCode1, registerCode2, Objects::equals);
  }

  private void doJmp(String jumpPointHex, Map<String, JumpPoint> jumpMap, BiFunction<Integer, Integer, Void> jumpFunction) {
    this.executeJump(jumpPointHex, jumpMap, jumpFunction);
  }

  private void doRet(JumpPoint returnPoint, BiFunction<Integer, Integer, Void> jumpFunction) {
    jumpFunction.apply(returnPoint.rowOrigin(), returnPoint.colOrigin());
  }

  private Registers getRegisterByCode(String registerCode) {
    final Optional<Registers> registerOptional = Arrays.stream(Registers.values()).filter(reg -> reg.getHexCode().equals(registerCode)).findFirst();
    if (registerOptional.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Register not found");
    }
    return registerOptional.get();
  }

  private int[] getAddressesFromHex(String hexAddress) {
    final int[] addresses = new int[2];

    final char[] chars = hexAddress.substring(2).toCharArray();
    for (int i = 0; i < chars.length; i++) {
      addresses[i] = Integer.valueOf(String.valueOf(chars[i]), 15);
    }
    return addresses;
  }

  // Executes an operation on two registers
  private void executeOperation(String registerCode1, String registerCode2, BiFunction<Integer, Integer, String> operation) {

    final Registers register1 = getRegisterByCode(registerCode1);
    final Registers register2 = getRegisterByCode(registerCode2);

    final String regVal1 = register1.getStack().pop();
    final String regVal2 = register2.getStack().pop();
    try {
      final Integer intVal1 = Integer.parseInt(regVal1);
      final Integer intVal2 = Integer.parseInt(regVal2);

      final String result = operation.apply(intVal1, intVal2);
      this.doMov(result, registerCode1);
    } catch (NumberFormatException e) {
      throw new RuntimeException("Value on register to add is not an integer!", e);
    }
  }

  private void executeJump(String jumpPointHex, Map<String, JumpPoint> jumpMap, BiFunction<Integer, Integer, Void> jumpFunction) {
    final JumpPoint jumpPoint = jumpMap.get(jumpPointHex);
    if (jumpPoint == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Jump point not found!");
    }

    jumpFunction.apply(jumpPoint.rowOrigin(), jumpPoint.colOrigin());
  }

  private boolean executeComparison(String jumpPointHex, Map<String, JumpPoint> jumpMap, BiFunction<Integer, Integer, Void> jumpFunction, String registerCode1, String registerCode2, BiFunction<Integer, Integer, Boolean> comparison) {
    final Registers register1 = getRegisterByCode(registerCode1);
    final Registers register2 = getRegisterByCode(registerCode2);

    final String regVal1 = register1.getStack().pop();
    final String regVal2 = register2.getStack().pop();
    try {
      final Integer intVal1 = Integer.parseInt(regVal1);
      final Integer intVal2 = Integer.parseInt(regVal2);

      final boolean comparisonResult = comparison.apply(intVal1, intVal2);
      if (comparisonResult) {
        this.executeJump(jumpPointHex, jumpMap, jumpFunction);
        return true;
      }
    } catch (NumberFormatException e) {
      throw new RuntimeException("Value on register to add is not an integer!", e);
    }
    return false;
  }
}
